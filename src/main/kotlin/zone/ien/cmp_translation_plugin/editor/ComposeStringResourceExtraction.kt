package zone.ien.cmp_translation_plugin.editor

import java.util.Locale

/** Parses Kotlin string literals and suggests Compose resource identifiers. */
internal object ComposeStringResourceExtraction {

    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")
    private val identifierCharacters = Regex("[^A-Za-z0-9]+")
    private val generatedResImport = Regex(
        "(?m)^\\s*import\\s+[A-Za-z_][A-Za-z0-9_.]*\\.Res(?:\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*))?\\s*$",
    )
    private val packageDeclaration = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*$")

    fun suggestResourceKey(value: String): String {
        val key = value
            .replace(camelCaseBoundary, "$1_$2")
            .replace(identifierCharacters, "_")
            .trim('_')
            .lowercase(Locale.ROOT)

        if (key.isEmpty()) return "string"
        return if (key.first().isDigit()) "string_$key" else key
    }

    fun uniqueResourceKey(value: String, existingKeys: Set<String>): String {
        val baseKey = suggestResourceKey(value)
        if (baseKey !in existingKeys) return baseKey

        var suffix = 2
        while ("${baseKey}_$suffix" in existingKeys) suffix++
        return "${baseKey}_$suffix"
    }

    fun extractLiteralValue(text: String): String? {
        val literal = text.trim()
        if (literal.length >= 6 && literal.startsWith("\"\"\"") && literal.endsWith("\"\"\"")) {
            return literal.substring(3, literal.length - 3).takeUnless { it.contains('$') }
        }
        if (literal.length < 2 || literal.first() != '"' || literal.last() != '"') return null

        val value = StringBuilder(literal.length - 2)
        var index = 1
        while (index < literal.lastIndex) {
            val character = literal[index]
            if (character == '$') return null
            if (character != '\\') {
                value.append(character)
                index++
                continue
            }

            if (index + 1 >= literal.lastIndex) return null
            val escaped = literal[index + 1]
            when (escaped) {
                'b' -> value.append('\b')
                't' -> value.append('\t')
                'n' -> value.append('\n')
                'r' -> value.append('\r')
                '\\' -> value.append('\\')
                '"' -> value.append('"')
                '\'' -> value.append('\'')
                '$' -> value.append('$')
                'u' -> {
                    if (index + 5 >= literal.lastIndex) return null
                    val hex = literal.substring(index + 2, index + 6)
                    val codePoint = hex.toIntOrNull(16) ?: return null
                    value.append(codePoint.toChar())
                    index += 4
                }
                else -> return null
            }
            index += 2
        }
        return value.toString()
    }

    fun replacementExpression(resourceKey: String, resReference: String): String =
        "stringResource($resReference.string.$resourceKey)"

    fun resReference(sourceText: String): String =
        generatedResImport.find(sourceText)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
            ?: generatedResImport.find(sourceText)?.value?.let { "Res" }
            ?: packageDeclaration.find(sourceText)?.groupValues?.get(1)?.let { "$it.generated.resources.Res" }
            ?: "Res"

    fun missingImport(sourceText: String, importName: String): String? {
        val importPattern = Regex("(?m)^\\s*import\\s+${Regex.escape(importName)}(?:\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*)?\\s*$")
        if (importPattern.containsMatchIn(sourceText)) return null
        return "import $importName\n"
    }

    fun insertMissingImport(sourceText: String, importName: String): String {
        val import = missingImport(sourceText, importName) ?: return sourceText
        val insertionOffset = importInsertionOffset(sourceText)
        val prefix = if (insertionOffset > 0 && sourceText[insertionOffset - 1] != '\n') "\n" else ""
        return buildString(sourceText.length + prefix.length + import.length) {
            append(sourceText, 0, insertionOffset)
            append(prefix)
            append(import)
            append(sourceText, insertionOffset, sourceText.length)
        }
    }

    fun importInsertionOffset(sourceText: String): Int {
        val importMatches = Regex("(?m)^\\s*import\\s+[^\\n]+(?:\\n|$)").findAll(sourceText).toList()
        importMatches.lastOrNull()?.let { return it.range.last + 1 }

        packageDeclaration.find(sourceText)?.let { match ->
            val lineEnd = sourceText.indexOf('\n', match.range.last + 1)
            return if (lineEnd == -1) sourceText.length else lineEnd + 1
        }
        return 0
    }
}
