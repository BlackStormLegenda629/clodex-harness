package app.clodex.harness.clodex.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance")

/** Persists the appearance configuration; every screen reads it through [AppearanceViewModel]. */
class AppearanceRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val accent = longPreferencesKey("accent")
        val glass = booleanPreferencesKey("liquidGlass")
        val design = stringPreferencesKey("design")
        val font = stringPreferencesKey("font")
        val radius = intPreferencesKey("radius")
        val themeMode = stringPreferencesKey("themeMode")
    }

    val settings: Flow<AppearanceSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = AppearanceSettings()
            AppearanceSettings(
                accent = prefs[Keys.accent]?.let { Color(it.toULong()) } ?: defaults.accent,
                liquidGlass = prefs[Keys.glass] ?: defaults.liquidGlass,
                design = prefs[Keys.design]?.let { saved -> DesignStyle.entries.firstOrNull { it.name == saved } } ?: defaults.design,
                font = prefs[Keys.font]?.let { saved -> AppFont.entries.firstOrNull { it.name == saved } } ?: defaults.font,
                radius = prefs[Keys.radius]?.let { Dp(it.toFloat()) } ?: defaults.radius,
                themeMode = prefs[Keys.themeMode]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: defaults.themeMode,
            )
        }

    suspend fun setAccent(color: Color) = dataStore.edit { it[Keys.accent] = color.value.toLong() }
    suspend fun setLiquidGlass(value: Boolean) = dataStore.edit { it[Keys.glass] = value }
    suspend fun setDesign(value: DesignStyle) = dataStore.edit { it[Keys.design] = value.name }
    suspend fun setFont(value: AppFont) = dataStore.edit { it[Keys.font] = value.name }
    suspend fun setRadius(value: Dp) = dataStore.edit { it[Keys.radius] = value.value.toInt() }
    suspend fun setThemeMode(value: ThemeMode) = dataStore.edit { it[Keys.themeMode] = value.name }

    companion object {
        fun of(context: Context): AppearanceRepository = AppearanceRepository(context.appearanceDataStore)
    }
}

class AppearanceViewModel(private val repository: AppearanceRepository) : ViewModel() {

    val state: StateFlow<AppearanceSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    fun setAccent(color: Color) {
        viewModelScope.launch { repository.setAccent(color) }
    }

    fun setLiquidGlass(value: Boolean) {
        viewModelScope.launch { repository.setLiquidGlass(value) }
    }

    fun setDesign(value: DesignStyle) {
        viewModelScope.launch { repository.setDesign(value) }
    }

    fun setFont(value: AppFont) {
        viewModelScope.launch { repository.setFont(value) }
    }

    fun setRadius(value: Dp) {
        viewModelScope.launch { repository.setRadius(value) }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(value) }
    }

    companion object {
        fun factory(repository: AppearanceRepository) = viewModelFactory {
            initializer { AppearanceViewModel(repository) }
        }
    }
}
