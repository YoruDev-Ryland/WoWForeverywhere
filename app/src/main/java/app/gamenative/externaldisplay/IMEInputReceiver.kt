package app.gamenative.externaldisplay

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import app.gamenative.wow.input.ComposingText
import app.gamenative.wow.input.UnicodeKeys
import com.winlator.xserver.Pointer
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Invisible view that receives IME (soft keyboard) input from Android system keyboard
 * and forwards it to XServer as keyboard events for the game.
 * 
 * This is needed when the system keyboard is pinned to the external display
 */
class IMEInputReceiver(
    context: Context,
    private val xServer: XServer,
    private val displayContext: Context = context,
) : FrameLayout(context) {

    val screenWidth: Int get() = xServer.screenInfo.width.toInt()
    val screenHeight: Int get() = xServer.screenInfo.height.toInt()

    suspend fun clickAt(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
        delay(60)
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
        delay(80)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        delay(60)
    }

    fun movePointerTo(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
    }

    companion object {
        private val SHIFTED_SYMBOLS = setOf(
            ')', '!', '@', '#', '$', '%', '^', '&', '*', '(',
            '_', '+', '{', '}', '|', ':', '"', '<', '>', '?', '~',
        )

        /**
         * Text this long, committed at once, is a paste rather than typing (a guide import string, #84). Typing it
         * key by key makes the game handle every character as a separate change, which can take minutes. Voice typing
         * commits a sentence at a time, well under this.
         */
        const val PASTE_LENGTH = 200

        /**
         * Wine checks the X clipboard every 2 seconds while a Windows program hasn't copied anything itself, and only
         * then knows there's new text. Ctrl+V waits that long, with a margin.
         */
        const val PASTE_DELAY_MS = 2_600L
    }

    private var imeSessionActive = false

    // Everything typed goes through one queue so characters reach the game in order, including the
    // non-US ones that may wait for a spare key (see UnicodeKeys).
    private val typing = Executors.newSingleThreadExecutor { r -> Thread(r, "ime-typing").apply { isDaemon = true } }
    private val unicodeKeys = UnicodeKeys()
    private val composing = ComposingText()

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onCheckIsTextEditor(): Boolean {
        val isEditor = imeSessionActive && hasFocus()
        Timber.d("IMEInputReceiver: onCheckIsTextEditor called - returning $isEditor")
        return isEditor
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (!imeSessionActive || !hasFocus()) {
            Timber.d(
                "IMEInputReceiver: onCreateInputConnection ignored (active=$imeSessionActive, hasFocus=${hasFocus()})",
            )
            return BaseInputConnection(this, false)
        }

        Timber.d("IMEInputReceiver: onCreateInputConnection called!")
        // A plain text field, so the keyboard offers its languages and voice typing (#38). No suggestions,
        // so most keyboards commit each key at once; the ones that still show pending text are handled below.
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
                              EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                              EditorInfo.IME_ACTION_NONE
        composing.finish()

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val committed = text?.toString().orEmpty()
                if (committed.length >= PASTE_LENGTH) {
                    Timber.d("IMEInputReceiver: commitText of %d characters, pasting it", committed.length)
                    replaceComposing("")
                    composing.finish()
                    pasteText(committed)
                    return true
                }
                Timber.d("IMEInputReceiver: commitText called with: '$text'")
                replaceComposing(committed)
                composing.finish()
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                replaceComposing(text?.toString().orEmpty())
                return true
            }

            override fun finishComposingText(): Boolean {
                composing.finish()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                Timber.d("IMEInputReceiver: deleteSurroundingText called")
                if (beforeLength > 0) backspace(beforeLength)
                return true
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                composing.finish()
                typeText("\n")
                return true
            }

            // The keyboard's own Paste button, on keyboards that send it as an action rather than as text.
            override fun performContextMenuAction(id: Int): Boolean {
                if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                    composing.finish()
                    pasteFromClipboard()
                    return true
                }
                return super.performContextMenuAction(id)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    composing.finish()
                    backspace(1)
                    Timber.v("IMEInputReceiver: Sent backspace from sendKeyEvent")
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    /**
     * Pastes [text] into the game the way Ctrl+V does on a PC: it becomes the X clipboard, and once Wine has had time
     * to see it, Ctrl+V goes to the game, which then gets the whole text at once.
     */
    fun pasteText(text: String) {
        if (text.isEmpty()) return
        xServer.lock(XServer.Lockable.WINDOW_MANAGER).use {
            xServer.selectionManager.offerClipboardText(text, xServer.windowManager.rootWindow)
        }
        typing.execute {
            Thread.sleep(PASTE_DELAY_MS)
            xServer.injectKeyPress(XKeycode.KEY_CTRL_L, 0)
            xServer.injectKeyPress(XKeycode.KEY_V, 0)
            xServer.injectKeyRelease(XKeycode.KEY_V)
            xServer.injectKeyRelease(XKeycode.KEY_CTRL_L)
        }
    }

    /** Pastes the Android clipboard's text into the game. False when there's no text to paste. */
    fun pasteFromClipboard(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return false
        val text = runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }
            .getOrNull()
        if (text.isNullOrEmpty()) return false
        pasteText(text)
        return true
    }

    private fun replaceComposing(text: String) {
        val (back, add) = composing.replaceWith(text)
        if (back > 0) backspace(back)
        if (add.isNotEmpty()) typeText(add)
    }

    private fun backspace(count: Int) {
        typing.execute {
            repeat(count) {
                xServer.injectKeyPress(XKeycode.KEY_BKSP, 0)
                xServer.injectKeyRelease(XKeycode.KEY_BKSP)
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Timber.d("IMEInputReceiver: onWindowFocusChanged: $hasWindowFocus")
        if (hasWindowFocus && imeSessionActive && !hasFocus()) {
            post { requestFocus() }
        }
    }

    private data class KeyDispatch(
        val keyCode: Int,
        val requiresShift: Boolean,
    )

    fun typeText(text: CharSequence) {
        val codePoints = text.codePoints().toArray()
        typing.execute {
            // a character that can't be sent is skipped; an exception on this thread would close the game
            codePoints.forEach { cp -> runCatching { sendCharacterToGame(cp) }.onFailure { Timber.w(it, "IMEInputReceiver: couldn't type U+%04X", cp) } }
        }
    }

    private fun sendCharacterToGame(codePoint: Int) {
        val keyDispatch = if (codePoint < 0x80) charToKeyDispatch(codePoint.toChar()) else null
        if (keyDispatch == null) {
            sendUnicode(codePoint)
            return
        }
        val metaState = if (keyDispatch.requiresShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        xServer.keyboard.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyDispatch.keyCode, 0, metaState))
        xServer.keyboard.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, keyDispatch.keyCode, 0, metaState))
    }

    /** A character with no US key: a spare keycode remapped to its keysym (#38). */
    private fun sendUnicode(codePoint: Int) {
        if (Character.isISOControl(codePoint)) return
        val (keycode, wait) = unicodeKeys.reserve(UnicodeKeys.keysymFor(codePoint))
        if (wait > 0) Thread.sleep(wait)
        xServer.injectKeyPress(keycode, UnicodeKeys.keysymFor(codePoint))
        xServer.injectKeyRelease(keycode)
        Timber.v("IMEInputReceiver: Sent U+%04X on keycode %d", codePoint, keycode.toInt() and 0xFF)
    }

    private fun charToKeyDispatch(char: Char): KeyDispatch? {
        val isUppercaseLetter = char in 'A'..'Z'
        val normalizedChar = if (isUppercaseLetter) char.lowercaseChar() else char

        val keyCode = when (normalizedChar) {
            'a' -> KeyEvent.KEYCODE_A
            'b' -> KeyEvent.KEYCODE_B
            'c' -> KeyEvent.KEYCODE_C
            'd' -> KeyEvent.KEYCODE_D
            'e' -> KeyEvent.KEYCODE_E
            'f' -> KeyEvent.KEYCODE_F
            'g' -> KeyEvent.KEYCODE_G
            'h' -> KeyEvent.KEYCODE_H
            'i' -> KeyEvent.KEYCODE_I
            'j' -> KeyEvent.KEYCODE_J
            'k' -> KeyEvent.KEYCODE_K
            'l' -> KeyEvent.KEYCODE_L
            'm' -> KeyEvent.KEYCODE_M
            'n' -> KeyEvent.KEYCODE_N
            'o' -> KeyEvent.KEYCODE_O
            'p' -> KeyEvent.KEYCODE_P
            'q' -> KeyEvent.KEYCODE_Q
            'r' -> KeyEvent.KEYCODE_R
            's' -> KeyEvent.KEYCODE_S
            't' -> KeyEvent.KEYCODE_T
            'u' -> KeyEvent.KEYCODE_U
            'v' -> KeyEvent.KEYCODE_V
            'w' -> KeyEvent.KEYCODE_W
            'x' -> KeyEvent.KEYCODE_X
            'y' -> KeyEvent.KEYCODE_Y
            'z' -> KeyEvent.KEYCODE_Z
            '0', ')' -> KeyEvent.KEYCODE_0
            '1', '!' -> KeyEvent.KEYCODE_1
            '2', '@' -> KeyEvent.KEYCODE_2
            '3', '#' -> KeyEvent.KEYCODE_3
            '4', '$' -> KeyEvent.KEYCODE_4
            '5', '%' -> KeyEvent.KEYCODE_5
            '6', '^' -> KeyEvent.KEYCODE_6
            '7', '&' -> KeyEvent.KEYCODE_7
            '8', '*' -> KeyEvent.KEYCODE_8
            '9', '(' -> KeyEvent.KEYCODE_9
            ' ' -> KeyEvent.KEYCODE_SPACE
            '\n' -> KeyEvent.KEYCODE_ENTER
            '\t' -> KeyEvent.KEYCODE_TAB
            '-', '_' -> KeyEvent.KEYCODE_MINUS
            '=', '+' -> KeyEvent.KEYCODE_EQUALS
            '[', '{' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']', '}' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\', '|' -> KeyEvent.KEYCODE_BACKSLASH
            ';', ':' -> KeyEvent.KEYCODE_SEMICOLON
            '\'', '"' -> KeyEvent.KEYCODE_APOSTROPHE
            ',', '<' -> KeyEvent.KEYCODE_COMMA
            '.', '>' -> KeyEvent.KEYCODE_PERIOD
            '/', '?' -> KeyEvent.KEYCODE_SLASH
            '`', '~' -> KeyEvent.KEYCODE_GRAVE
            else -> null
        } ?: return null

        val requiresShift = isUppercaseLetter || char in SHIFTED_SYMBOLS

        return KeyDispatch(keyCode = keyCode, requiresShift = requiresShift)
    }

    fun showKeyboard() {
        post {
            imeSessionActive = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            Timber.d("IMEInputReceiver: Requested to show keyboard")
        }
    }

    fun hideKeyboard() {
        post {
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            imeSessionActive = false
            isFocusable = false
            isFocusableInTouchMode = false
            Timber.d("IMEInputReceiver: Requested to hide keyboard")
        }
    }
}
