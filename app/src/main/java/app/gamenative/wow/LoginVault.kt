package app.gamenative.wow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Stores the Battle.net login for auto-login. Email and password are sealed together with
 * AES-256-GCM under a non-exportable Android Keystore key (hardware-backed where the device has it).
 * GCM authenticates the data, so a tampered value fails to open instead of decrypting to garbage.
 * Nothing is stored in the clear.
 *
 * The key deliberately doesn't use `setUnlockedDeviceRequired`: on Android 15 such a key can't be
 * used at all on a device without a screen lock (common on handhelds), and fails with
 * UserNotAuthenticatedException.
 *
 * The game itself still needs the password in plain text at launch (see `BattleNetSignIn`), so this
 * protects the saved copy, not that hand-off.
 */
class LoginVault(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Pair<String, String>? {
        val sealed = prefs.getString(KEY_SEALED, null) ?: return null
        val json = JSONObject(String(open(Base64.decode(sealed, Base64.NO_WRAP))))
        return json.getString("email") to json.getString("password")
    }

    fun save(email: String, password: String) {
        val json = JSONObject().put("email", email).put("password", password).toString().toByteArray()
        prefs.edit().clear().putString(KEY_SEALED, Base64.encodeToString(seal(json), Base64.NO_WRAP)).commit()
        json.fill(0)
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "Sealed login is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        // A key from an earlier build was created with setUnlockedDeviceRequired; it can't be fixed in place.
        if (store.containsAlias(OLD_KEY_ALIAS)) store.deleteEntry(OLD_KEY_ALIAS)
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val PREFS = "battle_net_vault"
        const val KEY_SEALED = "sealed"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "foreverywhere_battle_net_v2"
        const val OLD_KEY_ALIAS = "foreverywhere_battle_net"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
