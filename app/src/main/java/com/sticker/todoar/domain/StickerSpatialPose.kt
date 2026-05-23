package com.sticker.todoar.domain

data class StickerSpatialPose(
    val translationX: Float,
    val translationY: Float,
    val translationZ: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float,
    val rotationW: Float
) {
    fun hasFiniteValues(): Boolean =
        translationX.isFinite() &&
            translationY.isFinite() &&
            translationZ.isFinite() &&
            rotationX.isFinite() &&
            rotationY.isFinite() &&
            rotationZ.isFinite() &&
            rotationW.isFinite()
}
