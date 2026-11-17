package dev.keikuethas.dependencies

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

object PomParser {

    private val factory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }
            isExpandEntityReferences = false
            isNamespaceAware = false
        }
    }

    /**
     *
     * @throws IllegalArgumentException if root is not `<project>`.
     */
    fun parse(xml: String): PomDocument {

        val project = xmlToProject(xml)
        require(project.nodeName == "project") { "Root is <${project.nodeName}>, expected <project>" }

        val properties = parseProperties(project)
        val parent = parseParent(project)
        val (managed, imports) = parseDependencyManagement(project)
        val direct = parseDirectDependencies(project)

        return PomDocument(properties, parent, managed, imports, direct)
    }

    private fun xmlToProject(xml: String) = factory.newDocumentBuilder()
        .parse(xml.byteInputStream()).documentElement

    private fun parseDependencyManagement(project: Element):
            Pair<ManagedDependencies, Imports> {
        val managed = mutableListOf<Dependency>()
        val imports = mutableListOf<Dependency>()
        project.directChild("dependencyManagement")
            ?.directChild("dependencies")
            ?.directChildren("dependency")
            ?.forEach { element ->
                val dependency = parseDependency(element)
                if (dependency.scope == "import" && dependency.type == "pom") imports.add(dependency)
                else managed.add(dependency)
            }
        return Pair(ManagedDependencies(managed.toList()), Imports(imports.toList()))
    }

    private fun parseProperties(project: Element) =
        project.directChild("properties")?.let { p ->
        p.directChildren().associate { it.nodeName to it.textContent.trim() }
    } ?: emptyMap()

    private fun parseParent(project: Element) =
        project.directChild("parent")?.let { e ->
            val groupId = e.directChild("groupId")?.textContent?.trim() ?: return@let null
            val artifactId = e.directChild("artifactId")?.textContent?.trim() ?: return@let null
            val version = e.directChild("version")?.textContent?.trim() ?: return@let null
            ParentRef(groupId, artifactId, version)
        }

    private fun parseDirectDependencies(project: Element) =
        project.directChild("dependencies")
            ?.directChildren("dependency")
            ?.map { parseDependency(it) }
            ?: emptyList()

    /**
     * @throws IllegalStateException if `<groupId>` or `<artifactId>` is not present.
     */
    private fun parseDependency(el: Element): Dependency {
        val g = el.getDirectChildContent("groupId")
            ?: error("<dependency> без <groupId>")
        val a = el.getDirectChildContent("artifactId")
            ?: error("<dependency> без <artifactId>")
        return Dependency(
            groupId = g,
            artifactId = a,
            version = el.getDirectChildContent("version"),
            scope = el.getDirectChildContent("scope"),
            type = el.getDirectChildContent("type"),
            optional = el.getDirectChildContent("optional")?.toBoolean() ?: false
        )
    }
}

private fun Element.getDirectChildContent(name: String) =
    directChild(name)?.textContent?.trim()

private fun Element.directChild(name: String): Element? =
    directChildren().firstOrNull { it.nodeName == name }

private fun Element.directChildren(name: String): List<Element> =
    directChildren().filter { it.nodeName == name }

private fun Element.directChildren(): List<Element> {
    val result = mutableListOf<Element>()
    val kids = childNodes
    for (i in 0..<kids.length) (kids.item(i) as? Element)?.let { result.add(it) }
    return result
}