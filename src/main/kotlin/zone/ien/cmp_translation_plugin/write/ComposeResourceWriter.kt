package zone.ien.cmp_translation_plugin.write

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.XmlElementFactory
import zone.ien.cmp_translation_plugin.resource.ComposeResourceDocument
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet

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

    fun addStringResource(
        resourceSet: ComposeResourceSet,
        key: String,
        defaultValue: String,
        localizedValues: Map<ComposeResourceQualifier, String>,
        translatable: Boolean = true,
    ): Boolean = runWriteCommand {
        val defaultDocument = resourceSet.defaultDocument ?: return@runWriteCommand false
        val defaultFile = findXmlFile(defaultDocument) ?: return@runWriteCommand false
        if (key.isBlank() || findStringTag(defaultFile, key) != null) return@runWriteCommand false

        val localeFiles = resourceSet.localizedDocuments.mapNotNull { document ->
            findXmlFile(document)?.let { document.descriptor.qualifier to it }
        }
        if (localeFiles.size != resourceSet.localizedDocuments.size) return@runWriteCommand false

        addTag(defaultFile, key, defaultValue, translatable)
        localeFiles.forEach { (qualifier, file) ->
            val value = localizedValues[qualifier]?.takeIf(String::isNotBlank) ?: return@forEach
            val existingTag = findStringTag(file, key)
            if (existingTag != null) {
                existingTag.value.text = value
            } else {
                addTag(file, key, value)
            }
        }
        true
    }

    fun renameKey(resourceSet: ComposeResourceSet, oldKey: String, newKey: String): Boolean =
        runWriteCommand {
            if (newKey.isBlank()) return@runWriteCommand false
            if (oldKey == newKey) return@runWriteCommand true

            val files = resourceSet.documents.mapNotNull { document ->
                findXmlFile(document)
            }
            if (files.size != resourceSet.documents.size) return@runWriteCommand false
            if (files.any { file -> findStringTag(file, newKey) != null }) return@runWriteCommand false

            files.forEach { file ->
                findStringTag(file, oldKey)?.setAttribute("name", newKey)
            }
            true
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

    private fun addTag(file: XmlFile, key: String, value: String, translatable: Boolean = true): Boolean {
        val rootTag = file.rootTag ?: return false
        val newTag = XmlElementFactory.getInstance(project).createTagFromText("<string name=\"resource\" />")
        newTag.setAttribute("name", key)
        newTag.value.text = value
        rootTag.addSubTag(newTag, false)
        return true
    }

    fun setTranslatable(document: ComposeResourceDocument, key: String, translatable: Boolean): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            val tag = findStringTag(xmlFile, key) ?: return@runWriteCommand false
            if (translatable) {
                tag.setAttribute("translatable", null)
            } else {
                tag.setAttribute("translatable", "false")
            }
            true
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
