package dev.keikuethas

import dev.keikuethas.dependencies.Diagnostic
import dev.keikuethas.dependencies.Graph

fun printResults(results: Map<Configuration, Pair<Graph, List<Diagnostic>>>) {
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
    }
}

private fun printDiagnostics(ds: List<Diagnostic>) {
    for(d in ds) {
        println(d.message)
        println()
    }
}

private fun printTree(graph: Graph, depth: Int = 0) {
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