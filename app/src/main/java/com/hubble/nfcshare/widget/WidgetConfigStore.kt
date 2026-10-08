package com.hubble.nfcshare.widget

import android.content.Context
import com.hubble.nfcshare.share.SavedItemsStore

// Per-widget-instance configuration: which saved item (by key/label/icon, plus
// enough raw data to rebuild its NdefData at tap time) this specific widget id points to.
object WidgetConfigStore {

    private const val PREFS = "widget_config"

    fun save(context: Context, appWidgetId: Int, key: String, label: String, iconRes: Int) {
        prefs(context).edit()
            .putString("key_$appWidgetId", key)
            .putString("label_$appWidgetId", label)
            .putInt("icon_$appWidgetId", iconRes)
            .apply()
    }

    fun key(context: Context, appWidgetId: Int): String? = prefs(context).getString("key_$appWidgetId", null)
    fun label(context: Context, appWidgetId: Int): String? = prefs(context).getString("label_$appWidgetId", null)
    fun iconRes(context: Context, appWidgetId: Int): Int = prefs(context).getInt("icon_$appWidgetId", 0)

    fun saveLinkRaw(context: Context, appWidgetId: Int, raw: String) {
        prefs(context).edit().putString("raw_$appWidgetId", raw).apply()
    }

    fun linkRaw(context: Context, appWidgetId: Int): String? = prefs(context).getString("raw_$appWidgetId", null)

    fun saveWifi(context: Context, appWidgetId: Int, ssid: String, password: String, isOpen: Boolean) {
        prefs(context).edit()
            .putString("wifi_ssid_$appWidgetId", ssid)
            .putString("wifi_pass_$appWidgetId", password)
            .putBoolean("wifi_open_$appWidgetId", isOpen)
            .apply()
    }

    fun wifiSsid(context: Context, appWidgetId: Int): String? = prefs(context).getString("wifi_ssid_$appWidgetId", null)
    fun wifiPassword(context: Context, appWidgetId: Int): String = prefs(context).getString("wifi_pass_$appWidgetId", "") ?: ""
    fun wifiOpen(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean("wifi_open_$appWidgetId", false)

    fun saveContact(context: Context, appWidgetId: Int, contact: SavedItemsStore.SavedContact) {
        prefs(context).edit()
            .putString("contact_first_$appWidgetId", contact.first)
            .putString("contact_last_$appWidgetId", contact.last)
            .putString("contact_phone_$appWidgetId", contact.phone)
            .putString("contact_email_$appWidgetId", contact.email)
            .apply()
    }

    fun contact(context: Context, appWidgetId: Int): SavedItemsStore.SavedContact? {
        val prefs = prefs(context)
        val first = prefs.getString("contact_first_$appWidgetId", null) ?: return null
        return SavedItemsStore.SavedContact(
            first,
            prefs.getString("contact_last_$appWidgetId", "") ?: "",
            prefs.getString("contact_phone_$appWidgetId", "") ?: "",
            prefs.getString("contact_email_$appWidgetId", "") ?: "",
        )
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("key_$appWidgetId").remove("label_$appWidgetId").remove("icon_$appWidgetId")
            .remove("raw_$appWidgetId")
            .remove("wifi_ssid_$appWidgetId").remove("wifi_pass_$appWidgetId").remove("wifi_open_$appWidgetId")
            .remove("contact_first_$appWidgetId").remove("contact_last_$appWidgetId")
            .remove("contact_phone_$appWidgetId").remove("contact_email_$appWidgetId")
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
