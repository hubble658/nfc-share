package com.hubble.nfcshare.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.LinkResolver
import com.hubble.nfcshare.share.SavedItemsStore
import com.hubble.nfcshare.share.ShareStore
import com.hubble.nfcshare.widget.SavedItemWidgetProvider
import com.hubble.nfcshare.widget.WidgetConfigStore

class SavedItemWidgetConfigActivity : BaseActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)
        val container: LinearLayout = findViewById(R.id.container_widget_config_list)
        val empty: TextView = findViewById(R.id.text_widget_config_empty)

        val links = SavedItemsStore.loadLinks(this)
        val networks = SavedItemsStore.loadNetworks(this)
        val contacts = SavedItemsStore.loadContacts(this)
        empty.visibility = if (links.isEmpty() && networks.isEmpty() && contacts.isEmpty()) View.VISIBLE else View.GONE

        links.forEach { raw ->
            val label = if (raw.length > 44) raw.take(44) + "…" else raw
            addRow(container, label, LinkResolver.classifyIcon(raw)) {
                WidgetConfigStore.clear(this, appWidgetId)
                WidgetConfigStore.save(this, appWidgetId, ShareStore.keyForLink(raw), label, LinkResolver.classifyIcon(raw))
                WidgetConfigStore.saveLinkRaw(this, appWidgetId, raw)
                finishWithResult()
            }
        }
        networks.forEach { network ->
            val label = network.ssid + if (network.isOpen) getString(R.string.label_wifi_open_suffix) else ""
            addRow(container, label, R.drawable.ic_type_wifi) {
                WidgetConfigStore.clear(this, appWidgetId)
                WidgetConfigStore.save(this, appWidgetId, ShareStore.keyForWifi(network.ssid), label, R.drawable.ic_type_wifi)
                WidgetConfigStore.saveWifi(this, appWidgetId, network.ssid, network.password, network.isOpen)
                finishWithResult()
            }
        }
        contacts.forEach { contact ->
            val label = contact.displayName
            addRow(container, label, R.drawable.ic_type_contact) {
                WidgetConfigStore.clear(this, appWidgetId)
                WidgetConfigStore.save(this, appWidgetId, ShareStore.keyForContact(contact), label, R.drawable.ic_type_contact)
                WidgetConfigStore.saveContact(this, appWidgetId, contact)
                finishWithResult()
            }
        }
    }

    private fun addRow(container: LinearLayout, label: String, iconRes: Int, onClick: () -> Unit) {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.ellipsize = TextUtils.TruncateAt.END
        button.maxLines = 1
        button.setTextColor(ContextCompat.getColor(this, R.color.color_text))
        button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        button.setBackgroundResource(R.drawable.bg_saved_row)
        val padding = (14 * resources.displayMetrics.density).toInt()
        button.setPadding(padding, 0, padding, 0)
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.size_text_buttons))
        button.minimumHeight = resources.getDimensionPixelSize(R.dimen.size_button_min_height)
        button.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.size_margin_horizontal_buttons) * 2
        val icon = ContextCompat.getDrawable(this, iconRes)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.color_text))
        button.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        button.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = resources.getDimensionPixelSize(R.dimen.size_margin_vertical_buttons)
        }
        button.setOnClickListener { onClick() }
        container.addView(button)
    }

    private fun finishWithResult() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        SavedItemWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
