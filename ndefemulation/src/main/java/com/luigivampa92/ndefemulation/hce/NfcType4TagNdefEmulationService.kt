package com.luigivampa92.ndefemulation.hce

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock
import com.luigivampa92.ndefemulation.DataUtil
import com.luigivampa92.ndefemulation.Logger
import com.luigivampa92.ndefemulation.NdefEmulation

internal class NfcType4TagNdefEmulationService : HostApduService() {

    private companion object {
        // After a complete read, stay silent briefly: a reader phone held against us
        // re-detects the "tag" as soon as the field flickers and would open the same
        // link/contact twice in a row.
        private const val READ_COOLDOWN_MILLIS = 800L

        @Volatile
        private var lastFullReadAt = 0L
    }

    private var ndefEmulation: NdefEmulation? = null
    private var ndefEmulator: NfcType4TagNdefEmulator? = null

    override fun onDeactivated(reason: Int) {
        if (ndefEmulator?.ndefFileFullyRead == true) {
            lastFullReadAt = SystemClock.elapsedRealtime()
            ndefEmulation?.incrementReadCount()
            sendBroadcast(Intent(NdefEmulation.ACTION_NDEF_READ).setPackage(packageName))
        }
        ndefEmulation = null
        ndefEmulator = null
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        try {
            if (commandApdu == null) {
                return ApduConstants.SW_ERROR_INPUT_DATA_ABSENT
            }
            if (ndefEmulation == null || ndefEmulator == null) {
                if (lastFullReadAt != 0L && SystemClock.elapsedRealtime() - lastFullReadAt < READ_COOLDOWN_MILLIS) {
                    return ApduConstants.SW_ERROR_NO_DATA_PERSISTED
                }
                ndefEmulation = NdefEmulation(this)
                if (ndefEmulation?.isExpired == true) {
                    ndefEmulation?.currentEmulatedNdefData = null
                    return ApduConstants.SW_ERROR_NO_DATA_PERSISTED
                }
                ndefEmulation?.currentEmulatedNdefData?.let {
                    ndefEmulator = NfcType4TagNdefEmulator(it)
                } ?: return ApduConstants.SW_ERROR_NO_DATA_PERSISTED
            }
            Logger.i("RX: " + DataUtil.toHexString(commandApdu))
            val response = ndefEmulator?.transmitApdu(commandApdu) ?: return ApduConstants.SW_ERROR_NO_DATA_PERSISTED
            Logger.i("TX: " + DataUtil.toHexString(response))
            return if (response.isNotEmpty()) response else ApduConstants.SW_ERROR_OUTPUT_DATA_ABSENT
        } catch (e: Exception) {
            return ApduConstants.SW_ERROR_GENERAL
        }
    }
}
