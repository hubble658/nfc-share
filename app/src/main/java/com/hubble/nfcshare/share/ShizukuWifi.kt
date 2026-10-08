package com.hubble.nfcshare.share

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

// Optional "ADB mode": Android never hands saved WiFi passwords to normal apps, but
// since Android 11 the ADB shell user may call WifiManager's privileged
// getPrivilegedConfiguredNetworks(). Shizuku (started once over wireless debugging)
// runs as that shell user and lets us make the binder call through it. Same approach
// as the open-source WiFiList app (github.com/zacharee/WiFiList).
object ShizukuWifi {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isSupportedOs() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isRunning(): Boolean = try {
        isSupportedOs() && Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    @SuppressLint("PrivateApi", "NewApi")
    @Suppress("DEPRECATION")
    fun fetchSavedNetworks(): List<SavedItemsStore.SavedWifiNetwork> {
        HiddenApiBypass.addHiddenApiExemptions("L")

        val base = Class.forName("android.net.wifi.IWifiManager")
        val stub = Class.forName("android.net.wifi.IWifiManager\$Stub")
        val wifiManager = stub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.WIFI_SERVICE)))

        val user = when (Shizuku.getUid()) {
            0 -> "root"
            1000 -> "system"
            else -> "shell"
        }
        val pkg = "com.android.shell"

        val result = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            val attribution = AttributionSource::class.java.getConstructor(
                Int::class.java, String::class.java, String::class.java, Set::class.java, AttributionSource::class.java,
            ).newInstance(Shizuku.getUid(), pkg, pkg, null as Set<String>?, null)
            base.getMethod("getPrivilegedConfiguredNetworks", String::class.java, String::class.java, Bundle::class.java)
                .invoke(wifiManager, user, pkg, Bundle().apply { putParcelable("EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE", attribution) })
        } else {
            try {
                base.getMethod("getPrivilegedConfiguredNetworks", String::class.java, String::class.java)
                    .invoke(wifiManager, user, pkg)
            } catch (e: NoSuchMethodException) {
                base.getMethod("getPrivilegedConfiguredNetworks", String::class.java, String::class.java, Bundle::class.java)
                    .invoke(wifiManager, user, pkg, null)
            }
        }

        @Suppress("UNCHECKED_CAST")
        val configs = result?.let { it.javaClass.getMethod("getList").invoke(it) as List<WifiConfiguration> } ?: emptyList()

        return configs.mapNotNull { config ->
            val ssid = config.SSID?.removeSurrounding("\"")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val psk = config.preSharedKey?.takeIf { it.isNotEmpty() && it != "*" }?.removeSurrounding("\"")
            val isOpen = config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE) &&
                config.wepKeys.orEmpty().all { it == null }
            when {
                psk != null -> SavedItemsStore.SavedWifiNetwork(ssid, psk, false)
                isOpen -> SavedItemsStore.SavedWifiNetwork(ssid, "", true)
                else -> null // WEP / enterprise: can't be shared via a WSC NFC record anyway
            }
        }.distinctBy { it.ssid }
    }
}
