package zone.ien.cmp_translation_plugin.resource

import com.intellij.psi.xml.XmlFile

/** Parses direct string children from a strings.xml PSI file. */
class ComposeStringsXmlParser {

    fun parse(file: XmlFile): List<ComposeStringEntry> =
        file.rootTag
            ?.subTags
            ?.mapNotNull { tag ->
                val key = tag.getAttributeValue("name") ?: return@mapNotNull null
                val translatable = tag.getAttributeValue("translatable") != "false"
                when (tag.name) {
                    "string" -> ComposeStringEntry(
                        key = key,
                        value = tag.value.text,
                        translatable = translatable,
                    )
                    "string-array" -> {
                        val items = tag.findSubTags("item").mapIndexed { index, item ->
                            ComposeResourceItem(name = index.toString(), value = item.value.text)
                        }
                        ComposeStringEntry(
                            key = key,
                            value = "",
                            translatable = translatable,
                            placeholders = items.flatMap(ComposeResourceItem::placeholders),
                            type = ComposeResourceType.STRING_ARRAY,
                            items = items,
                        )
                    }
                    "plurals" -> {
                        val items = tag.findSubTags("item")
                            .mapNotNull { item ->
                                val quantity = item.getAttributeValue("quantity") ?: return@mapNotNull null
                                item.value.text.takeIf(String::isNotBlank)?.let { value ->
                                    ComposeResourceItem(name = quantity, value = value)
                                }
                            }
                        ComposeStringEntry(
                            key = key,
                            value = "",
                            translatable = translatable,
                            placeholders = items.flatMap(ComposeResourceItem::placeholders),
                            type = ComposeResourceType.PLURALS,
                            items = ComposePluralQuantities.sort(items),
                        )
                    }
                    else -> null
                }
            }
            .orEmpty()
}
