package com.hubble.nfcshare.share

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.luigivampa92.ndefemulation.NdefEmulation
import com.luigivampa92.ndefemulation.ndef.NdefData
import com.hubble.nfcshare.widget.WidgetUpdater

// Tracks not just *what* is currently emulated (that's NdefEmulation's job) but
// *which named source* asked for it — a saved link, a saved network, the clipboard
// widget, or a one-off manual share. Widgets compare this key against their own
// configured key to know whether they're the one currently "on", so whichever
// source was tapped last wins and every widget instance stays in sync with it.
//
// Every share is also time-limited (user-selectable, 20 s by default): the NFC
// service itself refuses to answer once the deadline passes, and an alarm clears
// the state afterwards so widgets/app flip back to idle even if nothing is open.
object ShareStore {

    const val KEY_MANUAL = "manual"
    const val KEY_CLIPBOARD = "clipboard"

    // 0 = no limit
    val DURATION_OPTIONS_SECONDS = intArrayOf(20, 60, 300, 0)
    private const val DEFAULT_DURATION_SECONDS = 20

    private const val PREFS = "share_state"
    private const val KEY_ACTIVE = "active_key"
    private const val KEY_DURATION = "duration_seconds"
    private const val KEY_STARTED_AT = "started_at"

    fun keyForLink(raw: String) = "link:$raw"
    fun keyForWifi(ssid: String) = "wifi:$ssid"
    fun keyForContact(contact: SavedItemsStore.SavedContact) = "contact:${contact.first}|${contact.last}|${contact.phone}|${contact.email}"

    // The library's HCE service; addressed by name since the class is internal to it
    const val HCE_SERVICE_CLASS = "com.luigivampa92.ndefemulation.hce.NfcType4TagNdefEmulationService"

    fun hceService(context: Context) = ComponentName(context.packageName, HCE_SERVICE_CLASS)

    // The NFC service is only registered with the system while something is being
    // shared. Idle, it would still claim the NDEF tag AID: a reader phone then shows
    // "empty tag", and it collides with other apps/system services claiming the same
    // AID (Android shows a "which app?" chooser that makes the read time out).
    fun syncServiceEnabled(context: Context) {
        val emulation = NdefEmulation(context)
        val shouldBeEnabled = emulation.currentEmulatedNdefData != null && !emulation.isExpired
        val wanted = if (shouldBeEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val packageManager = context.packageManager
        try {
            if (packageManager.getComponentEnabledSetting(hceService(context)) != wanted) {
                packageManager.setComponentEnabledSetting(hceService(context), wanted, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
        }
    }

    fun setActive(context: Context, key: String, data: NdefData) {
        val emulation = NdefEmulation(context)
        emulation.currentEmulatedNdefData = data
        syncServiceEnabled(context)
        prefs(context).edit()
            .putString(KEY_ACTIVE, key)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
        applyDeadline(context, emulation)
        WidgetUpdater.updateAll(context)
    }

    fun clearActive(context: Context) {
        NdefEmulation(context).currentEmulatedNdefData = null
        syncServiceEnabled(context)
        prefs(context).edit().remove(KEY_ACTIVE).remove(KEY_STARTED_AT).apply()
        cancelAlarm(context)
        WidgetUpdater.updateAll(context)
    }

    // Stops the share if its time is up. Returns true if it did.
    fun expireIfNeeded(context: Context): Boolean {
        val emulation = NdefEmulation(context)
        val expired = emulation.isExpired ||
            (emulation.currentEmulatedNdefData == null && prefs(context).contains(KEY_ACTIVE))
        if (expired) clearActive(context)
        return expired
    }

    fun activeKey(context: Context): String? {
        if (NdefEmulation(context).isExpired) return null
        return prefs(context).getString(KEY_ACTIVE, null)
    }

    // Wall-clock deadline of the current share, 0 when unlimited or nothing is shared.
    fun expiresAt(context: Context): Long = NdefEmulation(context).expiresAtMillis

    fun startedAt(context: Context): Long = prefs(context).getLong(KEY_STARTED_AT, 0L)

    fun readCount(context: Context): Int = NdefEmulation(context).readCount

    fun durationSeconds(context: Context): Int = prefs(context).getInt(KEY_DURATION, DEFAULT_DURATION_SECONDS)

    // Changing the duration while something is being shared restarts its countdown.
    fun setDurationSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_DURATION, seconds).apply()
        val emulation = NdefEmulation(context)
        if (emulation.currentEmulatedNdefData != null && !emulation.isExpired) {
            prefs(context).edit().putLong(KEY_STARTED_AT, System.currentTimeMillis()).apply()
            applyDeadline(context, emulation)
            WidgetUpdater.updateAll(context)
        }
    }

    private fun applyDeadline(context: Context, emulation: NdefEmulation) {
        val seconds = durationSeconds(context)
        if (seconds <= 0) {
            emulation.expiresAtMillis = 0L
            cancelAlarm(context)
            return
        }
        val deadline = System.currentTimeMillis() + seconds * 1000L
        emulation.expiresAtMillis = deadline
        scheduleAlarm(context, deadline)
    }

    private fun alarmIntent(context: Context): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, Intent(context, ShareExpiryReceiver::class.java), flags)
    }

    private fun scheduleAlarm(context: Context, deadline: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        when {
            canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pendingIntent)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pendingIntent)
            else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, deadline, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(context))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
