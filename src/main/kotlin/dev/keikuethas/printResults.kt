package dev.keikuethas

import dev.keikuethas.dependencies.Diagnostic
import dev.keikuethas.dependencies.Graph

fun printResults(
    results: Map<Configuration, Pair<Graph, List<Diagnostic>>>,
    revDepsFor: String?
) {

    val revDeps = mutableListOf<List<String>>()

    for ((cfg, result) in results) {
        if (!cfg.tree) continue
        val (graph, diagnostics) = result
        println("=== ${cfg.packageName} ===")
        if (diagnostics.isNotEmpty()) {
            println("= Diagnostics =")
            println()
            printDiagnostics(diagnostics)
            println()
        }
        println("= Graph =")
        println()
        printTree(graph, 1)
        println()
        println()

        if (revDepsFor != null)
            revDeps += getDependencyChain(graph, revDepsFor, listOf(cfg.packageName))
    }

    if (revDepsFor != null) {
        println("=== Depend on $revDepsFor ===")
        println()
        revDeps.forEach {
            printDepChain(it)
            println()
        }
    }
}

private fun printDepChain(chain: List<String>) {
    chain.asReversed().forEachIndexed { index, string ->
        println("- ".repeat(chain.size - index - 1) + string)
    }
}

private fun getDependencyChain(
    graph:Graph,
    dep: String,
    callChain: List<String> = emptyList()
): List<List<String>> {
    val results = mutableListOf<List<String>>()

    for ((key, value) in graph) {
        val fullName = value.coordinates?.fullKey
        val name = key

        if (dep == name || dep == fullName)
            results += callChain

        val deepResults = value.nodes?.let {
            if (it.isNotEmpty()) {
                getDependencyChain(it, dep, callChain + key)
            } else emptyList()
        } ?: emptyList()

        results += deepResults
    }

    return results
}

private fun printDiagnostics(ds: List<Diagnostic>) {
    for(d in ds) {
        println(d.message)
        println()
    }
}

private fun printTree(
    graph: Graph,
    depth: Int = 0,
) {

    for ((key, value) in graph) {
        val name = value.coordinates?.fullKey ?: key

        print("- ".repeat(depth))
        print(name)
        value.nodes?.let {
            if (it.isNotEmpty()) {
                println(" -> ")
                printTree(it, depth+1)
            } else println()
        } ?: println()
    }
}