package zone.ien.cmp_translation_plugin.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import zone.ien.cmp_translation_plugin.resource.ComposeResourceCatalog
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet

internal class ComposeTranslationGotoDeclarationHandler : GotoDeclarationHandlerBase() {

    override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor): PsiElement? {
        sourceElement ?: return null
        val key = ComposeTranslationNavigation.extractResourceKey(sourceElement) ?: return null
        val resourceSets = ComposeResourceCatalog(sourceElement.project).load()
        val resourceSet = ComposeTranslationNavigation.findResourceSetForUsage(
            resourceSets = resourceSets,
            sourceFile = sourceElement.containingFile?.virtualFile,
            key = key,
        ) ?: return null
        if (ComposeTranslationNavigation.rowFor(resourceSet, key) == null) return null

        return ComposeTranslationNavigationTarget(sourceElement, resourceSet, key)
    }
}

private class ComposeTranslationNavigationTarget(
    private val delegate: PsiElement,
    private val resourceSet: ComposeResourceSet,
    private val key: String,
) : FakePsiElement() {

    override fun getParent(): PsiElement? = delegate.parent

    override fun getContainingFile(): PsiFile? = delegate.containingFile

    override fun getManager(): PsiManager = delegate.manager

    override fun getTextRange(): TextRange = delegate.textRange ?: TextRange.EMPTY_RANGE

    override fun getTextOffset(): Int = delegate.textOffset

    override fun getText(): String = key

    override fun getName(): String = key

    override fun getPresentableText(): String = key

    override fun navigate(requestFocus: Boolean) {
        ComposeTranslationNavigation.showEditDialog(
            project = delegate.project,
            resourceSet = resourceSet,
            key = key,
        )
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = false

    override fun toString(): String = "ComposeTranslationNavigationTarget($key)"
}
