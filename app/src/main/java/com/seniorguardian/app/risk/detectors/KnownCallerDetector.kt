package com.seniorguardian.app.risk.detectors

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.seniorguardian.app.config.AppConfig
import com.seniorguardian.app.risk.CallInfo
import com.seniorguardian.app.risk.Detector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnownCallerDetector(private val context: Context) : Detector {

    override suspend fun detect(callInfo: CallInfo): Double {
        val number = callInfo.phoneNumber ?: run {
            Log.d(TAG, "no phone number available, treating as unknown")
            return 1.0
        }
        return withContext(Dispatchers.IO) {
            val inPhonebook = isInPhonebook(number)
            val calledRecently = calledInLastMonth(number)
            Log.d(TAG, "number=$number inPhonebook=$inPhonebook calledInLastMonth=$calledRecently")
            if (inPhonebook || calledRecently) 0.0 else 1.0
        }
    }

    private fun isInPhonebook(number: String): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { cursor -> return cursor.moveToFirst() }
        return false
    }

    private fun calledInLastMonth(number: String): Boolean {
        val lookbackMillis = AppConfig.KNOWN_CALLER_LOOKBACK_DAYS * 24 * 60 * 60 * 1000
        val since = System.currentTimeMillis() - lookbackMillis
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            null
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            while (cursor.moveToNext()) {
                val loggedNumber = cursor.getString(numberIdx) ?: continue
                if (PhoneNumberUtils.compare(number, loggedNumber)) return true
            }
        }
        return false
    }

    private companion object {
        const val TAG = "KnownCallerDetector"
    }
}
