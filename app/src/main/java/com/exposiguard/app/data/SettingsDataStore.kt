package com.exposiguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LOCALE_TAGS = stringPreferencesKey("locale_tags")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val USE_WIDGET = booleanPreferencesKey("use_widget")
    }

    val darkTheme: Flow<Boolean> = dataStore.data.map { it[Keys.DARK_THEME] ?: false }
    val localeTags: Flow<String> = dataStore.data.map { it[Keys.LOCALE_TAGS] ?: "en" }
    val persistentNotification: Flow<Boolean> = dataStore.data.map { it[Keys.PERSISTENT_NOTIFICATION] ?: true }
    val startOnBoot: Flow<Boolean> = dataStore.data.map { it[Keys.START_ON_BOOT] ?: false }
    val useWidget: Flow<Boolean> = dataStore.data.map { it[Keys.USE_WIDGET] ?: true }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setLocaleTags(tags: String) {
        dataStore.edit { it[Keys.LOCALE_TAGS] = tags }
    }

    suspend fun setPersistentNotification(enabled: Boolean) {
        dataStore.edit { it[Keys.PERSISTENT_NOTIFICATION] = enabled }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        dataStore.edit { it[Keys.START_ON_BOOT] = enabled }
    }

    suspend fun setUseWidget(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_WIDGET] = enabled }
    }
}
