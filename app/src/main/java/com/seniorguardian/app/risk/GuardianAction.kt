package com.seniorguardian.app.risk

sealed interface GuardianAction {
    data object Alert : GuardianAction
    data object NoAction : GuardianAction
    // Alert => run every registered Responder.
}
