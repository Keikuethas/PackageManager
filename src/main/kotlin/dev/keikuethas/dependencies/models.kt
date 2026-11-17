package dev.keikuethas.dependencies

import kotlinx.serialization.Serializable

data class Coordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null,
    val packaging: String? = null
) {
    override fun toString(): String = buildString {
        append("$groupId:$artifactId:$version")
        if (classifier != null) append(":$classifier")
        if (packaging != null) append(":$packaging")
    }

    val key: String get() = "$groupId:$artifactId"
    val fullKey: String get() = "$groupId:$artifactId:$version"

}

data class Dependency(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String?,
    val type: String?,
    val optional: Boolean
) {
    val key: String get() = "$groupId:$artifactId"
}

data class ParentRef(
    val groupId: String,
    val artifactId: String,
    val version: String
)

@JvmInline
value class ManagedDependencies(val value: List<Dependency>) : List<Dependency> by value

@JvmInline
value class Imports(val value: List<Dependency>) : List<Dependency> by value

class PomDocument(
    val properties: Map<String, String>,
    val parent: ParentRef?,
    val managedDependencies: ManagedDependencies,
    val imports: Imports,
    val directDependencies: List<Dependency>
)

enum class TraversalPriority { ROOT_OR_PARENT, IMPORT }

data class LoadTask(
    val coords: Coordinates,
    val priority: TraversalPriority,
    val seq: Long,
    val parentLevel: Int,
    val importLevel: Int
) : Comparable<LoadTask> {
    override fun compareTo(other: LoadTask): Int =
        compareValuesBy(this, other, { it.priority }, { it.seq })
}

class ManagedEntry(
    var rawVersion: String?,
    var rawScope: String?,
    val source: Coordinates,
    val depth: Int
) {
    var resolvedVersion: String? = null
    var resolvedScope: String? = null
}

enum class Field { VERSION, SCOPE }

data class SlotRef(val key: String, val field: Field, val rawText: String)

sealed interface VersionSource {
    data object Explicit : VersionSource
    data class Managed(val source: Coordinates, val property: String?) : VersionSource
}

data class ResolvedDependency(
    val groupId: String,
    val artifactId: String,
    val requestedVersion: String?,
    val resolvedVersion: String,
    val scope: String,
    val versionSource: VersionSource,
    val declaredIn: Coordinates
) {
    val key: String get() = "$groupId:$artifactId"
    val fullKey: String get() = "$groupId:$artifactId:$resolvedVersion"
    val coords: Coordinates = Coordinates(
        groupId,
        artifactId,
        resolvedVersion,
    )
}

@Serializable
sealed interface Diagnostic {
    val message: String
    val coords: Coordinates?


    data class FetchFailed(
        override val message: String,
        override val coords: Coordinates,
        val status: FetchStatus
    ) : Diagnostic


    data class PropertyUnresolved(
        override val message: String,
        override val coords: Coordinates?,
        val property: String
    ) : Diagnostic


    data class LimitExceeded(
        override val message: String,
        override val coords: Coordinates?
    ) : Diagnostic


    data class ParentUnresolved(
        override val message: String,
        override val coords: Coordinates
    ) : Diagnostic


    data class ImportUnresolved(
        override val message: String,
        override val coords: Coordinates
    ) : Diagnostic


    data class CycleFound(
        override val message: String,
        override val coords: Coordinates,
        val chain: List<Coordinates>
    ) : Diagnostic


    data class TestParseFailed(
        override val message: String
    ) : Diagnostic {
        override val coords: Coordinates?
            get() = null
    }


    data class TestCycleFound(
        override val message: String,
        val callChain: List<String>
    ) : Diagnostic {
        override val coords: Coordinates?
            get() = null
    }

}

enum class FetchStatus { NOT_FOUND, TRANSIENT, OTHER }

data class ResolutionResult(
    val resolved: List<ResolvedDependency>,
    val unresolved: List<Dependency>,
)

data class DeepResolutionResult(
    val main: ResolutionResult,
    val children: List<DeepResolutionResult>
)

/**
 * @property nodes direct compile dependencies.
 * **Empty** if depth limit is reached, **null** if dependency is not resolved.
 */
data class Node(
    val nodes: Graph?,
    val coordinates: Coordinates?
)
typealias Graph = Map<String, Node>

fun emptyGraph(): Graph = emptyMap()