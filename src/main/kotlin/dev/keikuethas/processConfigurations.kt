package dev.keikuethas

import dev.keikuethas.dependencies.Diagnostic
import dev.keikuethas.dependencies.Graph
import dev.keikuethas.dependencies.Limits
import dev.keikuethas.dependencies.ResolutionContext
import dev.keikuethas.dependencies.test.TestResolver
import dev.keikuethas.utils.toCoordinates
import dev.keikuethas.utils.toRepoUrl
import java.io.File
import java.nio.file.Path
import kotlin.to

fun processConfigurations(
    configs: List<Configuration>,
    limits: Limits,
    path: Path
): Map<Configuration, Pair<Graph, List<Diagnostic>>>  {
    /** Словарь "Репозиторий : Контекст" */
    val contexts = mutableMapOf<String, ResolutionContext>()

    /** Словарь "Конфигурация : Результат"*/
    val results = linkedMapOf<Configuration, Pair<Graph, List<Diagnostic>>>()

    for (config in configs) {
        when (config.repositoryType) {
            RepositoryType.URL -> {
                val repoUrl = config.toRepoUrl()
                val context = contexts.getOrPut(repoUrl) { ResolutionContext(repoUrl, limits, config.maxDepth) }

                val coords = try {
                    config.toCoordinates()
                } catch (e: IllegalArgumentException) {
                    println("${config.packageName}: ${e.message}")
                    continue
                }

                results[config] = (context.getGraph(coords) to context.diagnostics)
            }

            RepositoryType.TEST -> {
                val file: File = path.resolve(config.address).toFile()
                results[config] = TestResolver.resolve(file, config.maxDepth)
            }
        }
    }
    return results
}