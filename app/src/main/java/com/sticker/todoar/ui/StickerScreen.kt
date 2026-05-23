package com.sticker.todoar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.stickers.StickerUiState

internal const val DEFAULT_MAIN_PANEL_SCALE = 1f
internal const val MIN_MAIN_PANEL_SCALE = 0.75f
internal const val MAX_MAIN_PANEL_SCALE = 1.5f

@Composable
internal fun StickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2F6B5F),
            onPrimary = Color.White,
            surface = Color(0xFFF6F7F2),
            onSurface = Color(0xFF1F2320)
        ),
        content = content
    )
}

@Composable
internal fun StickerScreen(
    state: StickerUiState,
    mainPanelMinimized: Boolean,
    mainPanelScale: Float,
    onMainPanelMinimizedChange: (Boolean) -> Unit,
    onMainPanelScaleChange: (Float) -> Unit,
    onDraftChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit,
    onDraftColorChanged: (TodoStickerColor) -> Unit,
    onDraftPriorityChanged: (TodoStickerPriority) -> Unit,
    onSaveClicked: (String) -> Unit,
    onPlaceClicked: (String, String) -> Unit,
    onBringNotesHere: () -> Unit,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    if (mainPanelMinimized) {
        MinimizedStickerPanel(
            onRestore = { onMainPanelMinimizedChange(false) }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F7F2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            MainPanelControls(
                mainPanelScale = mainPanelScale,
                onMainPanelScaleChange = onMainPanelScaleChange,
                onMinimize = { onMainPanelMinimizedChange(true) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            StickerComposer(
                draftText = state.draftText,
                alarmTimeText = state.selectedAlarmTimeText,
                selectedColor = state.selectedColor,
                selectedPriority = state.selectedPriority,
                onDraftChanged = onDraftChanged,
                onAlarmTimeChanged = onAlarmTimeChanged,
                onAlarmCleared = onAlarmCleared,
                onColorChanged = onDraftColorChanged,
                onPriorityChanged = onDraftPriorityChanged,
                onSaveClicked = onSaveClicked,
                onPlaceClicked = onPlaceClicked,
                onBringNotesHere = onBringNotesHere
            )
            Text(
                text = state.status.asString(),
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                color = Color(0xFF3A403A),
                fontSize = 15.sp
            )
            StickerList(
                isLoading = state.isLoading,
                isError = state.isError,
                errorText = state.status,
                stickers = state.stickers,
                nowMillis = state.nowMillis,
                onEditSticker = onEditSticker,
                onSnoozeSticker = onSnoozeSticker,
                onToggleSticker = onToggleSticker,
                onDeleteSticker = onDeleteSticker
            )
        }
    }
}
