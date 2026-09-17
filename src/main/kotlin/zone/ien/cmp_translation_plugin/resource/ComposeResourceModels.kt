package zone.ien.cmp_translation_plugin.resource

/** Resource kinds that can be represented by the translation editor. */
enum class ComposeResourceType {
    STRING,
    STRING_ARRAY,
    PLURALS,
}

/** 번역 편집기에서 지원하는 Android 복수형 수량 키입니다. */
object ComposePluralQuantities {

    val all: List<String> = listOf("zero", "one", "two", "few", "many", "other")

    fun sort(items: Iterable<ComposeResourceItem>): List<ComposeResourceItem> = items.sortedWith(
        compareBy<ComposeResourceItem> { all.indexOf(it.name).takeIf { index -> index >= 0 } ?: all.size }
            .thenBy(ComposeResourceItem::name),
    )
}

/** One named child value of a multi-value Android resource. */
data class ComposeResourceItem(
    val name: String,
    val value: String,
    val placeholders: List<String> = ComposeStringPlaceholders.extract(value),
)

/** A resource entry parsed from a Compose Multiplatform strings.xml file. */
data class ComposeResourceEntry(
    val key: String,
    val value: String,
    val translatable: Boolean = true,
    val placeholders: List<String> = ComposeStringPlaceholders.extract(value),
    val type: ComposeResourceType = ComposeResourceType.STRING,
    val items: List<ComposeResourceItem> = emptyList(),
)

/** Compatibility name retained for callers that only handle string resources. */
typealias ComposeStringEntry = ComposeResourceEntry

/** Draft shared by the editor dialog and PSI writer for a resource mutation. */
data class ComposeResourceDraft(
    val key: String,
    val defaultValue: String,
    val localizedValues: Map<ComposeResourceQualifier, String>,
    val translatable: Boolean,
    val type: ComposeResourceType = ComposeResourceType.STRING,
    val defaultItems: List<ComposeResourceItem> = emptyList(),
    val localizedItems: Map<ComposeResourceQualifier, List<ComposeResourceItem>> = emptyMap(),
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
