package com.sticker.todoar.domain

data class TodoSticker(
    val id: Long,
    val text: String,
    val done: Boolean,
    val timerDurationMillis: Long?,
    val dueAtMillis: Long?,
    val placedAtMillis: Long?,
    val anchorProvider: String?,
    val anchorId: String?,
    val sizeScale: Float = 1f,
    val color: TodoStickerColor = TodoStickerColor.DEFAULT,
    val priority: TodoStickerPriority = TodoStickerPriority.DEFAULT,
    val lastAlarmTriggeredAtMillis: Long? = null,
    val activitySpacePose: StickerSpatialPose? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun isTimerExpired(nowMillis: Long): Boolean =
        !done && dueAtMillis != null && nowMillis >= dueAtMillis

    fun shouldTriggerAlarm(nowMillis: Long): Boolean =
        isTimerExpired(nowMillis) && dueAtMillis != lastAlarmTriggeredAtMillis

    val pinQuality: StickerPinQuality
        get() = when (anchorProvider) {
            "jetpack_xr_anchor" -> StickerPinQuality.ROOM
            "jetpack_xr_session_anchor" -> StickerPinQuality.SESSION
            "jetpack_xr_activity_space" -> StickerPinQuality.FALLBACK
            "jetpack_xr" -> StickerPinQuality.SESSION
            else -> StickerPinQuality.UNPLACED
        }
}
