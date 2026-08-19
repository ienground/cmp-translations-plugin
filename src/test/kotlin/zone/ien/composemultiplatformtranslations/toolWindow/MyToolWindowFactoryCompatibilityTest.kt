package zone.ien.composemultiplatformtranslations.toolWindow

import org.junit.Assert.assertFalse
import org.junit.Test

class MyToolWindowFactoryCompatibilityTest {

    @Test
    fun doesNotGenerateDeprecatedToolWindowFactoryBridges() {
        val deprecatedBridgeNames = setOf("isApplicable", "isDoNotActivateOnStart")

        assertFalse(
            MyToolWindowFactory::class.java.declaredMethods.any { it.name in deprecatedBridgeNames },
        )
    }
}
