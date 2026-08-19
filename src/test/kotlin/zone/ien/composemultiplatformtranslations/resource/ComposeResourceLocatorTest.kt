package zone.ien.composemultiplatformtranslations.resource

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals

class ComposeResourceLocatorTest : BasePlatformTestCase() {

    fun testFindsComposeResourcesAcrossLocalesAndIgnoresAndroidResources() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources />",
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            "<resources />",
        )
        myFixture.tempDirFixture.createFile(
            "src/main/res/values-ko/strings.xml",
            "<resources />",
        )

        val files = ComposeResourceLocator(project).findFiles()

        assertEquals(2, files.size)
        assertEquals(listOf("", "ko"), files.map { it.qualifier.rawValue })
        assertEquals(listOf("commonMain", "commonMain"), files.map { it.sourceSetName })
    }
}
