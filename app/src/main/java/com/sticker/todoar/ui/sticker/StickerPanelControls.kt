package com.sticker.todoar.ui.sticker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sticker.todoar.R

private const val MAIN_PANEL_SCALE_STEP = 0.1f

@Composable
internal fun MainPanelControls(
    mainPanelScale: Float,
    onMainPanelScaleChange: (Float) -> Unit,
    onMinimize: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            enabled = mainPanelScale > MIN_MAIN_PANEL_SCALE,
            onClick = {
                onMainPanelScaleChange(
                    (mainPanelScale - MAIN_PANEL_SCALE_STEP)
                        .coerceIn(MIN_MAIN_PANEL_SCALE, MAX_MAIN_PANEL_SCALE)
                )
            },
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.action_size_smaller))
        }
        OutlinedButton(
            enabled = mainPanelScale < MAX_MAIN_PANEL_SCALE,
            onClick = {
                onMainPanelScaleChange(
                    (mainPanelScale + MAIN_PANEL_SCALE_STEP)
                        .coerceIn(MIN_MAIN_PANEL_SCALE, MAX_MAIN_PANEL_SCALE)
                )
            },
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.action_size_larger))
        }
        OutlinedButton(
            onClick = onMinimize,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.action_minimize_panel))
        }
    }
}

@Composable
internal fun MinimizedStickerPanel(
    onRestore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F7F2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onRestore,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_restore_panel),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
