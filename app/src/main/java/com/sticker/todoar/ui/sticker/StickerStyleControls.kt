package com.sticker.todoar.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority

@Composable
internal fun StickerStyleControls(
    selectedColor: TodoStickerColor,
    selectedPriority: TodoStickerPriority,
    onColorSelected: (TodoStickerColor) -> Unit,
    onPrioritySelected: (TodoStickerPriority) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_note_color),
            color = Color(0xFF3A403A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerColor.entries.forEach { color ->
                val label = color.labelText()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color.backgroundColor(), RoundedCornerShape(8.dp))
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) Color(0xFF1F2320) else Color(0xFF7B8177),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .semantics { contentDescription = label }
                        .clickable { onColorSelected(color) }
                )
            }
        }
        Text(
            text = stringResource(R.string.label_priority),
            color = Color(0xFF3A403A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerPriority.entries.forEach { priority ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrioritySelected(priority) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = priority.labelText(),
                        color = if (priority == selectedPriority) Color(0xFF1B4F45) else Color(0xFF3A403A),
                        fontWeight = if (priority == selectedPriority) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
