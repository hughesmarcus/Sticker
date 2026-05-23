package com.sticker.todoar.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    companion object {
        fun resource(@StringRes resId: Int, vararg args: Any): UiText =
            Resource(resId = resId, args = args.toList())
    }
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Resource -> stringResource(resId, *args.toTypedArray())
    }
