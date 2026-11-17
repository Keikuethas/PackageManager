package dev.keikuethas.dependencies

import arrow.core.Either
import arrow.core.separateEither
import java.util.*

class ResolutionSession(
    private val ctx: ResolutionContext,
    private val depth: Int
) {
    private val localDM = mutableMapOf<String, ManagedEntry>()
    private val properties: MutableMap<String, String> = mutableMapOf()
    private val pendingProps = mutableMapOf<String, MutableList<SlotRef>>()
    private val needed = mutableSetOf<String>()
    private val visited = mutableSetOf<String>()
    private val queuedPriority = mutableMapOf<String, TraversalPriority>()
    private val queue = PriorityQueue<LoadTask>()
    private var seq = 0L
    private var rootCoords: Coordinates? = null

    fun resolve(
        root: Coordinates,
    ): ResolutionResult {
        rootCoords = root
        visited.add(root.toString())
        val rootPom = fetchPom(root)
            ?: return ResolutionResult(emptyList(), emptyList())
        loadRoot(rootPom, root)

        getUnresolvedDependencies(rootPom)
        processLoadQueue()

        return finalize(rootPom.directDependencies, root)
    }

    fun getResolvedVersions() =
        localDM.filterValues {
            it.resolvedVersion != null &&
                    (it.resolvedScope == null || it.resolvedScope == "compile")
        }

    private fun getUnresolvedDependencies(rootPom: PomDocument) {
        rootPom.directDependencies
            .filter { dep ->

                val scope = dep.scope ?: "compile"
                if (scope != "compile") return@filter false

                dep.version == null ||
                        dep.scope == null ||
                        PropertyExpander.containsUnresolved(dep.version) ||
                        PropertyExpander.containsUnresolved(dep.scope)
            }
            .mapTo(needed) { it.key }
    }

    private fun processLoadQueue(): Boolean {
        while (true) {
            if (needed.isNotEmpty() && needed.all { localDM[it]?.resolvedVersion != null }) {
                return true
            }

            if (ctx.loads >= ctx.limits.maxLoads) {
                limitExceeded("Download limit ${ctx.limits.maxLoads} exceeded", null)
                return false
            }

            val task = pollTask() ?: return false
            val pom = fetchPom(task.coords) ?: continue
            handleTask(pom, task)
        }
    }

    private fun loadRoot(rootPom: PomDocument, root: Coordinates) {
        properties.putAll(loadBuiltInProperties(rootPom, root))
        processPomContent(rootPom, root)
        enqueueChildren(
            pom = rootPom,
            coords = root,
            parentLevel = 0,
            importLevel = 0,
            priority = TraversalPriority.ROOT_OR_PARENT
        )
    }

    private fun handleTask(pom: PomDocument, task: LoadTask) {
        processPomContent(pom, task.coords)
        enqueueChildren(pom, task.coords, task.parentLevel, task.importLevel, task.priority)
    }

    private fun fetchPom(coords: Coordinates): PomDocument? {
        val key = coords.toString()
        ctx.pomCache[key]?.let { return it }
        return when (val res = ctx.downloader.fetch(coords)) {
            is FetchResult.Success -> try {
                val pom = PomParser.parse(res.body)
                ctx.pomCache[key] = pom
                ctx.loads++
                pom
            } catch (e: Exception) {
                fetchFailed("Parse error: ${e.message}", coords, FetchStatus.OTHER)
                null
            }

            is FetchResult.Failure -> {
                fetchFailed(res.message, coords, res.status)
                null
            }
        }
    }

    private fun loadBuiltInProperties(pom: PomDocument, coords: Coordinates): Map<String, String> {
        val props = mutableMapOf(
            "project.groupId" to coords.groupId,
            "project.artifactId" to coords.artifactId,
            "project.version" to coords.version
        )
        pom.parent?.let { p ->
            props["project.parent.groupId"] = p.groupId
            props["project.parent.artifactId"] = p.artifactId
            props["project.parent.version"] = p.version
        }
        return props
    }

    private fun processPomContent(pom: PomDocument, coords: Coordinates) {
        for ((name, raw) in pom.properties) {
            if (name !in properties) {
                properties[name] = raw
                onPropertyAdded(name)
            }
        }

        for (dep in pom.managedDependencies.value) {
            if (dep.key !in needed) continue
            if (dep.key in localDM) continue
            val entry = ManagedEntry(dep.version, dep.scope, coords, depth)

            ctx.winnerDeps[dep.key]?.let {
                if (it.depth <= depth)
                    localDM[dep.key] = it
            } ?: run { localDM[dep.key] = entry }

            localDM[dep.key]?.rawScope?.let {
                if (PropertyExpander.containsUnresolved(it))
                    registerSlot(SlotRef(dep.key, Field.SCOPE, it))
            } ?: localDM[dep.key]?.let { it.resolvedScope = it.rawScope }

            if (
                localDM[dep.key] != null &&
                (
                        localDM[dep.key]!!.resolvedScope == null ||
                                localDM[dep.key]!!.resolvedScope == "compile")
            ) {
                localDM[dep.key]?.rawVersion?.let {
                    if (PropertyExpander.containsUnresolved(it))
                        registerSlot(SlotRef(dep.key, Field.VERSION, it))
                } ?: localDM[dep.key]?.let { it.resolvedVersion = it.rawVersion }
            }
        }
    }

    private fun enqueueChildren(
        pom: PomDocument, coords: Coordinates,
        parentLevel: Int, importLevel: Int, priority: TraversalPriority
    ) {
        enqueueParent(pom, parentLevel, coords, priority, importLevel)
        enqueueImports(importLevel, pom, coords)
        return
    }

    private fun enqueueImports(
        importLevel: Int,
        pom: PomDocument,
        coords: Coordinates
    ) {
        if (importLevel >= ctx.limits.maxImportDepth) {
            if (pom.imports.isNotEmpty())
                limitExceeded("Import depth limit reached for $coords", coords)
            return
        }
        for (imp in pom.imports) {
            enqueueImport(imp, coords, importLevel)
        }
    }

    private fun enqueueImport(
        imp: Dependency,
        coords: Coordinates,
        importLevel: Int
    ) {
        if (imp.version == null) {
            if (imp.scope == null || imp.scope == "compile")
                ctx.diagnostics.add(
                    Diagnostic.ImportUnresolved(
                        "Import ${imp.key} with no version in $coords", coords
                    )
                )
            return
        }
        val c = expandCoords(imp.groupId, imp.artifactId, imp.version)
        if (c == null) {
            if (imp.scope == null || imp.scope == "compile")
                ctx.diagnostics.add(
                    Diagnostic.ImportUnresolved(
                        "Failed to resolve coordinates for import ${imp.key} in $coords",
                        coords
                    )
                )
        } else
            enqueue(c, TraversalPriority.IMPORT, 0, importLevel + 1)
    }

    private fun enqueueParent(
        pom: PomDocument,
        parentLevel: Int,
        coords: Coordinates,
        priority: TraversalPriority,
        importLevel: Int
    ) {
        pom.parent?.let { p ->
            if (parentLevel >= ctx.limits.maxParentDepth) {
                limitExceeded("Parent depth limit reached for $coords", coords)
                return@let
            }
            val c = expandCoords(p.groupId, p.artifactId, p.version)
            if (c == null)
                ctx.diagnostics.add(
                    Diagnostic.ParentUnresolved(
                        "Failed to resolve parent coordinates for $coords",
                        coords
                    )
                )
            else
                enqueue(c, priority, parentLevel + 1, importLevel)
        }
    }

    private fun enqueue(
        coords: Coordinates,
        priority: TraversalPriority,
        parentLevel: Int,
        importLevel: Int
    ) {
        val key = coords.toString()
        if (key in visited) return
        val existing = queuedPriority[key]
        if (existing != null && existing <= priority) return
        queuedPriority[key] = priority
        queue.add(LoadTask(coords, priority, seq++, parentLevel, importLevel))
    }

    private fun pollTask(): LoadTask? {
        while (queue.isNotEmpty()) {
            val task = queue.poll()
            val key = task.coords.toString()
            if (key in visited) continue
            if (queuedPriority[key] != task.priority) continue
            queuedPriority.remove(key)
            visited.add(key)
            return task
        }
        return null
    }

    private fun expandCoords(g: String, a: String, v: String): Coordinates? {
        val eg = PropertyExpander.tryExpand(g, properties) ?: return null
        val ea = PropertyExpander.tryExpand(a, properties) ?: return null
        val ev = PropertyExpander.tryExpand(v, properties) ?: return null
        return Coordinates(eg, ea, ev)
    }

    private fun registerSlot(slot: SlotRef) {
        val expanded = PropertyExpander.tryExpand(slot.rawText, properties)
        if (expanded != null) {
            applySlot(slot, expanded); return
        }
        val missing = PropertyExpander.findFirstMissing(slot.rawText, properties) ?: run {
            ctx.diagnostics.add(
                Diagnostic.PropertyUnresolved(
                    "Impossible to resolve property chain for ${slot.key}: ${slot.rawText}",
                    rootCoords, slot.rawText
                )
            )
            return
        }
        pendingProps.getOrPut(missing) { mutableListOf() }.add(slot)
    }

    private fun onPropertyAdded(name: String) {
        val slots = pendingProps.remove(name) ?: return
        for (slot in slots) {
            val expanded = PropertyExpander.tryExpand(slot.rawText, properties)
            if (expanded != null) applySlot(slot, expanded)
            else {
                val missing = PropertyExpander.findFirstMissing(slot.rawText, properties) ?: continue
                pendingProps.getOrPut(missing) { mutableListOf() }.add(slot)
            }
        }
    }

    private fun applySlot(slot: SlotRef, expanded: String) {
        val entry = localDM[slot.key] ?: return

        when (slot.field) {
            Field.VERSION -> entry.resolvedVersion = expanded
            Field.SCOPE -> entry.resolvedScope = expanded
        }
    }

    private fun finalize(directDeps: List<Dependency>, root: Coordinates): ResolutionResult {
        val (unresolved, resolved) =
            directDeps.separateEither { finalizeDependency(it, root) }

        for ((prop, slots) in pendingProps) {
            ctx.diagnostics.add(
                Diagnostic.PropertyUnresolved(
                    "Property '$prop' is not resolved (${slots.size} places)",
                    root, prop
                )
            )
        }
        return ResolutionResult(resolved.filter { it.scope == "compile" }, unresolved.filter { it.scope == "compile" })
    }

    private fun finalizeDependency(
        dep: Dependency,
        root: Coordinates,
    ): Either<Dependency, ResolvedDependency> {
        val entry = localDM[dep.key]

        var resolvedVersion: String? = null
        var versionSource: VersionSource? = null
        val requestedVersion = dep.version

        if (dep.version != null) {
            val v = PropertyExpander.tryExpand(dep.version, properties)
            if (v != null) {
                resolvedVersion = v
                versionSource = VersionSource.Explicit
            }
        }
        if (resolvedVersion == null && entry?.resolvedVersion != null) {
            resolvedVersion = entry.resolvedVersion
            versionSource = VersionSource.Managed(entry.source, entry.rawVersion)
        }

        var scope: String? = dep.scope?.let { PropertyExpander.tryExpand(it, properties) }
        if (scope == null) scope = entry?.resolvedScope ?: "compile"

        return if (resolvedVersion != null && versionSource != null)
            Either.Right(
                ResolvedDependency(
                    groupId = dep.groupId,
                    artifactId = dep.artifactId,
                    requestedVersion = requestedVersion,
                    resolvedVersion = resolvedVersion,
                    scope = scope,
                    versionSource = versionSource,
                    declaredIn = root
                )
            )
        else Either.Left(dep)
    }

    private fun limitExceeded(msg: String, coords: Coordinates?) =
        ctx.diagnostics.add(
            Diagnostic.LimitExceeded(msg, coords)
        )

    private fun fetchFailed(msg: String, coords: Coordinates, status: FetchStatus) =
        ctx.diagnostics.add(Diagnostic.FetchFailed(msg, coords, status))
}