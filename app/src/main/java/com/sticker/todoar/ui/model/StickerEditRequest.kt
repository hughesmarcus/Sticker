package com.sticker.todoar.ui.model

import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

data class StickerEditRequest(
    val id: Long,
    val text: String,
    val alarmTimeText: String,
    val color: TodoStickerColor,
    val priority: TodoStickerPriority
)
