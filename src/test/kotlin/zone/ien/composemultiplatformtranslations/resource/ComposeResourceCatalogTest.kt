package zone.ien.composemultiplatformtranslations.resource

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class ComposeResourceCatalogTest : BasePlatformTestCase() {

    fun testLoadsPsiEntriesGroupedByComposeResourcesRoot() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Login</string></resources>",
        )
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values-ko/strings.xml",
            "<resources><string name=\"login\">로그인</string></resources>",
        )

        val resourceSet = ComposeResourceCatalog(project).load().single()

        assertEquals("commonMain", resourceSet.sourceSetName)
        assertEquals(2, resourceSet.documents.size)
        assertNotNull(resourceSet.defaultDocument)
        assertEquals(listOf("login"), resourceSet.defaultDocument?.entries?.map { it.key })
        assertEquals("로그인", resourceSet.documentFor(ComposeResourceQualifier("ko"))?.entries?.single()?.value)
    }
}
