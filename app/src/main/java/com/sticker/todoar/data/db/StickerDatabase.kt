package com.sticker.todoar.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TodoStickerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun todoStickerDao(): TodoStickerDao
}
