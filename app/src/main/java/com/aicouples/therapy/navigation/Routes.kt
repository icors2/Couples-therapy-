package com.aicouples.therapy.navigation

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val AGE_GATE = "age_gate"
    const val PAIRING = "pairing"
    const val HOME = "home"
    const val THERAPY = "therapy/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val INTAKE = "intake/{relationshipId}"

    fun therapy(sessionId: String) = "therapy/$sessionId"
    fun intake(relationshipId: String) = "intake/$relationshipId"
}
