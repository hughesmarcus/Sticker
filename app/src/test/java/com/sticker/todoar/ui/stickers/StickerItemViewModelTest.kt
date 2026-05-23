package com.sticker.todoar.ui.stickers

import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class StickerItemViewModelTest {
    @Test
    fun startEditingCopiesStickerFieldsIntoDraftState() {
        val viewModel = StickerItemViewModel(
            sticker = sampleSticker(),
            nowMillis = 1_000L,
            alarmTimeText = "09:30"
        )

        viewModel.startEditing("09:30")

        val state = viewModel.uiState.value
        assertEquals(true, state.isEditing)
        assertEquals("Water plants", state.editingText)
        assertEquals("09:30", state.editingAlarmTimeText)
        assertEquals(TodoStickerColor.GREEN, state.selectedColor)
        assertEquals(TodoStickerPriority.HIGH, state.selectedPriority)
    }

    @Test
    fun stickerUpdatesDoNotOverwriteDraftWhileEditing() {
        val viewModel = StickerItemViewModel(
            sticker = sampleSticker(),
            nowMillis = 1_000L,
            alarmTimeText = "09:30"
        )
        viewModel.startEditing("09:30")
        viewModel.onEditingTextChanged("Draft text")

        viewModel.updateSticker(
            sticker = sampleSticker(text = "Saved text changed"),
            nowMillis = 2_000L,
            alarmTimeText = "10:00"
        )

        val state = viewModel.uiState.value
        assertEquals("Saved text changed", state.sticker.text)
        assertEquals("Draft text", state.editingText)
        assertEquals("09:30", state.editingAlarmTimeText)
        assertEquals(2_000L, state.nowMillis)
    }

    @Test
    fun saveEditingReturnsRequestAndClosesEditing() {
        val viewModel = StickerItemViewModel(
            sticker = sampleSticker(),
            nowMillis = 1_000L,
            alarmTimeText = "09:30"
        )
        viewModel.startEditing("09:30")
        viewModel.onEditingTextChanged("  ")
        viewModel.onEditingAlarmTimeChanged("")
        viewModel.onEditingColorChanged(TodoStickerColor.PINK)
        viewModel.onEditingPriorityChanged(TodoStickerPriority.LOW)

        val request = viewModel.saveEditing("New note")

        assertEquals(42L, request.id)
        assertEquals("New note", request.text)
        assertEquals("", request.alarmTimeText)
        assertEquals(TodoStickerColor.PINK, request.color)
        assertEquals(TodoStickerPriority.LOW, request.priority)
        assertEquals(false, viewModel.uiState.value.isEditing)
    }

    @Test
    fun cancelEditingResetsDraftFromCurrentSticker() {
        val viewModel = StickerItemViewModel(
            sticker = sampleSticker(),
            nowMillis = 1_000L,
            alarmTimeText = "09:30"
        )
        viewModel.startEditing("09:30")
        viewModel.onEditingTextChanged("Draft text")
        viewModel.updateSticker(
            sticker = sampleSticker(text = "Latest saved text", color = TodoStickerColor.BLUE),
            nowMillis = 2_000L,
            alarmTimeText = "10:00"
        )

        viewModel.cancelEditing("10:00")

        val state = viewModel.uiState.value
        assertEquals(false, state.isEditing)
        assertEquals("Latest saved text", state.editingText)
        assertEquals("10:00", state.editingAlarmTimeText)
        assertEquals(TodoStickerColor.BLUE, state.selectedColor)
    }

    private fun sampleSticker(
        text: String = "Water plants",
        color: TodoStickerColor = TodoStickerColor.GREEN
    ): TodoSticker =
        TodoSticker(
            id = 42L,
            text = text,
            done = false,
            timerDurationMillis = null,
            dueAtMillis = 3_600_000L,
            placedAtMillis = 1_000L,
            anchorProvider = "jetpack_xr_anchor",
            anchorId = "anchor-42",
            sizeScale = 1f,
            color = color,
            priority = TodoStickerPriority.HIGH,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L
        )
}
