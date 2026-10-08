package com.hubble.nfcshare.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.hubble.nfcshare.R

// Copies text and confirms it. Android 13+ shows its own system confirmation
// (Samsung's reads "Kopyalandı." with a period) and there's no way to suppress it:
// Samsung ignores ClipDescription.EXTRA_IS_REMOTE_DEVICE (tested: both toasts showed).
// So we only toast ourselves on older versions.
object ClipboardCopy {
    fun copy(context: Context, label: String, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        }
    }
}
