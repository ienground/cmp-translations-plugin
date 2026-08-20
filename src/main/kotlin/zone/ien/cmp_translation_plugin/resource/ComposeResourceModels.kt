package zone.ien.cmp_translation_plugin.resource

/** A string resource entry parsed from a Compose Multiplatform strings.xml file. */
data class ComposeStringEntry(
    val key: String,
    val value: String,
    val translatable: Boolean = true,
    val placeholders: List<String> = ComposeStringPlaceholders.extract(value),
)

/** Extracts printf-style placeholders while preserving their order and multiplicity. */
object ComposeStringPlaceholders {

    private val placeholderPattern = Regex("""%(?:\d+\${'$'})?[a-zA-Z]""")

    fun extract(value: String): List<String> =
        placeholderPattern.findAll(value).map { it.value }.toList()
}

/** The raw qualifier encoded by a Compose Multiplatform values directory. */
data class ComposeResourceQualifier(val rawValue: String) {

    val directoryName: String
        get() = if (rawValue.isEmpty()) "values" else "values-$rawValue"

    val displayName: String
        get() = if (rawValue.isEmpty()) "Default" else rawValue

    companion object {
        val DEFAULT = ComposeResourceQualifier("")

        fun fromDirectoryName(directoryName: String): ComposeResourceQualifier? = when {
            directoryName == "values" -> DEFAULT
            directoryName.startsWith("values-") && directoryName.length > "values-".length ->
                ComposeResourceQualifier(directoryName.removePrefix("values-"))
            else -> null
        }
    }
}
