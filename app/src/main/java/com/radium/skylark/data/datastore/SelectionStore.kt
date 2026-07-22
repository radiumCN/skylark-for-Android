package com.radium.skylark.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 当前选中的订阅（用于生成连接配置）。
 */
@Singleton
class SelectionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val selectedProfileId: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_PROFILE_ID]
    }

    suspend fun selectProfile(id: Long) {
        dataStore.edit { it[KEY_SELECTED_PROFILE_ID] = id }
    }

    private companion object {
        val KEY_SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
    }
}
