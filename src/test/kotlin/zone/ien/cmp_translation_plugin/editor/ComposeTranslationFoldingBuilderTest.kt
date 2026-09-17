package zone.ien.cmp_translation_plugin.editor

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier

class ComposeTranslationFoldingBuilderTest : BasePlatformTestCase() {

    fun testFoldsResourceCallToSelectedTranslation() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"close\">Close</string></resources>",
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            "<resources><string name=\"close\">닫기</string></resources>",
        )
        ComposeTranslationDisplaySettings.getInstance(project).select(
            ComposeResourceQualifier("ko"),
        )
        myFixture.configureByText("Usage.kt", "val value = stringResource(Res.string.close)")

        val builder = ComposeTranslationFoldingBuilder()
        val descriptors = builder.buildFoldRegions(
            myFixture.file.node,
            DocumentImpl(myFixture.file.text),
        )

        assertEquals(1, descriptors.size)
        assertEquals("\"닫기\"", descriptors.single().placeholderText)
        assertTrue(builder.isCollapsedByDefault(descriptors.single().element))
    }
}
