package app.gamenative.ui.screen.wow

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.gamenative.Crypto
import app.gamenative.wow.LoginVault
import app.gamenative.wow.product.WowProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

object BattleNetSignIn {
    data class Login(val email: String, val password: String)

    fun load(context: Context): Login? {
        migrateLegacy(context)
        return try {
            LoginVault(context).load()?.let { (email, password) -> Login(email, password) }
        } catch (e: Exception) {
            Timber.w(e, "Saved Battle.net login unreadable, clearing it")
            forget(context)
            null
        }
    }

    fun save(context: Context, login: Login) {
        LoginVault(context).save(login.email, login.password)
        prefs(context).edit().clear().commit()
    }

    fun forget(context: Context) {
        LoginVault(context).clear()
        prefs(context).edit().clear().commit()
    }

    /** Moves a login saved by the original app (plain-text email, AES-CBC password) into the vault. */
    private fun migrateLegacy(context: Context) {
        val prefs = prefs(context)
        val email = prefs.getString(KEY_EMAIL, null) ?: return
        val sealed = prefs.getString(KEY_PASSWORD, null)
        runCatching { sealed?.let { String(Crypto.decrypt(Base64.decode(it, Base64.NO_WRAP))) } }
            .getOrNull()
            ?.let { LoginVault(context).save(email, it) }
        prefs.edit().clear().commit()
    }

    fun writeLoginFile(gameRoot: File, product: WowProduct, login: Login) {
        try {
            val file = File(File(gameRoot, product.flavorDir), "login.txt")
            file.parentFile?.mkdirs()
            // Keeps the media scanner out of this folder, so only the game reads login.txt.
            File(file.parentFile, ".nomedia").takeUnless { it.exists() }?.createNewFile()
            file.delete()
            file.createNewFile()
            // Owner-only where the filesystem honours it (shared storage usually doesn't).
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.writeText("${login.email}\n${login.password}\n")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write login.txt")
        }
    }

    /** Wipes login.txt from every product folder, since a shared install can hold more than one. */
    fun removeLoginFile(gameRoot: File) {
        for (product in WowProduct.entries) {
            try {
                val file = File(File(gameRoot, product.flavorDir), "login.txt")
                if (file.exists()) {
                    file.writeBytes(ByteArray(file.length().coerceAtMost(4096).toInt()))
                    file.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove login.txt")
            }
        }
    }

    /**
     * Wipes login.txt as soon as it has done its job. The game reads it once at start-up, so the file
     * is wiped [AFTER_READ_MS] after its contents are first read. The original triggers stay as a backstop: a
     * large Hotfix.log (the client is past login) or [MAX_LIFETIME_MS] at most.
     */
    fun startLoginWatcher(gameRoot: File, product: WowProduct) {
        val dir = File(gameRoot, product.flavorDir)
        val readObserver = object : android.os.FileObserver(dir, ACCESS) {
            override fun onEvent(event: Int, path: String?) {
                if (path != "login.txt") return
                stopWatching()
                CoroutineScope(Dispatchers.IO).launch {
                    delay(AFTER_READ_MS)
                    removeLoginFile(gameRoot)
                }
            }
        }
        runCatching { readObserver.startWatching() }.onFailure { Timber.w(it, "Can't watch login.txt; using timers only") }
        CoroutineScope(Dispatchers.IO).launch {
            val hotfixLog = File(File(gameRoot, product.flavorDir), "Logs/Hotfix.log")
            hotfixLog.delete()
            val deadline = System.currentTimeMillis() + MAX_LIFETIME_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(1000L)
                if (hotfixLog.exists() && hotfixLog.length() > 50_000L) {
                    removeLoginFile(gameRoot)
                    return@launch
                }
            }
            removeLoginFile(gameRoot)
            readObserver.stopWatching()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "battle_net_login"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val AFTER_READ_MS = 20_000L
    private const val MAX_LIFETIME_MS = 180_000L
}

@Composable
fun BattleNetCredentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (BattleNetSignIn.Login) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(DIALOG_SETTLE_MS)
        emailFocus.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // No screenshots, recordings or recent-apps previews of the login form.
        properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        title = { Text("Battle.net login") },
        text = {
            Column {
                Text("Saved encrypted on this device. WoW will automatically log in on launch.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(emailFocus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = email.isNotBlank() && password.isNotEmpty(),
                onClick = { onConfirm(BattleNetSignIn.Login(email.trim(), password)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val DIALOG_SETTLE_MS = 300L
