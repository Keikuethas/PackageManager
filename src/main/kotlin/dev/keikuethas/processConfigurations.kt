package dev.keikuethas

import dev.keikuethas.dependencies.ResolutionContext
import dev.keikuethas.dependencies.ResolutionResult
import dev.keikuethas.utils.toCoordinates
import dev.keikuethas.utils.toRepoUrl

fun processConfigurations(configs: List<Configuration>): Map<Configuration, ResolutionResult> {
    /** Словарь "Репозиторий : Контекст" */
    val contexts = mutableMapOf<String, ResolutionContext>()
    /** Словарь "Конфигурация : Результат"*/
    val results = linkedMapOf<Configuration, ResolutionResult>()

    for (config in configs) {
        val repoUrl = config.toRepoUrl()
        val context = contexts.getOrPut(repoUrl) { ResolutionContext(repoUrl) }

        val coords = try {
            config.toCoordinates()
        } catch (e: IllegalArgumentException) {
            println("${config.packageName}: ${e.message}")
            continue
        }

        results[config] = context.newSession().resolve(coords)
    }
    return results
}