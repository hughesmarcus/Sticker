package com.sticker.todoar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoStickerDao {
    @Query(
        """
        SELECT * FROM todo_stickers
        ORDER BY
            done ASC,
            CASE WHEN dueAtMillis IS NULL THEN 1 ELSE 0 END ASC,
            dueAtMillis ASC,
            createdAtMillis DESC
        """
    )
    fun observeAll(): Flow<List<TodoStickerEntity>>

    @Query("SELECT * FROM todo_stickers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TodoStickerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sticker: TodoStickerEntity): Long

    @Query("UPDATE todo_stickers SET done = :done, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateDone(id: Long, done: Boolean, updatedAtMillis: Long)

    @Query("UPDATE todo_stickers SET text = :text, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAtMillis: Long)

    @Query(
        """
        UPDATE todo_stickers
        SET timerDurationMillis = NULL,
            dueAtMillis = :dueAtMillis,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateAlarm(id: Long, dueAtMillis: Long?, updatedAtMillis: Long)

    @Query(
        """
        UPDATE todo_stickers
        SET anchorProvider = :anchorProvider,
            anchorId = :anchorId,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateAnchor(
        id: Long,
        anchorProvider: String,
        anchorId: String,
        updatedAtMillis: Long
    )

    @Query("UPDATE todo_stickers SET sizeScale = :sizeScale, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateSizeScale(id: Long, sizeScale: Float, updatedAtMillis: Long)

    @Query(
        """
        UPDATE todo_stickers
        SET activitySpaceTranslationX = :translationX,
            activitySpaceTranslationY = :translationY,
            activitySpaceTranslationZ = :translationZ,
            activitySpaceRotationX = :rotationX,
            activitySpaceRotationY = :rotationY,
            activitySpaceRotationZ = :rotationZ,
            activitySpaceRotationW = :rotationW,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateActivitySpacePose(
        id: Long,
        translationX: Float,
        translationY: Float,
        translationZ: Float,
        rotationX: Float,
        rotationY: Float,
        rotationZ: Float,
        rotationW: Float,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM todo_stickers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
