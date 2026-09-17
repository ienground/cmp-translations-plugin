package zone.ien.cmp_translation_plugin.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeTranslationResourceCallTest {

    @Test
    fun `extracts resource key from stringResource call`() {
        assertEquals(
            "common_close",
            ComposeTranslationResourceCall.extractKey("stringResource(Res.string.common_close)"),
        )
    }

    @Test
    fun `finds each resource call and its source range`() {
        val source = "val text = stringResource(Res.string.common_close)"

        val references = ComposeTranslationResourceCall.find(source)

        assertEquals(1, references.size)
        assertEquals("common_close", references.single().key)
        assertEquals(
            "stringResource(Res.string.common_close)",
            source.substring(references.single().range),
        )
    }

    @Test
    fun `ignores non Compose resource references`() {
        assertNull(ComposeTranslationResourceCall.extractKey("R.string.common_close"))
        assertTrue(ComposeTranslationResourceCall.find("text(Res.string.common_close)").isEmpty())
    }
}
