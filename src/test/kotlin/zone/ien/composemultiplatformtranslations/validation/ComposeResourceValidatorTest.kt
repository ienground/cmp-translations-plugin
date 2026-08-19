package zone.ien.composemultiplatformtranslations.validation

import zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier
import zone.ien.composemultiplatformtranslations.resource.ComposeStringEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeResourceValidatorTest {

    private val ko = ComposeResourceQualifier("ko")
    private val ja = ComposeResourceQualifier("ja")

    @Test
    fun reportsMissingAndOrphanKeys() {
        val issues = ComposeResourceValidator().validate(
            defaultEntries = listOf(ComposeStringEntry("login", "Login")),
            localizedEntries = mapOf(
                ko to emptyList(),
                ja to listOf(ComposeStringEntry("legacy", "Legacy")),
            ),
        )

        assertTrue(issues.any { it.type == ComposeResourceIssueType.MISSING_KEY && it.key == "login" && it.qualifier == ko })
        assertTrue(issues.any { it.type == ComposeResourceIssueType.ORPHAN_KEY && it.key == "legacy" && it.qualifier == ja })
    }

    @Test
    fun reportsDuplicateKeysInDefaultAndLocalizedEntries() {
        val issues = ComposeResourceValidator().validate(
            defaultEntries = listOf(
                ComposeStringEntry("title", "Title"),
                ComposeStringEntry("title", "Other title"),
            ),
            localizedEntries = mapOf(
                ko to listOf(
                    ComposeStringEntry("title", "제목"),
                    ComposeStringEntry("title", "다른 제목"),
                ),
            ),
        )

        assertEquals(2, issues.count { it.type == ComposeResourceIssueType.DUPLICATE_KEY })
        assertTrue(issues.any { it.qualifier == null && it.key == "title" })
        assertTrue(issues.any { it.qualifier == ko && it.key == "title" })
    }

    @Test
    fun reportsPlaceholderMismatch() {
        val issues = ComposeResourceValidator().validate(
            defaultEntries = listOf(ComposeStringEntry("welcome", "Welcome, %1${'$'}s")),
            localizedEntries = mapOf(
                ko to listOf(ComposeStringEntry("welcome", "%1${'$'}d님, 환영합니다.")),
            ),
        )

        assertTrue(
            issues.any {
                it.type == ComposeResourceIssueType.PLACEHOLDER_MISMATCH &&
                    it.key == "welcome" &&
                    it.qualifier == ko
            },
        )
    }
}
