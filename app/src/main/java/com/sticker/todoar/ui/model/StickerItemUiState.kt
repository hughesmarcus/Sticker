package com.sticker.todoar.ui.model

import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

data class StickerItemUiState(
    val sticker: TodoSticker,
    val nowMillis: Long,
    val isEditing: Boolean = false,
    val editingText: String = sticker.text,
    val editingAlarmTimeText: String = "",
    val selectedColor: TodoStickerColor = sticker.color,
    val selectedPriority: TodoStickerPriority = sticker.priority
) {
    val isExpired: Boolean
        get() = sticker.isTimerExpired(nowMillis)
}
