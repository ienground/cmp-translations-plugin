package zone.ien.cmp_translation_plugin.editor

import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
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
) {

    val isComplete: Boolean
        get() = defaultValue?.isNotBlank() == true &&
            (!translatable || localizedValues.values.all { !it.isNullOrBlank() }) &&
            issues.isEmpty()

    val isMissing: Boolean
        get() = !isComplete
}

/** Table model for the translation matrix shown in the tool window. */
class ComposeTranslationTableModel : AbstractTableModel() {

    private var rows: List<TranslationRow> = emptyList()
    private var qualifiers: List<ComposeResourceQualifier> = emptyList()

    var onValueEdited: ((key: String, qualifier: ComposeResourceQualifier?, value: String) -> Unit)? = null
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

    override fun getColumnCount(): Int = 3 + qualifiers.size

    override fun getColumnName(column: Int): String = when (column) {
        0 -> MyBundle.message("translation.table.key")
        1 -> "Untranslatable"
        2 -> MyBundle.message("translation.table.default")
        else -> qualifiers[column - 3].displayName
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        1 -> java.lang.Boolean::class.java
        else -> String::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = visibleRows()[rowIndex]
        return when (columnIndex) {
            0 -> row.key
            1 -> !row.translatable
            2 -> row.defaultValue ?: MISSING_VALUE
            else -> row.localizedValues[qualifiers[columnIndex - 3]] ?: MISSING_VALUE
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex >= 0

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (!isCellEditable(rowIndex, columnIndex)) return
        val row = visibleRows().getOrNull(rowIndex) ?: return
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
        rows = keys.map { key ->
            TranslationRow(
                key = key,
                defaultValue = defaultByKey[key]?.value,
                localizedValues = qualifiers.associateWith { qualifier ->
                    localizedByQualifier[qualifier]?.get(key)?.value
                },
                issues = issuesByKey[key].orEmpty(),
                translatable = defaultByKey[key]?.translatable ?: true,
            )
        }
        fireTableStructureChanged()
    }

    fun visibleRows(): List<TranslationRow> = rows.filter { row ->
        val matchesFilter = when (filter) {
            TranslationFilter.ALL -> true
            TranslationFilter.MISSING -> row.isMissing
            TranslationFilter.COMPLETE -> row.isComplete
        }
        matchesFilter && row.matches(searchQuery)
    }

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

    private fun TranslationRow.matches(query: String): Boolean {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return true
        return sequenceOf(key, defaultValue.orEmpty())
            .plus(localizedValues.values.asSequence().map(String?::orEmpty))
            .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
    }

    companion object {
        const val MISSING_VALUE = "[Missing]"
    }
}
