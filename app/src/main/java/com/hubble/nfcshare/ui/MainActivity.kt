package com.hubble.nfcshare.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.luigivampa92.ndefemulation.NdefEmulation
import com.luigivampa92.ndefemulation.ndef.ContactNdefData
import com.luigivampa92.ndefemulation.ndef.LocationNdefData
import com.luigivampa92.ndefemulation.ndef.NdefData
import com.luigivampa92.ndefemulation.ndef.TextNdefData
import com.luigivampa92.ndefemulation.ndef.UriNdefData
import com.luigivampa92.ndefemulation.ndef.WifiNetworkNdefData
import com.luigivampa92.ndefemulation.ndef.WifiNetworkNdefDataProtectionType
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.ClipboardCopy
import com.hubble.nfcshare.share.LinkResolver
import com.hubble.nfcshare.share.NfcStatus
import com.hubble.nfcshare.share.SavedItemsStore
import com.hubble.nfcshare.share.ShareStore
import com.hubble.nfcshare.share.ShizukuWifi
import kotlin.math.max

class MainActivity : BaseActivity() {

    private companion object {
        private const val TAB_LINK = 0
        private const val TAB_WIFI = 1
        private const val TAB_CONTACT = 2

        private const val MAX_WIFI_SSID_BYTES = 32
        private const val MIN_WIFI_PASSWORD_BYTES = 8
        private const val MAX_WIFI_PASSWORD_BYTES = 64
        private const val MAX_NAME_LENGTH = 32
        private const val MAX_PHONE_LENGTH = 24
        private const val MAX_EMAIL_LENGTH = 48
        private const val MAX_SAVED_LINK_LABEL_LENGTH = 44

        private const val REQUEST_CODE_WIFI_LOCATION_PERMISSION = 1001
        private const val REQUEST_CODE_CONTACTS_PERMISSION = 1003
        private const val REQUEST_CODE_PICK_CONTACT = 1004

        private const val TICK_MILLIS = 250L
        private const val PROGRESS_STEPS = 20
        private const val PREFERRED_CLAIM_RETRY_MILLIS = 400L
        private const val PREFERRED_CLAIM_MAX_ATTEMPTS = 10
    }

    private lateinit var ndefEmulation: NdefEmulation

    private lateinit var cardStatus: View
    private lateinit var viewStatusDot: View
    private lateinit var viewStatusPulse: View
    private lateinit var textStatusLabel: TextView
    private lateinit var textStatusTimer: TextView
    private lateinit var textStatusValue: TextView
    private lateinit var textStatusReads: TextView
    private lateinit var progressStatus: ProgressBar
    private lateinit var durationButtons: List<Pair<Int, Button>>
    private lateinit var textFieldError: TextView

    private lateinit var tabButtons: List<Button>
    private lateinit var tabGroups: List<View>
    private var currentTab = TAB_LINK

    private lateinit var editLink: EditText
    private lateinit var textLinkHint: View
    private lateinit var labelSavedLinks: View
    private lateinit var listSavedLinks: LinearLayout

    private lateinit var labelSavedNetworks: View
    private lateinit var listSavedNetworks: LinearLayout
    private lateinit var editWifiSsid: EditText
    private lateinit var editWifiPassword: EditText
    private lateinit var groupWifiPassword: View
    private lateinit var switchWifiOpen: Switch

    private lateinit var editContactFirst: EditText
    private lateinit var editContactLast: EditText
    private lateinit var editContactPhone: EditText
    private lateinit var editContactEmail: EditText
    private lateinit var labelSavedContacts: View
    private lateinit var listSavedContacts: LinearLayout

    // Snapshot of the current share, refreshed by updateStatus() and read by the ticker
    // Saved item currently loaded into the fields via "Düzenle"; replaced on the next share
    private var editingLink: String? = null
    private var editingNetworkSsid: String? = null
    private var editingContact: SavedItemsStore.SavedContact? = null

    private var isSharing = false
    private var isResumed = false
    private var shareStartedAt = 0L
    private var shareDeadline = 0L
    private var lastReadCount = 0

    private var pulseAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    private val readReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateStatus()
            flashStatusCard()
            toast(getString(R.string.toast_read))
        }
    }

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateNfcBanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpEdgeToEdge()

        ndefEmulation = NdefEmulation(this)

        cardStatus = findViewById(R.id.card_status)
        viewStatusDot = findViewById(R.id.view_status_dot)
        viewStatusPulse = findViewById(R.id.view_status_pulse)
        textStatusLabel = findViewById(R.id.text_status_label)
        textStatusTimer = findViewById(R.id.text_status_timer)
        textStatusValue = findViewById(R.id.text_status_value)
        textStatusReads = findViewById(R.id.text_status_reads)
        progressStatus = findViewById(R.id.progress_status)
        textFieldError = findViewById(R.id.text_field_error)
        buildDurationButtons()

        tabGroups = listOf(findViewById(R.id.group_link), findViewById(R.id.group_wifi), findViewById(R.id.group_contact))

        editLink = findViewById(R.id.edit_link)
        textLinkHint = findViewById(R.id.text_link_hint)
        labelSavedLinks = findViewById(R.id.label_saved_links)
        listSavedLinks = findViewById(R.id.list_saved_links)

        labelSavedNetworks = findViewById(R.id.label_saved_networks)
        listSavedNetworks = findViewById(R.id.list_saved_networks)
        editWifiSsid = findViewById(R.id.edit_wifi_ssid)
        groupWifiPassword = findViewById(R.id.group_wifi_password)
        editWifiPassword = findViewById(R.id.edit_wifi_password)
        switchWifiOpen = findViewById(R.id.switch_wifi_open)

        editContactFirst = findViewById(R.id.edit_contact_first)
        editContactLast = findViewById(R.id.edit_contact_last)
        editContactPhone = findViewById(R.id.edit_contact_phone)
        editContactEmail = findViewById(R.id.edit_contact_email)
        labelSavedContacts = findViewById(R.id.label_saved_contacts)
        listSavedContacts = findViewById(R.id.list_saved_contacts)

        switchWifiOpen.setOnCheckedChangeListener { _, isChecked ->
            groupWifiPassword.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        editLink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString()?.trim().orEmpty()
                val isPlainText = raw.isNotEmpty() && LinkResolver.classifyIcon(raw) == R.drawable.ic_type_text
                textLinkHint.visibility = if (isPlainText) View.VISIBLE else View.GONE
            }
        })

        val tabLink: Button = findViewById(R.id.tab_link)
        val tabWifi: Button = findViewById(R.id.tab_wifi)
        val tabContact: Button = findViewById(R.id.tab_contact)
        tabButtons = listOf(tabLink, tabWifi, tabContact)
        tabLink.setOnClickListener { selectTab(TAB_LINK) }
        tabWifi.setOnClickListener { selectTab(TAB_WIFI) }
        tabContact.setOnClickListener { selectTab(TAB_CONTACT) }
        selectTab(TAB_LINK)

        findViewById<View>(R.id.button_banner_nfc).setOnClickListener { openNfcSettings() }
        findViewById<View>(R.id.button_settings).setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        findViewById<View>(R.id.button_paste_link).setOnClickListener { pasteIntoLink() }
        findViewById<View>(R.id.button_use_current_wifi).setOnClickListener { useCurrentWifiSsid() }
        findViewById<View>(R.id.link_shizuku_settings).setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        findViewById<View>(R.id.button_pick_contact).setOnClickListener { pickContact() }
        findViewById<View>(R.id.button_share).setOnClickListener { handleShare(currentTab) }
        findViewById<View>(R.id.button_clear).setOnClickListener {
            ShareStore.clearActive(this)
            updateStatus()
            toast(getString(R.string.toast_cleared))
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        ContextCompat.registerReceiver(this, readReceiver, IntentFilter(NdefEmulation.ACTION_NDEF_READ), ContextCompat.RECEIVER_NOT_EXPORTED)
        // System broadcast: NFC toggled from quick settings while we're open
        ContextCompat.registerReceiver(this, nfcStateReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        updateNfcBanner()
        ShareStore.expireIfNeeded(this)
        updateStatus()
        claimPreferredService()
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
        handler.removeCallbacks(preferredServiceClaim)
        try {
            cardEmulation()?.unsetPreferredService(this)
        } catch (e: Exception) {
        }
        unregisterReceiver(readReceiver)
        unregisterReceiver(nfcStateReceiver)
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
    }

    // While the app is on screen, win AID conflicts (e.g. Samsung's built-in "Yerleşik
    // etiket" service) without the system's "which app?" chooser. The service is only
    // enabled while sharing and the NFC stack picks that up asynchronously, so a claim
    // made right after enabling can be rejected — retry for a few seconds.
    private var preferredClaimAttempts = 0
    private val preferredServiceClaim = object : Runnable {
        override fun run() {
            val claimed = try {
                cardEmulation()?.setPreferredService(this@MainActivity, ShareStore.hceService(this@MainActivity)) == true
            } catch (e: Exception) {
                false
            }
            if (!claimed && isSharing && ++preferredClaimAttempts < PREFERRED_CLAIM_MAX_ATTEMPTS) {
                handler.postDelayed(this, PREFERRED_CLAIM_RETRY_MILLIS)
            }
        }
    }

    private fun claimPreferredService() {
        handler.removeCallbacks(preferredServiceClaim)
        preferredClaimAttempts = 0
        handler.post(preferredServiceClaim)
    }

    private fun cardEmulation(): CardEmulation? = NfcAdapter.getDefaultAdapter(this)?.let { CardEmulation.getInstance(it) }

    // Draw behind the system bars and pad for them ourselves — including the keyboard,
    // so the pinned PAYLAŞ/DURDUR bar and the focused field always stay above it.
    private fun setUpEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root: View = findViewById(R.id.main)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = !isNight
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, max(systemBars.bottom, ime.bottom))
            insets
        }
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        tabButtons.forEachIndexed { index, button ->
            val active = index == tab
            button.setBackgroundResource(if (active) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            button.setTextColor(ContextCompat.getColor(this, if (active) R.color.color_on_primary else R.color.color_text))
        }
        tabGroups.forEachIndexed { index, view -> view.visibility = if (index == tab) View.VISIBLE else View.GONE }
        textFieldError.visibility = View.GONE
    }

    // --- Status card: live countdown, pulse and read counter ---

    private fun buildDurationButtons() {
        val container: LinearLayout = findViewById(R.id.container_duration)
        val gap = resources.getDimensionPixelSize(R.dimen.size_margin_horizontal_buttons) / 2
        durationButtons = ShareStore.DURATION_OPTIONS_SECONDS.map { seconds ->
            val button = Button(this)
            button.text = when {
                seconds <= 0 -> getString(R.string.duration_unlimited)
                seconds < 60 -> getString(R.string.duration_seconds, seconds)
                else -> getString(R.string.duration_minutes, seconds / 60)
            }
            button.isAllCaps = false
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            button.minHeight = 0
            button.minimumHeight = dp(40)
            button.minWidth = 0
            button.minimumWidth = 0
            button.setPadding(0, 0, 0, 0)
            button.layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).also {
                it.marginStart = gap
                it.marginEnd = gap
            }
            button.setOnClickListener {
                ShareStore.setDurationSeconds(this, seconds)
                updateStatus()
            }
            container.addView(button)
            seconds to button
        }
    }

    private fun updateStatus() {
        val data = ndefEmulation.currentEmulatedNdefData.takeUnless { ndefEmulation.isExpired }
        isSharing = data != null
        shareStartedAt = ShareStore.startedAt(this)
        shareDeadline = if (isSharing) ShareStore.expiresAt(this) else 0L

        cardStatus.setBackgroundResource(if (isSharing) R.drawable.bg_card_active else R.drawable.bg_card)
        viewStatusDot.setBackgroundResource(if (isSharing) R.drawable.dot_status_active else R.drawable.dot_status_inactive)
        textStatusLabel.setText(if (isSharing) R.string.status_label_active else R.string.status_label_idle)
        textStatusLabel.setTextColor(ContextCompat.getColor(this, if (isSharing) R.color.color_status_active else R.color.color_section_title))
        textStatusValue.text = describe(data)
        textStatusTimer.visibility = if (isSharing) View.VISIBLE else View.GONE
        progressStatus.visibility = if (isSharing && shareDeadline > 0) View.VISIBLE else View.GONE

        val reads = if (isSharing) ShareStore.readCount(this) else 0
        lastReadCount = reads
        textStatusReads.visibility = if (isSharing) View.VISIBLE else View.GONE
        findViewById<View>(R.id.text_status_text_hint).visibility = if (data is TextNdefData) View.VISIBLE else View.GONE
        textStatusReads.text = if (reads > 0) getString(R.string.status_reads_count, reads) else getString(R.string.status_reads_none)
        textStatusReads.setTextColor(ContextCompat.getColor(this, if (reads > 0) R.color.color_status_active else R.color.color_section_title))

        val selectedDuration = ShareStore.durationSeconds(this)
        durationButtons.forEach { (seconds, button) ->
            val selected = seconds == selectedDuration
            button.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            button.setTextColor(ContextCompat.getColor(this, if (selected) R.color.color_on_primary else R.color.color_text))
        }

        if (isSharing) startPulse() else stopPulse()
        // The countdown ticker only runs while something is actually being shared
        handler.removeCallbacks(ticker)
        if (isSharing && isResumed) handler.post(ticker)
        updateTimer()
        refreshSavedLists()
    }

    private fun tick() {
        if (isSharing && shareDeadline > 0 && System.currentTimeMillis() >= shareDeadline) {
            ShareStore.expireIfNeeded(this)
            updateStatus()
            toast(getString(R.string.toast_expired))
            return
        }
        updateTimer()
    }

    private fun updateTimer() {
        if (!isSharing) return
        if (shareDeadline <= 0) {
            textStatusTimer.text = getString(R.string.status_timer_unlimited)
            return
        }
        val remaining = max(0L, shareDeadline - System.currentTimeMillis())
        val totalSeconds = (remaining + 999) / 1000
        textStatusTimer.text = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
        // Drops in visible steps (20 of them) instead of creeping down every frame
        val total = max(1L, shareDeadline - shareStartedAt)
        val step = kotlin.math.ceil(remaining.toDouble() * PROGRESS_STEPS / total).toInt()
        progressStatus.progress = step * progressStatus.max / PROGRESS_STEPS
    }

    private fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            viewStatusPulse,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2.4f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2.4f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f),
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        viewStatusPulse.alpha = 0f
    }

    private fun flashStatusCard() {
        cardStatus.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).withEndAction {
            cardStatus.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
    }

    private fun describe(data: NdefData?): String = when (data) {
        null -> getString(R.string.status_value_empty)
        is UriNdefData -> getString(R.string.status_value_uri, data.uri)
        is TextNdefData -> getString(R.string.status_value_text, data.text)
        is LocationNdefData -> getString(R.string.status_value_geo, data.latitude.toString(), data.longitude.toString())
        is WifiNetworkNdefData -> getString(R.string.status_value_wifi, data.wifiName)
        is ContactNdefData -> getString(R.string.status_value_contact, "${data.firstName} ${data.lastName.orEmpty()}".trim())
        else -> data.toString()
    }

    // --- Saved lists: everything shared is saved automatically, in every tab ---

    private fun refreshSavedLists() {
        refreshSavedLinksList()
        refreshSavedNetworksList()
        refreshSavedContactsList()
    }

    private fun tintedIcon(iconRes: Int, colorRes: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(this, iconRes)?.mutate()
        drawable?.setTint(ContextCompat.getColor(this, colorRes))
        return drawable
    }

    private fun addSavedRow(
        container: LinearLayout,
        label: String,
        iconRes: Int,
        key: String,
        copyText: String,
        onClick: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val isActive = isSharing && key == ShareStore.activeKey(this)

        // Row = tappable label (share) + trash button (delete, with confirmation)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(if (isActive) R.drawable.bg_saved_row_active else R.drawable.bg_saved_row)
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = resources.getDimensionPixelSize(R.dimen.size_margin_vertical_buttons)
        }

        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.ellipsize = TextUtils.TruncateAt.END
        button.maxLines = 1
        button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        button.setTextColor(ContextCompat.getColor(this, R.color.color_text))
        button.setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackground))
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.size_text_buttons))
        button.minimumHeight = resources.getDimensionPixelSize(R.dimen.size_button_min_height)
        button.setPadding(dp(14), 0, dp(8), 0)
        button.compoundDrawablePadding = dp(12)
        button.setCompoundDrawablesWithIntrinsicBounds(
            tintedIcon(iconRes, if (isActive) R.color.color_status_active else R.color.color_text), null, null, null,
        )
        button.setOnClickListener { onClick() }
        button.setOnLongClickListener {
            showSavedItemMenu(label, copyText, onEdit)
            true
        }
        row.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val trash = ImageButton(this)
        trash.setImageResource(R.drawable.ic_action_delete)
        trash.setColorFilter(ContextCompat.getColor(this, R.color.color_section_title))
        trash.setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackgroundBorderless))
        trash.contentDescription = getString(R.string.action_delete)
        trash.setOnClickListener { confirmDelete(label, onDelete) }
        val touch = resources.getDimensionPixelSize(R.dimen.size_button_min_height)
        row.addView(trash, LinearLayout.LayoutParams(touch, touch).also { it.marginEnd = dp(4) })

        container.addView(row)
    }

    private fun themeDrawable(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.resourceId
    }

    private fun confirmDelete(label: String, onDelete: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, label))
            .setPositiveButton(R.string.action_delete) { _, _ -> onDelete() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // Long-press menu: edit / copy. Deleting has its own trash button on the row.
    private fun showSavedItemMenu(title: String, copyText: String, onEdit: () -> Unit) {
        val actions = arrayOf(getString(R.string.action_edit), getString(R.string.action_copy))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        onEdit()
                        findViewById<ScrollView>(R.id.scroll_main).smoothScrollTo(0, tabGroups[currentTab].top)
                        toast(getString(R.string.toast_editing))
                    }
                    1 -> {
                        ClipboardCopy.copy(this, title, copyText)
                    }
                }
            }
            .show()
    }

    private fun startShare(key: String, data: NdefData) {
        textFieldError.visibility = View.GONE
        ShareStore.setActive(this, key, data)
        updateStatus()
        claimPreferredService()
        val nfc = NfcStatus.state(this)
        if (nfc == NfcStatus.State.READY) {
            toast(getString(R.string.toast_shared))
        } else {
            toast(getString(NfcStatus.messageRes(nfc)))
            findViewById<View>(R.id.banner_nfc).let { banner ->
                banner.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).withEndAction {
                    banner.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
            }
        }
    }

    private fun updateNfcBanner() {
        val state = NfcStatus.state(this)
        findViewById<View>(R.id.banner_nfc).visibility = if (state == NfcStatus.State.READY) View.GONE else View.VISIBLE
        if (state != NfcStatus.State.READY) findViewById<TextView>(R.id.text_banner_nfc).setText(NfcStatus.messageRes(state))
        // Nothing to switch on when the hardware just isn't there
        findViewById<View>(R.id.button_banner_nfc).visibility = if (state == NfcStatus.State.OFF) View.VISIBLE else View.GONE
    }

    private fun openNfcSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun refreshSavedLinksList() {
        listSavedLinks.removeAllViews()
        val links = SavedItemsStore.loadLinks(this)
        labelSavedLinks.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        links.forEach { raw ->
            val label = if (raw.length > MAX_SAVED_LINK_LABEL_LENGTH) raw.take(MAX_SAVED_LINK_LABEL_LENGTH) + "…" else raw
            addSavedRow(
                listSavedLinks, label, LinkResolver.classifyIcon(raw), ShareStore.keyForLink(raw), raw,
                onClick = {
                    val resolved = LinkResolver.resolveLinkShare(raw)
                    if (resolved != null) startShare(ShareStore.keyForLink(raw), resolved) else showError(getString(R.string.error_invalid_url))
                },
                onEdit = {
                    editingLink = raw
                    editLink.setText(raw)
                    editLink.requestFocus()
                    editLink.setSelection(editLink.text.length)
                },
                onDelete = {
                    SavedItemsStore.removeLink(this, raw)
                    refreshSavedLinksList()
                    toast(getString(R.string.toast_link_removed))
                },
            )
        }
    }

    private fun pasteIntoLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            toast(getString(R.string.error_clipboard_empty))
            return
        }
        editLink.setText(text)
        editLink.setSelection(editLink.text.length)
    }

    // --- WiFi ---

    private fun wifiData(network: SavedItemsStore.SavedWifiNetwork) = WifiNetworkNdefData(
        network.ssid,
        if (network.isOpen) WifiNetworkNdefDataProtectionType.OPEN else WifiNetworkNdefDataProtectionType.PASSWORD,
        // The library force-unwraps the password even for open networks
        if (network.isOpen) "" else network.password,
    )

    private fun refreshSavedNetworksList() {
        listSavedNetworks.removeAllViews()
        val networks = SavedItemsStore.loadNetworks(this)
        labelSavedNetworks.visibility = if (networks.isEmpty()) View.GONE else View.VISIBLE
        networks.forEach { network ->
            val label = network.ssid + if (network.isOpen) getString(R.string.label_wifi_open_suffix) else ""
            addSavedRow(
                listSavedNetworks, label, R.drawable.ic_type_wifi, ShareStore.keyForWifi(network.ssid),
                if (network.isOpen) network.ssid else network.password,
                onClick = { startShare(ShareStore.keyForWifi(network.ssid), wifiData(network)) },
                onEdit = {
                    editingNetworkSsid = network.ssid
                    fillWifiFields(network)
                    editWifiPassword.requestFocus()
                },
                onDelete = {
                    SavedItemsStore.removeNetwork(this, network.ssid)
                    refreshSavedNetworksList()
                    toast(getString(R.string.toast_network_removed))
                },
            )
        }
    }

    private fun useCurrentWifiSsid() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_WIFI_LOCATION_PERMISSION)
            return
        }
        fillCurrentWifiSsid()
    }

    @Suppress("DEPRECATION")
    private fun fillCurrentWifiSsid() {
        if (!isLocationEnabledCompat()) {
            toast(getString(R.string.error_location_services_off))
            return
        }
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
            toast(getString(R.string.error_wifi_not_connected))
            return
        }
        editWifiSsid.setText(ssid)
        val saved = SavedItemsStore.loadNetworks(this).find { it.ssid == ssid }
        if (saved != null) {
            fillWifiFields(saved)
        } else if (ShizukuWifi.hasPermission()) {
            importNetworksViaShizuku(fillSsid = ssid)
        }
    }

    private fun fillWifiFields(network: SavedItemsStore.SavedWifiNetwork) {
        editWifiSsid.setText(network.ssid)
        switchWifiOpen.isChecked = network.isOpen
        editWifiPassword.setText(network.password)
    }

    private fun isLocationEnabledCompat(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isLocationEnabled
        }
        return try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Settings.SettingNotFoundException) {
            true
        }
    }

    // --- WiFi via Shizuku: the import button lives in Settings; here it only fills in
    // the connected network's password once Shizuku has been granted ---

    private fun importNetworksViaShizuku(fillSsid: String) {
        Thread {
            try {
                val networks = ShizukuWifi.fetchSavedNetworks()
                runOnUiThread {
                    SavedItemsStore.importNetworks(this, networks)
                    refreshSavedNetworksList()
                    networks.find { it.ssid == fillSsid }?.let { fillWifiFields(it) }
                }
            } catch (e: Throwable) {
                val reason = (e.cause ?: e).let { it.message ?: it.javaClass.simpleName }
                runOnUiThread { toast(getString(R.string.error_shizuku_failed, reason)) }
            }
        }.start()
    }

    // --- Contact ---

    private fun refreshSavedContactsList() {
        listSavedContacts.removeAllViews()
        val contacts = SavedItemsStore.loadContacts(this)
        labelSavedContacts.visibility = if (contacts.isEmpty()) View.GONE else View.VISIBLE
        contacts.forEach { contact ->
            val label = listOf(contact.displayName, contact.phone.ifBlank { contact.email }).filter { it.isNotBlank() }.joinToString(" · ")
            addSavedRow(
                listSavedContacts, label, R.drawable.ic_type_contact, ShareStore.keyForContact(contact),
                contact.phone.ifBlank { contact.email.ifBlank { contact.displayName } },
                onClick = { startShare(ShareStore.keyForContact(contact), contact.toNdefData()) },
                onEdit = {
                    editingContact = contact
                    editContactFirst.setText(contact.first)
                    editContactLast.setText(contact.last)
                    editContactPhone.setText(contact.phone)
                    editContactEmail.setText(contact.email)
                    editContactFirst.requestFocus()
                },
                onDelete = {
                    SavedItemsStore.removeContact(this, contact)
                    refreshSavedContactsList()
                    toast(getString(R.string.toast_contact_removed))
                },
            )
        }
    }

    private fun pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CODE_CONTACTS_PERMISSION)
            return
        }
        launchContactPicker()
    }

    private fun launchContactPicker() {
        startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), REQUEST_CODE_PICK_CONTACT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_CONTACT && resultCode == RESULT_OK) {
            data?.data?.let { fillFromPickedContact(it) }
        }
    }

    private fun fillFromPickedContact(contactUri: Uri) {
        val contactId = contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return

        var given = ""
        var family = ""
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use {
            if (it.moveToFirst()) {
                given = it.getString(0) ?: ""
                family = it.getString(1) ?: ""
            }
        }
        if (given.isBlank() && family.isBlank()) {
            contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) given = it.getString(0) ?: ""
            }
        }

        var phone = ""
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use {
            if (it.moveToFirst()) phone = it.getString(0) ?: ""
        }

        var email = ""
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use {
            if (it.moveToFirst()) email = it.getString(0) ?: ""
        }

        editContactFirst.setText(given.take(MAX_NAME_LENGTH))
        editContactLast.setText(family.take(MAX_NAME_LENGTH))
        editContactPhone.setText(normalizePhone(phone).take(MAX_PHONE_LENGTH))
        editContactEmail.setText(email.take(MAX_EMAIL_LENGTH))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQUEST_CODE_WIFI_LOCATION_PERMISSION -> {
                if (granted) fillCurrentWifiSsid() else toast(getString(R.string.error_wifi_permission_denied))
            }
            REQUEST_CODE_CONTACTS_PERMISSION -> {
                if (granted) launchContactPicker() else toast(getString(R.string.error_contacts_permission_denied))
            }
        }
    }

    // --- Share button ---

    private fun handleShare(tab: Int) {
        textFieldError.visibility = View.GONE

        when (tab) {
            TAB_LINK -> {
                val raw = editLink.text.toString().trim()
                if (raw.isEmpty()) {
                    showError(getString(R.string.error_empty_url))
                    return
                }
                val resolved = LinkResolver.resolveLinkShare(raw)
                if (resolved == null) {
                    showError(getString(R.string.error_invalid_url))
                    return
                }
                editingLink?.takeIf { it != raw }?.let { SavedItemsStore.removeLink(this, it) }
                editingLink = null
                SavedItemsStore.saveLink(this, raw)
                editLink.setText("")
                hideKeyboard()
                startShare(ShareStore.keyForLink(raw), resolved)
            }
            TAB_WIFI -> {
                val ssid = editWifiSsid.text.toString()
                val isOpen = switchWifiOpen.isChecked
                val password = editWifiPassword.text.toString()
                if (ssid.isEmpty() || ssid.toByteArray().size > MAX_WIFI_SSID_BYTES) {
                    showError(getString(R.string.error_empty_ssid))
                    return
                }
                if (!isOpen && password.toByteArray().size !in MIN_WIFI_PASSWORD_BYTES..MAX_WIFI_PASSWORD_BYTES) {
                    showError(getString(R.string.error_short_password))
                    return
                }
                val network = SavedItemsStore.SavedWifiNetwork(ssid, if (isOpen) "" else password, isOpen)
                editingNetworkSsid?.takeIf { it != ssid }?.let { SavedItemsStore.removeNetwork(this, it) }
                editingNetworkSsid = null
                SavedItemsStore.upsertNetwork(this, network.ssid, network.password, network.isOpen)
                hideKeyboard()
                startShare(ShareStore.keyForWifi(ssid), wifiData(network))
            }
            TAB_CONTACT -> {
                val first = editContactFirst.text.toString().trim()
                val last = editContactLast.text.toString().trim()
                val phone = normalizePhone(editContactPhone.text.toString())
                val email = editContactEmail.text.toString().trim()
                if (first.isEmpty() || first.length > MAX_NAME_LENGTH || last.length > MAX_NAME_LENGTH) {
                    showError(getString(R.string.error_empty_first_name))
                    return
                }
                if (phone.isNotEmpty() && (phone.length > MAX_PHONE_LENGTH || !PhoneNumberUtils.isGlobalPhoneNumber(phone))) {
                    showError(getString(R.string.error_invalid_phone))
                    return
                }
                if (email.isNotEmpty() && (email.length > MAX_EMAIL_LENGTH || !Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
                    showError(getString(R.string.error_invalid_email))
                    return
                }
                val contact = SavedItemsStore.SavedContact(first, last, phone, email)
                editingContact?.takeIf { it != contact }?.let { SavedItemsStore.removeContact(this, it) }
                editingContact = null
                SavedItemsStore.saveContact(this, contact)
                hideKeyboard()
                startShare(ShareStore.keyForContact(contact), contact.toNdefData())
            }
        }
    }

    // "+90 555 012 36 00" / "(0555) 012-3600" -> "+905550123600"
    private fun normalizePhone(raw: String) = raw.replace(Regex("[\\s\\-().]"), "")

    private fun showError(message: String) {
        textFieldError.text = message
        textFieldError.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String?) {
        if (!message.isNullOrBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
