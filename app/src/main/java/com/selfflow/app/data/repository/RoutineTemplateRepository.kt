package com.selfflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.selfflow.app.data.preferences.PreferencesKeys
import com.selfflow.app.domain.model.RoutineTemplate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoutineTemplateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {

    val templates: Flow<List<RoutineTemplate>> = dataStore.data.map { preferences ->
        val json = preferences[PreferencesKeys.ROUTINE_TEMPLATES] ?: "[]"
        runCatching {
            gson.fromJson<List<RoutineTemplate>>(json, object : TypeToken<List<RoutineTemplate>>() {}.type)
                ?.filter { it.id.isNotBlank() }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun saveTemplate(template: RoutineTemplate) {
        dataStore.edit { preferences ->
            val current = loadList(preferences[PreferencesKeys.ROUTINE_TEMPLATES])
            val updated = current.filter { it.id != template.id } + template
            preferences[PreferencesKeys.ROUTINE_TEMPLATES] = gson.toJson(updated)
        }
    }

    suspend fun deleteTemplate(templateId: String) {
        dataStore.edit { preferences ->
            val current = loadList(preferences[PreferencesKeys.ROUTINE_TEMPLATES])
            val updated = current.filter { it.id != templateId }
            preferences[PreferencesKeys.ROUTINE_TEMPLATES] = gson.toJson(updated)
        }
    }

    private fun loadList(json: String?): List<RoutineTemplate> {
        return runCatching {
            gson.fromJson<List<RoutineTemplate>>(json ?: "[]", object : TypeToken<List<RoutineTemplate>>() {}.type)
                ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
