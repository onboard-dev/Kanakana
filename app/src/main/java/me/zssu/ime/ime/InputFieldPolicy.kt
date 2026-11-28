package me.zssu.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * The keyboard behaviour appropriate for an editor.
 *
 * Kept independent from the service so the security-sensitive password classification can be
 * unit-tested without constructing an InputMethodService.
 */
data class InputFieldPolicy(
    val plane: Plane,
    val showCandidates: Boolean,
    val incognito: Boolean,
    /** TYPE_NULL editors (notably terminals) accept raw keys, not surrounding-text edits. */
    val rawKeyEvents: Boolean = false,
) {
    enum class Plane { USER_DEFAULT, ASCII, NUMERIC }

    companion object {
        val DEFAULT = InputFieldPolicy(Plane.USER_DEFAULT, showCandidates = true, incognito = false)

        fun from(info: EditorInfo?): InputFieldPolicy {
            if (info == null) return DEFAULT

            val typeClass = info.inputType and InputType.TYPE_MASK_CLASS
            if (typeClass == InputType.TYPE_NULL) {
                // Android defines TYPE_NULL as a non-rich InputConnection. Keep the user's normal
                // keyboard plane, but deliver editor commands as physical-style key events.
                return DEFAULT.copy(rawKeyEvents = true)
            }
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            val isTextPassword = typeClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )
            val isNumberPassword =
                typeClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            if (isTextPassword || isNumberPassword) {
                return InputFieldPolicy(
                    plane = if (isNumberPassword) Plane.NUMERIC else Plane.ASCII,
                    showCandidates = false,
                    incognito = true,
                )
            }

            if (typeClass == InputType.TYPE_CLASS_NUMBER ||
                typeClass == InputType.TYPE_CLASS_PHONE ||
                typeClass == InputType.TYPE_CLASS_DATETIME
            ) {
                return InputFieldPolicy(Plane.NUMERIC, showCandidates = false, incognito = false)
            }

            // URL and e-mail fields are deliberately NOT switched to English: Chrome's address bar is a
            // URL field and is also where people search in Japanese, so the user's own mode applies.
            // Only passwords (above) and number fields change the keyboard by themselves.
            return DEFAULT
        }
    }
}
