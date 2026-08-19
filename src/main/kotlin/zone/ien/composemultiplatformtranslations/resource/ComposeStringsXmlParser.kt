package zone.ien.composemultiplatformtranslations.resource

import com.intellij.psi.xml.XmlFile

/** Parses direct string children from a strings.xml PSI file. */
class ComposeStringsXmlParser {

    fun parse(file: XmlFile): List<ComposeStringEntry> =
        file.rootTag
            ?.findSubTags("string")
            ?.mapNotNull { tag ->
                tag.getAttributeValue("name")?.let { key ->
                    val translatable = tag.getAttributeValue("translatable") != "false"
                    ComposeStringEntry(key = key, value = tag.value.text, translatable = translatable)
                }
            }
            .orEmpty()
}
