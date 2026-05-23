package com.sticker.todoar.ui.model

import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

data class RoomPlacementRequest(
    val text: String,
    val dueAtMillis: Long?,
    val color: TodoStickerColor,
    val priority: TodoStickerPriority
)
