package zone.ien.cmp_translation_plugin.editor

import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeStringEntry
import zone.ien.cmp_translation_plugin.resource.ComposeResourceItem
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeTranslationTableModelTest {

    private val ko = ComposeResourceQualifier("ko")
    private val ja = ComposeResourceQualifier("ja")

    @Test
    fun buildsLocaleColumnsAndMarksMissingValues() {
        val model = ComposeTranslationTableModel()

        model.setEntries(
            defaultEntries = listOf(
                ComposeStringEntry("app_name", "My App"),
                ComposeStringEntry("login", "Login"),
            ),
            localizedEntries = mapOf(
                ko to listOf(
                    ComposeStringEntry("app_name", "내 앱"),
                    ComposeStringEntry("login", "로그인"),
                ),
                ja to listOf(ComposeStringEntry("app_name", "マイアプリ")),
            ),
            issues = emptyList(),
        )

        assertEquals(listOf("Key", "Untranslatable", "Default", "ja", "ko"), (0 until model.columnCount).map(model::getColumnName))
        assertEquals(2, model.rowCount)
        assertEquals("My App", model.getValueAt(0, 2))
        assertEquals("[Missing]", model.getValueAt(1, 3))
    }

    @Test
    fun filtersMissingCompleteAndSearchesAllDisplayedText() {
        val model = ComposeTranslationTableModel()
        model.setEntries(
            defaultEntries = listOf(
                ComposeStringEntry("login", "Login"),
                ComposeStringEntry("logout", "Logout"),
            ),
            localizedEntries = mapOf(
                ko to listOf(
                    ComposeStringEntry("login", "로그인"),
                    ComposeStringEntry("logout", "로그아웃"),
                ),
                ja to listOf(
                    ComposeStringEntry("logout", "ログアウト"),
                ),
            ),
            issues = emptyList(),
        )

        model.filter = TranslationFilter.MISSING
        assertEquals(listOf("login"), model.visibleRows().map { it.key })

        model.filter = TranslationFilter.COMPLETE
        assertEquals(listOf("logout"), model.visibleRows().map { it.key })

        model.filter = TranslationFilter.ALL
        model.searchQuery = "로그인"
        assertEquals(listOf("login"), model.visibleRows().map { it.key })
        assertTrue(model.getValueAt(0, 4).toString().contains("로그인"))
    }

    @Test
    fun forwardsEditableCellChangesWithKeyAndLocale() {
        val model = ComposeTranslationTableModel()
        var edited: Triple<String, ComposeResourceQualifier?, String>? = null
        model.onValueEdited = { key, qualifier, value -> edited = Triple(key, qualifier, value) }
        model.setEntries(
            defaultEntries = listOf(ComposeStringEntry("login", "Login")),
            localizedEntries = mapOf(ko to listOf(ComposeStringEntry("login", "로그인"))),
            issues = emptyList(),
        )

        model.setValueAt("로그인 버튼", 0, 3)

        assertEquals(Triple("login", ko, "로그인 버튼"), edited)
    }

    @Test
    fun allowsEditingResourceKeysSeparatelyFromTranslationValues() {
        val model = ComposeTranslationTableModel()
        var editedKey: Pair<String, String>? = null
        model.onKeyEdited = { oldKey, newKey -> editedKey = oldKey to newKey }
        model.setEntries(
            defaultEntries = listOf(ComposeStringEntry("login", "Login")),
            localizedEntries = mapOf(ko to listOf(ComposeStringEntry("login", "로그인"))),
            issues = emptyList(),
        )

        assertTrue(model.isCellEditable(0, 0))
        model.setValueAt("sign_in", 0, 0)

        assertEquals("login" to "sign_in", editedKey)
    }

    @Test
    fun flattensArrayItemsAndCanCollapseTheirParentRow() {
        val model = ComposeTranslationTableModel()
        model.setEntries(
            defaultEntries = listOf(
                ComposeStringEntry(
                    key = "menu",
                    value = "",
                    type = ComposeResourceType.STRING_ARRAY,
                    items = listOf(
                        ComposeResourceItem("0", "Home"),
                        ComposeResourceItem("1", "Settings"),
                    ),
                ),
            ),
            localizedEntries = mapOf(
                ko to listOf(
                    ComposeStringEntry(
                        key = "menu",
                        value = "",
                        type = ComposeResourceType.STRING_ARRAY,
                        items = listOf(
                            ComposeResourceItem("0", "홈"),
                            ComposeResourceItem("1", "설정"),
                        ),
                    ),
                ),
            ),
            issues = emptyList(),
        )

        assertEquals(3, model.rowCount)
        assertEquals("menu", model.getValueAt(0, 0))
        assertEquals("└ 0", model.getValueAt(1, 0))
        assertEquals(1, model.visibleRows()[1].depth)
        assertTrue(model.toggleExpanded(0))
        assertEquals(1, model.rowCount)
        assertTrue(model.toggleExpanded(0))
        assertEquals(3, model.rowCount)
    }

    @Test
    fun forwardsArrayItemEditsWithParentKeyAndItemName() {
        val model = ComposeTranslationTableModel()
        var edited: Quadruple<String, String, ComposeResourceQualifier?, String>? = null
        model.onItemValueEdited = { key, itemName, qualifier, value ->
            edited = Quadruple(key, itemName, qualifier, value)
        }
        model.setEntries(
            defaultEntries = listOf(
                ComposeStringEntry(
                    key = "menu",
                    value = "",
                    type = ComposeResourceType.STRING_ARRAY,
                    items = listOf(ComposeResourceItem("0", "Home")),
                ),
            ),
            localizedEntries = mapOf(ko to listOf(
                ComposeStringEntry(
                    key = "menu",
                    value = "",
                    type = ComposeResourceType.STRING_ARRAY,
                    items = listOf(ComposeResourceItem("0", "홈")),
                ),
            )),
            issues = emptyList(),
        )

        model.setValueAt("집", 1, 3)

        assertEquals(Quadruple("menu", "0", ko, "집"), edited)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
