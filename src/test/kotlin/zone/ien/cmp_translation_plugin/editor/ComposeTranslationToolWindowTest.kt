package zone.ien.cmp_translation_plugin.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Insets
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.ListCellRenderer
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import java.awt.Container
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType
import zone.ien.cmp_translation_plugin.resource.ComposeResourceItem
import org.junit.Assert.assertNotEquals

class ComposeTranslationToolWindowTest : BasePlatformTestCase() {

    fun testToolbarUsesFlatIconActionsAndKeepsOuterMargin() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val toolbar = descendants(content).filterIsInstance<TranslationToolbar>().single()
        val buttons = descendants(toolbar).filterIsInstance<JButton>()
        val addButton = buttons.single { it.toolTipText == "Add string" }
        val deleteButton = buttons.single { it.toolTipText == "Delete selected" }
        val refreshButton = buttons.single { it.toolTipText == "Refresh" }
        val exportButton = buttons.single { it.toolTipText == "Export" }

        assertNotNull(content.border)
        assertEquals("", addButton.text)
        assertEquals("", deleteButton.text)
        assertEquals("", refreshButton.text)
        assertEquals("", exportButton.text)
        assertTrue(deleteButton.foreground.red > deleteButton.foreground.green)
        assertFalse(exportButton.isEnabled)
        assertNotNull(addButton.icon)
        assertNotNull(deleteButton.icon)
        assertNotNull(refreshButton.icon)
        assertNotNull(exportButton.icon)
    }

    fun testToolbarUsesToolWindowBackground() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val toolbar = descendants(content).filterIsInstance<TranslationToolbar>().single()

        assertFalse(toolbar.isOpaque)
    }

    fun testResourceSetAndSearchStackBeforeTheirContentsAreClipped() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val controls = descendants(content).filterIsInstance<ResponsiveControlsPanel>().single()
        val resourceSetCombo = descendants(controls).filterIsInstance<JComboBox<*>>().first()
        val searchField = descendants(controls).filterIsInstance<JBTextField>().single()

        controls.setSize(760, 240)
        controls.doLayout()

        val resourceSetLocation = SwingUtilities.convertPoint(resourceSetCombo, 0, 0, controls)
        val searchLocation = SwingUtilities.convertPoint(searchField, 0, 0, controls)
        assertTrue(searchLocation.y > resourceSetLocation.y)
        assertTrue(resourceSetLocation.x + resourceSetCombo.width <= controls.width)
        assertTrue(searchLocation.x + searchField.width <= controls.width)
    }

    fun testResourceSetAndSearchShrinkInsideVeryNarrowToolWindow() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val controls = descendants(content).filterIsInstance<ResponsiveControlsPanel>().single()
        val resourceSetCombo = descendants(controls).filterIsInstance<JComboBox<*>>().first()
        val searchField = descendants(controls).filterIsInstance<JBTextField>().single()

        controls.setSize(390, 240)
        controls.doLayout()

        val resourceSetLocation = SwingUtilities.convertPoint(resourceSetCombo, 0, 0, controls)
        val searchLocation = SwingUtilities.convertPoint(searchField, 0, 0, controls)
        assertTrue(controls.minimumSize.width < 390)
        assertEquals(0, searchField.parent.minimumSize.width)
        assertTrue(resourceSetLocation.x + resourceSetCombo.width <= controls.width)
        assertTrue(searchLocation.x + searchField.width <= controls.width)
        resourceSetCombo.parent.doLayout()
        assertTrue(resourceSetCombo.parent.height > 0)
        assertTrue(resourceSetCombo.height > 0)
    }

    fun testResourceSetAndSearchStayInsideNarrowToolWindowBounds() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val controls = descendants(content).filterIsInstance<ResponsiveControlsPanel>().single()
        val resourceSetCombo = descendants(controls).filterIsInstance<JComboBox<*>>().first()
        val searchField = descendants(controls).filterIsInstance<JBTextField>().single()
        val filterButton = descendants(controls).filterIsInstance<JButton>().single { it.toolTipText == "Filter" }

        content.setSize(390, 500)
        layoutRecursively(content)

        listOf(resourceSetCombo, searchField, filterButton).forEach { control ->
            val location = SwingUtilities.convertPoint(control, 0, 0, content)
            assertTrue(location.x >= 0)
            assertTrue(
                "${control.javaClass.simpleName} right edge ${location.x + control.width} exceeds ${content.width}: " +
                    ancestorBounds(control, content),
                location.x + control.width <= content.width,
            )
        }
    }

    fun testResourceSetAndSearchUseFlexibleHostsWithoutHorizontalMinimum() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val controls = descendants(content).filterIsInstance<ResponsiveControlsPanel>().single()
        val resourceSetCombo = descendants(controls).filterIsInstance<JComboBox<*>>().first()
        val searchField = descendants(controls).filterIsInstance<JBTextField>().single()

        assertEquals("FlexibleControlHost", resourceSetCombo.parent.javaClass.simpleName)
        assertEquals("FlexibleControlHost", searchField.parent.javaClass.simpleName)
        assertEquals(0, resourceSetCombo.parent.minimumSize.width)
        assertTrue(resourceSetCombo.parent.minimumSize.height > 0)
        assertEquals(0, searchField.parent.minimumSize.width)
        assertTrue(searchField.parent.minimumSize.height > 0)
    }

    fun testLongUiTextUsesEllipsisInsteadOfDisappearing() {
        val label = JLabel()
        val metrics: FontMetrics = label.getFontMetrics(label.font)
        val availableWidth = metrics.stringWidth("abc…")

        assertEquals("abc…", ellipsizeText("abcdef", metrics, availableWidth))
        assertEquals("abcdef", ellipsizeText("abcdef", metrics, metrics.stringWidth("abcdef")))
    }

    fun testResourceSetAndSearchUseEllipsisCapableComponents() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val searchField = descendants(content).filterIsInstance<EllipsisTextField>().single()
        val combo = resourceSetCombo(content)
        @Suppress("UNCHECKED_CAST")
        val renderer = combo.renderer as ListCellRenderer<Any?>
        val rendered = renderer.getListCellRendererComponent(
            JList<Any?>(),
            combo.getItemAt(0),
            0,
            false,
            false,
        )

        assertNotNull(searchField)
        assertTrue(descendants(rendered).any { it is EllipsisLabel })
    }

    fun testSearchFieldUsesExplicitHorizontalContentMargin() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val searchField = descendants(content).filterIsInstance<EllipsisTextField>().single()

        assertEquals(Insets(0, 8, 0, 8), searchField.margin)
    }

    fun testSearchPlaceholderPaintingHonorsHorizontalContentMargin() {
        val searchField = EllipsisTextField().apply {
            border = null
            background = Color.WHITE
            isOpaque = true
            emptyText.text = "Placeholder"
            setSize(320, 40)
        }

        val withoutMargin = placeholderStartX(searchField, 0)
        val withMargin = placeholderStartX(searchField, 8)

        assertEquals(8, withMargin - withoutMargin)
    }

    private fun placeholderStartX(searchField: EllipsisTextField, horizontalMargin: Int): Int {
        searchField.margin = Insets(0, horizontalMargin, 0, horizontalMargin)
        val image = BufferedImage(searchField.width, searchField.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        searchField.paint(graphics)
        graphics.dispose()

        return (0 until image.width).first { x ->
            (0 until image.height).any { y -> image.getRGB(x, y) != Color.WHITE.rgb }
        }
    }

    fun testSearchAreaShowsFilterIcon() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val filterButton = descendants(content).filterIsInstance<JButton>().single { it.toolTipText == "Filter" }

        assertEquals("", filterButton.text)
        assertNotNull(filterButton.icon)
    }

    fun testToolWindowChromeAndStatusUseMessageBundle() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val searchField = descendants(content).filterIsInstance<JBTextField>().single()
        val combo = resourceSetCombo(content)
        val status = descendants(content).filterIsInstance<JLabel>().single { label ->
            label.text != MyBundle.message("translation.resource-set.label") &&
                label.text != MyBundle.message("translation.search.label") &&
                label.text != MyBundle.message("translation.display-language.label")
        }
        val option = combo.selectedItem as ResourceSetOption

        assertEquals(MyBundle.message("translation.search.placeholder"), searchField.emptyText.text)
        assertEquals(
            MyBundle.message(
                "translation.status.summary",
                option.resourceSet.moduleName,
                option.resourceSet.sourceSetName,
                1,
                0,
            ),
            status.text,
        )
    }

    @Suppress("DEPRECATION")
    fun testFilterOptionsUseIntellijToggleActions() {
        val selectedFilters = mutableListOf<TranslationFilter>()
        val button = TranslationFilterButton { selectedFilters += it }
        val actions = button.filterActions()

        assertEquals(3, actions.size)
        assertTrue(actions.all { it is ToggleAction })

        val event = AnActionEvent.createFromAnAction(
            actions[1],
            null,
            "",
            DataContext.EMPTY_CONTEXT,
        )
        actions[1].actionPerformed(event)

        assertEquals(listOf(TranslationFilter.MISSING), selectedFilters)
    }

    fun testKeyColumnStartsInlineEditingInsteadOfReadonlyWarning() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()

        assertTrue(table.editCellAt(0, 0))
        table.removeEditor()
    }

    fun testKeyEditFromEdtDoesNotThrowDuringReload() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val failure = AtomicReference<Throwable?>()
        val edit = {
            try {
                table.setValueAt("sign_in", 0, 0)
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }

        if (SwingUtilities.isEventDispatchThread()) edit() else SwingUtilities.invokeAndWait(edit)

        assertEquals(null, failure.get())
    }



    fun testTableUsesMonospaceFontAndVisibleGrid() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()

        assertEquals(Font.MONOSPACED, table.font.family)
        assertTrue(table.showHorizontalLines)
        assertTrue(table.showVerticalLines)
        assertTrue(table.intercellSpacing.width > 0)
    }

    fun testMissingValueRendersAsBadgeWithoutSandBackgroundOrItalicRedText() {
        createResourceFiles(
            localizedXml = "<resources />",
        )

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val renderer = table.getCellRenderer(0, 3)
        val rendered = renderer.getTableCellRendererComponent(
            table,
            table.getValueAt(0, 3),
            false,
            false,
            0,
            3,
        )

        assertTrue(rendered is JPanel)
        assertFalse(rendered.isOpaque)
        assertNotEquals(Color(255, 248, 225), rendered.background)

        val badge = descendants(rendered).filterIsInstance<JLabel>().single()
        assertEquals("Missing", badge.text)
        assertFalse(badge.font.isItalic)
        assertNotEquals(Color.RED, badge.foreground)
    }

    fun testMissingValueEditorStartsWithEmptyText() {
        createResourceFiles(
            localizedXml = "<resources />",
        )

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val editor = table.getCellEditor(0, 3)
        val editorComponent = editor.getTableCellEditorComponent(
            table,
            table.getValueAt(0, 3),
            true,
            0,
            3,
        ) as JTextField

        assertEquals("", editorComponent.text)
    }

    fun testAddStringDialogPrefillsExtractionKeyAndDefaultValue() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = listOf(ComposeResourceQualifier("ko")),
            initialKey = "hello_world",
            initialDefaultValue = "Hello world",
        )

        val draft = dialog.draft()
        assertEquals("hello_world", draft.key)
        assertEquals("Hello world", draft.defaultValue)
        assertEquals("", draft.localizedValues[ComposeResourceQualifier("ko")])
    }

    fun testAddStringDialogSwitchesBetweenStringAndStringArrayTypes() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = listOf(ComposeResourceQualifier("ko")),
            initialKey = "menu",
        )
        val radios = dialog.typeRadioButtons
        val arrayRadio = radios.single { it.text == "String array" }

        assertEquals(ComposeResourceType.STRING, dialog.draft().type)
        arrayRadio.doClick()
        assertEquals(ComposeResourceType.STRING_ARRAY, dialog.draft().type)
        assertEquals(listOf("0"), dialog.draft().defaultItems.map(ComposeResourceItem::name))
    }

    fun testStringArrayDialogInitiallyShowsArrayFields() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = emptyList(),
            initialType = ComposeResourceType.STRING_ARRAY,
            initialArrayItems = listOf(ComposeResourceItem("0", "Zero")),
        )

        dialogContent(dialog)
        val visibleCard = typeCards(dialog).components.single { it.isVisible }

        assertTrue(descendants(visibleCard).contains(arrayItemsPanel(dialog)))
    }

    fun testStringArrayItemsAreRenumberedAfterDeleteAndAdd() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = emptyList(),
            initialType = ComposeResourceType.STRING_ARRAY,
            initialArrayItems = listOf(
                ComposeResourceItem("0", "Zero"),
                ComposeResourceItem("1", "One"),
                ComposeResourceItem("2", "Two"),
                ComposeResourceItem("3", "Three"),
            ),
        )

        val content = dialogContent(dialog)
        val removeButtons = arrayRemoveButtons(content)
        assertEquals(4, removeButtons.size)
        assertEquals(MyBundle.message("translation.dialog.array.remove-item"), removeButtons.first().text)

        removeButtons[1].doClick()
        assertEquals(listOf("0", "1", "2"), dialog.draft().defaultItems.map(ComposeResourceItem::name))

        arrayRemoveButtons(content).first().doClick()
        assertEquals(listOf("0", "1"), dialog.draft().defaultItems.map(ComposeResourceItem::name))

        descendants(content)
            .filterIsInstance<JButton>()
            .first { it.text == MyBundle.message("translation.dialog.array.add-item") }
            .doClick()
        assertEquals(listOf("0", "1", "2"), dialog.draft().defaultItems.map(ComposeResourceItem::name))
    }

    fun testStringArrayRowsStayPackedAndTextFieldsFillAvailableWidth() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = emptyList(),
            initialType = ComposeResourceType.STRING_ARRAY,
            initialArrayItems = listOf(
                ComposeResourceItem("0", "Zero"),
                ComposeResourceItem("1", "One"),
            ),
        )
        val content = arrayItemsPanel(dialog)
        content.setSize(900, 600)
        layoutRecursively(content)

        val rows = arrayRows(dialog)
        assertEquals(2, rows.size)
        assertEquals(rows[0].y + rows[0].height, rows[1].y)
        rows.forEach { row ->
            val field = descendants(row).filterIsInstance<JBTextField>().single()
            assertTrue(field.width > 500)
        }
    }

    fun testStringArrayRowsStayPackedWhenDialogCardGrows() {
        val dialog = AddStringDialog(
            project = project,
            qualifiers = emptyList(),
            initialType = ComposeResourceType.STRING_ARRAY,
            initialArrayItems = listOf(
                ComposeResourceItem("0", "Zero"),
                ComposeResourceItem("1", "One"),
            ),
        )
        val content = dialogContent(dialog)
        content.setSize(900, 900)
        layoutRecursively(content)

        val scrollPane = descendants(content).filterIsInstance<javax.swing.JScrollPane>().last()
        scrollPane.setSize(860, 700)
        layoutRecursively(scrollPane)

        val rows = arrayRows(dialog)
        assertEquals(2, rows.size)
        assertEquals(rows[0].y + rows[0].height, rows[1].y)
    }

    fun testArrayExpandArrowDoesNotStartKeyEditing() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string-array name=\"menu\"><item>Home</item><item>Settings</item></string-array></resources>",
        )

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val cell = table.getCellRect(0, 0, false)
        val event = MouseEvent(
            table,
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            cell.x + 8,
            cell.y + 8,
            1,
            false,
            MouseEvent.BUTTON1,
        )

        assertFalse(table.editCellAt(0, 0, event))
    }

    fun testToolWindowDisplaysExpandedStringArrayRowsWithIndentedKeys() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            """
            <resources>
                <string-array name="menu"><item>Home</item><item>Settings</item></string-array>
            </resources>
            """.trimIndent(),
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            """
            <resources>
                <string-array name="menu"><item>홈</item><item>설정</item></string-array>
            </resources>
            """.trimIndent(),
        )

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val renderer = table.columnModel.getColumn(0).cellRenderer

        assertEquals(3, table.rowCount)
        assertTrue(table.getValueAt(1, 0).toString().contains("0"))
        val rendered = renderer.getTableCellRendererComponent(table, table.getValueAt(1, 0), false, false, 1, 0)
        assertTrue((rendered as JLabel).text.contains("└ 0"))
    }

    fun testStringArrayChildrenHideUntranslatableCheckbox() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string-array name=\"menu\"><item>Home</item></string-array></resources>",
        )

        val content = ComposeTranslationToolWindow(project).component
        val table = descendants(content).filterIsInstance<JTable>().single()
        val childRenderer = table.getCellRenderer(1, 1)
        val childComponent = childRenderer.getTableCellRendererComponent(
            table,
            table.getValueAt(1, 1),
            false,
            false,
            1,
            1,
        )
        val parentComponent = table.getCellRenderer(0, 1).getTableCellRendererComponent(
            table,
            table.getValueAt(0, 1),
            false,
            false,
            0,
            1,
        )

        assertFalse(descendants(childComponent).any { it is JCheckBox })
        assertTrue(descendants(parentComponent).any { it is JCheckBox })
    }

    fun testToolbarWrapsWhenWindowBecomesNarrow() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val toolbar = descendants(content).filterIsInstance<TranslationToolbar>().single()
        val addButton = descendants(toolbar).filterIsInstance<JButton>().single { it.toolTipText == "Add string" }
        val refreshButton = descendants(toolbar).filterIsInstance<JButton>().single { it.toolTipText == "Refresh" }
        val exportButton = descendants(toolbar).filterIsInstance<JButton>().single { it.toolTipText == "Export" }

        toolbar.setSize(100, 160)
        toolbar.doLayout()

        assertTrue(refreshButton.y > addButton.y || exportButton.y > addButton.y)
    }

    fun testResourceSetSelectorRendersModuleNameAsBadge() {
        createResourceFiles()
        myFixture.tempDirFixture.createFile(
            "feature/src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Feature Login</string></resources>",
        )

        val content = ComposeTranslationToolWindow(project).component
        val combo = resourceSetCombo(content)
        assertEquals(2, combo.itemCount)

        @Suppress("UNCHECKED_CAST")
        val renderer = combo.renderer as ListCellRenderer<Any?>
        val rendered = renderer.getListCellRendererComponent(
            JList<Any?>(),
            combo.getItemAt(0),
            0,
            false,
            false,
        )

        val moduleBadge = descendants(rendered).filterIsInstance<ModuleBadge>().single()
        assertTrue(moduleBadge.text.isNotBlank())
    }

    fun testLanguageSelectorShowsResourceLocalesAndUpdatesDisplaySetting() {
        createResourceFiles()

        val content = ComposeTranslationToolWindow(project).component
        val combos = descendants(content).filterIsInstance<JComboBox<*>>()
        val languageCombo = combos.single { combo ->
            combo.itemCount == 2 &&
                (combo.getItemAt(1) as? ComposeResourceQualifier)?.rawValue == "ko"
        }

        languageCombo.selectedItem = ComposeResourceQualifier("ko")

        assertEquals(
            ComposeResourceQualifier("ko"),
            ComposeTranslationDisplaySettings.getInstance(project).selectedQualifier,
        )
    }

    private fun createResourceFiles(localizedXml: String = "<resources><string name=\"login\">로그인</string></resources>") {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Login</string></resources>",
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            localizedXml,
        )
    }

    private fun descendants(component: Component): List<Component> = buildList {
        add(component)
        if (component is java.awt.Container) {
            component.components.forEach { addAll(descendants(it)) }
        }
    }

    private fun resourceSetCombo(content: Component): JComboBox<*> =
        descendants(content).filterIsInstance<JComboBox<*>>().single { combo ->
            combo.getItemAt(0) is ResourceSetOption
        }

    private fun arrayRemoveButtons(content: Component): List<JButton> =
        descendants(content)
            .filterIsInstance<JButton>()
            .filter { it.text == MyBundle.message("translation.dialog.array.remove-item") }

    private fun arrayRows(dialog: AddStringDialog): List<Container> =
        descendants(arrayItemsPanel(dialog))
            .filterIsInstance<Container>()
            .filter { container ->
                container.components.any { component ->
                    component is JLabel && component.text.matches(Regex("\\[\\d+\\]"))
                }
            }
            .sortedBy { it.y }

    private fun arrayItemsPanel(dialog: AddStringDialog): JPanel =
        AddStringDialog::class.java.getDeclaredField("arrayItemsPanel").apply { isAccessible = true }
            .get(dialog) as JPanel

    private fun typeCards(dialog: AddStringDialog): JPanel =
        AddStringDialog::class.java.getDeclaredField("typeCards").apply { isAccessible = true }
            .get(dialog) as JPanel

    private fun dialogContent(dialog: AddStringDialog): Component =
        AddStringDialog::class.java.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            .invoke(dialog) as Component

    private fun layoutRecursively(component: Component) {
        if (component !is java.awt.Container) return
        component.doLayout()
        component.components.forEach(::layoutRecursively)
    }

    private fun ancestorBounds(component: Component, root: Component): String = buildList {
        var current: Component? = component
        while (current != null) {
            add("${current.javaClass.simpleName}[x=${current.x},width=${current.width},min=${current.minimumSize.width}]")
            if (current === root) break
            current = current.parent
        }
    }.joinToString(" <- ")
}
