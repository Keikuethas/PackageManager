package dev.keikuethas

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

class CLIMain : CliktCommand() {
    val path: Path by argument("file")
        .path(mustExist = true, mustBeReadable = true)

    override fun run() {
        try {
            val configs = extractConfigurations(path)
            val results = processConfigurations(configs)
            printResults(results)
        } catch (e: Exception) {
            echo("Error: ${path.toAbsolutePath()} - ${exToText(e)}")
        }
    }
}
