package zone.ien.cmp_translation_plugin.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeResourceQualifierTest {

    @Test
    fun parsesDefaultValuesDirectory() {
        assertEquals(ComposeResourceQualifier.DEFAULT, ComposeResourceQualifier.fromDirectoryName("values"))
    }

    @Test
    fun parsesLocaleQualifierWithoutChangingRawValue() {
        assertEquals("ko", ComposeResourceQualifier.fromDirectoryName("values-ko")?.rawValue)
        assertEquals("zh-rCN", ComposeResourceQualifier.fromDirectoryName("values-zh-rCN")?.rawValue)
    }

    @Test
    fun rejectsNonValuesDirectory() {
        assertNull(ComposeResourceQualifier.fromDirectoryName("drawable-ko"))
        assertNull(ComposeResourceQualifier.fromDirectoryName("values-"))
    }
}
