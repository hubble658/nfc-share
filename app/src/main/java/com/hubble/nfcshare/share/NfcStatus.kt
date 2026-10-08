package com.hubble.nfcshare.share

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.widget.Toast
import com.hubble.nfcshare.R

// Whether this phone can actually send right now. Sharing with NFC switched off
// silently does nothing, which looks exactly like "the app doesn't work".
object NfcStatus {

    enum class State { READY, OFF, NO_NFC, NO_HCE }

    fun state(context: Context): State {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return State.NO_NFC
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) return State.NO_HCE
        return if (adapter.isEnabled) State.READY else State.OFF
    }

    fun messageRes(state: State): Int = when (state) {
        State.READY -> 0
        State.OFF -> R.string.nfc_off
        State.NO_NFC -> R.string.nfc_missing
        State.NO_HCE -> R.string.nfc_no_hce
    }

    // For surfaces without a screen of their own (widgets, share sheet): a toast
    // when the share just started can't reach anyone. Returns true if all good.
    fun warnIfUnavailable(context: Context): Boolean {
        val state = state(context)
        if (state == State.READY) return true
        Toast.makeText(context, context.getString(messageRes(state)), Toast.LENGTH_LONG).show()
        return false
    }
}
