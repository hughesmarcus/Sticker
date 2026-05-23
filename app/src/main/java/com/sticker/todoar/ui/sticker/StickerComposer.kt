package com.sticker.todoar.ui.sticker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

@Composable
internal fun StickerComposer(
    draftText: String,
    alarmTimeText: String,
    selectedColor: TodoStickerColor,
    selectedPriority: TodoStickerPriority,
    onDraftChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit,
    onColorChanged: (TodoStickerColor) -> Unit,
    onPriorityChanged: (TodoStickerPriority) -> Unit,
    onSaveClicked: (String) -> Unit,
    onPlaceClicked: (String, String) -> Unit,
    onBringNotesHere: () -> Unit
) {
    val defaultNoteText = stringResource(R.string.default_note_text)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = draftText,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 18.sp),
                    placeholder = { Text(stringResource(R.string.label_todo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSaveClicked(draftText) }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onSaveClicked(draftText) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AlarmTimeEditor(
                alarmTimeText = alarmTimeText,
                onAlarmTimeChanged = onAlarmTimeChanged,
                onAlarmCleared = onAlarmCleared
            )
            Spacer(modifier = Modifier.height(12.dp))
            StickerStyleControls(
                selectedColor = selectedColor,
                selectedPriority = selectedPriority,
                onColorSelected = onColorChanged,
                onPrioritySelected = onPriorityChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBringNotesHere,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_bring_notes_here))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { onPlaceClicked(draftText, defaultNoteText) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_spawn_note), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun AlarmTimeEditor(
    alarmTimeText: String,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = alarmTimeText,
            onValueChange = onAlarmTimeChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.label_alarm_time)) },
            placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        )
        OutlinedButton(
            onClick = onAlarmCleared,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.action_no_alarm))
        }
    }
}
