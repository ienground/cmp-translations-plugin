package zone.ien.composemultiplatformtranslations.write

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceCatalog
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceDocument
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceSet
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier
import zone.ien.composemultiplatformtranslations.resource.ComposeStringEntry
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
        val document = loadSet().documentFor(zone.ien.composemultiplatformtranslations.resource.ComposeResourceQualifier("ko"))!!

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
