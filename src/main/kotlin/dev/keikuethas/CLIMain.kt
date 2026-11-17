package dev.keikuethas

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import dev.keikuethas.dependencies.Limits
import java.nio.file.Path

class CLIMain : CliktCommand() {
    val dataPath: Path by argument("file")
        .path(mustExist = true, mustBeReadable = true)

    val parentLimit: Int by option("--plim", help = "Parent depth limit (default 20)")
        .int(acceptsValueWithoutName = false)
        .default(20)

    val importLimit: Int by option("--ilim", help = "Import depth limit (default 20)")
        .int(acceptsValueWithoutName = false)
        .default(20)

    val loadLimit: Int by option("--llim", help = "POM downloads limit (default 1000)")
        .int(acceptsValueWithoutName = false)
        .default(1000)

    val resolveFromDataLoc: Boolean by option(
        "--D",
        help = "Resolve test files paths from .csv file directory " +
                "instead of current directory where the program is run."
    )
        .flag(default = false)

    val runDir: Path? by option("--dir", hidden = true)
        .path(mustExist = true, mustBeReadable = true, canBeDir = true, canBeFile = false)

    val revDep: String? by option(
        "--rev",
        help = "Package to show reverse dependencies for (default null)"
    )

    override fun run() {
        try {
            val configs = extractConfigurations(dataPath)
            val limits = buildLimits()
            val results = processConfigurations(
                configs,
                limits,
                initDir
            )
            printResults(results, revDep)
        } catch (e: Exception) {
            echo("Error: ${dataPath.toAbsolutePath()} - ${exToText(e)}")
        }
    }

    private val initDir: Path
        get() =
            if (resolveFromDataLoc) dataPath.parent
            else runDir?.toAbsolutePath()?.normalize()
                ?: Path.of("").toAbsolutePath()

    private fun buildLimits() = Limits(
        maxParentDepth = parentLimit,
        maxImportDepth = importLimit,
        maxLoads = loadLimit
    )
}
