package app.tinynovelwriter

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A text for the user that non-UI code can produce without a Context: a string resource
 * (all wording lives in res/values/strings.xml), or raw text such as an exception message.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun of(e: Throwable): UiText = Raw(e.message ?: e.toString())
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Raw -> text
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    is UiText.Raw -> text
}
