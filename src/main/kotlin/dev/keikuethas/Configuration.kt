package dev.keikuethas

import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    val packageName: String,
    val address: String,
    val repositoryType: RepositoryType = RepositoryType.URL,
    val outFileName: String = "output.png",
    val tree: Boolean = false,
    val maxDepth: Int = 1
)

enum class RepositoryType { URL, TEST }
