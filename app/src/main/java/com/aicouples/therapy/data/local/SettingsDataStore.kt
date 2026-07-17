package com.aicouples.therapy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val openAiKey = stringPreferencesKey("openai_api_key")
    private val aiModelKey = stringPreferencesKey("ai_model")

    val openAiApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[openAiKey]
    }

    val preferredModel: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[aiModelKey]
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { it[openAiKey] = key.trim() }
    }

    suspend fun clearOpenAiApiKey() {
        context.dataStore.edit { it.remove(openAiKey) }
    }

    suspend fun setPreferredModel(model: String) {
        context.dataStore.edit { it[aiModelKey] = model }
    }
}
