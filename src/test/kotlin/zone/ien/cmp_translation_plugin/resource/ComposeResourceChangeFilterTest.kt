package zone.ien.cmp_translation_plugin.resource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeResourceChangeFilterTest {

    private val resourceRoot = "/project/src/commonMain/composeResources"

    @Test
    fun `accepts strings xml changes inside selected resource root`() {
        assertTrue(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "$resourceRoot/values/strings.xml",
            ),
        )
        assertTrue(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "$resourceRoot/values-ko/strings.xml",
            ),
        )
    }

    @Test
    fun `accepts values directory changes to detect new locale files`() {
        assertTrue(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "$resourceRoot/values-ja",
            ),
        )
    }

    @Test
    fun `ignores changes outside selected resource root`() {
        assertFalse(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "/project/src/iosMain/composeResources/values/strings.xml",
            ),
        )
        assertFalse(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "/project/src/commonMain/composeResources/images/logo.png",
            ),
        )
        assertFalse(
            ComposeResourceChangeFilter.isRelevant(
                resourceRoot,
                "$resourceRoot/values-ko/colors.xml",
            ),
        )
    }
}
