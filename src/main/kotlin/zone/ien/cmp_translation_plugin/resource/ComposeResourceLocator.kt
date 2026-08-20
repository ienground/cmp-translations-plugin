package zone.ien.cmp_translation_plugin.resource

import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/** Finds Compose Multiplatform strings.xml files in the current project. */
class ComposeResourceLocator(private val project: Project) {

    fun findFiles(): List<ComposeResourceDescriptor> =
        FilenameIndex
            .getVirtualFilesByName("strings.xml", GlobalSearchScope.projectScope(project))
            .mapNotNull(ComposeResourceDescriptor::from)
            .sortedWith(compareBy({ it.resourceRoot.path }, { it.qualifier.rawValue }))
}
