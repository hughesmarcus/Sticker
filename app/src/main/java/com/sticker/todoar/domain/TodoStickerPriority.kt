package com.sticker.todoar.domain

enum class TodoStickerPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    companion object {
        val DEFAULT = NORMAL

        fun fromValue(value: Int): TodoStickerPriority =
            entries.firstOrNull { priority -> priority.value == value } ?: DEFAULT
    }
}
