package zone.ien.cmp_translation_plugin.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog

/** Provides optional code folding that replaces a Compose resource call with its translation. */
internal class ComposeTranslationFoldingBuilder : FoldingBuilder {

    override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
        val file = node.psi?.containingFile ?: return emptyArray()
        val resourceSets = ComposeResourceCatalog(file.project).load()
        val qualifier = ComposeTranslationDisplaySettings.getInstance(file.project).selectedQualifier

        return PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java)
            .mapNotNull { call ->
                val key = resourceKey(call) ?: return@mapNotNull null
                val resourceSet = ComposeTranslationNavigation.findResourceSetForUsage(
                    resourceSets = resourceSets,
                    sourceFile = file.virtualFile,
                    key = key,
                ) ?: return@mapNotNull null
                val value = ComposeTranslationLanguageSelection.value(resourceSet, key, qualifier)
                    ?: return@mapNotNull null

                FoldingDescriptor(
                    call.node,
                    call.textRange,
                    null,
                    "\"${value.asDisplayText()}\"",
                )
            }
            .toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = node.psi?.text.orEmpty()

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true

    private fun resourceKey(call: KtCallExpression): String? {
        if (call.calleeExpression?.text != STRING_RESOURCE_FUNCTION) return null
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return null
        return ComposeTranslationResourceCall.extractKey(argument)
    }

    private fun String.asDisplayText(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private companion object {
        const val STRING_RESOURCE_FUNCTION = "stringResource"
    }
}
