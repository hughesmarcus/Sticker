package com.sticker.todoar.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoSticker

@Entity(tableName = "todo_stickers")
data class TodoStickerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    val timerDurationMillis: Long? = null,
    val dueAtMillis: Long? = null,
    val placedAtMillis: Long? = null,
    val anchorProvider: String? = null,
    val anchorId: String? = null,
    val sizeScale: Float = 1f,
    val activitySpaceTranslationX: Float? = null,
    val activitySpaceTranslationY: Float? = null,
    val activitySpaceTranslationZ: Float? = null,
    val activitySpaceRotationX: Float? = null,
    val activitySpaceRotationY: Float? = null,
    val activitySpaceRotationZ: Float? = null,
    val activitySpaceRotationW: Float? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): TodoSticker =
        TodoSticker(
            id = id,
            text = text,
            done = done,
            timerDurationMillis = timerDurationMillis,
            dueAtMillis = dueAtMillis,
            placedAtMillis = placedAtMillis,
            anchorProvider = anchorProvider,
            anchorId = anchorId,
            sizeScale = sizeScale,
            activitySpacePose = toActivitySpacePose(),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )

    private fun toActivitySpacePose(): StickerSpatialPose? {
        val translationX = activitySpaceTranslationX ?: return null
        val translationY = activitySpaceTranslationY ?: return null
        val translationZ = activitySpaceTranslationZ ?: return null
        val rotationX = activitySpaceRotationX ?: return null
        val rotationY = activitySpaceRotationY ?: return null
        val rotationZ = activitySpaceRotationZ ?: return null
        val rotationW = activitySpaceRotationW ?: return null
        return StickerSpatialPose(
            translationX = translationX,
            translationY = translationY,
            translationZ = translationZ,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            rotationW = rotationW
        ).takeIf { it.hasFiniteValues() }
    }
}
