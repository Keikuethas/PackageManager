package dev.keikuethas.dependencies

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
value class ManagedDependencies(val value: List<Dependency>)
@JvmInline
value class Imports(val value: List<Dependency>)

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
    val source: Coordinates
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
)

sealed interface Diagnostic {
    val message: String
    val coords: Coordinates?

    data class FetchFailed(override val message: String, override val coords: Coordinates,  val status: FetchStatus) : Diagnostic
    data class PropertyUnresolved(override val message: String, override val coords: Coordinates?, val property: String) : Diagnostic
    data class LimitExceeded(override val message: String, override val coords: Coordinates?) : Diagnostic
    data class ParentUnresolved(override val message: String, override val coords: Coordinates) : Diagnostic
    data class ImportUnresolved(override val message: String, override val coords: Coordinates) : Diagnostic
}

enum class FetchStatus { NOT_FOUND, TRANSIENT, OTHER }

data class ResolutionResult(
    val resolved: List<ResolvedDependency>,
    val unresolved: List<Dependency>,
    val diagnostics: List<Diagnostic>
)