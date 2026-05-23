package com.sticker.todoar.di

import com.sticker.todoar.data.DefaultTodoStickerRepository
import com.sticker.todoar.data.TodoStickerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTodoStickerRepository(
        repository: DefaultTodoStickerRepository
    ): TodoStickerRepository
}
