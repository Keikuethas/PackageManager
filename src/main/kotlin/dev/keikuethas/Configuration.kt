package dev.keikuethas

import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    val packageName: String,
    val address: String,
    val repositoryType: RepositoryType = RepositoryType.URL,
    val outFileName: String,
    val tree: Boolean = false,
    val maxDepth: UInt = 1.toUInt()
)

enum class RepositoryType { URL, FILE }
