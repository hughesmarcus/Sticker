package com.sticker.todoar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.stickers.StickerItemViewModel

private const val SNOOZE_SHORT_MINUTES = 5
private const val SNOOZE_LONG_MINUTES = 15

@Composable
internal fun StickerItem(
    sticker: TodoSticker,
    nowMillis: Long,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    val existingAlarmTimeText = sticker.dueAtMillis?.toClockTimeText().orEmpty()
    val itemViewModel = remember(sticker.id) {
        StickerItemViewModel(
            sticker = sticker,
            nowMillis = nowMillis,
            alarmTimeText = existingAlarmTimeText
        )
    }
    LaunchedEffect(itemViewModel, sticker, nowMillis, existingAlarmTimeText) {
        itemViewModel.updateSticker(
            sticker = sticker,
            nowMillis = nowMillis,
            alarmTimeText = existingAlarmTimeText
        )
    }
    val itemState by itemViewModel.uiState.collectAsStateWithLifecycle()
    val currentSticker = itemState.sticker
    val timerText = currentSticker.timerText(itemState.nowMillis)
    val placementText = currentSticker.placementText()
    val priorityText = currentSticker.priority.labelText()
    val metadataText = listOfNotNull(timerText, priorityText, placementText)
        .joinToString(stringResource(R.string.metadata_separator))
    val expired = itemState.isExpired
    val currentAlarmTimeText = currentSticker.dueAtMillis?.toClockTimeText().orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                currentSticker.done -> Color(0xFFDDE1DA)
                expired -> Color(0xFFFFC7B8)
                else -> currentSticker.color.backgroundColor()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                textDecoration = if (currentSticker.done) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                        ) {
                            append(currentSticker.text)
                        }
                    },
                    color = Color(0xFF2C3028),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (metadataText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metadataText,
                        color = if (expired) Color(0xFF8B2F1D) else Color(0xFF586053),
                        fontSize = 13.sp,
                        fontWeight = if (expired) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (expired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSnoozeSticker(currentSticker.id, SNOOZE_SHORT_MINUTES) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_snooze_5))
                        }
                        OutlinedButton(
                            onClick = { onSnoozeSticker(currentSticker.id, SNOOZE_LONG_MINUTES) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_snooze_15))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = { itemViewModel.startEditing(currentAlarmTimeText) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_edit))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onToggleSticker(currentSticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    stringResource(
                        if (currentSticker.done) R.string.action_undo else R.string.action_done
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onDeleteSticker(currentSticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }

    if (itemState.isEditing) {
        val defaultNoteText = stringResource(R.string.default_note_text)
        EditStickerDialog(
            state = itemState,
            onTextChanged = itemViewModel::onEditingTextChanged,
            onAlarmTimeChanged = itemViewModel::onEditingAlarmTimeChanged,
            onColorChanged = itemViewModel::onEditingColorChanged,
            onPriorityChanged = itemViewModel::onEditingPriorityChanged,
            onDismiss = { itemViewModel.cancelEditing(currentAlarmTimeText) },
            onSave = {
                val request = itemViewModel.saveEditing(defaultNoteText)
                onEditSticker(
                    request.id,
                    request.text,
                    request.alarmTimeText,
                    request.color,
                    request.priority
                )
            }
        )
    }
}
