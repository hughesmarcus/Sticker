package com.sticker.todoar.data

import com.sticker.todoar.data.db.TodoStickerDao
import com.sticker.todoar.data.db.TodoStickerEntity
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TodoStickerRepository {
    fun observeStickers(): Flow<List<TodoSticker>>

    suspend fun addSticker(
        text: String,
        timerDurationMillis: Long? = null,
        dueAtMillis: Long? = null,
        placedAtMillis: Long? = null,
        anchorProvider: String? = null,
        anchorId: String? = null,
        color: TodoStickerColor = TodoStickerColor.DEFAULT,
        priority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
        activitySpacePose: StickerSpatialPose? = null
    ): TodoSticker

    suspend fun toggleDone(id: Long)

    suspend fun updateText(
        id: Long,
        text: String
    )

    suspend fun updateAlarm(
        id: Long,
        dueAtMillis: Long?
    )

    suspend fun updateAnchor(
        id: Long,
        anchorProvider: String,
        anchorId: String
    )

    suspend fun updateSizeScale(
        id: Long,
        sizeScale: Float
    )

    suspend fun updateStyle(
        id: Long,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    )

    suspend fun markAlarmTriggered(id: Long)

    suspend fun updateActivitySpacePose(
        id: Long,
        pose: StickerSpatialPose
    )

    suspend fun deleteSticker(id: Long)
}

@Singleton
class DefaultTodoStickerRepository @Inject constructor(
    private val dao: TodoStickerDao
) : TodoStickerRepository {
    override fun observeStickers(): Flow<List<TodoSticker>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addSticker(
        text: String,
        timerDurationMillis: Long?,
        dueAtMillis: Long?,
        placedAtMillis: Long?,
        anchorProvider: String?,
        anchorId: String?,
        color: TodoStickerColor,
        priority: TodoStickerPriority,
        activitySpacePose: StickerSpatialPose?
    ): TodoSticker {
        val now = System.currentTimeMillis()
        val entity = TodoStickerEntity(
            text = text,
            timerDurationMillis = timerDurationMillis,
            dueAtMillis = dueAtMillis ?: timerDurationMillis?.let { now + it },
            placedAtMillis = placedAtMillis,
            anchorProvider = anchorProvider,
            anchorId = anchorId,
            colorKey = color.key,
            priority = priority.value,
            activitySpaceTranslationX = activitySpacePose?.translationX,
            activitySpaceTranslationY = activitySpacePose?.translationY,
            activitySpaceTranslationZ = activitySpacePose?.translationZ,
            activitySpaceRotationX = activitySpacePose?.rotationX,
            activitySpaceRotationY = activitySpacePose?.rotationY,
            activitySpaceRotationZ = activitySpacePose?.rotationZ,
            activitySpaceRotationW = activitySpacePose?.rotationW,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun toggleDone(id: Long) {
        val sticker = dao.getById(id) ?: return
        dao.updateDone(id, !sticker.done, System.currentTimeMillis())
    }

    override suspend fun updateText(id: Long, text: String) {
        dao.updateText(id, text, System.currentTimeMillis())
    }

    override suspend fun updateAlarm(id: Long, dueAtMillis: Long?) {
        dao.updateAlarm(id, dueAtMillis, System.currentTimeMillis())
    }

    override suspend fun updateAnchor(
        id: Long,
        anchorProvider: String,
        anchorId: String
    ) {
        dao.updateAnchor(id, anchorProvider, anchorId, System.currentTimeMillis())
    }

    override suspend fun updateSizeScale(id: Long, sizeScale: Float) {
        dao.updateSizeScale(id, sizeScale, System.currentTimeMillis())
    }

    override suspend fun updateStyle(
        id: Long,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    ) {
        dao.updateStyle(id, color.key, priority.value, System.currentTimeMillis())
    }

    override suspend fun markAlarmTriggered(id: Long) {
        val sticker = dao.getById(id) ?: return
        val dueAtMillis = sticker.dueAtMillis ?: return
        dao.updateLastAlarmTriggered(id, dueAtMillis, System.currentTimeMillis())
    }

    override suspend fun updateActivitySpacePose(id: Long, pose: StickerSpatialPose) {
        if (!pose.hasFiniteValues()) return
        dao.updateActivitySpacePose(
            id = id,
            translationX = pose.translationX,
            translationY = pose.translationY,
            translationZ = pose.translationZ,
            rotationX = pose.rotationX,
            rotationY = pose.rotationY,
            rotationZ = pose.rotationZ,
            rotationW = pose.rotationW,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun deleteSticker(id: Long) {
        dao.deleteById(id)
    }
}
