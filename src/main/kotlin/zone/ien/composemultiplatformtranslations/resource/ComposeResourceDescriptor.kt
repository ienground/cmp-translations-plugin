package zone.ien.composemultiplatformtranslations.resource

import com.intellij.openapi.vfs.VirtualFile

/** Describes a Compose Multiplatform strings.xml file and its source set. */
data class ComposeResourceDescriptor(
    val file: VirtualFile,
    val resourceRoot: VirtualFile,
    val sourceSetName: String,
    val qualifier: ComposeResourceQualifier,
) {

    companion object {
        fun from(file: VirtualFile): ComposeResourceDescriptor? {
            if (file.name != "strings.xml") return null

            val valuesDirectory = file.parent ?: return null
            val qualifier = ComposeResourceQualifier.fromDirectoryName(valuesDirectory.name) ?: return null
            val resourceRoot = valuesDirectory.parent ?: return null
            if (resourceRoot.name != "composeResources") return null

            val sourceSetDirectory = resourceRoot.parent ?: return null
            val sourceDirectory = sourceSetDirectory.parent ?: return null
            if (sourceDirectory.name != "src") return null

            return ComposeResourceDescriptor(
                file = file,
                resourceRoot = resourceRoot,
                sourceSetName = sourceSetDirectory.name,
                qualifier = qualifier,
            )
        }
    }
}
