package com.hubble.nfcshare.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.ShareStore

// Refreshes every placed widget instance (of both types) so they reflect whichever
// source was most recently shared or stopped, no matter where that action came from.
object WidgetUpdater {

    // Shows a live countdown (ticked by the launcher itself, no updates needed from
    // us) on an active widget whose share has a time limit.
    fun applyCountdown(context: Context, views: RemoteViews, isActive: Boolean, color: Int) {
        val expiresAt = ShareStore.expiresAt(context)
        if (!isActive || expiresAt <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            return
        }
        val base = SystemClock.elapsedRealtime() + (expiresAt - System.currentTimeMillis())
        views.setChronometer(R.id.widget_timer, base, null, true)
        views.setChronometerCountDown(R.id.widget_timer, true)
        views.setTextColor(R.id.widget_timer, color)
        views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, SavedItemWidgetProvider::class.java)).forEach {
            SavedItemWidgetProvider.updateWidget(context, manager, it)
        }
        manager.getAppWidgetIds(ComponentName(context, ClipboardWidgetProvider::class.java)).forEach {
            ClipboardWidgetProvider.updateWidget(context, manager, it)
        }
    }
}
