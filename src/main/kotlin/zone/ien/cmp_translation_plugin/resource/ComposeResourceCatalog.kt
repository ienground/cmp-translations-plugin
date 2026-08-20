package zone.ien.cmp_translation_plugin.resource

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import java.util.concurrent.Callable

data class ComposeResourceDocument(
    val descriptor: ComposeResourceDescriptor,
    val entries: List<ComposeStringEntry>,
)

data class ComposeResourceSet(
    val resourceRoot: VirtualFile,
    val moduleName: String,
    val sourceSetName: String,
    val documents: List<ComposeResourceDocument>,
) {

    val defaultDocument: ComposeResourceDocument?
        get() = documentFor(ComposeResourceQualifier.DEFAULT)

    val localizedDocuments: List<ComposeResourceDocument>
        get() = documents.filterNot { it.descriptor.qualifier == ComposeResourceQualifier.DEFAULT }

    fun documentFor(qualifier: ComposeResourceQualifier): ComposeResourceDocument? =
        documents.firstOrNull { it.descriptor.qualifier == qualifier }
}

/** Loads strings.xml PSI entries and groups them by composeResources directory. */
class ComposeResourceCatalog(
    private val project: Project,
    private val locator: ComposeResourceLocator = ComposeResourceLocator(project),
    private val parser: ComposeStringsXmlParser = ComposeStringsXmlParser(),
) {

    fun load(): List<ComposeResourceSet> = ReadAction.nonBlocking(Callable {
        loadInReadAction()
    }).executeSynchronously()

    fun loadAsync(onLoaded: (List<ComposeResourceSet>) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val resourceSets = load()
            if (project.isDisposed) return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater(
                {
                    if (!project.isDisposed) onLoaded(resourceSets)
                },
                ModalityState.defaultModalityState(),
            )
        }
    }

    private fun loadInReadAction(): List<ComposeResourceSet> {
        val psiManager = PsiManager.getInstance(project)
        val documents = locator.findFiles().mapNotNull { descriptor ->
            val xmlFile = psiManager.findFile(descriptor.file) as? XmlFile ?: return@mapNotNull null
            ComposeResourceDocument(descriptor, parser.parse(xmlFile))
        }

        return documents
            .groupBy { it.descriptor.resourceRoot.url }
            .values
            .map { groupedDocuments ->
                val first = groupedDocuments.first()
                ComposeResourceSet(
                    resourceRoot = first.descriptor.resourceRoot,
                    moduleName = first.descriptor.moduleName,
                    sourceSetName = first.descriptor.sourceSetName,
                    documents = groupedDocuments.sortedBy { it.descriptor.qualifier.rawValue },
                )
            }
            .sortedWith(compareBy({ it.sourceSetName != "commonMain" }, { it.moduleName }, { it.sourceSetName }, { it.resourceRoot.url }))
    }
}
