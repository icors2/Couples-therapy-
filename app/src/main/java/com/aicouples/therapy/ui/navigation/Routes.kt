package com.aicouples.therapy.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val PAIRING = "pairing"
    const val HOME = "home"
    const val THERAPY = "therapy/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SESSION_INVITE = "session_invite/{sessionId}"

    fun therapy(sessionId: String) = "therapy/$sessionId"
    fun sessionInvite(sessionId: String) = "session_invite/$sessionId"
}
