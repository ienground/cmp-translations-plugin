package zone.ien.cmp_translation_plugin.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.write.ComposeResourceWriter

/** Offers extraction of a Kotlin string literal into Compose Multiplatform resources. */
internal class ComposeStringResourceIntentionAction(
    private val draftProvider: (Project, List<ComposeResourceQualifier>, String, String) -> StringResourceDraft? =
        ::showAddDialog,
) : IntentionAction {

    override fun getText(): String = MyBundle.message("translation.extract.action.name")

    override fun getFamilyName(): String = MyBundle.message("translation.extract.action.family")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        findLiteral(file, editor.caretModel.offset) != null

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val literal = findLiteral(file, editor.caretModel.offset) ?: return
        val value = ComposeStringResourceExtraction.extractLiteralValue(literal.text) ?: return
        val resourceSet = ComposeTranslationNavigation.findResourceSetForSource(
            resourceSets = ComposeResourceCatalog(project).load(),
            sourceFile = file.virtualFile,
        )
        if (resourceSet == null) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.extract.error.no-resource-set"),
                MyBundle.message("translation.title"),
            )
            return
        }

        val suggestedKey = ComposeStringResourceExtraction.uniqueResourceKey(
            value = value,
            existingKeys = resourceSet.documents.flatMapTo(mutableSetOf()) { document ->
                document.entries.map { it.key }
            },
        )
        val draft = draftProvider(
            project,
            resourceSet.localizedDocuments.map { it.descriptor.qualifier },
            suggestedKey,
            value,
        ) ?: return
        val writer = ComposeResourceWriter(project)
        val added = writer.addStringResource(
            resourceSet = resourceSet,
            key = draft.key,
            defaultValue = draft.defaultValue,
            localizedValues = draft.localizedValues,
            translatable = draft.translatable,
        )
        if (!added) {
            Messages.showErrorDialog(
                project,
                MyBundle.message("translation.error.add-failed"),
                MyBundle.message("translation.title"),
            )
            return
        }

        replaceLiteral(
            project = project,
            file = file,
            literalRange = literal.range,
            resourceKey = draft.key,
        )
    }

    override fun startInWriteAction(): Boolean = false

    private fun replaceLiteral(
        project: Project,
        file: PsiFile,
        literalRange: TextRange,
        resourceKey: String,
    ) {
        val virtualFile = file.virtualFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
            ?: FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: return
        val resReference = ComposeStringResourceExtraction.resReference(document.text)
        val replacement = ComposeStringResourceExtraction.replacementExpression(resourceKey, resReference)

        WriteCommandAction.runWriteCommandAction(
            project,
            MyBundle.message("translation.extract.command"),
            null,
            Runnable {
                document.replaceString(literalRange.startOffset, literalRange.endOffset, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)

                val updatedText = ComposeStringResourceExtraction.insertMissingImport(
                    sourceText = document.text,
                    importName = STRING_RESOURCE_IMPORT,
                )
                if (updatedText == document.text) return@Runnable
                document.replaceString(0, document.textLength, updatedText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            },
        )
    }

    private fun findLiteral(file: PsiFile, offset: Int): LiteralOccurrence? {
        if (file.virtualFile?.extension?.lowercase() !in KOTLIN_EXTENSIONS) return null
        if (file.textLength == 0) return null

        val safeOffset = offset.coerceIn(0, file.textLength - 1)
        val element = file.findElementAt(safeOffset)
        val psiLiteral = element?.let {
            generateSequence(it) { parent -> parent.parent }
                .take(8)
                .firstOrNull { ComposeStringResourceExtraction.extractLiteralValue(it.text) != null }
        }
        if (psiLiteral != null) {
            return LiteralOccurrence(
                range = psiLiteral.textRange ?: return null,
                text = psiLiteral.text,
            )
        }

        return KOTLIN_STRING_LITERAL.findAll(file.text)
            .firstOrNull { safeOffset in it.range }
            ?.let { match -> LiteralOccurrence(TextRange(match.range.first, match.range.last + 1), match.value) }
    }

    private companion object {
        const val STRING_RESOURCE_IMPORT = "org.jetbrains.compose.resources.stringResource"
        val KOTLIN_EXTENSIONS = setOf("kt", "kts")
        val KOTLIN_STRING_LITERAL = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"")

        fun showAddDialog(
            project: Project,
            qualifiers: List<ComposeResourceQualifier>,
            initialKey: String,
            initialDefaultValue: String,
        ): StringResourceDraft? {
            val dialog = AddStringDialog(
                project = project,
                qualifiers = qualifiers,
                initialKey = initialKey,
                initialDefaultValue = initialDefaultValue,
            )
            return dialog.takeIf { it.showAndGet() }?.draft()
        }
    }

    private data class LiteralOccurrence(
        val range: TextRange,
        val text: String,
    )
}
