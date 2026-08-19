package zone.ien.composemultiplatformtranslations.resource

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeStringsXmlParserTest : BasePlatformTestCase() {

    fun testParsesStringTagsThroughXmlPsi() {
        val psiFile = myFixture.configureByText(
            XmlFileType.INSTANCE,
            """
            <resources>
                <string name="app_name">My App</string>
                <string name="welcome">Welcome, %1${'$'}s</string>
            </resources>
            """.trimIndent(),
        )

        val entries = ComposeStringsXmlParser().parse(assertInstanceOf(psiFile, XmlFile::class.java))

        assertEquals(
            listOf(
                ComposeStringEntry("app_name", "My App"),
                ComposeStringEntry("welcome", "Welcome, %1${'$'}s"),
            ),
            entries,
        )
        assertEquals(listOf("%1${'$'}s"), entries[1].placeholders)
    }

    fun testIgnoresTagsWithoutNameAttribute() {
        val psiFile = myFixture.configureByText(
            XmlFileType.INSTANCE,
            "<resources><string>invalid</string><string name=\"valid\">Valid</string></resources>",
        )

        val entries = ComposeStringsXmlParser().parse(assertInstanceOf(psiFile, XmlFile::class.java))

        assertEquals(listOf(ComposeStringEntry("valid", "Valid")), entries)
    }
}
