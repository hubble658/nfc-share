package com.hubble.nfcshare.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.LinkResolver
import com.hubble.nfcshare.share.SavedItemsStore
import com.hubble.nfcshare.share.ShareStore

// Lets this app appear in other apps' native "Share" sheet (YouTube, Instagram,
// Chrome, ...): picking it there starts emulating the shared text/link immediately
// and opens the app so the status card and the stop button are right there.
class ShareIntentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()

        val data = text?.let { LinkResolver.resolveLinkShare(it) }
        if (text != null && data != null) {
            SavedItemsStore.saveLink(this, text)
            ShareStore.setActive(this, ShareStore.keyForLink(text), data)
            Toast.makeText(this, getString(R.string.toast_shared), Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        } else {
            Toast.makeText(this, getString(R.string.error_invalid_url), Toast.LENGTH_LONG).show()
        }

        finish()
    }
}
