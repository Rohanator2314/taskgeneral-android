package dev.rohans.taskwarrior.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

object EncryptedPreferencesHelper {
    private const val PREFS_FILE = "taskwarrior_prefs"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    
    fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            // On reinstall, Keystore keys survive but prefs files are wiped,
            // causing MAC/signature verification failure. Clear and retry.
            clearCorruptedPrefs(context)
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearCorruptedPrefs(context: Context) {
        val prefsFile = File(context.filesDir.parent, "shared_prefs/${PREFS_FILE}.xml")
        prefsFile.delete()

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        } catch (_: Exception) { }
    }
}
