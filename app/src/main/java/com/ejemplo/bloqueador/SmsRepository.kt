package com.ejemplo.bloqueador

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.util.UUID

data class SmsLine(val id: Int, val label: String)

object SmsRepository {
    fun isDefault(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    fun roleAvailable(context: Context) = context.getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_SMS)
    val permissions = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    @Suppress("DEPRECATION")
    fun manager(context: Context, subId: Int): SmsManager = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
    else SmsManager.getSmsManagerForSubscriptionId(subId)

    fun lines(context: Context): List<SmsLine> {
        val active = try {
            if (PhoneData.has(context, Manifest.permission.READ_PHONE_STATE))
                context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                    .map { SmsLine(it.subscriptionId, "SIM ${it.simSlotIndex + 1} · ${it.displayName}") }
            else emptyList()
        } catch (_: SecurityException) { emptyList() }
        if (active.isNotEmpty()) return active
        val id = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (SubscriptionManager.isValidSubscriptionId(id)) listOf(SmsLine(id, "SIM predeterminada")) else emptyList()
    }

    /** Solo se invoca desde un botón Enviar o la respuesta rápida protegida del sistema. */
    fun send(context: Context, address: String, body: String, subId: Int): Uri {
        check(isDefault(context)) { "Elige Bloqueador como app de SMS predeterminada." }
        check(PhoneData.has(context, Manifest.permission.SEND_SMS)) { "Falta permiso para enviar SMS." }
        val to = SmsPolicy.recipient(address) ?: error("Escribe un único número válido.")
        require(body.isNotBlank()) { "Escribe un mensaje." }
        require(SubscriptionManager.isValidSubscriptionId(subId)) { "Elige una SIM activa." }
        val sms = manager(context, subId)
        val parts = sms.divideMessage(body)
        require(parts.size in 1..10) { "El mensaje supera el máximo de 10 partes SMS." }
        val created = System.currentTimeMillis()
        val uri = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, to); put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, created); put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId); put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }) ?: error("No se pudo guardar el mensaje antes de enviarlo.")
        val token = UUID.randomUUID().toString()
        try {
            SmsJournal(context).use { it.create(token, uri.toString(), parts.size, created) }
            val intents = ArrayList(parts.indices.map { part ->
                PendingIntent.getBroadcast(context, 0,
                    Intent(context, SmsSentReceiver::class.java).setAction("${context.packageName}.SMS_SENT")
                        .setData(Uri.parse("sms-status://$token/$part")).putExtra("token", token).putExtra("part", part),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            })
            // No hay reintentos automáticos: tras un fallo, reenviar podría duplicar partes ya entregadas.
            sms.sendMultipartTextMessage(to, null, parts, intents, null)
            try {
                androidx.work.WorkManager.getInstance(context).enqueue(
                    androidx.work.OneTimeWorkRequestBuilder<SmsPendingWorker>().setInitialDelay(10, java.util.concurrent.TimeUnit.MINUTES).build())
            } catch (_: Exception) { /* Las confirmaciones del módem siguen siendo independientes del trabajador. */ }
            return uri
        } catch (e: Exception) {
            context.contentResolver.update(uri, ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED); put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
            }, "${Telephony.Sms.DATE}=?", arrayOf(created.toString()))
            throw e
        }
    }

    fun threadFor(context: Context, message: Uri): Long? = context.contentResolver.query(message,
        arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }

    fun deleteThread(context: Context, thread: SmsThread): Int {
        check(isDefault(context)) { "Elige Bloqueador como app de SMS predeterminada." }
        return context.contentResolver.delete(Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms._ID}<=? AND ${Telephony.Sms.DATE}<=?",
            arrayOf(thread.threadId.toString(), thread.last.id.toString(), thread.last.date.toString()))
    }

    fun deleteMessage(context: Context, message: SmsMsg): Int {
        check(isDefault(context)) { "Elige Bloqueador como app de SMS predeterminada." }
        return context.contentResolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.id),
            "${Telephony.Sms.DATE}=?", arrayOf(message.date.toString()))
    }

    fun markRead(context: Context, messages: List<SmsMsg>) {
        if (!isDefault(context) || messages.isEmpty()) return
        val ids = messages.filter { it.incoming && !it.read }.map { it.id }
        if (ids.isEmpty()) return
        context.contentResolver.update(Telephony.Sms.CONTENT_URI,
            ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
            "${Telephony.Sms._ID} IN (${ids.joinToString(",")}) AND ${Telephony.Sms.READ}=0", null)
    }

    /** Un callback perdido se muestra como envío no confirmado, nunca como enviado ni se reintenta solo. */
    fun recoverPending(context: Context) {
        if (!isDefault(context)) return
        SmsJournal(context).use { journal -> journal.stale().forEach { record ->
            context.contentResolver.update(Uri.parse(record.uri), ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
            }, "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}=?", arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(), record.date.toString()))
        }
            journal.completed().forEach { (uri, state, date) ->
                context.contentResolver.update(Uri.parse(uri), ContentValues().apply {
                    put(Telephony.Sms.TYPE, if (state == "sent") Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
                    put(Telephony.Sms.STATUS, if (state == "sent") Telephony.Sms.STATUS_NONE else Telephony.Sms.STATUS_FAILED)
                }, "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}=?", arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(), date.toString()))
            }
        }
    }
}
