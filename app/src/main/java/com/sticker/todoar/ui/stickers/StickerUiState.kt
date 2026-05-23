package com.sticker.todoar.ui.stickers

import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.ui.UiText

data class StickerUiState(
    val draftText: String = "",
    val selectedAlarmTimeText: String = "",
    val stickers: List<TodoSticker> = emptyList(),
    val status: UiText = UiText.resource(R.string.status_ready),
    val nowMillis: Long = System.currentTimeMillis()
)
