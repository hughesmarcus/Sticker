package com.sticker.todoar.domain

enum class TodoStickerColor(val key: String) {
    YELLOW("yellow"),
    BLUE("blue"),
    GREEN("green"),
    PINK("pink");

    companion object {
        val DEFAULT = YELLOW

        fun fromKey(key: String?): TodoStickerColor =
            entries.firstOrNull { color -> color.key == key } ?: DEFAULT
    }
}
