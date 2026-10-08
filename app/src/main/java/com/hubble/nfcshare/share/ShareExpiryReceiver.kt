package com.hubble.nfcshare.share

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Fired by the alarm ShareStore schedules at the end of a time-limited share.
class ShareExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ShareStore.expireIfNeeded(context)
    }
}
