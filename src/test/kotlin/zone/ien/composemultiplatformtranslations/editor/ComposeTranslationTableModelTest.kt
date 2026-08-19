package zone.ien.composemultiplatformtranslations.editor

import zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier
import zone.ien.composemultiplatformtranslations.resource.ComposeStringEntry
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

        assertEquals(listOf("Key", "Default", "ja", "ko"), (0 until model.columnCount).map(model::getColumnName))
        assertEquals(2, model.rowCount)
        assertEquals("My App", model.getValueAt(0, 1))
        assertEquals("[Missing]", model.getValueAt(1, 2))
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
        assertTrue(model.getValueAt(0, 3).toString().contains("로그인"))
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

        model.setValueAt("로그인 버튼", 0, 2)

        assertEquals(Triple("login", ko, "로그인 버튼"), edited)
    }
}
