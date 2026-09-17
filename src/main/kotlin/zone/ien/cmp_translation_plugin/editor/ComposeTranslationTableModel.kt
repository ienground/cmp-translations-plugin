package zone.ien.cmp_translation_plugin.editor

import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType
import zone.ien.cmp_translation_plugin.resource.ComposeStringEntry
import zone.ien.cmp_translation_plugin.validation.ComposeResourceIssue
import java.util.Locale
import javax.swing.table.AbstractTableModel

enum class TranslationFilter(private val messageKey: String) {
    ALL("translation.filter.all"),
    MISSING("translation.filter.missing"),
    COMPLETE("translation.filter.complete");

    val displayName: String
        get() = MyBundle.message(messageKey)

    override fun toString(): String = displayName
}

data class TranslationRow(
    val key: String,
    val defaultValue: String?,
    val localizedValues: Map<ComposeResourceQualifier, String?>,
    val issues: List<ComposeResourceIssue>,
    val translatable: Boolean = true,
    val resourceType: ComposeResourceType = ComposeResourceType.STRING,
    val parentKey: String? = null,
    val itemName: String? = null,
    val depth: Int = 0,
    val children: List<TranslationRow> = emptyList(),
) {

    val resourceKey: String
        get() = parentKey ?: key

    val isChild: Boolean
        get() = parentKey != null

    val hasChildren: Boolean
        get() = children.isNotEmpty()

    val isComplete: Boolean
        get() = if (isChild || resourceType == ComposeResourceType.STRING) {
            defaultValue?.isNotBlank() == true &&
                (!translatable || localizedValues.values.all { !it.isNullOrBlank() }) &&
                issues.isEmpty()
        } else {
            children.isNotEmpty() && children.all(TranslationRow::isComplete) && issues.isEmpty()
        }

    val isMissing: Boolean
        get() = !isComplete

    val displayKey: String
        get() = if (isChild) "└ ${itemName.orEmpty()}" else key

    fun displayValue(value: String?, itemCount: Int = children.size): String = when {
        value != null -> value
        !isChild && resourceType != ComposeResourceType.STRING ->
            MyBundle.message("translation.table.items", itemCount)
        else -> ComposeTranslationTableModel.MISSING_VALUE
    }
}

/** Table model for the translation matrix shown in the tool window. */
class ComposeTranslationTableModel : AbstractTableModel() {

    private data class RowGroup(
        val parent: TranslationRow,
        val children: List<TranslationRow>,
    )

    private var groups: List<RowGroup> = emptyList()
    private var qualifiers: List<ComposeResourceQualifier> = emptyList()
    private val expandedKeys = mutableSetOf<String>()
    private var hasInitializedRows = false

    var onValueEdited: ((key: String, qualifier: ComposeResourceQualifier?, value: String) -> Unit)? = null
    var onItemValueEdited: ((key: String, itemName: String, qualifier: ComposeResourceQualifier?, value: String) -> Unit)? = null
    var onKeyEdited: ((oldKey: String, newKey: String) -> Unit)? = null
    var onTranslatableEdited: ((key: String, translatable: Boolean) -> Unit)? = null

    var filter: TranslationFilter = TranslationFilter.ALL
        set(value) {
            field = value
            fireTableDataChanged()
        }

    var searchQuery: String = ""
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = visibleRows().size

    val resourceCount: Int
        get() = groups.size

    override fun getColumnCount(): Int = 3 + qualifiers.size

    override fun getColumnName(column: Int): String = when (column) {
        0 -> MyBundle.message("translation.table.key")
        1 -> MyBundle.message("translation.table.untranslatable")
        2 -> MyBundle.message("translation.table.default")
        else -> qualifiers[column - 3].displayName
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        1 -> Boolean::class.javaObjectType
        else -> String::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = visibleRows()[rowIndex]
        return when (columnIndex) {
            0 -> row.displayKey
            1 -> !row.translatable
            2 -> row.displayValue(row.defaultValue)
            else -> {
                val qualifier = qualifiers[columnIndex - 3]
                row.displayValue(row.localizedValues[qualifier])
            }
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        val row = visibleRows().getOrNull(rowIndex) ?: return false
        if (row.isChild) return columnIndex >= 2
        if (row.resourceType != ComposeResourceType.STRING) return columnIndex < 2
        return columnIndex >= 0
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (!isCellEditable(rowIndex, columnIndex)) return
        val row = visibleRows().getOrNull(rowIndex) ?: return
        if (row.isChild) {
            val qualifier = if (columnIndex == 2) null else qualifiers.getOrNull(columnIndex - 3)
            onItemValueEdited?.invoke(row.resourceKey, row.itemName.orEmpty(), qualifier, value?.toString().orEmpty())
            return
        }
        if (columnIndex == 0) {
            onKeyEdited?.invoke(row.key, value?.toString().orEmpty())
            return
        }
        if (columnIndex == 1) {
            val isUntranslatable = value as? Boolean ?: false
            onTranslatableEdited?.invoke(row.key, !isUntranslatable)
            return
        }
        val qualifier = if (columnIndex == 2) null else qualifiers.getOrNull(columnIndex - 3)
        onValueEdited?.invoke(row.key, qualifier, value?.toString().orEmpty())
    }

    fun setEntries(
        defaultEntries: List<ComposeStringEntry>,
        localizedEntries: Map<ComposeResourceQualifier, List<ComposeStringEntry>>,
        issues: List<ComposeResourceIssue>,
    ) {
        qualifiers = localizedEntries.keys.sortedBy(ComposeResourceQualifier::rawValue)
        val defaultByKey = defaultEntries.associateBy(ComposeStringEntry::key)
        val localizedByQualifier = localizedEntries.mapValues { (_, entries) ->
            entries.associateBy(ComposeStringEntry::key)
        }
        val keys = buildList {
            defaultEntries.forEach { add(it.key) }
            localizedEntries.values.flatten().forEach { add(it.key) }
        }.distinct()
        val issuesByKey = issues.groupBy(ComposeResourceIssue::key)

        groups = keys.map { key ->
            val defaultEntry = defaultByKey[key]
            val localeEntries = localizedByQualifier.mapValues { (_, entries) -> entries[key] }
            val type = defaultEntry?.type
                ?: localeEntries.values.firstNotNullOfOrNull { it?.type }
                ?: ComposeResourceType.STRING
            val parentIssues = issuesByKey[key].orEmpty().filter { it.itemName == null }
            val itemNames = buildList {
                defaultEntry?.items?.forEach { add(it.name) }
                localeEntries.values.filterNotNull().flatMap { it.items }.forEach { add(it.name) }
            }.distinct()
            val children = if (type == ComposeResourceType.STRING) {
                emptyList()
            } else {
                itemNames.map { itemName ->
                    TranslationRow(
                        key = key,
                        defaultValue = defaultEntry?.items?.firstOrNull { it.name == itemName }?.value,
                        localizedValues = qualifiers.associateWith { qualifier ->
                            localeEntries[qualifier]?.items?.firstOrNull { it.name == itemName }?.value
                        },
                        issues = issuesByKey[key].orEmpty().filter { it.itemName == itemName },
                        translatable = defaultEntry?.translatable ?: true,
                        resourceType = type,
                        parentKey = key,
                        itemName = itemName,
                        depth = 1,
                    )
                }
            }
            val parent = TranslationRow(
                key = key,
                defaultValue = defaultEntry?.takeIf { type == ComposeResourceType.STRING }?.value,
                localizedValues = qualifiers.associateWith { qualifier ->
                    localeEntries[qualifier]?.takeIf { type == ComposeResourceType.STRING }?.value
                },
                issues = parentIssues,
                translatable = defaultEntry?.translatable ?: true,
                resourceType = type,
                children = children,
            )
            RowGroup(parent, children)
        }
        expandedKeys.retainAll(groups.map { it.parent.key }.toSet())
        if (!hasInitializedRows) {
            expandedKeys += groups.filter { it.parent.hasChildren }.map { it.parent.key }
            hasInitializedRows = true
        }
        fireTableStructureChanged()
    }

    fun visibleRows(): List<TranslationRow> = groups.flatMap { group ->
        val matchingChildren = group.children.filter(::matches)
        if (!matches(group.parent) && matchingChildren.isEmpty()) return@flatMap emptyList()
        buildList {
            add(group.parent)
            if (expandedKeys.contains(group.parent.key) || searchQuery.isNotBlank()) addAll(matchingChildren)
        }
    }

    fun toggleExpanded(rowIndex: Int): Boolean {
        val row = visibleRows().getOrNull(rowIndex) ?: return false
        if (!row.hasChildren) return false
        if (!expandedKeys.add(row.key)) expandedKeys.remove(row.key)
        fireTableDataChanged()
        return true
    }

    fun isExpanded(key: String): Boolean = key in expandedKeys

    private var valueCountsByColumn: Map<Int, Map<String, Int>> = emptyMap()

    override fun fireTableDataChanged() {
        recalculateValueCounts()
        super.fireTableDataChanged()
    }

    override fun fireTableStructureChanged() {
        recalculateValueCounts()
        super.fireTableStructureChanged()
    }

    private fun recalculateValueCounts() {
        val counts = mutableMapOf<Int, Map<String, Int>>()
        val cols = getColumnCount()
        val currentRows = visibleRows()
        for (c in 2 until cols) {
            val qualifier = if (c == 2) null else qualifiers.getOrNull(c - 3)
            counts[c] = currentRows
                .mapNotNull { if (c == 2) it.defaultValue else it.localizedValues[qualifier] }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
        }
        valueCountsByColumn = counts
    }

    fun isValueDuplicated(columnIndex: Int, value: String): Boolean {
        if (value.isBlank()) return false
        val count = valueCountsByColumn[columnIndex]?.get(value) ?: 0
        return count > 1
    }

    fun qualifierAtColumn(columnIndex: Int): ComposeResourceQualifier? =
        if (columnIndex < 3) null else qualifiers.getOrNull(columnIndex - 3)

    private fun matches(row: TranslationRow): Boolean {
        val matchesFilter = when (filter) {
            TranslationFilter.ALL -> true
            TranslationFilter.MISSING -> row.isMissing
            TranslationFilter.COMPLETE -> row.isComplete
        }
        return matchesFilter && row.matches(searchQuery)
    }

    private fun TranslationRow.matches(query: String): Boolean {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return true
        return sequenceOf(key, itemName.orEmpty(), defaultValue.orEmpty())
            .plus(localizedValues.values.asSequence().map(String?::orEmpty))
            .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
    }

    companion object {
        const val MISSING_VALUE = "[Missing]"
    }
}
