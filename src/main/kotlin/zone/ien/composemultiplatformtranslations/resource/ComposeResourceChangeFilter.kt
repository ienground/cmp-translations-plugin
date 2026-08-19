package zone.ien.composemultiplatformtranslations.resource

/** Selects VFS changes that can affect the currently selected resource set. */
object ComposeResourceChangeFilter {

    fun isRelevant(resourceRootPath: String, changedPath: String): Boolean {
        val root = normalize(resourceRootPath)
        val changed = normalize(changedPath)
        if (root.isEmpty() || changed == root) return changed == root

        val prefix = "$root/"
        if (!changed.startsWith(prefix)) return false

        val relativePath = changed.removePrefix(prefix)
        val segments = relativePath.split('/').filter(String::isNotEmpty)
        if (segments.isEmpty() || !isValuesDirectory(segments.first())) return false

        return segments.size == 1 ||
            (segments.size == 2 && segments[1] == "strings.xml")
    }

    private fun isValuesDirectory(name: String): Boolean =
        name == "values" || (name.startsWith("values-") && name.length > "values-".length)

    private fun normalize(path: String): String =
        path.replace('\\', '/').trimEnd('/')
}
