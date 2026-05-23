package com.sticker.todoar.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TodoStickerEntity::class],
    version = 5,
    exportSchema = false
)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun todoStickerDao(): TodoStickerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN timerDurationMillis INTEGER")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN dueAtMillis INTEGER")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN placedAtMillis INTEGER")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN anchorProvider TEXT")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN anchorId TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN sizeScale REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceTranslationX REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceTranslationY REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceTranslationZ REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceRotationX REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceRotationY REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceRotationZ REAL")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN activitySpaceRotationW REAL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN colorKey TEXT NOT NULL DEFAULT 'yellow'")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE todo_stickers ADD COLUMN lastAlarmTriggeredAtMillis INTEGER")
            }
        }
    }
}
