package zone.ien.cmp_translation_plugin.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet
import zone.ien.cmp_translation_plugin.write.ComposeResourceWriter

internal object ComposeTranslationNavigation {

    private val resourceKeyPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val composeStringReferencePattern = Regex(
        "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)*Res\\.string\\.([A-Za-z_][A-Za-z0-9_]*)(?![A-Za-z0-9_])",
    )

    fun extractResourceKey(sourceText: String, ancestorTexts: Iterable<String>): String? {
        composeStringReferencePattern.find(sourceText)?.let { match ->
            return match.groupValues[1]
        }

        val key = sourceText.trim()
        if (!resourceKeyPattern.matches(key)) return null
        return key.takeIf {
            ancestorTexts.any { ancestorText ->
                composeStringReferencePattern.findAll(ancestorText).any { match ->
                    match.groupValues[1] == key
                }
            }
        }
    }

    fun extractResourceKey(sourceElement: PsiElement): String? {
        val ancestorTexts = generateSequence(sourceElement) { it.parent }
            .take(8)
            .mapNotNull(PsiElement::getText)
            .toList()
        return extractResourceKey(sourceElement.text, ancestorTexts)
    }

    fun findResourceSetForUsage(
        resourceSets: List<ComposeResourceSet>,
        sourceFile: VirtualFile?,
        key: String,
    ): ComposeResourceSet? {
        val candidates = resourceSets.filter { resourceSet ->
            resourceSet.documents.any { document -> document.entries.any { it.key == key } }
        }
        if (candidates.isEmpty()) return null

        val sourceSetCandidates = sourceFile?.let { file ->
            candidates.filter { resourceSet ->
                val sourceSetRoot = resourceSet.resourceRoot.parent ?: return@filter false
                file.path == sourceSetRoot.path || file.path.startsWith("${sourceSetRoot.path}/")
            }
        }.orEmpty()

        return sourceSetCandidates.maxByOrNull { it.resourceRoot.path.length }
            ?: candidates.singleOrNull()
    }

    fun rowFor(resourceSet: ComposeResourceSet, key: String): TranslationRow? {
        val defaultEntry = resourceSet.defaultDocument?.entries?.firstOrNull { it.key == key }
        val localizedValues = resourceSet.localizedDocuments.associate { document ->
            document.descriptor.qualifier to document.entries.firstOrNull { it.key == key }?.value
        }
        if (defaultEntry == null && localizedValues.values.all { it == null }) return null

        return TranslationRow(
            key = key,
            defaultValue = defaultEntry?.value,
            localizedValues = localizedValues,
            issues = emptyList(),
            translatable = defaultEntry?.translatable ?: true,
        )
    }

    fun showEditDialog(
        project: Project,
        resourceSet: ComposeResourceSet,
        key: String,
        writer: ComposeResourceWriter = ComposeResourceWriter(project),
        onUpdated: () -> Unit = {},
    ): Boolean {
        val row = rowFor(resourceSet, key) ?: return false
        val dialog = AddStringDialog(
            project = project,
            qualifiers = resourceSet.localizedDocuments.map { it.descriptor.qualifier },
            initialRow = row,
        )
        if (!dialog.showAndGet()) return true
        val draft = dialog.draft()

        ApplicationManager.getApplication().invokeLater {
            val success = writer.updateStringResource(
                resourceSet = resourceSet,
                oldKey = row.key,
                newKey = draft.key,
                defaultValue = draft.defaultValue,
                localizedValues = draft.localizedValues,
                translatable = draft.translatable,
            )
            if (!success) {
                Messages.showErrorDialog(
                    project,
                    MyBundle.message("translation.error.locale-edit"),
                    MyBundle.message("translation.title"),
                )
            } else {
                onUpdated()
            }
        }
        return true
    }
}
