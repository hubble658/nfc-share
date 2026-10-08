package com.hubble.nfcshare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.luigivampa92.ndefemulation.ndef.WifiNetworkNdefData
import com.luigivampa92.ndefemulation.ndef.WifiNetworkNdefDataProtectionType
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.LinkResolver
import com.hubble.nfcshare.share.NfcStatus
import com.hubble.nfcshare.share.ShareStore
import com.hubble.nfcshare.ui.SavedItemWidgetConfigActivity

class SavedItemWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.hubble.nfcshare.widget.ACTION_TOGGLE_SAVED_ITEM"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val key = WidgetConfigStore.key(context, appWidgetId)
            val label = WidgetConfigStore.label(context, appWidgetId) ?: context.getString(R.string.widget_not_configured)
            val iconRes = WidgetConfigStore.iconRes(context, appWidgetId).takeIf { it != 0 } ?: R.drawable.ic_type_link
            val isActive = key != null && key == ShareStore.activeKey(context)
            val tint = ContextCompat.getColor(context, if (isActive) R.color.color_on_primary else R.color.color_text)

            val views = RemoteViews(context.packageName, R.layout.widget_saved_item)
            views.setTextViewText(R.id.widget_label, label)
            views.setImageViewResource(R.id.widget_icon, iconRes)
            views.setInt(R.id.widget_icon, "setColorFilter", tint)
            views.setTextColor(R.id.widget_label, tint)
            views.setInt(R.id.widget_reconfigure, "setColorFilter", tint)
            views.setInt(R.id.widget_root, "setBackgroundResource", if (isActive) R.drawable.bg_widget_active else R.drawable.bg_widget_idle)
            WidgetUpdater.applyCountdown(context, views, isActive, tint)

            val toggleIntent = Intent(context, SavedItemWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://saved-item-toggle/$appWidgetId")
            }
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, appWidgetId, toggleIntent, pendingIntentFlags()))

            val reconfigureIntent = Intent(context, SavedItemWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                data = Uri.parse("widget://saved-item-reconfigure/$appWidgetId")
            }
            views.setOnClickPendingIntent(
                R.id.widget_reconfigure,
                PendingIntent.getActivity(context, appWidgetId + 100000, reconfigureIntent, pendingIntentFlags()),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun pendingIntentFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            return flags
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfigStore.clear(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val key = WidgetConfigStore.key(context, appWidgetId) ?: return

        if (ShareStore.activeKey(context) == key) {
            ShareStore.clearActive(context)
            return
        }

        val ssid = WidgetConfigStore.wifiSsid(context, appWidgetId)
        val contact = WidgetConfigStore.contact(context, appWidgetId)
        val data = when {
            ssid != null -> WifiNetworkNdefData(
                ssid,
                if (WidgetConfigStore.wifiOpen(context, appWidgetId)) WifiNetworkNdefDataProtectionType.OPEN else WifiNetworkNdefDataProtectionType.PASSWORD,
                WidgetConfigStore.wifiPassword(context, appWidgetId),
            )
            contact != null -> contact.toNdefData()
            else -> WidgetConfigStore.linkRaw(context, appWidgetId)?.let { LinkResolver.resolveLinkShare(it) }
        }
        if (data != null) {
            ShareStore.setActive(context, key, data)
            NfcStatus.warnIfUnavailable(context)
        }
    }
}
