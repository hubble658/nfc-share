package com.hubble.nfcshare.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.LinkResolver
import com.hubble.nfcshare.share.NfcStatus
import com.hubble.nfcshare.share.ShareStore

// Invisible trampoline: Android 10+ blocks clipboard reads from apps that aren't
// in the foreground, and a widget tap alone doesn't count as foreground. Briefly
// launching this (transparent, no layout) gains just enough focus to read it.
class ClipboardShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ShareStore.activeKey(this) == ShareStore.KEY_CLIPBOARD) {
            ShareStore.clearActive(this)
        } else {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            val data = text?.let { LinkResolver.resolveLinkShare(it) }
            if (data != null) {
                ShareStore.setActive(this, ShareStore.KEY_CLIPBOARD, data)
                NfcStatus.warnIfUnavailable(this)
            } else {
                Toast.makeText(this, getString(R.string.error_clipboard_empty), Toast.LENGTH_LONG).show()
            }
        }

        finish()
    }
}
