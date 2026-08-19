package zone.ien.composemultiplatformtranslations.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import zone.ien.composemultiplatformtranslations.MyBundle
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceCatalog
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceChangeFilter
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceSet
import zone.ien.composemultiplatformtranslations.validation.ComposeResourceValidator
import zone.ien.composemultiplatformtranslations.write.ComposeResourceWriter
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

    val component: JComponent = createComponent()

    init {
        configureTable()
        searchField.emptyText.text = MyBundle.message("translation.search.placeholder")
        tableModel.onValueEdited = { key, qualifier, value -> editValue(key, qualifier, value) }
        tableModel.onKeyEdited = ::editKey
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                    val column = table.columnAtPoint(event.point)
                    if (column > 0) {
                        navigateToCell(table.rowAtPoint(event.point), column)
                    }
                }
            }
        })
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                tableModel.searchQuery = searchField.text
            }
        })
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
            }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(content, BorderLayout.CENTER)
        }
    }

    private fun configureTable() {
        table.setShowGrid(true)
        table.gridColor = JBColor(Color(210, 210, 210), Color(60, 63, 65))
        table.intercellSpacing = JBUI.size(1, 1)
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.rowHeight = 24
        table.font = Font(Font.MONOSPACED, Font.PLAIN, table.font.size)
        table.tableHeader.font = table.font.deriveFont(Font.BOLD)
        table.tableHeader.background = JBColor(Color(245, 245, 245), Color(60, 63, 65))
        table.tableHeader.foreground = JBColor(Color(80, 80, 80), Color(169, 183, 198))
        table.selectionBackground = JBColor(Color(220, 235, 255), Color(75, 90, 110))
        table.selectionForeground = JBColor(Color(30, 30, 30), Color(230, 240, 250))
        table.setDefaultRenderer(String::class.java, TranslationCellRenderer())
        table.setDefaultEditor(String::class.java, MissingAwareCellEditor())
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
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.locale-edit"),
                MyBundle.message("translation.title"),
            )
            return
        }
        reload()
    }

    private fun editKey(oldKey: String, newKey: String) {
        val resourceSet = selectedResourceSet() ?: return
        val normalizedKey = newKey.trim()
        if (normalizedKey.isEmpty()) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.invalid-key"),
                MyBundle.message("translation.title"),
            )
            return
        }
        if (!writer.renameKey(resourceSet, oldKey, normalizedKey)) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.rename-failed"),
                MyBundle.message("translation.title"),
            )
            return
        }
        reload()
    }

    private fun addString() {
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
        if (!writer.addStringResource(resourceSet, draft.key, draft.defaultValue, draft.localizedValues)) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.add-failed"),
                MyBundle.message("translation.title"),
            )
            return
        }
        reload()
    }

    private fun deleteSelected() {
        val resourceSet = selectedResourceSet() ?: return
        val row = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.visibleRows().getOrNull(it) } ?: return
        val answer = Messages.showYesNoDialog(
            project,
            MyBundle.message("translation.dialog.delete.message", row.key),
            MyBundle.message("translation.dialog.delete.title"),
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

internal data class StringResourceDraft(
    val key: String,
    val defaultValue: String,
    val localizedValues: Map<ComposeResourceQualifier, String>,
)

internal class AddStringDialog(
    project: Project,
    private val qualifiers: List<ComposeResourceQualifier>,
) : DialogWrapper(project) {

    private val keyField = JBTextField()
    private val defaultField = JBTextField()
    private val localizedFields = qualifiers.associateWith { JBTextField() }

    init {
        title = MyBundle.message("translation.dialog.add.title")
        init()
    }

    fun draft(): StringResourceDraft = StringResourceDraft(
        key = keyField.text.trim(),
        defaultValue = defaultField.text,
        localizedValues = localizedFields.mapValues { it.value.text },
    )

    override fun createCenterPanel(): JComponent = panel {
        row(MyBundle.message("translation.dialog.add.key")) {
            cell(keyField).align(Align.FILL)
        }
        row(MyBundle.message("translation.dialog.add.default")) {
            cell(defaultField).align(Align.FILL)
        }
        qualifiers.forEach { qualifier ->
            row(MyBundle.message("translation.dialog.add.locale", qualifier.displayName)) {
                cell(localizedFields.getValue(qualifier)).align(Align.FILL)
            }
        }
    }.apply {
        preferredSize = JBUI.size(420, 100 + qualifiers.size * 34)
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

private class MissingAwareCellEditor : DefaultCellEditor(JTextField()) {

    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component = super.getTableCellEditorComponent(table, value, isSelected, row, column)
        if (value == ComposeTranslationTableModel.MISSING_VALUE) {
            (component as JTextField).text = ""
        }
        return component
    }
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
        component.foreground = table.foreground
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
