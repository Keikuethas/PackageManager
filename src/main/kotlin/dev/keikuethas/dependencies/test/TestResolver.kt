package dev.keikuethas.dependencies.test

import arrow.core.Either
import dev.keikuethas.dependencies.Diagnostic
import dev.keikuethas.dependencies.Graph
import dev.keikuethas.dependencies.Node
import dev.keikuethas.dependencies.emptyGraph
import java.io.File

object TestResolver {

    private val reg = "([A-Z]+)(?:->([A-Z]+))?".toRegex()

    fun resolve(file: File, maxDepth: Int): Pair<Graph, List<Diagnostic>> {
        val content = file.readLines()

        val parseResult = parse(content)
        val direct = parseResult.directDeps
        val deps = parseResult.deps
        val parseDiagnostics = parseResult.diagnostics

        val (graph, buildDiagnostics) = buildGraph(direct, deps, maxDepth)
        val diagnostics = parseDiagnostics + buildDiagnostics

        return (graph to diagnostics)
    }

    private val defaultNode = Node(emptyGraph(), null)

    private fun buildGraph(
        direct: Set<String>,
        deps: Map<String, Set<String>>,
        maxDepth: Int,
        callChain: List<String> = emptyList(),
    ): Pair<Graph, List<Diagnostic>> {

        if (direct.isEmpty() || callChain.size == maxDepth)
            return (emptyGraph() to emptyList())

        val t: List<Pair<Pair<String, Node>, List<Diagnostic>>> = direct.map { directDep ->
            val subDeps = deps[directDep] ?: emptySet()
            if (directDep in callChain)
                (
                        (directDep to defaultNode)
                                to
                                listOf(
                                    Diagnostic.TestCycleFound(
                                        "$directDep leads to cycle.",
                                        callChain
                                    )
                                )
                        )
            else {
                val (subGraph, diag) = buildGraph(subDeps, deps, maxDepth, callChain + directDep)
                val node = (directDep to Node(subGraph, null))

                (node to diag)
            }
        }

        val graph = t.associate { it.first }
        val diagnostics = t.map { it.second }.foldRight(emptyList<Diagnostic>()) { newList, acc -> acc + newList }
        return graph to diagnostics
    }

    private data class ParseResult(
        val directDeps: Set<String>,
        val deps: Map<String, Set<String>>,
        val diagnostics: List<Diagnostic>
    )

    private fun parse(lines: List<String>): ParseResult {
        val diagnostics = mutableListOf<Diagnostic>()
        val deps = mutableMapOf<String, MutableSet<String>>()
        val directDeps = mutableSetOf<String>()

        for (line in lines) {
            if (line.isBlank()) continue

            when (val parsed = parseLine(line)) {

                is Either.Left -> {
                    diagnostics.add(parsed.value)
                }

                is Either.Right -> {
                    val (key, value) = parsed.value
                    val list = deps.getOrPut(key) { mutableSetOf() }
                    value?.let { list.add(it) }
                        ?: directDeps.add(key)
                }
            }
        }

        return ParseResult(directDeps, deps, diagnostics)
    }

    private fun parseLine(line: String): Either<Diagnostic, Pair<String, String?>> {
        val normalized = line.replace(" ", "")
        return reg.matchEntire(normalized)?.destructured?.let {
            val second = it.component2().takeIf { str -> str.isNotBlank() }
            Either.Right(it.component1() to second)
        } ?: Either.Left(Diagnostic.TestParseFailed("Failed to parse \"$line\""))
    }
}