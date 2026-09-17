package zone.ien.cmp_translation_plugin.editor

/** Finds Compose string-resource calls in Kotlin source text. */
internal object ComposeTranslationResourceCall {

    private val callPattern = Regex(
        "(?<![A-Za-z0-9_])stringResource\\s*\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)*Res\\.string\\.([A-Za-z_][A-Za-z0-9_]*)\\s*\\)",
    )
    private val resourceArgumentPattern = Regex(
        "(?:[A-Za-z_][A-Za-z0-9_]*\\.)*Res\\.string\\.([A-Za-z_][A-Za-z0-9_]*)",
    )

    fun extractKey(sourceText: String): String? =
        callPattern.matchEntire(sourceText.trim())?.groupValues?.get(1)
            ?: resourceArgumentPattern.matchEntire(sourceText.trim())?.groupValues?.get(1)

    fun find(sourceText: String): List<Reference> = callPattern.findAll(sourceText).map { match ->
        Reference(
            key = match.groupValues[1],
            range = match.range,
        )
    }.toList()

    data class Reference(
        val key: String,
        val range: IntRange,
    )
}
