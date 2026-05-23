package com.sticker.todoar.ui.sticker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.model.StickerItemUiState

@Composable
internal fun EditStickerDialog(
    state: StickerItemUiState,
    onTextChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onColorChanged: (TodoStickerColor) -> Unit,
    onPriorityChanged: (TodoStickerPriority) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_edit_note)) },
        text = {
            Column {
                TextField(
                    value = state.editingText,
                    onValueChange = onTextChanged,
                    label = { Text(stringResource(R.string.label_todo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave() }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.editingAlarmTimeText,
                    onValueChange = onAlarmTimeChanged,
                    label = { Text(stringResource(R.string.label_alarm_time)) },
                    placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave() }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                StickerStyleControls(
                    selectedColor = state.selectedColor,
                    selectedPriority = state.selectedPriority,
                    onColorSelected = onColorChanged,
                    onPrioritySelected = onPriorityChanged
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onAlarmTimeChanged("") },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_no_alarm))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
