package zone.ien.composemultiplatformtranslations.write

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.XmlElementFactory
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceDocument
import zone.ien.composemultiplatformtranslations.resource.ComposeResourceSet

/** Applies translation changes through IntelliJ XML PSI write commands. */
class ComposeResourceWriter(private val project: Project) {

    fun updateValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            findXmlFile(document)?.let { xmlFile ->
                findStringTag(xmlFile, key)?.let { tag ->
                    tag.value.text = value
                    true
                }
            } ?: false
        }

    fun upsertValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            val existingTag = findStringTag(xmlFile, key)
            if (existingTag != null) {
                existingTag.value.text = value
                true
            } else {
                addTag(xmlFile, key, value)
            }
        }

    fun addValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            if (xmlFile.rootTag == null) return@runWriteCommand false
            if (findStringTag(xmlFile, key) != null) return@runWriteCommand false

            addTag(xmlFile, key, value)
        }

    fun removeKey(resourceSet: ComposeResourceSet, key: String): Int =
        runWriteCommand {
            resourceSet.documents.sumOf { document ->
                val tag = findXmlFile(document)?.let { findStringTag(it, key) } ?: return@sumOf 0
                tag.delete()
                1
            }
        }

    private fun findXmlFile(document: ComposeResourceDocument): XmlFile? =
        PsiManager.getInstance(project).findFile(document.descriptor.file) as? XmlFile

    private fun findStringTag(file: XmlFile, key: String): XmlTag? =
        file.rootTag?.findSubTags("string")?.firstOrNull { it.getAttributeValue("name") == key }

    private fun addTag(file: XmlFile, key: String, value: String): Boolean {
        val rootTag = file.rootTag ?: return false
        val newTag = XmlElementFactory.getInstance(project).createTagFromText("<string name=\"resource\" />")
        newTag.setAttribute("name", key)
        newTag.value.text = value
        rootTag.addSubTag(newTag, false)
        return true
    }

    private fun <T> runWriteCommand(action: () -> T): T {
        var result: T? = null
        WriteCommandAction.runWriteCommandAction(project) {
            result = action()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
