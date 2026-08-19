package zone.ien.composemultiplatformtranslations.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Insets
import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.ListCellRenderer
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicReference
import zone.ien.composemultiplatformtranslations.MyBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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
        val combo = descendants(content).filterIsInstance<JComboBox<*>>().first()
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
        val combo = descendants(content).filterIsInstance<JComboBox<*>>().first()
        val status = descendants(content).filterIsInstance<JLabel>().single { label ->
            label.text != MyBundle.message("translation.resource-set.label") &&
                label.text != MyBundle.message("translation.search.label")
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
        val renderer = table.getCellRenderer(0, 2)
        val rendered = renderer.getTableCellRendererComponent(
            table,
            table.getValueAt(0, 2),
            false,
            false,
            0,
            2,
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
        val editor = table.getCellEditor(0, 2)
        val editorComponent = editor.getTableCellEditorComponent(
            table,
            table.getValueAt(0, 2),
            true,
            0,
            2,
        ) as JTextField

        assertEquals("", editorComponent.text)
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
        val combo = descendants(content).filterIsInstance<JComboBox<*>>().first()
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
