package com.tribalfs.milko.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class MilkoSettings(
    val docUri: Uri? = null,
    val thresholdPercent: Int = 80,
    val interval: Int = 3
)

@Singleton
class Preference @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val PREF_STORE_URI = stringPreferencesKey("storageUri")
        val PREF_THRESHOLD_PERCENT = intPreferencesKey("thresholdPercent")
        val PREF_INTERVAL = intPreferencesKey("interval")
    }

    val milkoSettingsFlow: Flow<MilkoSettings> = dataStore.data.map {
        MilkoSettings(
            it[PREF_STORE_URI]?.toUri(),
            it[PREF_THRESHOLD_PERCENT] ?: 80,
            it[PREF_INTERVAL] ?: 3
        )
    }

    suspend fun setDocUri(uri: Uri) {
        dataStore.edit { it[PREF_STORE_URI] = uri.toString() }
    }

    suspend fun setThresholdPercent(percent: Int) {
        dataStore.edit { it[PREF_THRESHOLD_PERCENT] = percent }
    }

    suspend fun setInterval(minutes: Int) {
        dataStore.edit { it[PREF_INTERVAL] = minutes }
    }
}
