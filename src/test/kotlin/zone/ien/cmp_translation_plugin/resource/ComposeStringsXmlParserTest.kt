package zone.ien.cmp_translation_plugin.resource

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

    fun testParsesStringArrayItemsAsTypedResourceEntry() {
        val psiFile = myFixture.configureByText(
            XmlFileType.INSTANCE,
            """
            <resources>
                <string-array name="menu">
                    <item>Home</item>
                    <item>Settings</item>
                </string-array>
            </resources>
            """.trimIndent(),
        )

        val entry = ComposeStringsXmlParser()
            .parse(assertInstanceOf(psiFile, XmlFile::class.java))
            .single()

        assertEquals(ComposeResourceType.STRING_ARRAY, entry.type)
        assertEquals(listOf("0", "1"), entry.items.map(ComposeResourceItem::name))
        assertEquals(listOf("Home", "Settings"), entry.items.map(ComposeResourceItem::value))
    }

    fun testParsesPluralQuantitiesAndSkipsBlankItems() {
        val psiFile = myFixture.configureByText(
            XmlFileType.INSTANCE,
            """
            <resources>
                <plurals name="inbox_count">
                    <item quantity="one">%d message</item>
                    <item quantity="zero"></item>
                    <item quantity="other">%d messages</item>
                </plurals>
            </resources>
            """.trimIndent(),
        )

        val entry = ComposeStringsXmlParser()
            .parse(assertInstanceOf(psiFile, XmlFile::class.java))
            .single()

        assertEquals(ComposeResourceType.PLURALS, entry.type)
        assertEquals(listOf("one", "other"), entry.items.map(ComposeResourceItem::name))
        assertEquals(listOf("%d message", "%d messages"), entry.items.map(ComposeResourceItem::value))
    }
}
