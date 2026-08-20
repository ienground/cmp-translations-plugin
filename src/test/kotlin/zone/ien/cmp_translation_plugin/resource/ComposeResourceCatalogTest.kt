package zone.ien.cmp_translation_plugin.resource

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    fun testLoadsAsynchronouslyAndFinishesOnEdt() {
        myFixture.tempDirFixture.createFile(
            "src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"login\">Login</string></resources>",
        )

        val callbackCalled = AtomicBoolean(false)
        val callbackOnEdt = AtomicReference(false)
        val loadedSets = AtomicReference<List<ComposeResourceSet>>()

        ComposeResourceCatalog(project).loadAsync { resourceSets ->
            callbackOnEdt.set(javax.swing.SwingUtilities.isEventDispatchThread())
            loadedSets.set(resourceSets)
            callbackCalled.set(true)
        }

        PlatformTestUtil.waitWithEventsDispatching(
            "resource catalog load",
            callbackCalled::get,
            5_000,
        )

        assertTrue(callbackOnEdt.get())
        assertEquals(1, loadedSets.get().size)
    }
}
