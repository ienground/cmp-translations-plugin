package zone.ien.cmp_translation_plugin.editor

import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier

class ComposeStringResourceIntentionTest : com.intellij.testFramework.fixtures.BasePlatformTestCase() {

    fun testIsAvailableForKotlinStringLiteral() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "fun screen() = \"Hello world\"",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertTrue(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableForNonKotlinFiles() {
        val file = myFixture.configureByText("Usage.txt", "\"Hello world\"")

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testInvokeAddsResourceAndReplacesLiteral() {
        val stringsFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/Usage.kt",
            "package sample.ui\n\nimport sample.generated.resources.Res\n\nfun screen() = \"Hello world\"",
        )
        myFixture.openFileInEditor(usageFile)
        myFixture.editor.caretModel.moveToOffset(usageFile.contentsToByteArray().decodeToString().indexOf('"'))
        val usagePsi = PsiManager.getInstance(project).findFile(usageFile)
        val resourceSets = ComposeResourceCatalog(project).load()
        assertEquals(1, resourceSets.size)
        assertEquals(
            resourceSets.single(),
            ComposeTranslationNavigation.findResourceSetForSource(resourceSets, usageFile),
        )

        ComposeStringResourceIntentionAction(
            draftProvider = { _, qualifiers, suggestedKey, defaultValue ->
                assertEquals("hello_world", suggestedKey)
                assertTrue(qualifiers.isEmpty())
                StringResourceDraft(
                    key = "greeting",
                    defaultValue = defaultValue,
                    localizedValues = mapOf(ComposeResourceQualifier("ko") to "안녕하세요"),
                    translatable = true,
                )
            },
        ).invoke(project, myFixture.editor, usagePsi!!)

        val stringsPsi = PsiManager.getInstance(project).findFile(stringsFile) as XmlFile
        assertEquals(
            "Hello world",
            stringsPsi.rootTag?.findSubTags("string")?.single()?.value?.text,
        )
        assertEquals(
            "package sample.ui\n\nimport sample.generated.resources.Res\nimport org.jetbrains.compose.resources.stringResource\n\nfun screen() = stringResource(Res.string.greeting)",
            FileDocumentManager.getInstance().getDocument(usageFile)?.text,
        )
    }
}
