package com.sticker.todoar.ui.sticker

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sticker.todoar.R
import com.sticker.todoar.domain.StickerPinQuality
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import java.util.Date
import kotlin.math.max

@Composable
internal fun TodoSticker.timerText(nowMillis: Long): String? {
    val dueAt = dueAtMillis ?: return null
    if (done) return stringResource(R.string.alarm_complete)

    val remainingMillis = dueAt - nowMillis
    if (remainingMillis <= 0) return stringResource(R.string.alarm_due)

    return stringResource(
        R.string.alarm_due_at_in,
        dueAt.toClockTimeText(),
        remainingMillis.formatDuration()
    )
}

@Composable
internal fun Long.toClockTimeText(): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date(this))

@Composable
internal fun TodoSticker.placementText(): String? =
    when (pinQuality) {
        StickerPinQuality.ROOM -> stringResource(R.string.pin_quality_room)
        StickerPinQuality.SESSION -> stringResource(R.string.pin_quality_session)
        StickerPinQuality.FALLBACK -> stringResource(R.string.pin_quality_fallback)
        StickerPinQuality.UNPLACED -> null
    }

@Composable
internal fun TodoStickerPriority.labelText(): String =
    when (this) {
        TodoStickerPriority.LOW -> stringResource(R.string.priority_low)
        TodoStickerPriority.NORMAL -> stringResource(R.string.priority_normal)
        TodoStickerPriority.HIGH -> stringResource(R.string.priority_high)
    }

@Composable
internal fun TodoStickerColor.labelText(): String =
    when (this) {
        TodoStickerColor.YELLOW -> stringResource(R.string.note_color_yellow)
        TodoStickerColor.BLUE -> stringResource(R.string.note_color_blue)
        TodoStickerColor.GREEN -> stringResource(R.string.note_color_green)
        TodoStickerColor.PINK -> stringResource(R.string.note_color_pink)
    }

internal fun TodoStickerColor.backgroundColor(): Color =
    when (this) {
        TodoStickerColor.YELLOW -> Color(0xFFFFE067)
        TodoStickerColor.BLUE -> Color(0xFFBFDDF8)
        TodoStickerColor.GREEN -> Color(0xFFCDECCF)
        TodoStickerColor.PINK -> Color(0xFFFFC9DE)
    }

@Composable
private fun Long.formatDuration(): String {
    val totalSeconds = max(0L, this / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes_seconds, minutes, seconds)
    }
}
