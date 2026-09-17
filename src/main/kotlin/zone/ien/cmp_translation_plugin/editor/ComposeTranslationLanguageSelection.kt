package zone.ien.cmp_translation_plugin.editor

import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier
import zone.ien.cmp_translation_plugin.resource.ComposeResourceSet

/** Resolves the display language and value for a Compose resource set. */
internal object ComposeTranslationLanguageSelection {

    fun options(resourceSet: ComposeResourceSet): List<ComposeResourceQualifier> =
        resourceSet.documents
            .map { it.descriptor.qualifier }
            .distinct()
            .sortedBy { it.rawValue }

    fun value(
        resourceSet: ComposeResourceSet,
        key: String,
        qualifier: ComposeResourceQualifier,
    ): String? = resourceSet.documentFor(qualifier)
        ?.entries
        ?.firstOrNull { it.key == key }
        ?.value
        ?: resourceSet.defaultDocument
            ?.entries
            ?.firstOrNull { it.key == key }
            ?.value
}
