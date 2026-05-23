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
    val activitySpacePose: StickerSpatialPose? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun isTimerExpired(nowMillis: Long): Boolean =
        !done && dueAtMillis != null && nowMillis >= dueAtMillis
}
