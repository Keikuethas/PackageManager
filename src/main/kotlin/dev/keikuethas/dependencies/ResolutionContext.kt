package dev.keikuethas.dependencies

import arrow.core.fold
import java.net.http.HttpClient
import java.time.Duration

data class Limits(
    val maxParentDepth: Int = 20,
    val maxImportDepth: Int = 20,
    val maxLoads: Int = 1000
)

class ResolutionContext(
    repoUrl: String,
    val limits: Limits = Limits(),
    private val maxDepth: Int,
    httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {
    val downloader: PomDownloader = PomDownloader(repoUrl, httpClient)
    val pomCache: MutableMap<String, PomDocument> = mutableMapOf()
    val winnerDeps = mutableMapOf<String, ManagedEntry>()
    val resolved = mutableMapOf<Coordinates, ResolutionResult>()
    var loads: Int = 0
    val diagnostics = mutableListOf<Diagnostic>()

    fun getGraph(coords: Coordinates): Graph =
        generateSequence(buildGraph(coords)) { currentGraph ->
            val (newGraph, changed) = rebuildGraph(currentGraph)
            if (changed) newGraph else null
        }.last()

    private fun rebuildGraph(graph: Graph, depth: Int = 0): Pair<Graph, Boolean> {
        if (depth > maxDepth) return Pair(graph, false)

        return graph.fold(
            initial = Pair(emptyMap(), false),
            operation = { acc, entry ->
                val (rebuilt, changed) = rebuildNode(entry.value, depth)
                Pair(acc.first + (entry.key to rebuilt), changed)
            }
        )
    }

    private fun rebuildNode(node: Node, depth: Int): Pair<Node, Boolean> {
        val coordinate = node.coordinates ?: return Pair(node, false)
        val dep = winnerDeps[coordinate.key] ?: return Pair(node, false)
        val winVersion = dep.resolvedVersion ?: return Pair(node, false)

        return if (winVersion != coordinate.version) {
            val newCoords = coordinate.copy(version = winVersion)
            Pair(
                Node(
                    buildGraph(newCoords, depth),
                    newCoords
                ), true
            )
        } else (node.nodes?.let
        {
            val (rebuiltNodes, changed) = rebuildGraph(it, depth + 1)
            Pair(node.copy(nodes = rebuiltNodes), changed)
        } ?: Pair(node, false))
    }

    private fun buildGraph(
        coords: Coordinates,
        depth: Int = 0,
        callChain: List<Coordinates> = emptyList()
    ): Graph {
        if (depth == maxDepth) return emptyMap()
        if (coords in callChain) {
            diagnostics.add(
                Diagnostic.CycleFound("$coords leads to cycle.", coords, callChain + coords)
            )
            return emptyMap()
        }

        val session = newSession(depth)
        val result: ResolutionResult = resolved.getOrPut(coords) { session.resolve(coords) }
        mergeVersions(session)

        val nodes =
            result.unresolved.associate { it.key to Node(null, null) } +
                    result.resolved.associate {
                        it.key to Node(
                            buildGraph(
                                it.coords,
                                depth + 1,
                                callChain + coords
                            ),
                            it.coords,
                        )
                    }
        return nodes
    }

    private fun mergeVersions(session: ResolutionSession): Boolean {
        var result = false
        val news = session.getResolvedVersions()
        for ((key, value) in news)
            winnerDeps[key]?.let {
                if (value.depth < it.depth) {
                    winnerDeps[key] = value
                    result = true
                }
            } ?: run { winnerDeps[key] = value; result = true }
        return result
    }

    private fun newSession(depth: Int): ResolutionSession = ResolutionSession(this, depth)


}