package zone.ien.composemultiplatformtranslations.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceCatalog
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceChangeFilter
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceSet
import zone.ien.composemultiplatformtranslations.validation.ComposeResourceValidator
import zone.ien.composemultiplatformtranslations.write.ComposeResourceWriter
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer

private data class ResourceSetOption(val resourceSet: ComposeResourceSet) {
    override fun toString(): String = "${resourceSet.sourceSetName}: ${resourceSet.resourceRoot.path}"
}

/** Tool Window content that displays Compose Multiplatform translations. */
class ComposeTranslationToolWindow(private val project: Project) {

    private val tableModel = ComposeTranslationTableModel()
    private val table = JBTable(tableModel)
    private val searchField = JBTextField()
    private val filterCombo = ComboBox(TranslationFilter.entries.toTypedArray())
    private val resourceSetCombo = ComboBox<ResourceSetOption>()
    private val statusLabel = JBLabel()
    private val catalog = ComposeResourceCatalog(project)
    private val validator = ComposeResourceValidator()
    private val writer = ComposeResourceWriter(project)
    private val vfsConnection = project.messageBus.connect(project)
    private var resourceSets: List<ComposeResourceSet> = emptyList()

    val component: JComponent = createComponent()

    init {
        configureTable()
        tableModel.onValueEdited = { key, qualifier, value -> editValue(key, qualifier, value) }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                    navigateToCell(table.rowAtPoint(event.point), table.columnAtPoint(event.point))
                }
            }
        })
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                tableModel.searchQuery = searchField.text
            }
        })
        filterCombo.addActionListener {
            tableModel.filter = filterCombo.selectedItem as? TranslationFilter ?: TranslationFilter.ALL
        }
        resourceSetCombo.addActionListener { renderSelectedResourceSet() }
        vfsConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val changedPaths = events.map(VFileEvent::getPath)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    val resourceRootPath = selectedResourceSet()?.resourceRoot?.path ?: return@invokeLater
                    if (changedPaths.any { path ->
                            ComposeResourceChangeFilter.isRelevant(resourceRootPath, path)
                        }
                    ) {
                        reload()
                    }
                }
            }
        })
        reload()
    }

    private fun createComponent(): JComponent = panel {
        row {
            label("Resource set")
            cell(resourceSetCombo).resizableColumn()
            button("Add string") { addString() }
            button("Delete selected") { deleteSelected() }
            button("Refresh") { reload() }
        }
        row {
            label("Search")
            cell(searchField).resizableColumn()
            label("Filter")
            cell(filterCombo)
        }
        row {
            cell(JScrollPane(table))
                .align(Align.FILL)
                .resizableColumn()
        }.resizableRow()
        row {
            cell(statusLabel)
        }
    }

    private fun configureTable() {
        table.setShowGrid(true)
        table.gridColor = JBColor.border()
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.rowHeight = 24
        table.setDefaultRenderer(String::class.java, TranslationCellRenderer(tableModel))
    }

    private fun reload() {
        resourceSets = catalog.load()
        resourceSetCombo.model = DefaultComboBoxModel(resourceSets.map(::ResourceSetOption).toTypedArray())
        renderSelectedResourceSet()
    }

    private fun renderSelectedResourceSet() {
        val resourceSet = (resourceSetCombo.selectedItem as? ResourceSetOption)?.resourceSet
        if (resourceSet == null) {
            tableModel.setEntries(emptyList(), emptyMap(), emptyList())
            statusLabel.text = "composeResources/values/strings.xml을 찾을 수 없습니다."
            return
        }

        val defaultEntries = resourceSet.defaultDocument?.entries.orEmpty()
        val localizedEntries = resourceSet.localizedDocuments.associate {
            it.descriptor.qualifier to it.entries
        }
        val issues = validator.validate(defaultEntries, localizedEntries)
        tableModel.setEntries(defaultEntries, localizedEntries, issues)
        statusLabel.text = "${resourceSet.sourceSetName} · ${tableModel.rowCount}개 리소스 · ${issues.size}개 경고"
    }

    private fun selectedResourceSet(): ComposeResourceSet? =
        (resourceSetCombo.selectedItem as? ResourceSetOption)?.resourceSet

    private fun editValue(
        key: String,
        qualifier: zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier?,
        value: String,
    ) {
        val resourceSet = selectedResourceSet() ?: return
        val document = if (qualifier == null) {
            resourceSet.defaultDocument
        } else {
            resourceSet.documentFor(qualifier)
        }
        if (document == null || !writer.upsertValue(document, key, value)) {
            Messages.showErrorDialog(project, "선택한 locale의 strings.xml을 수정할 수 없습니다.", "Translation Editor")
            return
        }
        reload()
    }

    private fun addString() {
        val document = selectedResourceSet()?.defaultDocument
        if (document == null) {
            Messages.showErrorDialog(project, "기본 values/strings.xml을 먼저 추가해 주세요.", "Translation Editor")
            return
        }

        val key = Messages.showInputDialog(project, "Resource key", "Add String Resource", null)?.trim().orEmpty()
        if (key.isEmpty()) return
        val value = Messages.showInputDialog(project, "Default value", "Add String Resource", null) ?: return
        if (!writer.addValue(document, key, value)) {
            Messages.showErrorDialog(project, "이미 존재하는 key이거나 XML을 수정할 수 없습니다.", "Translation Editor")
            return
        }
        reload()
    }

    private fun deleteSelected() {
        val resourceSet = selectedResourceSet() ?: return
        val row = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.visibleRows().getOrNull(it) } ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "'${row.key}'를 모든 locale의 strings.xml에서 삭제할까요?",
            "Delete String Resource",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return

        writer.removeKey(resourceSet, row.key)
        reload()
    }

    private fun navigateToCell(rowIndex: Int, columnIndex: Int) {
        val resourceSet = selectedResourceSet() ?: return
        val row = tableModel.visibleRows().getOrNull(rowIndex) ?: return
        val qualifier = tableModel.qualifierAtColumn(columnIndex)
        val document = if (qualifier == null) {
            resourceSet.defaultDocument
        } else {
            resourceSet.documentFor(qualifier)
        } ?: return
        val psiFile = PsiManager.getInstance(project).findFile(document.descriptor.file) ?: return
        val stringTag = (psiFile as? com.intellij.psi.xml.XmlFile)?.rootTag
            ?.findSubTags("string")
            ?.firstOrNull { it.getAttributeValue("name") == row.key }
            ?: return
        OpenFileDescriptor(project, document.descriptor.file, stringTag.textRange.startOffset).navigate(true)
    }
}

private class TranslationCellRenderer(
    private val tableModel: ComposeTranslationTableModel,
) : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val missing = value == ComposeTranslationTableModel.MISSING_VALUE
        val hasIssue = tableModel.visibleRows().getOrNull(row)?.issues?.isNotEmpty() == true

        if (!isSelected) {
            component.background = if (hasIssue) Color(255, 248, 225) else table.background
            component.foreground = if (missing) JBColor.RED else table.foreground
        }
        component.font = table.font
        if (missing) {
            component.font = component.font.deriveFont(Font.ITALIC)
        }
        return component
    }
}
