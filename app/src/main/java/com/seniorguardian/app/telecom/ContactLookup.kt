package com.seniorguardian.app.telecom

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactLookup {

    /** Returns the saved contact display name for [number], or null if it's not a known contact. */
    fun lookupName(context: Context, number: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return null
    }
}
