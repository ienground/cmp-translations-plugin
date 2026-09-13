package zone.ien.cmp_translation_plugin.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import zone.ien.cmp_translation_plugin.MyBundle
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.write.ComposeResourceWriter

/** Offers extraction of a Kotlin string literal into Compose Multiplatform resources. */
internal class ComposeStringResourceIntentionAction(
    private val draftProvider: (Project, List<ComposeResourceQualifier>, String, String) -> StringResourceDraft? =
        ::showAddDialog,
    private val sourceDocumentProvider: (Project, PsiFile) -> Document? = { project, file ->
        PsiDocumentManager.getInstance(project).getDocument(file)
            ?: file.virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
    },
    private val errorNotifier: (Project, String, String) -> Unit = { project, message, title ->
        Messages.showErrorDialog(project, message, title)
    },
) : IntentionAction {

    override fun getText(): String = MyBundle.message("translation.extract.action.name")

    override fun getFamilyName(): String = MyBundle.message("translation.extract.action.family")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        findLiteral(file, editor.caretModel.offset)?.let { isComposableContext(file, it.range) } == true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val literal = findLiteral(file, editor.caretModel.offset)
            ?.takeIf { isComposableContext(file, it.range) }
            ?: return
        val value = ComposeStringResourceExtraction.extractLiteralValue(literal.text) ?: return
        val sourceDocument = sourceDocumentProvider(project, file)
        if (sourceDocument == null || !file.isWritable || file.virtualFile?.isWritable != true) {
            errorNotifier(
                project,
                MyBundle.message("translation.error.source-edit-failed"),
                MyBundle.message("translation.title"),
            )
            return
        }
        val resReference = ComposeStringResourceExtraction.resReference(sourceDocument.text)
            ?: run {
                errorNotifier(
                    project,
                    MyBundle.message("translation.error.res-import-required"),
                    MyBundle.message("translation.title"),
                )
                return
            }
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

        val existingKeys = resourceSet.documents.flatMapTo(mutableSetOf()) { document ->
            document.entries.map { it.key }
        }
        val suggestedKey = ComposeStringResourceExtraction.uniqueResourceKey(value, existingKeys)
        val draft = draftProvider(
            project,
            resourceSet.localizedDocuments.map { it.descriptor.qualifier },
            suggestedKey,
            value,
        ) ?: return
        if (draft.key in existingKeys) {
            errorNotifier(
                project,
                MyBundle.message("translation.error.add-failed"),
                MyBundle.message("translation.title"),
            )
            return
        }
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
            document = sourceDocument,
            resReference = resReference,
            literalRange = literal.range,
            resourceKey = draft.key,
        ).takeIf { it } ?: run {
            writer.removeKey(resourceSet, draft.key)
            errorNotifier(
                project,
                MyBundle.message("translation.error.source-edit-failed"),
                MyBundle.message("translation.title"),
            )
        }
    }

    override fun startInWriteAction(): Boolean = false

    private fun replaceLiteral(
        project: Project,
        document: Document,
        resReference: String,
        literalRange: TextRange,
        resourceKey: String,
    ): Boolean {
        if (literalRange.startOffset < 0 || literalRange.endOffset > document.textLength) return false
        val originalText = document.text
        val stringResourceFunction = ComposeStringResourceExtraction.stringResourceReference(document.text)
        val replacement = ComposeStringResourceExtraction.replacementExpression(
            resourceKey = resourceKey,
            resReference = resReference,
            stringResourceFunction = stringResourceFunction,
        )

        return try {
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
                    if (updatedText != document.text) {
                        document.replaceString(0, document.textLength, updatedText)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                },
            )
            true
        } catch (_: RuntimeException) {
            runCatching {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.replaceString(0, document.textLength, originalText)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            }
            false
        }
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

    private fun isComposableContext(file: PsiFile, literalRange: TextRange): Boolean {
        val element = file.findElementAt(literalRange.startOffset) ?: return false
        val ancestors = generateSequence(element) { it.parent }
            .takeWhile { it !is PsiFile }
            .toList()
        if (ancestors.any { it is KtParameter || it is KtAnnotationEntry }) return false
        ancestors.filterIsInstance<KtLambdaExpression>().firstOrNull()?.let { lambda ->
            val property = ancestors.filterIsInstance<KtProperty>().firstOrNull()
            val isComposableProperty =
                property?.initializer == lambda && property.typeReference?.hasComposableAnnotation() == true
            if (property?.initializer == lambda && !isComposableProperty) {
                return false
            }
            if (!isComposableProperty && !isComposableLambda(lambda)) {
                return false
            }
        }

        return ancestors
            .filterIsInstance<KtAnnotated>()
            .any { annotated ->
                annotated.hasComposableAnnotation()
            }
    }

    private fun isComposableLambda(lambda: KtLambdaExpression): Boolean {
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val call = generateSequence(lambdaArgument.parent) { it.parent }
            .firstOrNull { it is KtCallExpression } as? KtCallExpression
            ?: return false
        val declaration = call.calleeExpression?.references?.firstOrNull()?.resolve() as? KtCallableDeclaration
        if (declaration == null) {
            val functionName = call.calleeExpression?.text
                ?.substringAfterLast('.')
                ?: return false
            if (lambdaArgument.getArgumentName()?.asName?.asString()?.startsWith("on") == true) return false
            if (functionName in NON_COMPOSABLE_LAMBDA_FUNCTIONS) return false
            if (functionName in COMPOSABLE_LAMBDA_FUNCTIONS) return true
            return functionName.firstOrNull()?.isUpperCase() == true
        }
        val parameterName = lambdaArgument.getArgumentName()?.asName?.asString()
        val parameter = parameterName?.let { name ->
            declaration.valueParameters.firstOrNull { it.name == name }
        } ?: call.valueArguments.indexOf(lambdaArgument).takeIf { it >= 0 }?.let { index ->
            declaration.valueParameters.getOrNull(index)
        } ?: declaration.valueParameters.lastOrNull()
        return parameter?.typeReference?.hasComposableAnnotation() == true
    }

    private fun KtAnnotated.hasComposableAnnotation(): Boolean = annotationEntries.any { entry ->
        entry.shortName?.asString() == COMPOSABLE_ANNOTATION
    }

    private companion object {
        const val STRING_RESOURCE_IMPORT = "org.jetbrains.compose.resources.stringResource"
        const val COMPOSABLE_ANNOTATION = "Composable"
        val COMPOSABLE_LAMBDA_FUNCTIONS = setOf("application", "setContent", "singleWindowApplication")
        val NON_COMPOSABLE_LAMBDA_FUNCTIONS = setOf(
            "also",
            "async",
            "collect",
            "collectLatest",
            "derivedStateOf",
            "DisposableEffect",
            "forEach",
            "LaunchedEffect",
            "launch",
            "let",
            "map",
            "produceState",
            "remember",
            "rememberSaveable",
            "run",
            "runCatching",
            "SideEffect",
            "snapshotFlow",
            "with",
        )
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
