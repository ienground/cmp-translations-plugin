package zone.ien.cmp_translation_plugin.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.pom.Navigatable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier

class ComposeTranslationNavigationTest : BasePlatformTestCase() {

    fun testExtractsOnlyComposeStringReferences() {
        assertEquals(
            "login",
            ComposeTranslationNavigation.extractResourceKey(
                sourceText = "Res.string.login",
                ancestorTexts = emptyList(),
            ),
        )
        assertEquals(
            "menu",
            ComposeTranslationNavigation.extractResourceKey(
                sourceText = "Res.array.menu",
                ancestorTexts = emptyList(),
            ),
        )
        assertEquals(
            "login",
            ComposeTranslationNavigation.extractResourceKey(
                sourceText = "login",
                ancestorTexts = listOf("string.login", "foo(Res.string.other, Res.string.login)"),
            ),
        )
        assertNull(
            ComposeTranslationNavigation.extractResourceKey(
                sourceText = "login",
                ancestorTexts = listOf("R.string.login"),
            ),
        )
    }

    fun testSelectsResourceSetMatchingUsageSourceSet() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Common</string></resources>",
        )
        myFixture.tempDirFixture.createFile(
            "feature/src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Feature</string></resources>",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "feature/src/commonMain/kotlin/Usage.kt",
            "val value = Res.string.login",
        )

        val resourceSets = ComposeResourceCatalog(project).load()
        val selected = ComposeTranslationNavigation.findResourceSetForUsage(
            resourceSets = resourceSets,
            sourceFile = usageFile,
            key = "login",
        )

        assertEquals("feature", selected?.moduleName)
        assertEquals("commonMain", selected?.sourceSetName)
    }

    fun testSelectsResourceSetMatchingNewUsageSourceFile() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Common</string></resources>",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/Usage.kt",
            "fun screen() = \"Hello\"",
        )

        val selected = ComposeTranslationNavigation.findResourceSetForSource(
            resourceSets = ComposeResourceCatalog(project).load(),
            sourceFile = usageFile,
        )

        assertEquals("commonMain", selected?.sourceSetName)
    }

    fun testSelectedLanguageResolvesLocalizedValueAndFallsBackToDefault() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"close\">Close</string></resources>",
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            "<resources><string name=\"close\">닫기</string></resources>",
        )
        val resourceSet = ComposeResourceCatalog(project).load().single()

        assertEquals(
            listOf(ComposeResourceQualifier.DEFAULT, ComposeResourceQualifier("ko")),
            ComposeTranslationLanguageSelection.options(resourceSet),
        )
        assertEquals(
            "닫기",
            ComposeTranslationLanguageSelection.value(
                resourceSet = resourceSet,
                key = "close",
                qualifier = ComposeResourceQualifier("ko"),
            ),
        )
        assertEquals(
            "Close",
            ComposeTranslationLanguageSelection.value(
                resourceSet = resourceSet,
                key = "close",
                qualifier = ComposeResourceQualifier("ja"),
            ),
        )
    }

    fun testGotoHandlerReturnsNavigableTranslationTarget() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Login</string></resources>",
        )
        val usageFile = myFixture.configureByText("Usage.txt", "Res.string.login")
        val target = ComposeTranslationGotoDeclarationHandler()
            .getGotoDeclarationTarget(usageFile, myFixture.editor)

        assertNotNull(target)
        assertTrue(target is Navigatable && target.canNavigate())
    }
}
