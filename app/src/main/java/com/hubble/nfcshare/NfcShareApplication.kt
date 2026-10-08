package com.hubble.nfcshare

import android.app.Application
import com.hubble.nfcshare.share.ShareStore

class NfcShareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fresh installs/updates start with the NFC service enabled; line it up with
        // whether anything is actually being shared.
        ShareStore.expireIfNeeded(this)
        ShareStore.syncServiceEnabled(this)
    }
}
