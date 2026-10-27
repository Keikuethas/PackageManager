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

            msg.contains("Unknown column", ignoreCase = true) -> {
                val columnName = msg
                    .substringAfter("Unknown column ")
                    .substringBefore(" in")
                "Extra column found in the CSV file: $columnName." +
                        " Remove it or add it to the Configuration data class."
            }

            msg.contains("is not a valid", ignoreCase = true) ||
                    msg.contains("UInt", ignoreCase = true) ->
                "Data type error. Expected an unsigned integer." +
                        " Details: ${msg.removePrefix("Value ")}"


            msg.contains("expected", ignoreCase = true) ||
                    msg.contains("column count", ignoreCase = true) ->
                "Invalid CSV structure: incorrect number of columns in a row. Details: $msg"

            else -> "CSV format error: $msg"
        }
    }

    is NumberFormatException ->
        "Failed to parse number. Please check the 'maxDepth' field. Details: ${e.message ?: ""}"

    else -> "Unexpected error: ${e.message ?: e::class.simpleName}"
}