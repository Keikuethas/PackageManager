package dev.keikuethas

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException

@OptIn(ExperimentalSerializationApi::class)
fun exToText(e: Exception): String = when (e) {
    is MissingFieldException -> {
        val missing = e.missingFields.joinToString(", ") { "'$it'" }
        "Required columns are missing from the CSV file: $missing. Please check the headers."
    }

    is SerializationException -> {
        val msg = e.message ?: ""
        when {
            /* The kotlinx-serialization-csv library usually says: "Unknown column 'name'" */
            msg.contains("Unknown column", ignoreCase = true) -> {
                val columnName = msg.substringAfter("Unknown column ").substringBefore(" in")
                "Extra column found in the CSV file: $columnName. Remove it or add it to the Configuration data class."
            }
            /* Data type error (e.g., letters in the maxDepth column) */
            msg.contains("is not a valid", ignoreCase = true) || msg.contains("UInt", ignoreCase = true) -> {
                "Data type error. Expected an unsigned integer. Details: ${msg.removePrefix("Value ")}"
            }
            /* Column count error (e.g., a missing comma)  */
            msg.contains("expected", ignoreCase = true) || msg.contains("column count", ignoreCase = true) -> {
                "Invalid CSV structure: incorrect number of columns in a row. Details: $msg"
            }

            else -> "CSV format error: $msg"
        }
    }

    is NumberFormatException -> {
        "Failed to parse number. Please check the 'maxDepth' field. Details: ${e.message ?: ""}"
    }

    else -> "Unexpected error: ${e.message ?: e::class.simpleName}"
}