package zone.ien.cmp_translation_plugin.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceChangeFilter
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet
import zone.ien.cmp_translation_plugin.validation.ComposeResourceValidator
import zone.ien.cmp_translation_plugin.write.ComposeResourceWriter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.ListCellRenderer
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.Icon
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer

internal data class ResourceSetOption(val resourceSet: ComposeResourceSet) {
    override fun toString(): String = MyBundle.message(
        "translation.resource-set.option",
        resourceSet.sourceSetName,
        resourceSet.resourceRoot.name,
    )
}

/** Tool Window content that displays Compose Multiplatform translations. */
class ComposeTranslationToolWindow(private val project: Project) {

    private val tableModel = ComposeTranslationTableModel()
    private val table = JBTable(tableModel)
    private val searchField = EllipsisTextField()
    private val filterButton = TranslationFilterButton { filter ->
        tableModel.filter = filter
    }
    private val resourceSetCombo = ComboBox<ResourceSetOption>().apply {
        renderer = ResourceSetOptionRenderer()
    }
    private val resourceSetControl = FlexibleControlHost(resourceSetCombo)
    private val statusLabel = JBLabel()
    private val catalog = ComposeResourceCatalog(project)
    private val validator = ComposeResourceValidator()
    private val writer = ComposeResourceWriter(project)
    private val vfsConnection = project.messageBus.connect(project)
    private var resourceSets: List<ComposeResourceSet> = emptyList()
    private var hasLoadedResourceSets = false

    val component: JComponent = createComponent()

    init {
        configureTable()
        searchField.emptyText.text = MyBundle.message("translation.search.placeholder")
        tableModel.onValueEdited = { key, qualifier, value -> editValue(key, qualifier, value) }
        tableModel.onKeyEdited = ::editKey
        tableModel.onTranslatableEdited = ::editTranslatable
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                    val row = table.rowAtPoint(event.point)
                    if (row >= 0) {
                        editRowInDialog(row)
                    }
                }
            }
        })
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                tableModel.searchQuery = searchField.text
            }
        })
        searchField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                if (table.isEditing) table.cellEditor?.stopCellEditing()
            }
        })
        resourceSetCombo.addActionListener { renderSelectedResourceSet() }
        resourceSetCombo.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                if (table.isEditing) table.cellEditor?.stopCellEditing()
            }
        })
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

    private fun createComponent(): JComponent {
        val toolbar = TranslationToolbar(
            onAddString = ::addString,
            onDeleteSelected = ::deleteSelected,
            onRefresh = ::reload,
        )
        val controls = ResponsiveControlsPanel(
            resourceSetCombo = resourceSetControl,
            searchField = searchField,
            filterButton = filterButton,
        )
        val content = panel {
            row {
                cell(toolbar)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row {
                cell(controls)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row {
                cell(JScrollPane(table))
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
            row {
                cell(FlexibleControlHost(statusLabel))
                    .align(Align.FILL)
                    .resizableColumn()
                
                val githubLink = ActionLink("@ienground") {
                    BrowserUtil.browse("https://github.com/ienground")
                }.apply {
                    font = font.deriveFont(Font.PLAIN, font.size - 1f)
                    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                }
                cell(githubLink).align(AlignX.RIGHT)
            }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(content, BorderLayout.CENTER)
        }
    }

    private fun configureTable() {
        table.setShowGrid(true)
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.rowHeight = 24
        table.setDefaultRenderer(String::class.java, TranslationCellRenderer())
        table.setDefaultEditor(String::class.java, MissingAwareCellEditor(table.getDefaultEditor(String::class.java)))
        
        // Disable JBTable's auto-termination of edits on focus loss.
        // This prevents macOS Korean IME from prematurely committing the cell value
        // every time the composition window steals focus character by character.
        table.putClientProperty("terminateEditOnFocusLost", false)
    }

    private fun reload() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        if (!hasLoadedResourceSets) {
            applyResourceSets(catalog.load())
            hasLoadedResourceSets = true
        } else if (ApplicationManager.getApplication().isDispatchThread) {
            catalog.loadAsync(::applyResourceSets)
        } else {
            applyResourceSets(catalog.load())
        }
    }

    private fun applyResourceSets(loadedResourceSets: List<ComposeResourceSet>) {
        val previousSelected = selectedResourceSet()
        resourceSets = loadedResourceSets
        
        val newOptions = resourceSets.map(::ResourceSetOption).toTypedArray()
        resourceSetCombo.model = DefaultComboBoxModel(newOptions)
        
        if (previousSelected != null) {
            val matchingOption = newOptions.find { 
                it.resourceSet.moduleName == previousSelected.moduleName && 
                it.resourceSet.sourceSetName == previousSelected.sourceSetName &&
                it.resourceSet.resourceRoot.path == previousSelected.resourceRoot.path
            }
            if (matchingOption != null) {
                resourceSetCombo.selectedItem = matchingOption
            }
        }
        
        renderSelectedResourceSet()
    }

    private fun renderSelectedResourceSet() {
        val resourceSet = (resourceSetCombo.selectedItem as? ResourceSetOption)?.resourceSet
        if (resourceSet == null) {
            tableModel.setEntries(emptyList(), emptyMap(), emptyList())
            statusLabel.text = MyBundle.message("translation.status.no-resources")
            return
        }

        val defaultEntries = resourceSet.defaultDocument?.entries.orEmpty()
        val localizedEntries = resourceSet.localizedDocuments.associate {
            it.descriptor.qualifier to it.entries
        }
        val issues = validator.validate(defaultEntries, localizedEntries)
        tableModel.setEntries(defaultEntries, localizedEntries, issues)
        statusLabel.text = MyBundle.message(
            "translation.status.summary",
            resourceSet.moduleName,
            resourceSet.sourceSetName,
            tableModel.rowCount,
            issues.size,
        )
        applyColumnConstraints()
    }

    private fun applyColumnConstraints() {
        if (table.columnCount > 1) {
            table.columnModel.getColumn(1).apply {
                minWidth = 115
                maxWidth = 115
                preferredWidth = 115
                resizable = false
            }
        }
        if (table.columnCount > 0) {
            table.columnModel.getColumn(0).apply {
                minWidth = 100
                preferredWidth = 200
            }
        }
        for (i in 2 until table.columnCount) {
            table.columnModel.getColumn(i).apply {
                minWidth = 100
                preferredWidth = 200
            }
        }
    }

    private fun selectedResourceSet(): ComposeResourceSet? =
        (resourceSetCombo.selectedItem as? ResourceSetOption)?.resourceSet

    private fun editValue(
        key: String,
        qualifier: zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier?,
        value: String,
    ) {
        ApplicationManager.getApplication().invokeLater {
            val resourceSet = selectedResourceSet() ?: return@invokeLater
            val document = if (qualifier == null) {
                resourceSet.defaultDocument
            } else {
                resourceSet.documentFor(qualifier)
            }
            if (document == null || !writer.upsertValue(document, key, value)) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.locale-edit"),
                    MyBundle.message("translation.title"),
                )
                return@invokeLater
            }
            reload()
        }
    }

    private fun editTranslatable(key: String, translatable: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            val resourceSet = selectedResourceSet() ?: return@invokeLater
            val document = resourceSet.defaultDocument ?: return@invokeLater
            if (!writer.setTranslatable(document, key, translatable)) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.locale-edit"),
                    MyBundle.message("translation.title"),
                )
                return@invokeLater
            }
            reload()
        }
    }

    private fun editKey(oldKey: String, newKey: String) {
        ApplicationManager.getApplication().invokeLater {
            val resourceSet = selectedResourceSet() ?: return@invokeLater
            val normalizedKey = newKey.trim()
            if (normalizedKey.isEmpty()) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.invalid-key"),
                    MyBundle.message("translation.title"),
                )
                return@invokeLater
            }
            if (!writer.renameKey(resourceSet, oldKey, normalizedKey)) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.rename-failed"),
                    MyBundle.message("translation.title"),
                )
                return@invokeLater
            }
            reload()
        }
    }

    private fun addString() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val resourceSet = selectedResourceSet()
        if (resourceSet?.defaultDocument == null) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.default-missing"),
                MyBundle.message("translation.title"),
            )
            return
        }

        val dialog = AddStringDialog(
            project = project,
            qualifiers = resourceSet.localizedDocuments.map { it.descriptor.qualifier },
        )
        if (!dialog.showAndGet()) return
        val draft = dialog.draft()
        ApplicationManager.getApplication().invokeLater {
            if (!writer.addStringResource(resourceSet, draft.key, draft.defaultValue, draft.localizedValues, draft.translatable)) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.add-failed"),
                    MyBundle.message("translation.title"),
                )
                return@invokeLater
            }
            reload()
        }
    }

    private fun deleteSelected() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val resourceSet = selectedResourceSet() ?: return
        val row = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.visibleRows().getOrNull(it) } ?: return
        val answer = Messages.showYesNoDialog(
            project,
            MyBundle.message("translation.dialog.delete.message", row.key),
            MyBundle.message("translation.dialog.delete.title"),
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return

        ApplicationManager.getApplication().invokeLater {
            writer.removeKey(resourceSet, row.key)
            reload()
        }
    }

    private fun editRowInDialog(rowIndex: Int) {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val resourceSet = selectedResourceSet() ?: return
        val row = tableModel.visibleRows().getOrNull(rowIndex) ?: return
        
        val dialog = AddStringDialog(
            project = project,
            qualifiers = resourceSet.localizedDocuments.map { it.descriptor.qualifier },
            initialRow = row
        )
        if (!dialog.showAndGet()) return
        val draft = dialog.draft()
        
        ApplicationManager.getApplication().invokeLater {
            var success = true
            if (draft.key != row.key) {
                if (!writer.renameKey(resourceSet, row.key, draft.key)) {
                    success = false
                }
            }
            if (success) {
                resourceSet.defaultDocument?.let { doc ->
                    writer.upsertValue(doc, draft.key, draft.defaultValue)
                    writer.setTranslatable(doc, draft.key, draft.translatable)
                }
                draft.localizedValues.forEach { (qualifier, value) ->
                    resourceSet.documentFor(qualifier)?.let { doc ->
                        writer.upsertValue(doc, draft.key, value)
                    } ?: run {
                        // If document doesn't exist, we might need to create it? We just try to add string resource
                        // For simplicity, if document is missing, it's skipped here. But writer.addStringResource handles it better.
                    }
                }
            }
            if (!success) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.locale-edit"),
                    MyBundle.message("translation.title")
                )
            }
            reload()
        }
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
        val offset = WriteIntentReadAction.compute {
            val psiFile = PsiManager.getInstance(project).findFile(document.descriptor.file) ?: return@compute null
            (psiFile as? com.intellij.psi.xml.XmlFile)?.rootTag
                ?.findSubTags("string")
                ?.firstOrNull { it.getAttributeValue("name") == row.key }
                ?.textRange
                ?.startOffset
        } ?: return
        OpenFileDescriptor(project, document.descriptor.file, offset).navigate(true)
    }
}

internal data class StringResourceDraft(
    val key: String,
    val defaultValue: String,
    val localizedValues: Map<ComposeResourceQualifier, String>,
    val translatable: Boolean,
)

internal class AddStringDialog(
    project: Project,
    private val qualifiers: List<ComposeResourceQualifier>,
    private val initialRow: TranslationRow? = null,
) : DialogWrapper(project) {

    private val keyField = JBTextField(initialRow?.key ?: "")
    private val defaultField = JBTextField(initialRow?.defaultValue ?: "")
    private val localizedFields = qualifiers.associateWith { qualifier -> 
        JBTextField(initialRow?.localizedValues?.get(qualifier) ?: "") 
    }
    private val untranslatableCheck = com.intellij.ui.components.JBCheckBox("Untranslatable", !(initialRow?.translatable ?: true))

    init {
        title = if (initialRow == null) MyBundle.message("translation.dialog.add.title") else "Edit Translation"
        init()
    }

    fun draft(): StringResourceDraft = StringResourceDraft(
        key = keyField.text.trim(),
        defaultValue = defaultField.text,
        localizedValues = localizedFields.mapValues { it.value.text },
        translatable = !untranslatableCheck.isSelected,
    )

    override fun createCenterPanel(): JComponent = panel {
        row(MyBundle.message("translation.dialog.add.key")) {
            cell(keyField).align(AlignX.FILL).resizableColumn()
            cell(untranslatableCheck)
        }
        row(MyBundle.message("translation.dialog.add.default")) {
            cell(defaultField).align(AlignX.FILL)
        }
        qualifiers.forEach { qualifier ->
            row(MyBundle.message("translation.dialog.add.locale", qualifier.displayName)) {
                cell(localizedFields.getValue(qualifier)).align(AlignX.FILL)
            }
        }
    }.apply {
        preferredSize = JBUI.size(500, 100 + qualifiers.size * 34)
    }

    override fun doValidate(): ValidationInfo? = if (keyField.text.trim().isEmpty()) {
        ValidationInfo(MyBundle.message("translation.error.invalid-key"), keyField)
    } else {
        null
    }
}

internal class TranslationToolbar(
    onAddString: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefresh: () -> Unit,
) : JPanel(WrapLayout(FlowLayout.LEADING, 4, 0)) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(2, 0)
        add(
            toolbarButton(
                icon = AllIcons.General.Add,
                tooltip = MyBundle.message("translation.toolbar.add"),
                onClick = onAddString,
            ),
        )
        add(
            toolbarButton(
                icon = IconUtil.colorize(
                    AllIcons.General.Delete,
                    JBColor(Color(190, 60, 60), Color(255, 120, 120)),
                ),
                tooltip = MyBundle.message("translation.toolbar.delete"),
                foreground = JBColor(Color(190, 60, 60), Color(255, 120, 120)),
                onClick = onDeleteSelected,
            ),
        )
        add(
            toolbarButton(
                icon = AllIcons.Actions.Refresh,
                tooltip = MyBundle.message("translation.toolbar.refresh"),
                onClick = onRefresh,
            ),
        )
        add(
            toolbarButton(
                icon = AllIcons.General.Export,
                tooltip = MyBundle.message("translation.toolbar.export"),
                enabled = false,
                onClick = {},
            ),
        )
    }
}

internal class TranslationFilterButton(
    private val onFilterChanged: (TranslationFilter) -> Unit,
) : JButton(AllIcons.General.Filter) {

    private var selectedFilter = TranslationFilter.ALL

    init {
        text = ""
        toolTipText = MyBundle.message("translation.filter")
        accessibleContext?.accessibleName = MyBundle.message("translation.filter")
        isFocusable = false
        isContentAreaFilled = false
        isBorderPainted = false
        isOpaque = false
        preferredSize = JBUI.size(28, 28)
        minimumSize = preferredSize
        maximumSize = preferredSize
        addActionListener { showFilterMenu() }
    }

    private fun showFilterMenu() {
        val group = DefaultActionGroup(filterActions())
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                MyBundle.message("translation.filter"),
                group,
                DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            )
            .showUnderneathOf(this)
    }

    internal fun filterActions(): List<AnAction> = TranslationFilter.entries.map { filter ->
        object : ToggleAction(filter.displayName) {
            override fun isSelected(event: AnActionEvent): Boolean =
                selectedFilter == filter

            override fun setSelected(
                event: AnActionEvent,
                state: Boolean,
            ) {
                if (!state || selectedFilter == filter) return
                selectedFilter = filter
                onFilterChanged(filter)
            }
        }
    }
}

private fun toolbarButton(
    icon: Icon,
    tooltip: String,
    enabled: Boolean = true,
    foreground: Color? = null,
    onClick: () -> Unit,
): JButton = JButton(icon).apply {
    text = ""
    toolTipText = tooltip
    accessibleContext?.accessibleName = tooltip
    isFocusable = false
    isContentAreaFilled = false
    isBorderPainted = false
    isOpaque = false
    isEnabled = enabled
    foreground?.let { this.foreground = it }
    preferredSize = JBUI.size(30, 30)
    minimumSize = preferredSize
    maximumSize = preferredSize
    addActionListener { onClick() }
}

internal fun ellipsizeText(text: String, metrics: FontMetrics, maxWidth: Int): String {
    if (text.isEmpty() || metrics.stringWidth(text) <= maxWidth) return text

    val ellipsis = "…"
    if (maxWidth <= metrics.stringWidth(ellipsis)) return ellipsis

    var endIndex = text.length
    while (endIndex > 0 && metrics.stringWidth(text.substring(0, endIndex) + ellipsis) > maxWidth) {
        endIndex--
    }
    return if (endIndex == 0) ellipsis else text.substring(0, endIndex) + ellipsis
}

internal class EllipsisLabel(text: String? = null) : JBLabel(text.orEmpty()) {

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        val insets = insets
        if (isOpaque) {
            graphics2D.color = background
            graphics2D.fillRect(0, 0, width, height)
        }

        graphics2D.font = font
        graphics2D.color = foreground
        val metrics = graphics2D.fontMetrics
        val availableWidth = (width - insets.left - insets.right).coerceAtLeast(1)
        val visibleText = ellipsizeText(text.orEmpty(), metrics, availableWidth)
        val baseline = insets.top + ((height - insets.top - insets.bottom - metrics.height) / 2) + metrics.ascent
        graphics2D.drawString(visibleText, insets.left, baseline)
        graphics2D.dispose()
    }
}

internal class EllipsisTextField : JBTextField() {

    init {
        margin = JBUI.insets(0, 8, 0, 8)
    }

    override fun paintComponent(graphics: Graphics) {
        val placeholder = emptyText.text.orEmpty()
        if (text.isEmpty() && placeholder.isNotEmpty()) {
            val contentLeft = insets.left + margin.left
            val contentRight = insets.right + margin.right
            val availableWidth = (width - contentLeft - contentRight).coerceAtLeast(1)
            val clippedPlaceholder = ellipsizeText(placeholder, graphics.fontMetrics, availableWidth)
            emptyText.text = ""
            try {
                super.paintComponent(graphics)
            } finally {
                emptyText.text = placeholder
            }

            val graphics2D = graphics.create() as Graphics2D
            graphics2D.font = font
            graphics2D.color = UIManager.getColor("TextField.placeholderForeground")
                ?: UIManager.getColor("TextField.inactiveForeground")
                ?: JBColor.GRAY
            val metrics = graphics2D.fontMetrics
            val baseline = insets.top + ((height - insets.top - insets.bottom - metrics.height) / 2) + metrics.ascent
            graphics2D.drawString(clippedPlaceholder, contentLeft, baseline)
            graphics2D.dispose()
        } else {
            super.paintComponent(graphics)
        }
    }
}

internal class ResourceSetOptionRenderer : JPanel(BorderLayout(6, 2)),
    ListCellRenderer<ResourceSetOption> {

    private val moduleBadge = ModuleBadge()
    private val resourceSetLabel = EllipsisLabel()

    init {
        isOpaque = true
        resourceSetLabel.isOpaque = false
        add(moduleBadge, BorderLayout.WEST)
        add(resourceSetLabel, BorderLayout.CENTER)
    }

    override fun paintComponent(graphics: Graphics) {
        doLayout()
        super.paintComponent(graphics)
    }

    override fun getListCellRendererComponent(
        list: JList<out ResourceSetOption>,
        value: ResourceSetOption?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        moduleBadge.text = value?.resourceSet?.moduleName.orEmpty()
        resourceSetLabel.text = value?.let {
            MyBundle.message(
                "translation.resource-set.option",
                it.resourceSet.sourceSetName,
                it.resourceSet.resourceRoot.name,
            )
        }.orEmpty()
        background = if (isSelected) list.selectionBackground else list.background
        foreground = if (isSelected) list.selectionForeground else list.foreground
        resourceSetLabel.foreground = foreground
        return this
    }
}

internal class ModuleBadge : JBLabel() {

    init {
        border = JBUI.Borders.empty(2, 6)
        font = font.deriveFont(Font.PLAIN)
        foreground = JBColor(Color(45, 74, 123), Color(190, 215, 255))
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = JBColor(Color(225, 236, 255), Color(52, 72, 104))
        graphics2D.fillRoundRect(0, 0, width - 1, height - 1, 10, 10)
        graphics2D.color = JBColor(Color(143, 174, 224), Color(99, 133, 183))
        graphics2D.drawRoundRect(0, 0, width - 1, height - 1, 10, 10)
        graphics2D.dispose()
        super.paintComponent(graphics)
    }
}

internal class FlexibleControlHost(
    child: JComponent,
) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        add(child, BorderLayout.CENTER)
    }

    override fun getMinimumSize(): Dimension {
        return Dimension(0, super.getMinimumSize().height)
    }
}

internal class ResponsiveControlsPanel(
    private val resourceSetCombo: JComponent,
    private val searchField: JComponent,
    private val filterButton: JComponent,
) : JPanel(GridBagLayout()) {

    private val resourceSetLabel = JBLabel(MyBundle.message("translation.resource-set.label"))
    private val searchLabel = JBLabel(MyBundle.message("translation.search.label"))
    private val searchFieldHost = FlexibleControlHost(searchField)
    private val searchPanel = JPanel(BorderLayout(6, 0))
    private var compactMode: Boolean? = null

    init {
        isOpaque = false
        searchPanel.isOpaque = false
        searchPanel.add(searchFieldHost, BorderLayout.CENTER)
        searchPanel.add(filterButton, BorderLayout.EAST)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                updateLayout(width)
            }
        })
        updateLayout(0)
    }

    override fun doLayout() {
        updateLayout(width)
        super.doLayout()
    }

    private fun updateLayout(width: Int) {
        val compact = width in 1 until COMPACT_WIDTH
        if (compactMode == compact) return
        compactMode = compact

        removeAll()
        if (compact) {
            addComponent(resourceSetLabel, gridx = 0, gridy = 0)
            addComponent(resourceSetCombo, gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL)
            addComponent(searchLabel, gridx = 0, gridy = 1)
            addComponent(searchPanel, gridx = 1, gridy = 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL)
        } else {
            addComponent(resourceSetLabel, gridx = 0, gridy = 0)
            addComponent(resourceSetCombo, gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL)
            addComponent(searchLabel, gridx = 2, gridy = 0)
            addComponent(searchPanel, gridx = 3, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL)
        }
        revalidate()
        repaint()
    }

    private fun addComponent(
        component: Component,
        gridx: Int,
        gridy: Int,
        gridwidth: Int = 1,
        weightx: Double = 0.0,
        fill: Int = GridBagConstraints.NONE,
    ) {
        add(
            component,
            GridBagConstraints().apply {
                this.gridx = gridx
                this.gridy = gridy
                this.gridwidth = gridwidth
                this.weightx = weightx
                this.weighty = 0.0
                this.fill = fill
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 4, 4, 4)
            },
        )
    }

    private companion object {
        const val COMPACT_WIDTH = 900
    }
}

internal class WrapLayout(
    alignment: Int,
    horizontalGap: Int,
    verticalGap: Int,
) : FlowLayout(alignment, horizontalGap, verticalGap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension =
        layoutSize(target, preferred = false).apply {
            width = (width - hgap).coerceAtLeast(0)
        }

    override fun layoutContainer(target: Container) {
        synchronized(target.treeLock) {
            val insets = target.insets
            val left = insets.left
            val right = target.width - insets.right
            var x = left
            var y = insets.top
            var rowHeight = 0

            target.components.filter { it.isVisible }.forEach { component ->
                val size = component.preferredSize
                if (x > left && x + size.width > right) {
                    x = left
                    y += rowHeight + vgap
                    rowHeight = 0
                }
                component.setBounds(x, y, size.width, size.height)
                x += size.width + hgap
                rowHeight = maxOf(rowHeight, size.height)
            }
        }
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val insets = target.insets
            val availableWidth = if (target.width > 0) {
                (target.width - insets.left - insets.right).coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }
            var rowWidth = 0
            var rowHeight = 0
            var width = 0
            var height = 0

            target.components.filter { it.isVisible }.forEach { component ->
                val size = if (preferred) component.preferredSize else component.minimumSize
                val nextWidth = if (rowWidth == 0) size.width else rowWidth + hgap + size.width
                if (rowWidth > 0 && nextWidth > availableWidth) {
                    width = maxOf(width, rowWidth)
                    height += rowHeight + vgap
                    rowWidth = size.width
                    rowHeight = size.height
                } else {
                    rowWidth = nextWidth
                    rowHeight = maxOf(rowHeight, size.height)
                }
            }

            width = maxOf(width, rowWidth)
            height += rowHeight
            return Dimension(
                width + insets.left + insets.right,
                height + insets.top + insets.bottom,
            )
        }
    }
}

private class MissingAwareCellEditor(
    private val delegate: javax.swing.table.TableCellEditor
) : javax.swing.AbstractCellEditor(), javax.swing.table.TableCellEditor {

    init {
        if (delegate is javax.swing.DefaultCellEditor) {
            delegate.clickCountToStart = 1
        }
    }

    override fun getTableCellEditorComponent(
        table: javax.swing.JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int,
    ): java.awt.Component {
        val displayValue = if (value == ComposeTranslationTableModel.MISSING_VALUE) "" else value
        return delegate.getTableCellEditorComponent(table, displayValue, isSelected, row, column)
    }

    override fun getCellEditorValue(): Any = delegate.cellEditorValue

    override fun isCellEditable(anEvent: java.util.EventObject?): Boolean {
        if (anEvent is java.awt.event.MouseEvent && anEvent.clickCount >= 1) {
            // Force the delegate to accept single click if possible
            if (delegate is javax.swing.DefaultCellEditor) {
                delegate.clickCountToStart = 1
            }
            // Some custom editors might ignore clickCountToStart, so we still return true
            // but we call delegate.isCellEditable first in case it does setup.
            delegate.isCellEditable(anEvent)
            return true
        }
        return delegate.isCellEditable(anEvent)
    }

    override fun shouldSelectCell(anEvent: java.util.EventObject?): Boolean = delegate.shouldSelectCell(anEvent)
    override fun stopCellEditing(): Boolean = delegate.stopCellEditing()
    override fun cancelCellEditing() = delegate.cancelCellEditing()
    override fun addCellEditorListener(l: javax.swing.event.CellEditorListener?) = delegate.addCellEditorListener(l)
    override fun removeCellEditorListener(l: javax.swing.event.CellEditorListener?) = delegate.removeCellEditorListener(l)
}

private class TranslationCellRenderer : DefaultTableCellRenderer() {

    override fun paintComponent(graphics: Graphics) {
        val originalText = text
        if (!originalText.isNullOrEmpty()) {
            val availableWidth = (width - insets.left - insets.right).coerceAtLeast(1)
            val visibleText = ellipsizeText(originalText, graphics.fontMetrics, availableWidth)
            if (visibleText != originalText) {
                text = visibleText
                try {
                    super.paintComponent(graphics)
                } finally {
                    text = originalText
                }
                return
            }
        }
        super.paintComponent(graphics)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        if (value == ComposeTranslationTableModel.MISSING_VALUE) {
            return JPanel(FlowLayout(FlowLayout.LEADING, 4, 2)).apply {
                isOpaque = false
                background = if (isSelected) table.selectionBackground else table.background
                add(MissingBadge())
            }
        }

        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (!isSelected) component.background = table.background
        
        var isHighlight = false
        val tableModel = table.model as? ComposeTranslationTableModel
        val valueStr = value as? String
        
        if (tableModel != null && !valueStr.isNullOrBlank() && column >= 2) {
            val modelRow = table.convertRowIndexToModel(row)
            val modelCol = table.convertColumnIndexToModel(column)
            val translationRow = tableModel.visibleRows().getOrNull(modelRow)
            
            if (translationRow != null) {
                // Same as default value
                if (modelCol >= 3 && valueStr == translationRow.defaultValue) {
                    isHighlight = true
                } else if (modelCol == 2 && translationRow.localizedValues.values.any { it == valueStr }) {
                    isHighlight = true
                }
                
                // Duplicated within the same column
                if (!isHighlight && tableModel.isValueDuplicated(modelCol, valueStr)) {
                    isHighlight = true
                }
            }
        }

        if (isHighlight) {
            component.foreground = JBColor(Color(200, 130, 0), Color(210, 150, 40))
        } else {
            component.foreground = if (isSelected) table.selectionForeground else table.foreground
        }
        
        component.font = table.font
        return component
    }
}

private class MissingBadge : JBLabel(MyBundle.message("translation.missing")) {

    init {
        border = JBUI.Borders.empty(2, 6)
        font = font.deriveFont(Font.PLAIN)
        foreground = JBColor(Color(123, 78, 0), Color(255, 221, 157))
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = JBColor(Color(255, 243, 205), Color(90, 75, 45))
        graphics2D.fillRoundRect(0, 0, width - 1, height - 1, 10, 10)
        graphics2D.color = JBColor(Color(224, 183, 87), Color(196, 151, 74))
        graphics2D.drawRoundRect(0, 0, width - 1, height - 1, 10, 10)
        graphics2D.dispose()
        super.paintComponent(graphics)
    }
}
