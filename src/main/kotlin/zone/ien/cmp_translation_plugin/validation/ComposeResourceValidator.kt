package zone.ien.cmp_translation_plugin.validation

import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeStringEntry

enum class ComposeResourceIssueType {
    MISSING_KEY,
    ORPHAN_KEY,
    DUPLICATE_KEY,
    PLACEHOLDER_MISMATCH,
}

data class ComposeResourceIssue(
    val type: ComposeResourceIssueType,
    val key: String,
    val qualifier: ComposeResourceQualifier?,
    val expectedPlaceholders: List<String> = emptyList(),
    val actualPlaceholders: List<String> = emptyList(),
)

/** Compares the default strings.xml entries with each localized file. */
class ComposeResourceValidator {

    fun validate(
        defaultEntries: List<ComposeStringEntry>,
        localizedEntries: Map<ComposeResourceQualifier, List<ComposeStringEntry>>,
    ): List<ComposeResourceIssue> {
        val issues = mutableListOf<ComposeResourceIssue>()
        issues += duplicateIssues(defaultEntries, null)

        localizedEntries.forEach { (qualifier, entries) ->
            issues += duplicateIssues(entries, qualifier)
        }

        val defaultByKey = defaultEntries.associateBy(ComposeStringEntry::key)
        localizedEntries.forEach { (qualifier, entries) ->
            val localizedByKey = entries.associateBy(ComposeStringEntry::key)

            defaultByKey.values
                .filter { it.translatable }
                .map { it.key }
                .filterNot(localizedByKey::containsKey)
                .forEach { key ->
                    issues += ComposeResourceIssue(
                        type = ComposeResourceIssueType.MISSING_KEY,
                        key = key,
                        qualifier = qualifier,
                    )
                }

            localizedByKey.keys
                .filterNot(defaultByKey::containsKey)
                .forEach { key ->
                    issues += ComposeResourceIssue(
                        type = ComposeResourceIssueType.ORPHAN_KEY,
                        key = key,
                        qualifier = qualifier,
                    )
                }

            localizedByKey.forEach { (key, localizedEntry) ->
                val defaultEntry = defaultByKey[key] ?: return@forEach
                if (defaultEntry.placeholders != localizedEntry.placeholders) {
                    issues += ComposeResourceIssue(
                        type = ComposeResourceIssueType.PLACEHOLDER_MISMATCH,
                        key = key,
                        qualifier = qualifier,
                        expectedPlaceholders = defaultEntry.placeholders,
                        actualPlaceholders = localizedEntry.placeholders,
                    )
                }
            }
        }

        return issues
    }

    private fun duplicateIssues(
        entries: List<ComposeStringEntry>,
        qualifier: ComposeResourceQualifier?,
    ): List<ComposeResourceIssue> =
        entries
            .groupingBy(ComposeStringEntry::key)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .map { key ->
                ComposeResourceIssue(
                    type = ComposeResourceIssueType.DUPLICATE_KEY,
                    key = key,
                    qualifier = qualifier,
                )
            }
}
