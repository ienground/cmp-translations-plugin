package zone.ien.cmp_translation_plugin.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType
import zone.ien.cmp_translation_plugin.write.ComposeResourceWriter

internal object ComposeTranslationNavigation {

    private val resourceKeyPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val composeResourceReferencePattern = Regex(
        "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)*Res\\.(?:string|array|plurals)\\.([A-Za-z_][A-Za-z0-9_]*)(?![A-Za-z0-9_])",
    )

    fun extractResourceKey(sourceText: String, ancestorTexts: Iterable<String>): String? {
        composeResourceReferencePattern.find(sourceText)?.let { match ->
            return match.groupValues[1]
        }

        val key = sourceText.trim()
        if (!resourceKeyPattern.matches(key)) return null
        return key.takeIf {
            ancestorTexts.any { ancestorText ->
                composeResourceReferencePattern.findAll(ancestorText).any { match ->
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

    fun findResourceSetForSource(
        resourceSets: List<ComposeResourceSet>,
        sourceFile: VirtualFile?,
    ): ComposeResourceSet? {
        val file = sourceFile ?: return null
        return resourceSets
            .filter { resourceSet ->
                val sourceSetRoot = resourceSet.resourceRoot.parent ?: return@filter false
                file.path == sourceSetRoot.path || file.path.startsWith("${sourceSetRoot.path}/")
            }
            .maxByOrNull { it.resourceRoot.path.length }
    }

    fun rowFor(resourceSet: ComposeResourceSet, key: String): TranslationRow? {
        val defaultEntry = resourceSet.defaultDocument?.entries?.firstOrNull { it.key == key }
        val localizedEntries = resourceSet.localizedDocuments.associate { document ->
            document.descriptor.qualifier to document.entries.firstOrNull { it.key == key }
        }
        if (defaultEntry == null && localizedEntries.values.all { it == null }) return null

        val type = defaultEntry?.type
            ?: localizedEntries.values.firstNotNullOfOrNull { it?.type }
            ?: ComposeResourceType.STRING
        val children = if (type == ComposeResourceType.STRING) {
            emptyList()
        } else {
            val itemNames = buildList {
                defaultEntry?.items?.forEach { add(it.name) }
                localizedEntries.values.filterNotNull().flatMap { it.items }.forEach { add(it.name) }
            }.distinct()
            itemNames.map { itemName ->
                TranslationRow(
                    key = key,
                    defaultValue = defaultEntry?.items?.firstOrNull { it.name == itemName }?.value,
                    localizedValues = localizedEntries.mapValues { (_, entry) ->
                        entry?.items?.firstOrNull { it.name == itemName }?.value
                    },
                    issues = emptyList(),
                    translatable = defaultEntry?.translatable ?: true,
                    resourceType = type,
                    parentKey = key,
                    itemName = itemName,
                    depth = 1,
                )
            }
        }

        return TranslationRow(
            key = key,
            defaultValue = defaultEntry?.takeIf { type == ComposeResourceType.STRING }?.value,
            localizedValues = resourceSet.localizedDocuments.associate { document ->
                document.descriptor.qualifier to localizedEntries[document.descriptor.qualifier]
                    ?.takeIf { type == ComposeResourceType.STRING }
                    ?.value
            },
            issues = emptyList(),
            translatable = defaultEntry?.translatable ?: true,
            resourceType = type,
            children = children,
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
            initialType = row.resourceType,
            initialArrayItems = resourceSet.defaultDocument?.entries
                ?.firstOrNull { it.key == key }
                ?.items
                .orEmpty(),
            initialLocalizedItems = resourceSet.localizedDocuments.associate { document ->
                document.descriptor.qualifier to document.entries.firstOrNull { it.key == key }?.items.orEmpty()
            },
        )
        if (!dialog.showAndGet()) return true
        val draft = dialog.draft()

        ApplicationManager.getApplication().invokeLater {
            val success = writer.updateResource(
                resourceSet = resourceSet,
                oldKey = row.key,
                draft = draft,
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
