package com.seniorguardian.app.risk.detectors

import android.telephony.PhoneNumberUtils
import android.util.Log
import com.seniorguardian.app.config.AppConfig
import com.seniorguardian.app.risk.CallInfo
import com.seniorguardian.app.risk.Detector

class BlacklistDetector : Detector {

    override suspend fun detect(callInfo: CallInfo): Double {
        val number = callInfo.phoneNumber ?: return 0.0
        val isBlacklisted = AppConfig.BLACKLIST_NUMBERS.any { PhoneNumberUtils.compare(number, it) }
        if (isBlacklisted) {
            Log.e(TAG, "BLACKLISTED NUMBER: $number")
        }
        return if (isBlacklisted) 1.0 else 0.0
    }

    private companion object {
        const val TAG = "BlacklistDetector"
    }
}
