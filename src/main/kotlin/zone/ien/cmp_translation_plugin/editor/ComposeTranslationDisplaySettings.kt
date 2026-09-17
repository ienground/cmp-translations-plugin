package zone.ien.cmp_translation_plugin.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import zone.ien.cmp_translation_plugin.resource.ComposeResourceQualifier

/** Holds the language used for resource-value folding in one project. */
internal class ComposeTranslationDisplaySettings private constructor() {

    @Volatile
    var selectedQualifier: ComposeResourceQualifier = ComposeResourceQualifier.DEFAULT
        private set

    fun select(qualifier: ComposeResourceQualifier) {
        selectedQualifier = qualifier
    }

    companion object {
        private val KEY = Key.create<ComposeTranslationDisplaySettings>(
            "zone.ien.cmp_translation_plugin.display-settings",
        )

        fun getInstance(project: Project): ComposeTranslationDisplaySettings =
            project.getUserData(KEY) ?: ComposeTranslationDisplaySettings().also { project.putUserData(KEY, it) }
    }
}
