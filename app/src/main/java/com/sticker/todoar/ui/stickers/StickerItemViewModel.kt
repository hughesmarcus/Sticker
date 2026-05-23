package com.sticker.todoar.ui.stickers

import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class StickerItemViewModel(
    sticker: TodoSticker,
    nowMillis: Long,
    alarmTimeText: String
) {
    private val _uiState = MutableStateFlow(
        StickerItemUiState(
            sticker = sticker,
            nowMillis = nowMillis,
            editingAlarmTimeText = alarmTimeText
        )
    )
    val uiState: StateFlow<StickerItemUiState> = _uiState.asStateFlow()

    fun updateSticker(
        sticker: TodoSticker,
        nowMillis: Long,
        alarmTimeText: String
    ) {
        _uiState.update { current ->
            if (current.isEditing) {
                current.copy(
                    sticker = sticker,
                    nowMillis = nowMillis
                )
            } else {
                current.copy(
                    sticker = sticker,
                    nowMillis = nowMillis,
                    editingText = sticker.text,
                    editingAlarmTimeText = alarmTimeText,
                    selectedColor = sticker.color,
                    selectedPriority = sticker.priority
                )
            }
        }
    }

    fun startEditing(alarmTimeText: String) {
        _uiState.update { current ->
            current.copy(
                isEditing = true,
                editingText = current.sticker.text,
                editingAlarmTimeText = alarmTimeText,
                selectedColor = current.sticker.color,
                selectedPriority = current.sticker.priority
            )
        }
    }

    fun cancelEditing(alarmTimeText: String) {
        _uiState.update { current ->
            current.copy(
                isEditing = false,
                editingText = current.sticker.text,
                editingAlarmTimeText = alarmTimeText,
                selectedColor = current.sticker.color,
                selectedPriority = current.sticker.priority
            )
        }
    }

    fun onEditingTextChanged(text: String) {
        _uiState.update { current -> current.copy(editingText = text) }
    }

    fun onEditingAlarmTimeChanged(text: String) {
        _uiState.update { current -> current.copy(editingAlarmTimeText = text) }
    }

    fun onEditingColorChanged(color: TodoStickerColor) {
        _uiState.update { current -> current.copy(selectedColor = color) }
    }

    fun onEditingPriorityChanged(priority: TodoStickerPriority) {
        _uiState.update { current -> current.copy(selectedPriority = priority) }
    }

    fun saveEditing(defaultText: String): StickerEditRequest {
        val current = _uiState.value
        val request = StickerEditRequest(
            id = current.sticker.id,
            text = current.editingText.ifBlank { defaultText },
            alarmTimeText = current.editingAlarmTimeText,
            color = current.selectedColor,
            priority = current.selectedPriority
        )
        _uiState.update { state -> state.copy(isEditing = false) }
        return request
    }
}

data class StickerEditRequest(
    val id: Long,
    val text: String,
    val alarmTimeText: String,
    val color: TodoStickerColor,
    val priority: TodoStickerPriority
)
