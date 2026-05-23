package com.sticker.todoar.di

import android.content.Context
import androidx.room.Room
import com.sticker.todoar.data.db.StickerDatabase
import com.sticker.todoar.data.db.TodoStickerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideStickerDatabase(
        @ApplicationContext context: Context
    ): StickerDatabase =
        Room.databaseBuilder(
            context,
            StickerDatabase::class.java,
            "sticker.db"
        )
            .addMigrations(StickerDatabase.MIGRATION_1_2)
            .addMigrations(StickerDatabase.MIGRATION_2_3)
            .addMigrations(StickerDatabase.MIGRATION_3_4)
            .addMigrations(StickerDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun provideTodoStickerDao(database: StickerDatabase): TodoStickerDao =
        database.todoStickerDao()
}
