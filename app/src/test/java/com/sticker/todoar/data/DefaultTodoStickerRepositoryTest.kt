package com.sticker.todoar.data

import com.sticker.todoar.data.db.TodoStickerDao
import com.sticker.todoar.data.db.TodoStickerEntity
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTodoStickerRepositoryTest {
    @Test
    fun addStickerStoresTimerAndAnchorMetadata() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val dueAtMillis = System.currentTimeMillis() + 5 * 60_000L

        val sticker = repository.addSticker(
            text = "Check laundry",
            dueAtMillis = dueAtMillis,
            placedAtMillis = 123L,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-1",
            color = TodoStickerColor.BLUE,
            priority = TodoStickerPriority.HIGH,
            activitySpacePose = StickerSpatialPose(
                translationX = 1f,
                translationY = 2f,
                translationZ = -3f,
                rotationX = 0f,
                rotationY = 0f,
                rotationZ = 0f,
                rotationW = 1f
            )
        )
        val savedSticker = repository.observeStickers().first().single()

        assertEquals(sticker, savedSticker)
        assertEquals("Check laundry", savedSticker.text)
        assertEquals("jetpack_xr_anchor", savedSticker.anchorProvider)
        assertEquals("anchor-1", savedSticker.anchorId)
        assertEquals(TodoStickerColor.BLUE, savedSticker.color)
        assertEquals(TodoStickerPriority.HIGH, savedSticker.priority)
        assertEquals(1f, savedSticker.activitySpacePose?.translationX ?: 0f, 0.001f)
        assertEquals(-3f, savedSticker.activitySpacePose?.translationZ ?: 0f, 0.001f)
        assertNotNull(savedSticker.dueAtMillis)
        assertTrue(savedSticker.dueAtMillis!! >= dueAtMillis)
    }

    @Test
    fun toggleDoneFlipsStoredCompletionState() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Start dishwasher")

        repository.toggleDone(sticker.id)

        assertEquals(true, repository.observeStickers().first().single().done)
    }

    @Test
    fun updateTextChangesStoredStickerText() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Old")

        repository.updateText(sticker.id, "New")

        assertEquals("New", repository.observeStickers().first().single().text)
    }

    @Test
    fun updateAlarmChangesStoredAlarmTime() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Alarm")
        val dueAtMillis = System.currentTimeMillis() + 60_000L

        repository.updateAlarm(sticker.id, dueAtMillis)

        assertEquals(dueAtMillis, repository.observeStickers().first().single().dueAtMillis)
    }

    @Test
    fun updateSizeScaleChangesStoredNoteSize() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Resize me")

        repository.updateSizeScale(sticker.id, 1.45f)

        assertEquals(1.45f, repository.observeStickers().first().single().sizeScale, 0.001f)
    }

    @Test
    fun updateStyleChangesStoredColorAndPriority() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Style me")

        repository.updateStyle(sticker.id, TodoStickerColor.GREEN, TodoStickerPriority.HIGH)

        val savedSticker = repository.observeStickers().first().single()
        assertEquals(TodoStickerColor.GREEN, savedSticker.color)
        assertEquals(TodoStickerPriority.HIGH, savedSticker.priority)
    }

    @Test
    fun markAlarmTriggeredStoresCurrentDueTime() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val dueAtMillis = System.currentTimeMillis() + 60_000L
        val sticker = repository.addSticker(text = "Alarm", dueAtMillis = dueAtMillis)

        repository.markAlarmTriggered(sticker.id)

        assertEquals(dueAtMillis, repository.observeStickers().first().single().lastAlarmTriggeredAtMillis)
    }

    @Test
    fun updateActivitySpacePoseChangesStoredFallbackPose() = runTest {
        val repository: TodoStickerRepository = DefaultTodoStickerRepository(FakeTodoStickerDao())
        val sticker = repository.addSticker(text = "Move me")

        repository.updateActivitySpacePose(
            sticker.id,
            StickerSpatialPose(
                translationX = 0.3f,
                translationY = 1.2f,
                translationZ = -0.8f,
                rotationX = 0f,
                rotationY = 0.7f,
                rotationZ = 0f,
                rotationW = 0.7f
            )
        )

        val pose = repository.observeStickers().first().single().activitySpacePose
        assertNotNull(pose)
        assertEquals(0.3f, pose!!.translationX, 0.001f)
        assertEquals(-0.8f, pose.translationZ, 0.001f)
    }
}

private class FakeTodoStickerDao : TodoStickerDao {
    private val stickers = mutableListOf<TodoStickerEntity>()
    private val stickerFlow = MutableStateFlow<List<TodoStickerEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<TodoStickerEntity>> = stickerFlow

    override suspend fun getById(id: Long): TodoStickerEntity? =
        stickers.firstOrNull { it.id == id }

    override suspend fun insert(sticker: TodoStickerEntity): Long {
        val savedSticker = sticker.copy(id = nextId++)
        stickers += savedSticker
        publish()
        return savedSticker.id
    }

    override suspend fun updateDone(id: Long, done: Boolean, updatedAtMillis: Long) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(done = done, updatedAtMillis = updatedAtMillis)
            publish()
        }
    }

    override suspend fun updateText(id: Long, text: String, updatedAtMillis: Long) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(text = text, updatedAtMillis = updatedAtMillis)
            publish()
        }
    }

    override suspend fun updateAlarm(id: Long, dueAtMillis: Long?, updatedAtMillis: Long) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                timerDurationMillis = null,
                dueAtMillis = dueAtMillis,
                lastAlarmTriggeredAtMillis = null,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun updateStyle(
        id: Long,
        colorKey: String,
        priority: Int,
        updatedAtMillis: Long
    ) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                colorKey = colorKey,
                priority = priority,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun updateLastAlarmTriggered(
        id: Long,
        lastAlarmTriggeredAtMillis: Long,
        updatedAtMillis: Long
    ) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                lastAlarmTriggeredAtMillis = lastAlarmTriggeredAtMillis,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun updateAnchor(
        id: Long,
        anchorProvider: String,
        anchorId: String,
        updatedAtMillis: Long
    ) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                anchorProvider = anchorProvider,
                anchorId = anchorId,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun updateSizeScale(id: Long, sizeScale: Float, updatedAtMillis: Long) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                sizeScale = sizeScale,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun updateActivitySpacePose(
        id: Long,
        translationX: Float,
        translationY: Float,
        translationZ: Float,
        rotationX: Float,
        rotationY: Float,
        rotationZ: Float,
        rotationW: Float,
        updatedAtMillis: Long
    ) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index >= 0) {
            stickers[index] = stickers[index].copy(
                activitySpaceTranslationX = translationX,
                activitySpaceTranslationY = translationY,
                activitySpaceTranslationZ = translationZ,
                activitySpaceRotationX = rotationX,
                activitySpaceRotationY = rotationY,
                activitySpaceRotationZ = rotationZ,
                activitySpaceRotationW = rotationW,
                updatedAtMillis = updatedAtMillis
            )
            publish()
        }
    }

    override suspend fun deleteById(id: Long) {
        stickers.removeAll { it.id == id }
        publish()
    }

    private fun publish() {
        stickerFlow.value = stickers.toList()
    }
}
