package app.clodex.harness.clodex.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Overlay state of [AppRoot]: the menu drawer and the model picker are state, not routes.
 * Kept outside the composable so the transitions can be unit-tested.
 */
class ShellState(val models: List<String>, initialModel: Int = 2) {
    var drawer by mutableStateOf(false)
        private set
    var picker by mutableStateOf(false)
        private set
    var modelIndex by mutableStateOf(initialModel)
        private set

    val model: String get() = models.getOrElse(modelIndex) { models.first() }

    fun openDrawer() { drawer = true }
    fun closeDrawer() { drawer = false }
    fun openPicker() { picker = true }
    fun closePicker() { picker = false }

    /** Picking a model always closes the sheet; an unknown name keeps the current one. */
    fun selectModel(index: Int) {
        if (index in models.indices) modelIndex = index
        picker = false
    }

    fun selectModel(name: String) = selectModel(models.indexOf(name))
}
