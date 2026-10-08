package com.hubble.nfcshare.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.TextView
import com.hubble.nfcshare.R
import com.hubble.nfcshare.share.ClipboardCopy
import java.nio.charset.Charset

// Receiving side: when *this* phone reads a plain-text NFC record (e.g. from another
// phone running this app), Android shows it read-only at best. With this app
// installed the text opens here instead, selectable and with a copy button.
class ReceivedTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = readText(intent)
        if (text.isNullOrEmpty()) {
            finish()
            return
        }

        setContentView(R.layout.activity_received_text)
        findViewById<TextView>(R.id.text_received).text = text

        findViewById<View>(R.id.button_copy).setOnClickListener {
            ClipboardCopy.copy(this, getString(R.string.received_title), text)
            finish()
        }

        val link = Regex("https?://\\S+").find(text)?.value
        val buttonOpen: View = findViewById(R.id.button_open_link)
        buttonOpen.visibility = if (link != null) View.VISIBLE else View.GONE
        buttonOpen.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            } catch (e: Exception) {
            }
            finish()
        }

        findViewById<View>(R.id.button_close).setOnClickListener { finish() }
        findViewById<View>(R.id.received_root).setOnClickListener { finish() }
    }

    @Suppress("DEPRECATION")
    private fun readText(intent: Intent?): String? {
        val messages: Array<Parcelable> = intent?.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return null
        return messages.filterIsInstance<NdefMessage>()
            .flatMap { it.records.toList() }
            .mapNotNull { decodeTextRecord(it) }
            .joinToString("\n")
            .ifEmpty { null }
    }

    // NFC Forum "Text" RTD: status byte (bit 7 = UTF-16, bits 0-5 = language code length),
    // then the language code, then the text.
    private fun decodeTextRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val charset = if (status and 0x80 != 0) Charset.forName("UTF-16") else Charsets.UTF_8
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return null
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }
}
