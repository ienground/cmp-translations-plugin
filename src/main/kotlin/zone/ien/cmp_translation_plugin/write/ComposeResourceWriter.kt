package zone.ien.cmp_translation_plugin.write

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import zone.ien.cmp_translation_plugin.resource.ComposeResourceDocument
import zone.ien.cmp_translation_plugin.resource.ComposeResourceDraft
import zone.ien.cmp_translation_plugin.resource.ComposeResourceItem
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet
import zone.ien.cmp_translation_plugin.resource.ComposeResourceType

/** Applies translation changes through IntelliJ XML PSI write commands. */
class ComposeResourceWriter(private val project: Project) {

    fun updateValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            findXmlFile(document)?.let { xmlFile ->
                findResourceTag(xmlFile, key)?.takeIf { it.name == "string" }?.let { tag ->
                    tag.value.text = value
                    true
                }
            } ?: false
        }

    fun upsertValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            val existingTag = findResourceTag(xmlFile, key)
            if (existingTag != null && existingTag.name == "string") {
                existingTag.value.text = value
                true
            } else if (existingTag == null) {
                addStringTag(xmlFile, key, value)
            } else {
                false
            }
        }

    fun addValue(document: ComposeResourceDocument, key: String, value: String): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            if (xmlFile.rootTag == null || findResourceTag(xmlFile, key) != null) return@runWriteCommand false
            addStringTag(xmlFile, key, value)
        }

    fun addStringResource(
        resourceSet: ComposeResourceSet,
        key: String,
        defaultValue: String,
        localizedValues: Map<ComposeResourceQualifier, String>,
        translatable: Boolean = true,
    ): Boolean = addResource(
        resourceSet,
        ComposeResourceDraft(
            key = key,
            defaultValue = defaultValue,
            localizedValues = localizedValues,
            translatable = translatable,
        ),
    )

    fun addResource(resourceSet: ComposeResourceSet, draft: ComposeResourceDraft): Boolean = runWriteCommand {
        val defaultDocument = resourceSet.defaultDocument ?: return@runWriteCommand false
        val defaultFile = findXmlFile(defaultDocument) ?: return@runWriteCommand false
        if (draft.key.isBlank() || findResourceTag(defaultFile, draft.key) != null) return@runWriteCommand false

        val localeFiles = resourceSet.localizedDocuments.mapNotNull { document ->
            findXmlFile(document)?.let { document.descriptor.qualifier to it }
        }
        if (localeFiles.size != resourceSet.localizedDocuments.size) return@runWriteCommand false

        if (!addDraftTag(defaultFile, draft)) return@runWriteCommand false
        localeFiles.forEach { (qualifier, file) ->
            val existingTag = findResourceTag(file, draft.key)
            val hasValues = when (draft.type) {
                ComposeResourceType.STRING -> draft.localizedValues[qualifier]?.isNotBlank() == true || existingTag != null
                else -> draft.localizedItems[qualifier]?.let { items ->
                    existingTag != null || items.any { it.value.isNotBlank() }
                } == true
            }
            if (!hasValues) return@forEach
            val localizedDraft = draft.copy(
                defaultValue = draft.localizedValues[qualifier].orEmpty(),
                defaultItems = draft.localizedItems[qualifier].orEmpty(),
                localizedValues = emptyMap(),
                localizedItems = emptyMap(),
            )
            if (existingTag == null) addDraftTag(file, localizedDraft)
            else updateOrReplaceTag(file, existingTag, localizedDraft)
        }
        true
    }

    fun renameKey(resourceSet: ComposeResourceSet, oldKey: String, newKey: String): Boolean =
        runWriteCommand {
            if (newKey.isBlank()) return@runWriteCommand false
            if (oldKey == newKey) return@runWriteCommand true

            val files = resourceSet.documents.mapNotNull(::findXmlFile)
            if (files.size != resourceSet.documents.size) return@runWriteCommand false
            if (files.any { file -> findResourceTag(file, newKey) != null }) return@runWriteCommand false

            files.forEach { file -> findResourceTag(file, oldKey)?.setAttribute("name", newKey) }
            true
        }

    fun removeKey(resourceSet: ComposeResourceSet, key: String): Int =
        runWriteCommand {
            resourceSet.documents.sumOf { document ->
                val tag = findXmlFile(document)?.let { findResourceTag(it, key) } ?: return@sumOf 0
                tag.delete()
                1
            }
        }

    fun updateStringResource(
        resourceSet: ComposeResourceSet,
        oldKey: String,
        newKey: String,
        defaultValue: String,
        localizedValues: Map<ComposeResourceQualifier, String>,
        translatable: Boolean = true,
    ): Boolean = updateResource(
        resourceSet,
        oldKey,
        ComposeResourceDraft(
            key = newKey,
            defaultValue = defaultValue,
            localizedValues = localizedValues,
            translatable = translatable,
        ),
    )

    fun updateResource(
        resourceSet: ComposeResourceSet,
        oldKey: String,
        draft: ComposeResourceDraft,
    ): Boolean = runWriteCommand {
        if (draft.key.isBlank()) return@runWriteCommand false

        val files = resourceSet.documents.mapNotNull(::findXmlFile)
        if (files.size != resourceSet.documents.size) return@runWriteCommand false
        if (oldKey != draft.key && files.any { findResourceTag(it, draft.key) != null }) {
            return@runWriteCommand false
        }

        files.forEach { file ->
            findResourceTag(file, oldKey)?.also { tag ->
                if (oldKey != draft.key) tag.setAttribute("name", draft.key)
            }
        }
        val defaultFile = findXmlFile(resourceSet.defaultDocument ?: return@runWriteCommand false)
            ?: return@runWriteCommand false
        val defaultTag = findResourceTag(defaultFile, draft.key)
        if (!updateOrReplaceTag(defaultFile, defaultTag, draft)) return@runWriteCommand false

        resourceSet.localizedDocuments.forEach { document ->
            val file = findXmlFile(document) ?: return@forEach
            val tag = findResourceTag(file, draft.key)
            val hasValues = when (draft.type) {
                ComposeResourceType.STRING -> draft.localizedValues[document.descriptor.qualifier]?.isNotBlank() == true || tag != null
                else -> draft.localizedItems[document.descriptor.qualifier]?.let { items ->
                    tag != null || items.any { it.value.isNotBlank() }
                } == true
            }
            if (hasValues) {
                updateOrReplaceTag(
                    file,
                    tag,
                    draft.copy(
                        defaultValue = draft.localizedValues[document.descriptor.qualifier].orEmpty(),
                        defaultItems = draft.localizedItems[document.descriptor.qualifier].orEmpty(),
                        localizedValues = emptyMap(),
                        localizedItems = emptyMap(),
                    ),
                )
            }
        }
        true
    }

    fun updateItemValue(
        document: ComposeResourceDocument,
        key: String,
        itemName: String,
        value: String,
    ): Boolean = runWriteCommand {
        val file = findXmlFile(document) ?: return@runWriteCommand false
        val tag = findResourceTag(file, key)?.takeIf { it.name == "string-array" } ?: return@runWriteCommand false
        val item = tag.findSubTags("item").getOrNull(itemName.toIntOrNull() ?: return@runWriteCommand false)
            ?: return@runWriteCommand false
        item.value.text = value
        true
    }

    fun setTranslatable(document: ComposeResourceDocument, key: String, translatable: Boolean): Boolean =
        runWriteCommand {
            val xmlFile = findXmlFile(document) ?: return@runWriteCommand false
            val tag = findResourceTag(xmlFile, key) ?: return@runWriteCommand false
            tag.setAttribute("translatable", if (translatable) null else "false")
            true
        }

    private fun findXmlFile(document: ComposeResourceDocument): XmlFile? =
        PsiManager.getInstance(project).findFile(document.descriptor.file) as? XmlFile

    private fun findResourceTag(file: XmlFile, key: String): XmlTag? =
        file.rootTag?.subTags?.firstOrNull { tag ->
            tag.getAttributeValue("name") == key && tag.name in SUPPORTED_TAGS
        }

    private fun addDraftTag(file: XmlFile, draft: ComposeResourceDraft): Boolean {
        return when (draft.type) {
            ComposeResourceType.STRING -> addStringTag(file, draft.key, draft.defaultValue, draft.translatable)
            ComposeResourceType.STRING_ARRAY -> addArrayTag(file, draft.key, draft.defaultItems, draft.translatable)
            ComposeResourceType.PLURALS -> false
        }
    }

    private fun updateOrReplaceTag(file: XmlFile, tag: XmlTag?, draft: ComposeResourceDraft): Boolean {
        if (tag == null) return addDraftTag(file, draft)
        val expectedName = draft.type.xmlName
        if (tag.name != expectedName) {
            tag.delete()
            return addDraftTag(file, draft)
        }
        tag.setAttribute("translatable", if (draft.translatable) null else "false")
        when (draft.type) {
            ComposeResourceType.STRING -> tag.value.text = draft.defaultValue
            ComposeResourceType.STRING_ARRAY -> updateArrayItems(tag, draft.defaultItems)
            ComposeResourceType.PLURALS -> return false
        }
        return true
    }

    private fun addStringTag(file: XmlFile, key: String, value: String, translatable: Boolean = true): Boolean {
        val rootTag = file.rootTag ?: return false
        val newTag = XmlElementFactory.getInstance(project).createTagFromText("<string name=\"resource\" />")
        newTag.setAttribute("name", key)
        newTag.setAttribute("translatable", if (translatable) null else "false")
        newTag.value.text = value
        rootTag.addSubTag(newTag, false)
        return true
    }

    private fun addArrayTag(
        file: XmlFile,
        key: String,
        items: List<ComposeResourceItem>,
        translatable: Boolean = true,
    ): Boolean {
        val rootTag = file.rootTag ?: return false
        val arrayTag = XmlElementFactory.getInstance(project).createTagFromText("<string-array name=\"resource\" />")
        arrayTag.setAttribute("name", key)
        arrayTag.setAttribute("translatable", if (translatable) null else "false")
        items.forEach { item ->
            val itemTag = XmlElementFactory.getInstance(project).createTagFromText("<item />")
            itemTag.value.text = item.value
            arrayTag.addSubTag(itemTag, false)
        }
        rootTag.addSubTag(arrayTag, false)
        return true
    }

    private fun updateArrayItems(tag: XmlTag, items: List<ComposeResourceItem>) {
        val existingItems = tag.findSubTags("item")
        existingItems.forEachIndexed { index, itemTag ->
            val item = items.getOrNull(index)
            if (item == null) itemTag.delete() else itemTag.value.text = item.value
        }
        if (items.size > existingItems.size) {
            items.drop(existingItems.size).forEach { item ->
                val itemTag = XmlElementFactory.getInstance(project).createTagFromText("<item />")
                itemTag.value.text = item.value
                tag.addSubTag(itemTag, false)
            }
        }
    }

    private fun <T> runWriteCommand(action: () -> T): T {
        var result: T? = null
        WriteCommandAction.runWriteCommandAction(project) {
            result = action()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private val ComposeResourceType.xmlName: String
        get() = when (this) {
            ComposeResourceType.STRING -> "string"
            ComposeResourceType.STRING_ARRAY -> "string-array"
            ComposeResourceType.PLURALS -> "plurals"
        }

    private companion object {
        val SUPPORTED_TAGS = setOf("string", "string-array", "plurals")
    }
}
