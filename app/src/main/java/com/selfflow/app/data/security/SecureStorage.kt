package com.selfflow.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences? = try {
        createEncryptedPreferences()
    } catch (e: Throwable) {
        null
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        safeEdit { putString(KEY_PIN_HASH, hashPin(pin)) }
    }

    suspend fun clearPin() = withContext(Dispatchers.IO) {
        safeEdit { remove(KEY_PIN_HASH) }
    }

    suspend fun isPinSet(): Boolean = withContext(Dispatchers.IO) {
        prefs?.getString(KEY_PIN_HASH, null).isNullOrBlank().not()
    }

    fun isPinSetFlow(): Flow<Boolean> = stringPresenceFlow(KEY_PIN_HASH)

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val stored = prefs?.getString(KEY_PIN_HASH, null)
        stored != null && stored == hashPin(pin)
    }

    suspend fun setAppLockEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        safeEdit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) }
    }

    fun isAppLockEnabled(): Flow<Boolean> = booleanPreferenceFlow(KEY_APP_LOCK_ENABLED, false)

    suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        safeEdit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    fun isBiometricEnabled(): Flow<Boolean> = booleanPreferenceFlow(KEY_BIOMETRIC_ENABLED, false)

    fun isBiometricAvailable(): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun safeEdit(action: SharedPreferences.Editor.() -> Unit) {
        try {
            prefs?.edit()?.apply(action)?.commit()
        } catch (_: Throwable) {
            // Ignore failures caused by corrupted secure storage.
        }
    }

    private fun booleanPreferenceFlow(key: String, default: Boolean): Flow<Boolean> {
        val sharedPreferences = prefs ?: return flowOf(default)
        return callbackFlow {
            val listener = OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(sharedPreferences.getBoolean(key, default))
                }
            }
            trySend(sharedPreferences.getBoolean(key, default))
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    private fun stringPresenceFlow(key: String): Flow<Boolean> {
        val sharedPreferences = prefs ?: return flowOf(false)
        return callbackFlow {
            val listener = OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(sharedPreferences.getString(key, null).isNullOrBlank().not())
                }
            }
            trySend(sharedPreferences.getString(key, null).isNullOrBlank().not())
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        private const val PREFS_NAME = "secure_storage"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
