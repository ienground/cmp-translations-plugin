package zone.ien.cmp_translation_plugin.editor

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier

class ComposeStringResourceIntentionTest : com.intellij.testFramework.fixtures.BasePlatformTestCase() {

    fun testIsAvailableForKotlinStringLiteral() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = \"Hello world\"",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertTrue(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testRegisteredIntentionIsAvailableInEditor() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = \"Hello world\"",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertTrue(
            myFixture.availableIntentions.any { intention ->
                intention.text == MyBundle.message("translation.extract.action.name")
            },
        )
    }

    fun testIsNotAvailableOutsideComposableContext() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "fun screen() = \"Hello world\"",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInComposableFunctionDefaultParameter() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen(title: String = \"Hello world\") = title",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInNonComposableLambdaInsideComposableFunction() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() {\n    val value = { \"Hello world\" }\n}",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInLambdaPassedToNonComposableFunction() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\nfun host(content: () -> String) = content()\n\n@Composable\nfun screen() = host { \"Hello world\" }",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInUnresolvedLowercaseLambdaCall() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = remember { \"Hello world\" }",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsAvailableInKnownComposableLambdaCall() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = setContent { \"Hello world\" }",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertTrue(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInKnownNonComposableLambdaCall() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = LaunchedEffect(Unit) { \"Hello world\" }",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsNotAvailableInNonComposableNamedLambdaArgument() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = Button(onClick = { \"Hello world\" }) {}",
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf('"'))

        assertFalse(ComposeStringResourceIntentionAction().isAvailable(project, myFixture.editor, file))
    }

    fun testIsAvailableInComposableLambdaPassedToComposableFunction() {
        val file = myFixture.configureByText(
            "Usage.kt",
            "import androidx.compose.runtime.Composable\n\n@Composable\nfun host(content: @Composable () -> String) = content()\n\n@Composable\nfun screen() = host { \"Hello world\" }",
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
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\n\n@Composable\nfun screen() = \"Hello world\"",
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
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\nimport org.jetbrains.compose.resources.stringResource\n\n@Composable\nfun screen() = stringResource(Res.string.greeting)",
            FileDocumentManager.getInstance().getDocument(usageFile)?.text,
        )
    }

    fun testInvokeDoesNotAddResourceWhenSourceIsReadOnly() {
        val stringsFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/Usage.kt",
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\n\n@Composable\nfun screen() = \"Hello world\"",
        )
        myFixture.openFileInEditor(usageFile)
        myFixture.editor.caretModel.moveToOffset(usageFile.contentsToByteArray().decodeToString().indexOf('"'))
        val usagePsi = PsiManager.getInstance(project).findFile(usageFile)

        ComposeStringResourceIntentionAction(
            draftProvider = { _, _, _, defaultValue ->
                StringResourceDraft(
                    key = "greeting",
                    defaultValue = defaultValue,
                    localizedValues = emptyMap(),
                    translatable = true,
                )
            },
            sourceDocumentProvider = { _, _ -> null },
            errorNotifier = { _, _, _ -> },
        ).invoke(project, myFixture.editor, usagePsi!!)

        val stringsPsi = PsiManager.getInstance(project).findFile(stringsFile) as XmlFile
        assertTrue(stringsPsi.rootTag?.findSubTags("string").isNullOrEmpty())
        assertEquals(
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\n\n@Composable\nfun screen() = \"Hello world\"",
            FileDocumentManager.getInstance().getDocument(usageFile)?.text,
        )
    }

    fun testInvokeDoesNotAddResourceWhenGeneratedResIsNotImported() {
        val stringsFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/Usage.kt",
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\n\n@Composable\nfun screen() = \"Hello world\"",
        )
        myFixture.openFileInEditor(usageFile)
        myFixture.editor.caretModel.moveToOffset(usageFile.contentsToByteArray().decodeToString().indexOf('"'))
        val usagePsi = PsiManager.getInstance(project).findFile(usageFile)

        ComposeStringResourceIntentionAction(
            draftProvider = { _, _, _, _ -> error("draft must not be requested") },
            errorNotifier = { _, _, _ -> },
        ).invoke(project, myFixture.editor, usagePsi!!)

        val stringsPsi = PsiManager.getInstance(project).findFile(stringsFile) as XmlFile
        assertTrue(stringsPsi.rootTag?.findSubTags("string").isNullOrEmpty())
    }

    fun testInvokeRemovesResourceWhenSourceReplacementFails() {
        val stringsFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )
        val usageFile = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/Usage.kt",
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\n\n@Composable\nfun screen() = \"Hello world\"",
        )
        myFixture.openFileInEditor(usageFile)
        myFixture.editor.caretModel.moveToOffset(usageFile.contentsToByteArray().decodeToString().indexOf('"'))
        val usagePsi = PsiManager.getInstance(project).findFile(usageFile)

        ComposeStringResourceIntentionAction(
            draftProvider = { _, _, _, defaultValue ->
                StringResourceDraft(
                    key = "greeting",
                    defaultValue = defaultValue,
                    localizedValues = emptyMap(),
                    translatable = true,
                )
            },
            sourceDocumentProvider = { _, _ -> DocumentImpl("import sample.generated.resources.Res\n") },
            errorNotifier = { _, _, _ -> },
        ).invoke(project, myFixture.editor, usagePsi!!)

        val stringsPsi = PsiManager.getInstance(project).findFile(stringsFile) as XmlFile
        assertTrue(stringsPsi.rootTag?.findSubTags("string").isNullOrEmpty())
        assertEquals(
            "package sample.ui\n\nimport androidx.compose.runtime.Composable\nimport sample.generated.resources.Res\n\n@Composable\nfun screen() = \"Hello world\"",
            FileDocumentManager.getInstance().getDocument(usageFile)?.text,
        )
    }
}
