package com.aread.app.blocking

import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import com.aread.app.data.Prefs

/**
 * During a focus session, incoming calls are silenced and sent to the missed-call log,
 * except from numbers the student marked as allowed (e.g. parents).
 * Requires the user to set Aread as the call screening app (ROLE_CALL_SCREENING).
 */
class CallBlockerService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val focusing = Prefs.isFocusing(this) || System.currentTimeMillis() < Prefs.forcedLockUntilMs(this)
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!focusing || !incoming || !Prefs.blockCalls(this)) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val allowed = number in Prefs.allowedCallers(this) || isStarredContact(number)
        val response = CallResponse.Builder()
            .setDisallowCall(!allowed)
            .setSilenceCall(!allowed)
            .setSkipNotification(!allowed)
            .build()
        respondToCall(details, response)
    }

    private fun isStarredContact(number: String): Boolean {
        if (number.isBlank()) return false
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.STARRED), null, null, null)?.use {
                it.moveToFirst() && it.getInt(0) == 1
            } ?: false
        }.getOrDefault(false)
    }
}
