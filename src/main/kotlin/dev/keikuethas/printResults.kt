package dev.keikuethas

import dev.keikuethas.dependencies.Diagnostic
import dev.keikuethas.dependencies.ResolutionResult
import dev.keikuethas.dependencies.VersionSource

fun printResults(results: Map<Configuration, ResolutionResult>) {
    for ((cfg, result) in results) {
        println("\n=== ${cfg.packageName}  (${cfg.repositoryType}, ${cfg.address}) ===")

        if (result.resolved.isEmpty() && result.diagnostics.isNotEmpty()) {
            println("  Could not get dependencies:")
            result.diagnostics.forEach { d -> println("    ⚠ ${d.message}${d.coords?.let { " @$it" } ?: ""}") }
            continue
        }

        for (dep in result.resolved) {
            val src = when (val s = dep.versionSource) {
                is VersionSource.Explicit -> "explicit"
                is VersionSource.Managed  -> "managed by ${s.source}"
            }
            println("  ${dep.groupId}:${dep.artifactId}:${dep.resolvedVersion}  [${dep.scope}]  <- $src")
        }

        if (result.unresolved.isNotEmpty()) {
            println("  not resolved (${result.unresolved.size}):")
            result.unresolved.forEach { d -> println("    ? ${d.groupId}:${d.artifactId}") }
        }

        val nonFetchDiagnostics = result.diagnostics.filterNot { it is Diagnostic.FetchFailed }
        if (nonFetchDiagnostics.isNotEmpty()) {
            println("  warnings:")
            nonFetchDiagnostics.forEach { d -> println("     ${d.message}") }
        }
    }
}