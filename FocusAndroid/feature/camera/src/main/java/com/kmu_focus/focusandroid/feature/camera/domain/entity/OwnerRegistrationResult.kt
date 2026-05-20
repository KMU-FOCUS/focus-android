package com.kmu_focus.focusandroid.feature.camera.domain.entity

data class OwnerRegistrationResult(
    val success: Boolean,
    val ownerId: Int? = null,
    val thumbnailPath: String? = null,
)
