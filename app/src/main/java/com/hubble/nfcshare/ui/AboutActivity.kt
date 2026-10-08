package com.hubble.nfcshare.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.SavedItemsStore
import com.hubble.nfcshare.share.ShizukuWifi
import rikka.shizuku.Shizuku

// Settings / about: what the app does (and doesn't do) with data, why each
// permission is asked, then version + project link at the bottom.
class AboutActivity : BaseActivity() {

    private companion object {
        private const val GITHUB_URL = "https://github.com/hubble658"
        private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        private const val REQUEST_CODE_SHIZUKU = 1005
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_CODE_SHIZUKU) return@OnRequestPermissionResultListener
        runOnUiThread {
            updateShizukuStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) importNetworks() else toast(getString(R.string.error_shizuku_denied))
        }
    }

    private class InfoItem(val iconRes: Int, val titleRes: Int, val bodyRes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.button_about_back).setOnClickListener { finish() }
        findViewById<View>(R.id.button_shizuku_import).setOnClickListener { onShizukuImportClicked() }
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            // Shizuku not usable on this device; the status text explains why
        }

        fillGroup(
            findViewById(R.id.container_about_privacy),
            listOf(
                InfoItem(R.drawable.ic_info_lock, R.string.about_privacy_internet_title, R.string.about_privacy_internet_body),
                InfoItem(R.drawable.ic_info_nfc, R.string.about_privacy_nfc_title, R.string.about_privacy_nfc_body),
            ),
        )
        fillGroup(
            findViewById(R.id.container_about_permissions),
            listOf(
                InfoItem(R.drawable.ic_type_maps, R.string.about_permission_location_title, R.string.about_permission_location_body),
                InfoItem(R.drawable.ic_type_contact, R.string.about_permission_contacts_title, R.string.about_permission_contacts_body),
                InfoItem(R.drawable.ic_info_nfc_logo, R.string.about_permission_nfc_title, R.string.about_permission_nfc_body),
                InfoItem(R.drawable.ic_type_wifi, R.string.about_permission_shizuku_title, R.string.about_permission_shizuku_body),
            ),
        )

        @Suppress("DEPRECATION")
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.text_about_version).text = getString(R.string.about_version, versionName)
        findViewById<View>(R.id.button_about_github).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        }
    }

    override fun onResume() {
        super.onResume()
        // Shizuku may have been installed/started in the meantime
        updateShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
        }
    }

    // --- WiFi passwords via Shizuku (optional, one-time import) ---

    private fun updateShizukuStatus() {
        findViewById<TextView>(R.id.text_shizuku_status).setText(
            when {
                !ShizukuWifi.isSupportedOs() -> R.string.shizuku_status_unsupported
                !ShizukuWifi.isInstalled(this) -> R.string.shizuku_status_not_installed
                !ShizukuWifi.isRunning() -> R.string.shizuku_status_not_running
                !ShizukuWifi.hasPermission() -> R.string.shizuku_status_no_permission
                else -> R.string.shizuku_status_ready
            },
        )
    }

    private fun onShizukuImportClicked() {
        when {
            !ShizukuWifi.isSupportedOs() -> toast(getString(R.string.shizuku_status_unsupported))
            !ShizukuWifi.isInstalled(this) -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL)))
            !ShizukuWifi.isRunning() -> packageManager.getLaunchIntentForPackage(ShizukuWifi.SHIZUKU_PACKAGE)?.let { startActivity(it) }
            !ShizukuWifi.hasPermission() -> Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            else -> importNetworks()
        }
    }

    private fun importNetworks() {
        Thread {
            try {
                val networks = ShizukuWifi.fetchSavedNetworks()
                runOnUiThread {
                    val added = SavedItemsStore.importNetworks(this, networks)
                    toast(getString(R.string.toast_shizuku_imported, networks.size, added))
                }
            } catch (e: Throwable) {
                val reason = (e.cause ?: e).let { it.message ?: it.javaClass.simpleName }
                runOnUiThread { toast(getString(R.string.error_shizuku_failed, reason)) }
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // One row per item: tinted icon, bold title, secondary body; hairline between rows.
    private fun fillGroup(container: LinearLayout, items: List<InfoItem>) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                container.addView(
                    View(this).apply { setBackgroundColor(ContextCompat.getColor(this@AboutActivity, R.color.color_outline)) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(56) },
                )
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(dp(16), dp(16), dp(16), dp(16))

            val icon = ImageView(this)
            icon.setImageResource(item.iconRes)
            icon.setColorFilter(ContextCompat.getColor(this, R.color.color_primary))
            row.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)).also {
                it.marginEnd = dp(16)
                it.gravity = Gravity.TOP
            })

            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL

            val title = TextView(this)
            title.setText(item.titleRes)
            title.setTextColor(ContextCompat.getColor(this, R.color.color_text))
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.size_text_buttons))
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            texts.addView(title)

            val body = TextView(this)
            body.setText(item.bodyRes)
            body.setTextColor(ContextCompat.getColor(this, R.color.color_section_title))
            body.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.size_text_body))
            body.setLineSpacing(dp(3).toFloat(), 1f)
            texts.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(4)
            })

            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(row)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
