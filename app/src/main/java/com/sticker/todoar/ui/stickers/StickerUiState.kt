package com.sticker.todoar.ui.stickers

import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.UiText

data class StickerUiState(
    val isLoading: Boolean = true,
    val draftText: String = "",
    val selectedAlarmTimeText: String = "",
    val selectedColor: TodoStickerColor = TodoStickerColor.DEFAULT,
    val selectedPriority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
    val stickers: List<TodoSticker> = emptyList(),
    val status: UiText = UiText.resource(R.string.status_loading_stickers),
    val nowMillis: Long = System.currentTimeMillis()
)
