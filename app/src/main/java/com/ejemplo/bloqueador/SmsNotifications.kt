package com.ejemplo.bloqueador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime

object SmsNotifications {
    fun shouldSilence(context: Context, sender: String): Boolean {
        val prefs = Prefs(context)
        if (!prefs.smsEnabled || !Contacts.hasPermission(context)) return false
        val profile = PremiumStore(context).read().activeProfile
        if (profile != null && (!profile.silenceUnknownSms || !profile.schedule.contains(LocalDateTime.now()))) return false
        val business = profile?.includeBusinessSms ?: prefs.smsIncludeBusiness
        val phone = sender.all { it.isDigit() || it in "+-() ." } && sender.any { it.isDigit() }
        return if (phone) {
            !Contacts.isNumberInContacts(context, sender) && (sender.count { it.isDigit() } > 6 || business)
        } else !Contacts.isNameInContacts(context, sender) && business
    }

    fun incoming(context: Context, threadId: Long, sender: String, body: String) {
        show(context, "thread:$threadId", "SMS de $sender", body, threadId)
    }
    fun error(context: Context, body: String) = show(context, "sms-error", "Revisa tus mensajes", body, null)
    fun clear(context: Context, threadId: Long) = context.getSystemService(NotificationManager::class.java).cancel("thread:$threadId", 1)

    private fun show(context: Context, tag: String, title: String, body: String, threadId: Long?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = if (threadId == null) "sms_errors" else "sms_messages"
        manager.createNotificationChannel(NotificationChannel(channel, if (threadId == null) "Problemas de mensajes" else "Mensajes recibidos", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("bloqueador://sms/${threadId ?: "inbox"}"))
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(tag, 1, notification) } catch (_: SecurityException) { /* La bandeja conserva el mensaje aunque se deniegue notificar. */ }
    }
}
