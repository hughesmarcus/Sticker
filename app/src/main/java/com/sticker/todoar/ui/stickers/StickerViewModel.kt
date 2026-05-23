package com.sticker.todoar.ui.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sticker.todoar.R
import com.sticker.todoar.data.TodoStickerRepository
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StickerViewModel @Inject constructor(
    private val repository: TodoStickerRepository
) : ViewModel() {
    private val draftText = MutableStateFlow("")
    private val selectedAlarmTimeText = MutableStateFlow(defaultAlarmTimeText())
    private val selectedColor = MutableStateFlow(TodoStickerColor.DEFAULT)
    private val selectedPriority = MutableStateFlow(TodoStickerPriority.DEFAULT)
    private val status = MutableStateFlow(STATUS_READY)
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val editorState = combine(
        draftText,
        selectedAlarmTimeText,
        selectedColor,
        selectedPriority,
        status
    ) { draft, alarmTimeText, color, priority, message ->
        EditorState(draft, alarmTimeText, color, priority, message)
    }

    val uiState = combine(
        repository.observeStickers(),
        editorState,
        clock
    ) { stickers, editor, nowMillis ->
        StickerUiState(
            draftText = editor.draftText,
            selectedAlarmTimeText = editor.selectedAlarmTimeText,
            selectedColor = editor.selectedColor,
            selectedPriority = editor.selectedPriority,
            stickers = stickers,
            status = statusText(
                message = editor.status,
                total = stickers.size,
                open = stickers.count { !it.done },
                expired = stickers.count { it.isTimerExpired(nowMillis) }
            ),
            nowMillis = nowMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StickerUiState()
    )

    fun onDraftChanged(text: String) {
        draftText.value = text
    }

    fun onAlarmTimeChanged(text: String) {
        selectedAlarmTimeText.value = text
    }

    fun onAlarmCleared() {
        selectedAlarmTimeText.value = ""
    }

    fun onDraftColorChanged(color: TodoStickerColor) {
        selectedColor.value = color
    }

    fun onDraftPriorityChanged(priority: TodoStickerPriority) {
        selectedPriority.value = priority
    }

    fun onPlaceRequested(text: String, fallbackText: String): RoomPlacementRequest? {
        val trimmedText = text.trim().ifBlank { fallbackText }
        val alarmDueAt = resolveAlarmDueAtMillisOrShowError(selectedAlarmTimeText.value) ?: return null

        draftText.value = trimmedText
        setStatus(UiText.resource(R.string.status_placing_in_room))
        return RoomPlacementRequest(
            text = trimmedText,
            dueAtMillis = alarmDueAt.millis,
            color = selectedColor.value,
            priority = selectedPriority.value
        )
    }

    fun onSaveRequested(text: String) {
        saveSticker(text)
    }

    fun onRoomPlacementSucceeded(
        text: String,
        dueAtMillis: Long?,
        anchorProvider: String,
        anchorId: String,
        color: TodoStickerColor,
        priority: TodoStickerPriority,
        activitySpacePose: StickerSpatialPose?
    ) {
        viewModelScope.launch {
            repository.addSticker(
                text = text,
                dueAtMillis = dueAtMillis,
                placedAtMillis = System.currentTimeMillis(),
                anchorProvider = anchorProvider,
                anchorId = anchorId,
                color = color,
                priority = priority,
                activitySpacePose = activitySpacePose
            )
            draftText.value = ""
            setStatus(UiText.resource(R.string.status_spawned_note))
        }
    }

    fun onRoomPlacementFailed(message: UiText) {
        setStatus(message)
    }

    fun onStickerAnchorUpdated(id: Long, anchorId: String) {
        viewModelScope.launch {
            repository.updateAnchor(id, ANCHOR_PROVIDER_JETPACK_XR_ANCHOR, anchorId)
            setStatus(UiText.resource(R.string.status_room_position_saved))
        }
    }

    fun onStickerActivitySpacePoseUpdated(id: Long, pose: StickerSpatialPose) {
        viewModelScope.launch {
            repository.updateActivitySpacePose(id, pose)
        }
    }

    fun onStickerTextUpdated(id: Long, text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            setStatus(UiText.resource(R.string.status_add_todo_first))
            return
        }

        viewModelScope.launch {
            repository.updateText(id, trimmedText)
            setStatus(UiText.resource(R.string.status_note_updated))
        }
    }

    fun onStickerEdited(
        id: Long,
        text: String,
        alarmTimeText: String,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            setStatus(UiText.resource(R.string.status_add_todo_first))
            return
        }
        val alarmDueAt = resolveAlarmDueAtMillisOrShowError(alarmTimeText) ?: return

        viewModelScope.launch {
            repository.updateText(id, trimmedText)
            repository.updateAlarm(id, alarmDueAt.millis)
            repository.updateStyle(id, color, priority)
            setStatus(UiText.resource(R.string.status_note_updated))
        }
    }

    fun onStickerAlarmUpdated(id: Long, alarmTimeText: String) {
        val alarmDueAt = resolveAlarmDueAtMillisOrShowError(alarmTimeText) ?: return

        viewModelScope.launch {
            repository.updateAlarm(id, alarmDueAt.millis)
            setStatus(UiText.resource(R.string.status_alarm_updated))
        }
    }

    fun onStickerSizeChanged(id: Long, sizeScale: Float) {
        viewModelScope.launch {
            repository.updateSizeScale(id, sanitizeSizeScale(sizeScale))
            setStatus(UiText.resource(R.string.status_note_resized))
        }
    }

    fun onStickerStyleChanged(
        id: Long,
        color: TodoStickerColor,
        priority: TodoStickerPriority
    ) {
        viewModelScope.launch {
            repository.updateStyle(id, color, priority)
            setStatus(UiText.resource(R.string.status_note_style_updated))
        }
    }

    fun onStickerSnoozed(id: Long, minutes: Int) {
        viewModelScope.launch {
            repository.updateAlarm(id, System.currentTimeMillis() + minutes * 60_000L)
            setStatus(UiText.resource(R.string.status_alarm_snoozed, minutes))
        }
    }

    fun onAlarmTriggered(id: Long, noteText: String) {
        viewModelScope.launch {
            repository.markAlarmTriggered(id)
            setStatus(UiText.resource(R.string.status_alarm_triggered, noteText))
        }
    }

    fun onXrStatusChanged(message: UiText) {
        setStatus(message)
    }

    fun onStickerToggled(id: Long) {
        viewModelScope.launch {
            repository.toggleDone(id)
            setStatus(UiText.resource(R.string.status_updated))
        }
    }

    fun onStickerRemoved(id: Long) {
        viewModelScope.launch {
            repository.deleteSticker(id)
            setStatus(UiText.resource(R.string.status_removed))
        }
    }

    private fun setStatus(message: UiText) {
        if (status.value != message) {
            status.value = message
        }
    }

    private fun saveSticker(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            setStatus(UiText.resource(R.string.status_add_todo_first))
            return
        }

        val alarmDueAt = resolveAlarmDueAtMillisOrShowError(selectedAlarmTimeText.value) ?: return

        viewModelScope.launch {
            repository.addSticker(
                text = trimmedText,
                dueAtMillis = alarmDueAt.millis,
                placedAtMillis = null,
                anchorProvider = null,
                color = selectedColor.value,
                priority = selectedPriority.value
            )
            draftText.value = ""
            setStatus(UiText.resource(R.string.status_saved))
        }
    }

    private fun statusText(
        message: UiText,
        total: Int,
        open: Int,
        expired: Int
    ): UiText {
        if (message != STATUS_READY) return message
        if (expired > 0) return UiText.resource(R.string.status_timer_due_count, expired)
        if (total > 0) return UiText.resource(R.string.status_open_saved_count, open, total)
        return STATUS_READY
    }

    private fun resolveAlarmDueAtMillisOrShowError(text: String): AlarmDueAt? {
        if (text.isBlank()) return AlarmDueAt(null)
        val parsedClockTime = parseClockTime(text)
        if (parsedClockTime == null) {
            setStatus(UiText.resource(R.string.status_invalid_alarm_time))
            return null
        }
        return AlarmDueAt(nextClockOccurrenceMillis(parsedClockTime.hour, parsedClockTime.minute))
    }

    private fun parseClockTime(text: String): ClockTime? {
        val twelveHourMatch = TWELVE_HOUR_TIME.matchEntire(text.trim())
        if (twelveHourMatch != null) {
            val hour = twelveHourMatch.groupValues[1].toInt()
            val minute = twelveHourMatch.groupValues[2].toInt()
            val meridiem = twelveHourMatch.groupValues[3].lowercase(Locale.US)
            if (hour !in 1..12 || minute !in 0..59) return null
            val adjustedHour = when {
                meridiem == "am" && hour == 12 -> 0
                meridiem == "pm" && hour < 12 -> hour + 12
                else -> hour
            }
            return ClockTime(adjustedHour, minute)
        }

        val twentyFourHourMatch = TWENTY_FOUR_HOUR_TIME.matchEntire(text.trim()) ?: return null
        val hour = twentyFourHourMatch.groupValues[1].toInt()
        val minute = twentyFourHourMatch.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return ClockTime(hour, minute)
    }

    private fun nextClockOccurrenceMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun sanitizeSizeScale(sizeScale: Float): Float =
        if (sizeScale.isFinite()) {
            sizeScale.coerceIn(MIN_NOTE_SIZE_SCALE, MAX_NOTE_SIZE_SCALE)
        } else {
            DEFAULT_NOTE_SIZE_SCALE
        }

    private data class EditorState(
        val draftText: String,
        val selectedAlarmTimeText: String,
        val selectedColor: TodoStickerColor,
        val selectedPriority: TodoStickerPriority,
        val status: UiText
    )

    private data class ClockTime(
        val hour: Int,
        val minute: Int
    )

    private data class AlarmDueAt(
        val millis: Long?
    )

    private companion object {
        val STATUS_READY = UiText.resource(R.string.status_ready)
        const val ANCHOR_PROVIDER_JETPACK_XR_ANCHOR = "jetpack_xr_anchor"
        const val DEFAULT_NOTE_SIZE_SCALE = 1f
        const val MIN_NOTE_SIZE_SCALE = 0.7f
        const val MAX_NOTE_SIZE_SCALE = 1.8f
        val TWENTY_FOUR_HOUR_TIME = Regex("""^(\d{1,2}):(\d{2})$""")
        val TWELVE_HOUR_TIME = Regex("""^(\d{1,2}):(\d{2})\s*([aApP][mM])$""")

        fun defaultAlarmTimeText(): String {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 15)
            }
            return SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)
        }
    }
}
