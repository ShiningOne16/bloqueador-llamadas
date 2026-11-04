package com.ejemplo.bloqueador

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun messageChanges(enabled: Boolean): Int {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context, enabled) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { version++ }
        }
        if (enabled) {
            try {
                context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
            } catch (_: SecurityException) { /* La pantalla permite recuperar el permiso. */ }
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return version
}
