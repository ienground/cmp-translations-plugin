package zone.ien.cmp_translation_plugin.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeStringResourceExtractionTest {

    @Test
    fun suggestsSnakeCaseIdFromAlphabeticText() {
        assertEquals("hello_world", ComposeStringResourceExtraction.suggestResourceKey("HelloWorld"))
        assertEquals("welcome_user_2", ComposeStringResourceExtraction.suggestResourceKey("welcomeUser 2"))
    }

    @Test
    fun usesStringFallbackWhenTextHasNoResourceIdentifierCharacters() {
        assertEquals("string", ComposeStringResourceExtraction.suggestResourceKey("안녕"))
        assertEquals("string", ComposeStringResourceExtraction.suggestResourceKey("!!!"))
    }

    @Test
    fun avoidsExistingResourceKeyCollisions() {
        val existingKeys = setOf("hello_world", "hello_world_2")

        assertEquals(
            "hello_world_3",
            ComposeStringResourceExtraction.uniqueResourceKey("HelloWorld", existingKeys),
        )
        assertEquals(
            "welcome_user",
            ComposeStringResourceExtraction.uniqueResourceKey("WelcomeUser", existingKeys),
        )
    }

    @Test
    fun extractsRegularAndRawKotlinStringLiterals() {
        assertEquals("안녕", ComposeStringResourceExtraction.extractLiteralValue("\"안녕\""))
        assertEquals("hello\\\"world", ComposeStringResourceExtraction.extractLiteralValue("\"hello\\\\\\\"world\""))
        assertEquals("안녕\n세계", ComposeStringResourceExtraction.extractLiteralValue("\"\"\"안녕\n세계\"\"\""))
        assertNull(ComposeStringResourceExtraction.extractLiteralValue("value"))
    }

    @Test
    fun ignoresInterpolatedStrings() {
        assertNull(ComposeStringResourceExtraction.extractLiteralValue("\"Hello \$name\""))
        assertNull(ComposeStringResourceExtraction.extractLiteralValue("\"\"\"Hello \${name}\"\"\""))
    }

    @Test
    fun buildsComposeReplacementFromGeneratedResourceReference() {
        assertEquals(
            "stringResource(Res.string.hello_world)",
            ComposeStringResourceExtraction.replacementExpression("hello_world", "Res"),
        )
        assertEquals(
            "stringResource(Resources.string.hello_world)",
            ComposeStringResourceExtraction.replacementExpression("hello_world", "Resources"),
        )
    }

    @Test
    fun findsExistingGeneratedResImportAndImportInsertionPoint() {
        val source = """package sample.ui

            import sample.generated.resources.Res
            import androidx.compose.material3.Text

            fun screen() = Text(\"Hello\")
        """.trimIndent()

        assertEquals("Res", ComposeStringResourceExtraction.resReference(source))
        assertEquals(
            "import org.jetbrains.compose.resources.stringResource\n",
            ComposeStringResourceExtraction.missingImport(source, "org.jetbrains.compose.resources.stringResource"),
        )
    }

    @Test
    fun insertsMissingImportAfterExistingImports() {
        val source = "package sample.ui\n\nimport sample.generated.resources.Res\n\nfun screen() = Res.string.login"

        assertEquals(
            "package sample.ui\n\nimport sample.generated.resources.Res\nimport org.jetbrains.compose.resources.stringResource\n\nfun screen() = Res.string.login",
            ComposeStringResourceExtraction.insertMissingImport(
                source,
                "org.jetbrains.compose.resources.stringResource",
            ),
        )
    }
}
