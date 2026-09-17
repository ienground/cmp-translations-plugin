package zone.ien.cmp_translation_plugin.validation

import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType
import zone.ien.cmp_translation_plugin.resource.ComposeStringEntry

enum class ComposeResourceIssueType {
    MISSING_KEY,
    ORPHAN_KEY,
    DUPLICATE_KEY,
    PLACEHOLDER_MISMATCH,
    TYPE_MISMATCH,
    MISSING_ITEM,
    EXTRA_ITEM,
}

data class ComposeResourceIssue(
    val type: ComposeResourceIssueType,
    val key: String,
    val qualifier: ComposeResourceQualifier?,
    val expectedPlaceholders: List<String> = emptyList(),
    val actualPlaceholders: List<String> = emptyList(),
    val itemName: String? = null,
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
                if (defaultEntry.type != localizedEntry.type) {
                    issues += ComposeResourceIssue(
                        type = ComposeResourceIssueType.TYPE_MISMATCH,
                        key = key,
                        qualifier = qualifier,
                    )
                    return@forEach
                }

                if (defaultEntry.type == ComposeResourceType.STRING) {
                    if (defaultEntry.placeholders != localizedEntry.placeholders) {
                        issues += ComposeResourceIssue(
                            type = ComposeResourceIssueType.PLACEHOLDER_MISMATCH,
                            key = key,
                            qualifier = qualifier,
                            expectedPlaceholders = defaultEntry.placeholders,
                            actualPlaceholders = localizedEntry.placeholders,
                        )
                    }
                } else {
                    val localizedItems = localizedEntry.items.associateBy { it.name }
                    defaultEntry.items.forEach { defaultItem ->
                        val localizedItem = localizedItems[defaultItem.name]
                        if (localizedItem == null || localizedItem.value.isBlank()) {
                            issues += ComposeResourceIssue(
                                type = ComposeResourceIssueType.MISSING_ITEM,
                                key = key,
                                qualifier = qualifier,
                                itemName = defaultItem.name,
                            )
                        } else if (defaultItem.placeholders != localizedItem.placeholders) {
                            issues += ComposeResourceIssue(
                                type = ComposeResourceIssueType.PLACEHOLDER_MISMATCH,
                                key = key,
                                qualifier = qualifier,
                                expectedPlaceholders = defaultItem.placeholders,
                                actualPlaceholders = localizedItem.placeholders,
                                itemName = defaultItem.name,
                            )
                        }
                    }
                    localizedEntry.items
                        .filterNot { localizedItem -> defaultEntry.items.any { it.name == localizedItem.name } }
                        .forEach { localizedItem ->
                            issues += ComposeResourceIssue(
                                type = ComposeResourceIssueType.EXTRA_ITEM,
                                key = key,
                                qualifier = qualifier,
                                itemName = localizedItem.name,
                            )
                        }
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
