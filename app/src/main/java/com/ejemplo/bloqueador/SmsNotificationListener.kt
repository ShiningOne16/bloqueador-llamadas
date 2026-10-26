package com.ejemplo.bloqueador

import android.app.Notification
import android.os.Parcelable
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Lee las notificaciones de la app de mensajes. Si el SMS viene de alguien que no está
 * en tus contactos, quita la notificación (eso también corta el sonido y la vibración).
 * El mensaje sigue en tu app de SMS; solo se evita el aviso.
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Error al revisar notificación", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!isSmsApp(sbn.packageName)) return

        val prefs = Prefs(this)
        if (!prefs.smsEnabled) return
        // Sin permiso de contactos no sabemos quién es conocido: no tocamos nada
        if (!Contacts.hasPermission(this)) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = n.extras
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return

        val (sender, text) = senderAndText(n) ?: return
        if (!isUnknownSender(sender, prefs.smsIncludeBusiness)) return

        cancelNotification(sbn.key)
        prefs.addSilencedSms(sender, text, System.currentTimeMillis())
        Log.i(TAG, "SMS de $sender silenciado")
    }

    /** Remitente y último texto, tomados del estilo de mensajería o del título de la notificación. */
    private fun senderAndText(n: Notification): Pair<String, String>? {
        val extras = n.extras

        @Suppress("DEPRECATION")
        val messages: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).lastOrNull()

        val senderFromStyle = last?.senderPerson?.name?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val sender = (senderFromStyle ?: title)?.trim().orEmpty()
        if (sender.isEmpty()) return null

        val text = last?.text?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        return sender to text
    }

    /**
     * Las apps de SMS muestran el nombre si el remitente es un contacto,
     * y el número si no lo es.
     */
    private fun isUnknownSender(sender: String, includeBusiness: Boolean): Boolean {
        val digits = sender.count { it.isDigit() }
        val looksLikePhone = sender.all { it.isDigit() || it in "+-() ." } && digits > 0

        return if (looksLikePhone) {
            when {
                Contacts.isNumberInContacts(this, sender) -> false
                digits <= 6 -> includeBusiness        // código corto (2525, 7777…)
                else -> true                          // número normal desconocido
            }
        } else {
            // Tiene letras: es un contacto (nombre guardado) o una empresa (BANCO, TELCEL…)
            if (Contacts.isNameInContacts(this, sender)) false else includeBusiness
        }
    }

    private fun isSmsApp(pkg: String): Boolean =
        pkg == Telephony.Sms.getDefaultSmsPackage(this) || pkg in KNOWN_SMS_APPS

    companion object {
        private const val TAG = "SmsListener"

        private val KNOWN_SMS_APPS = setOf(
            "com.google.android.apps.messaging",   // Google Mensajes
            "com.samsung.android.messaging",       // Samsung Mensajes
            "com.android.mms",                     // Xiaomi / AOSP
            "com.oneplus.mms",
            "com.motorola.messaging"
        )
    }
}
