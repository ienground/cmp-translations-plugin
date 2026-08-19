package zone.ien.composemultiplatformtranslations.resource

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class ComposeResourceDescriptorTest : BasePlatformTestCase() {

    fun testRecognizesComposeResourcesStringsFile() {
        val file = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-zh-rCN/strings.xml",
            "<resources />",
        )

        val descriptor = ComposeResourceDescriptor.from(file)

        assertEquals("commonMain", descriptor?.sourceSetName)
        assertEquals("zh-rCN", descriptor?.qualifier?.rawValue)
        assertEquals("composeResources", descriptor?.resourceRoot?.name)
    }

    fun testExtractsModuleNameFromResourcePath() {
        val file = myFixture.tempDirFixture.createFile(
            "feature/src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )

        val descriptor = ComposeResourceDescriptor.from(file)

        assertEquals("feature", descriptor?.moduleName)
    }

    fun testRejectsAndroidResStringsFile() {
        val file = myFixture.tempDirFixture.createFile(
            "src/main/res/values-ko/strings.xml",
            "<resources />",
        )

        assertNull(ComposeResourceDescriptor.from(file))
    }

    fun testRejectsNonStringsXmlFile() {
        val file = myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/colors.xml",
            "<resources />",
        )

        assertNull(ComposeResourceDescriptor.from(file))
    }
}
