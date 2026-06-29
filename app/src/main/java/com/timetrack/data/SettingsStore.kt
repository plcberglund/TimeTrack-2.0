package com.timetrack.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val userNameKey = stringPreferencesKey("user_name")

    val userName: Flow<String> = context.dataStore.data.map { it[userNameKey] ?: "" }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name }
    }
}
