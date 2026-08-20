package zone.ien.cmp_translation_plugin.write

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceDocument
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeStringEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class ComposeResourceWriterTest : BasePlatformTestCase() {

    fun testUpdatesExistingValueThroughXmlPsi() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        val document = loadSet().defaultDocument!!

        ComposeResourceWriter(project).updateValue(document, "login", "Sign in")
        commitPsi()

        assertEquals("Sign in", findStringValue(document.descriptor.file, "login"))
    }

    fun testAddsValueToDefaultDocument() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        val document = loadSet().defaultDocument!!

        ComposeResourceWriter(project).addValue(document, "profile", "Profile")
        commitPsi()

        assertEquals("Profile", findStringValue(document.descriptor.file, "profile"))
    }

    fun testUpsertsMissingLocalizedValue() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        createFile("src/commonMain/composeResources/values-ko/strings.xml", "<resources />")
        val document = loadSet().documentFor(zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier("ko"))!!

        ComposeResourceWriter(project).upsertValue(document, "login", "로그인")
        commitPsi()

        assertEquals("로그인", findStringValue(document.descriptor.file, "login"))
    }

    fun testRemovesKeyFromEveryLocaleDocument() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        createFile("src/commonMain/composeResources/values-ko/strings.xml", "<resources><string name=\"login\">로그인</string></resources>")
        val resourceSet = loadSet()

        ComposeResourceWriter(project).removeKey(resourceSet, "login")
        commitPsi()

        resourceSet.documents.forEach { document ->
            assertEquals(null, findStringValue(document.descriptor.file, "login"))
        }
    }

    fun testRenamesKeyAcrossEveryLocaleDocument() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        createFile("src/commonMain/composeResources/values-ko/strings.xml", "<resources><string name=\"login\">로그인</string></resources>")
        val resourceSet = loadSet()

        assertEquals(true, ComposeResourceWriter(project).renameKey(resourceSet, "login", "sign_in"))
        commitPsi()

        resourceSet.documents.forEach { document ->
            assertEquals(null, findStringValue(document.descriptor.file, "login"))
            assertNotNull(findStringValue(document.descriptor.file, "sign_in"))
        }
    }

    fun testAddsDefaultAndProvidedLocaleValuesTogether() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources />")
        createFile("src/commonMain/composeResources/values-ko/strings.xml", "<resources />")
        val resourceSet = loadSet()

        assertEquals(
            true,
            ComposeResourceWriter(project).addStringResource(
                resourceSet = resourceSet,
                key = "profile_title",
                defaultValue = "Profile",
                localizedValues = mapOf(ComposeResourceQualifier("ko") to "프로필"),
            ),
        )
        commitPsi()

        assertEquals("Profile", findStringValue(resourceSet.defaultDocument!!.descriptor.file, "profile_title"))
        assertEquals("프로필", findStringValue(resourceSet.documentFor(ComposeResourceQualifier("ko"))!!.descriptor.file, "profile_title"))
    }

    fun testAddsUntranslatableStringResource() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources />")
        val resourceSet = loadSet()

        assertEquals(
            true,
            ComposeResourceWriter(project).addStringResource(
                resourceSet = resourceSet,
                key = "app_name",
                defaultValue = "My App",
                localizedValues = emptyMap(),
                translatable = false,
            ),
        )
        commitPsi()

        val xmlFile = PsiManager.getInstance(project).findFile(resourceSet.defaultDocument!!.descriptor.file) as XmlFile
        val tag = xmlFile.rootTag?.findSubTags("string")?.firstOrNull { it.getAttributeValue("name") == "app_name" }
        assertEquals("false", tag?.getAttributeValue("translatable"))
    }

    fun testUpdatesStringResourceAtomically() {
        createFile("src/commonMain/composeResources/values/strings.xml", "<resources><string name=\"login\">Login</string></resources>")
        createFile("src/commonMain/composeResources/values-ko/strings.xml", "<resources><string name=\"login\">로그인</string></resources>")
        val resourceSet = loadSet()

        assertEquals(
            true,
            ComposeResourceWriter(project).updateStringResource(
                resourceSet = resourceSet,
                oldKey = "login",
                newKey = "sign_in",
                defaultValue = "Sign in",
                localizedValues = mapOf(ComposeResourceQualifier("ko") to "로그인하기"),
                translatable = false,
            ),
        )
        commitPsi()

        val defaultDoc = resourceSet.defaultDocument!!.descriptor.file
        val koDoc = resourceSet.documentFor(ComposeResourceQualifier("ko"))!!.descriptor.file
        assertEquals(null, findStringValue(defaultDoc, "login"))
        assertEquals("Sign in", findStringValue(defaultDoc, "sign_in"))
        val defaultXml = PsiManager.getInstance(project).findFile(defaultDoc) as XmlFile
        val defaultTag = defaultXml.rootTag?.findSubTags("string")?.firstOrNull { it.getAttributeValue("name") == "sign_in" }
        assertEquals("false", defaultTag?.getAttributeValue("translatable"))
        assertEquals("로그인하기", findStringValue(koDoc, "sign_in"))
    }

    private fun loadSet(): ComposeResourceSet = ComposeResourceCatalog(project).load().single()

    private fun createFile(path: String, text: String): VirtualFile =
        myFixture.tempDirFixture.createFile(path, text)

    private fun commitPsi() {
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    private fun findStringValue(file: VirtualFile, key: String): String? {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        return xmlFile.rootTag?.findSubTags("string")
            ?.firstOrNull { it.getAttributeValue("name") == key }
            ?.value
            ?.text
    }
}
