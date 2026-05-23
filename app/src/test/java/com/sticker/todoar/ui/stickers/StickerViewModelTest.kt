package com.sticker.todoar.ui.stickers

import com.sticker.todoar.R
import com.sticker.todoar.data.TodoStickerRepository
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateShowsLoadingUntilStickersEmit() = runTest {
        val viewModel = StickerViewModel(FakeTodoStickerRepository())

        assertEquals(true, viewModel.uiState.value.isLoading)
        assertEquals(false, viewModel.uiState.value.isError)
        assertEquals(R.string.status_loading_stickers, viewModel.uiState.value.statusResId())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(R.string.status_ready, viewModel.uiState.value.statusResId())
    }

    @Test
    fun repositoryFailureShowsErrorState() = runTest {
        val viewModel = StickerViewModel(FailingTodoStickerRepository())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(true, viewModel.uiState.value.isError)
        assertEquals(R.string.status_stickers_load_failed, viewModel.uiState.value.statusResId())
    }

    @Test
    fun blankSpawnRequestUsesFallbackNoteText() = runTest {
        val viewModel = StickerViewModel(FakeTodoStickerRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val request = viewModel.onPlaceRequested("   ", "New note")
        runCurrent()

        assertEquals("New note", request?.text)
        assertEquals(R.string.status_placing_in_room, viewModel.uiState.value.statusResId())
    }

    @Test
    fun spawnReturnsClockAlarmPlacementRequestAndShowsPlacingStatus() = runTest {
        val viewModel = StickerViewModel(FakeTodoStickerRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val request = viewModel.onPlaceRequested("  Water plants  ", "New note")
        runCurrent()

        assertEquals("Water plants", request?.text)
        assertNotNull(request?.dueAtMillis)
        assertEquals(R.string.status_placing_in_room, viewModel.uiState.value.statusResId())
    }

    @Test
    fun roomPlacementSuccessPersistsStickerState() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val request = viewModel.onPlaceRequested("Take out trash", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = StickerSpatialPose(
                translationX = 0.1f,
                translationY = 0.2f,
                translationZ = -0.3f,
                rotationX = 0f,
                rotationY = 0f,
                rotationZ = 0f,
                rotationW = 1f
            )
        )
        runCurrent()

        val state = viewModel.uiState.value
        val sticker = state.stickers.single()
        assertEquals("Take out trash", sticker.text)
        assertEquals("jetpack_xr_anchor", sticker.anchorProvider)
        assertEquals("anchor-123", sticker.anchorId)
        assertEquals(-0.3f, sticker.activitySpacePose?.translationZ ?: 0f, 0.001f)
        assertEquals("", state.draftText)
        assertEquals(R.string.status_spawned_note, state.statusResId())
    }

    @Test
    fun editStickerUpdatesSavedText() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Old text", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        viewModel.onStickerTextUpdated(id, "  New text  ")
        runCurrent()

        assertEquals("New text", viewModel.uiState.value.stickers.single().text)
        assertEquals(R.string.status_note_updated, viewModel.uiState.value.statusResId())
    }

    @Test
    fun editStickerUpdatesSavedAlarm() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Old text", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        viewModel.onStickerEdited(
            id,
            "New text",
            "",
            TodoStickerColor.GREEN,
            TodoStickerPriority.HIGH
        )
        runCurrent()

        val sticker = viewModel.uiState.value.stickers.single()
        assertEquals("New text", sticker.text)
        assertEquals(null, sticker.dueAtMillis)
        assertEquals(TodoStickerColor.GREEN, sticker.color)
        assertEquals(TodoStickerPriority.HIGH, sticker.priority)
    }

    @Test
    fun resizeStickerStoresClampedScale() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Resize me", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        viewModel.onStickerSizeChanged(id, 4f)
        runCurrent()

        assertEquals(1.8f, viewModel.uiState.value.stickers.single().sizeScale, 0.001f)
        assertEquals(R.string.status_note_resized, viewModel.uiState.value.statusResId())
    }

    @Test
    fun activitySpacePoseUpdatePersistsFallbackPosition() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Stay here", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_activity_space",
            anchorId = "activity-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        viewModel.onStickerActivitySpacePoseUpdated(
            id,
            StickerSpatialPose(
                translationX = 0.4f,
                translationY = 0.5f,
                translationZ = -1.1f,
                rotationX = 0f,
                rotationY = 0f,
                rotationZ = 0f,
                rotationW = 1f
            )
        )
        runCurrent()

        val pose = viewModel.uiState.value.stickers.single().activitySpacePose
        assertNotNull(pose)
        assertEquals(-1.1f, pose!!.translationZ, 0.001f)
    }

    @Test
    fun draftStyleIsSavedOnRoomPlacement() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onDraftColorChanged(TodoStickerColor.PINK)
        viewModel.onDraftPriorityChanged(TodoStickerPriority.HIGH)
        val request = viewModel.onPlaceRequested("Priority note", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = request.dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val sticker = viewModel.uiState.value.stickers.single()
        assertEquals(TodoStickerColor.PINK, sticker.color)
        assertEquals(TodoStickerPriority.HIGH, sticker.priority)
    }

    @Test
    fun snoozeMovesAlarmIntoTheFuture() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Snooze me", "New note")
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = System.currentTimeMillis() - 1_000L,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        val beforeSnooze = System.currentTimeMillis()
        viewModel.onStickerSnoozed(id, 5)
        runCurrent()

        val sticker = viewModel.uiState.value.stickers.single()
        assertNotNull(sticker.dueAtMillis)
        assertEquals(true, sticker.dueAtMillis!! >= beforeSnooze + 5 * 60_000L)
        assertEquals(R.string.status_alarm_snoozed, viewModel.uiState.value.statusResId())
    }

    @Test
    fun alarmTriggeredMarksCurrentDueTime() = runTest {
        val repository = FakeTodoStickerRepository()
        val viewModel = StickerViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val request = viewModel.onPlaceRequested("Alarm me", "New note")
        val dueAtMillis = System.currentTimeMillis() - 1_000L
        viewModel.onRoomPlacementSucceeded(
            text = request!!.text,
            dueAtMillis = dueAtMillis,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-123",
            color = request.color,
            priority = request.priority,
            activitySpacePose = null
        )
        runCurrent()

        val id = viewModel.uiState.value.stickers.single().id
        viewModel.onAlarmTriggered(id, "Alarm me")
        runCurrent()

        assertEquals(dueAtMillis, viewModel.uiState.value.stickers.single().lastAlarmTriggeredAtMillis)
        assertEquals(R.string.status_alarm_triggered, viewModel.uiState.value.statusResId())
    }

    private fun StickerUiState.statusResId(): Int =
        (status as UiText.Resource).resId
}

private class FailingTodoStickerRepository : TodoStickerRepository {
    override fun observeStickers(): Flow<List<TodoSticker>> = flow {
        throw IllegalStateException("Could not read stickers")
    }

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
        error("Not used")
    }

    override suspend fun toggleDone(id: Long) = error("Not used")

    override suspend fun updateText(id: Long, text: String) = error("Not used")

    override suspend fun updateAlarm(id: Long, dueAtMillis: Long?) = error("Not used")

    override suspend fun updateAnchor(id: Long, anchorProvider: String, anchorId: String) =
        error("Not used")

    override suspend fun updateSizeScale(id: Long, sizeScale: Float) = error("Not used")

    override suspend fun updateStyle(
        id: Long,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    ) = error("Not used")

    override suspend fun markAlarmTriggered(id: Long) = error("Not used")

    override suspend fun updateActivitySpacePose(id: Long, pose: StickerSpatialPose) =
        error("Not used")

    override suspend fun deleteSticker(id: Long) = error("Not used")
}

private class FakeTodoStickerRepository : TodoStickerRepository {
    private val stickers = MutableStateFlow<List<TodoSticker>>(emptyList())
    private var nextId = 1L

    override fun observeStickers() = stickers

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
        val sticker = TodoSticker(
            id = nextId++,
            text = text,
            done = false,
            timerDurationMillis = timerDurationMillis,
            dueAtMillis = dueAtMillis ?: timerDurationMillis?.let { now + it },
            placedAtMillis = placedAtMillis,
            anchorProvider = anchorProvider,
            anchorId = anchorId,
            sizeScale = 1f,
            color = color,
            priority = priority,
            lastAlarmTriggeredAtMillis = null,
            activitySpacePose = activitySpacePose,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        stickers.value += sticker
        return sticker
    }

    override suspend fun toggleDone(id: Long) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(done = !sticker.done)
            } else {
                sticker
            }
        }
    }

    override suspend fun updateText(id: Long, text: String) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(text = text)
            } else {
                sticker
            }
        }
    }

    override suspend fun updateAlarm(id: Long, dueAtMillis: Long?) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(
                    dueAtMillis = dueAtMillis,
                    timerDurationMillis = null,
                    lastAlarmTriggeredAtMillis = null
                )
            } else {
                sticker
            }
        }
    }

    override suspend fun updateStyle(
        id: Long,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    ) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(color = color, priority = priority)
            } else {
                sticker
            }
        }
    }

    override suspend fun markAlarmTriggered(id: Long) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id && sticker.dueAtMillis != null) {
                sticker.copy(lastAlarmTriggeredAtMillis = sticker.dueAtMillis)
            } else {
                sticker
            }
        }
    }

    override suspend fun updateAnchor(
        id: Long,
        anchorProvider: String,
        anchorId: String
    ) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(anchorProvider = anchorProvider, anchorId = anchorId)
            } else {
                sticker
            }
        }
    }

    override suspend fun updateSizeScale(id: Long, sizeScale: Float) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(sizeScale = sizeScale)
            } else {
                sticker
            }
        }
    }

    override suspend fun updateActivitySpacePose(id: Long, pose: StickerSpatialPose) {
        stickers.value = stickers.value.map { sticker ->
            if (sticker.id == id) {
                sticker.copy(activitySpacePose = pose)
            } else {
                sticker
            }
        }
    }

    override suspend fun deleteSticker(id: Long) {
        stickers.value = stickers.value.filterNot { it.id == id }
    }
}
