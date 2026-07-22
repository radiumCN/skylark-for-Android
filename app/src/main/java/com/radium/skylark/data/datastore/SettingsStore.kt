package com.radium.skylark.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radium.skylark.update.UpdateChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用设置持久化（更新通道等）。
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** 更新通道；未设置时返回 null，由上层按当前版本推断默认值。 */
    val updateChannel: Flow<UpdateChannel?> = dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_CHANNEL]?.let { name ->
            runCatching { UpdateChannel.valueOf(name) }.getOrNull()
        }
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        dataStore.edit { it[KEY_UPDATE_CHANNEL] = channel.name }
    }

    private companion object {
        val KEY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    }
}
