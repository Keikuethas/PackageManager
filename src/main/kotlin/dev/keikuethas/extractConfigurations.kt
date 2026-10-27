package dev.keikuethas

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.csv.Csv
import java.nio.file.Path
import kotlin.io.path.readText

@OptIn(ExperimentalSerializationApi::class)
fun extractConfigurations(path: Path): List<Configuration> {
    val csv = Csv { hasHeaderRecord = true }

    val csvContent = path.readText()

    return csv.decodeFromString(
        ListSerializer(Configuration.serializer()),
        csvContent
    )
}