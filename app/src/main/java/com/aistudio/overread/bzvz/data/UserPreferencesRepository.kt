package com.aistudio.overread.bzvz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val TARGET_LANGUAGE = stringPreferencesKey("target_language")
    private val READING_MODE = booleanPreferencesKey("reading_mode") // false = Normal, true = Reading
    private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    private val FLOATING_BUTTON_SIZE = stringPreferencesKey("floating_button_size")
    private val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    val targetLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TARGET_LANGUAGE] ?: "en" // default to English
    }

    val readingModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[READING_MODE] ?: false // default Normal mode
    }

    val overlayOpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_OPACITY] ?: 0.8f // default 80%
    }

    val floatingButtonSizeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_BUTTON_SIZE] ?: "Medium" // Small, Medium, Large
    }

    val tutorialSeenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TUTORIAL_SEEN] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setTargetLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_LANGUAGE] = languageCode
        }
    }

    suspend fun setReadingMode(isReadingMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[READING_MODE] = isReadingMode
        }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_OPACITY] = opacity
        }
    }

    suspend fun setFloatingButtonSize(size: String) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_BUTTON_SIZE] = size
        }
    }

    suspend fun setTutorialSeen(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TUTORIAL_SEEN] = seen
        }
    }
}
