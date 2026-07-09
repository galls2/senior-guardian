package com.seniorguardian.app.risk

data class CallInfo(
    val phoneNumber: String?,
    val receivedAtMillis: Long = System.currentTimeMillis()
    // TODO: metadata taxonomy undecided — extend here once defined (e.g. simSlot, isKnownContact)
)
