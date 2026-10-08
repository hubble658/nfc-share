package com.hubble.nfcshare.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.hubble.nfcshare.R

abstract class BaseActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.getBoolean(R.bool.is_tablet)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}