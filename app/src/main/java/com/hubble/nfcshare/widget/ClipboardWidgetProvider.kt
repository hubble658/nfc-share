package com.hubble.nfcshare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.ShareStore
import com.hubble.nfcshare.ui.ClipboardShareActivity

class ClipboardWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val isActive = ShareStore.activeKey(context) == ShareStore.KEY_CLIPBOARD
            val tint = ContextCompat.getColor(context, if (isActive) R.color.color_on_primary else R.color.color_text)

            val views = RemoteViews(context.packageName, R.layout.widget_clipboard)
            views.setTextColor(R.id.widget_label, tint)
            views.setInt(R.id.widget_icon, "setColorFilter", tint)
            views.setInt(R.id.widget_root, "setBackgroundResource", if (isActive) R.drawable.bg_widget_active else R.drawable.bg_widget_idle)
            WidgetUpdater.applyCountdown(context, views, isActive, tint)

            // A plain broadcast can't read the clipboard on Android 10+ (foreground-only
            // restriction), so the tap launches a transparent trampoline activity instead.
            val tapIntent = Intent(context, ClipboardShareActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, appWidgetId, tapIntent, flags))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }
}
