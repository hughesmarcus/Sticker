package com.sticker.todoar.ui.model

import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

sealed interface StickerUiState {
    val draftText: String
    val selectedAlarmTimeText: String
    val selectedColor: TodoStickerColor
    val selectedPriority: TodoStickerPriority
    val stickers: List<TodoSticker>
    val status: UiText
    val nowMillis: Long

    data class Loading(
        override val draftText: String = "",
        override val selectedAlarmTimeText: String = "",
        override val selectedColor: TodoStickerColor = TodoStickerColor.DEFAULT,
        override val selectedPriority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
        override val stickers: List<TodoSticker> = emptyList(),
        override val status: UiText = UiText.resource(R.string.status_loading_stickers),
        override val nowMillis: Long = System.currentTimeMillis()
    ) : StickerUiState

    data class Success(
        override val draftText: String = "",
        override val selectedAlarmTimeText: String = "",
        override val selectedColor: TodoStickerColor = TodoStickerColor.DEFAULT,
        override val selectedPriority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
        override val stickers: List<TodoSticker> = emptyList(),
        override val status: UiText = UiText.resource(R.string.status_ready),
        override val nowMillis: Long = System.currentTimeMillis()
    ) : StickerUiState

    data class Error(
        val message: UiText,
        override val draftText: String = "",
        override val selectedAlarmTimeText: String = "",
        override val selectedColor: TodoStickerColor = TodoStickerColor.DEFAULT,
        override val selectedPriority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
        override val stickers: List<TodoSticker> = emptyList(),
        override val status: UiText = message,
        override val nowMillis: Long = System.currentTimeMillis()
    ) : StickerUiState
}
