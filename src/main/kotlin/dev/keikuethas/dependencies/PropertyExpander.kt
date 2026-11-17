package dev.keikuethas.dependencies.mavenresolver

object PropertyExpander {
    fun containsUnresolved(raw: String): Boolean = raw.contains("\${")

    fun tryExpand(raw: String, props: Map<String, String>, visiting: MutableSet<String> = mutableSetOf()): String? {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (i + 1 < raw.length && raw[i] == '$' && raw[i + 1] == '{') {
                val end = raw.indexOf('}', i + 2)
                if (end == -1) return null
                val name = raw.substring(i + 2, end)
                if (name in visiting) return null
                val value = props[name] ?: return null
                visiting.add(name)
                val sub = tryExpand(value, props, visiting)
                visiting.remove(name)
                if (sub == null) return null
                sb.append(sub)
                i = end + 1
            } else {
                sb.append(raw[i])
                i++
            }
        }
        return sb.toString()
    }

    fun findFirstMissing(raw: String, props: Map<String, String>, visiting: MutableSet<String> = mutableSetOf()): String? {
        var i = 0
        while (i < raw.length) {
            if (i + 1 < raw.length && raw[i] == '$' && raw[i + 1] == '{') {
                val end = raw.indexOf('}', i + 2)
                if (end == -1) return null
                val name = raw.substring(i + 2, end)
                if (name in visiting) return null
                val value = props[name] ?: return name
                visiting.add(name)
                val sub = findFirstMissing(value, props, visiting)
                visiting.remove(name)
                if (sub != null) return sub
                i = end + 1
            } else {
                i++
            }
        }
        return null
    }
}