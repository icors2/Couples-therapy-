package com.aicouples.therapy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileInsert(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("google_id") val googleId: String? = null,
)
