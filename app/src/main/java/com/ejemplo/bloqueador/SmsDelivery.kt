package com.ejemplo.bloqueador

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.UserManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.AtomicFile
import android.util.Base64
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object SmsInboxQueue {
    private fun directory(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "incoming_messages").apply { mkdirs() }
    fun files(context: Context) = directory(context).listFiles().orEmpty()
        .map { File(it.parentFile, it.name.removeSuffix(".bak")) }.filter { it.extension == "json" }.distinctBy { it.name }

    fun store(context: Context, intent: Intent, kind: String): File {
        val sub = intent.getIntExtra("subscription", intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID))
        val pdus = JSONArray()
        if (kind == "sms") {
            @Suppress("DEPRECATION")
            val raw = intent.extras?.get("pdus") as? Array<*> ?: error("SMS sin PDUs")
            raw.forEach { pdus.put(Base64.encodeToString(it as ByteArray, Base64.NO_WRAP)) }
        } else {
            pdus.put(Base64.encodeToString(intent.getByteArrayExtra("data") ?: error("MMS sin datos"), Base64.NO_WRAP))
        }
        val payload = JSONObject().put("kind", kind).put("pdus", pdus).put("format", intent.getStringExtra("format") ?: "3gpp")
            .put("sub", sub).put("received", System.currentTimeMillis())
        // La misma retransmisión produce el mismo fichero aunque cambie la hora de recepción local.
        val digest = MessageDigest.getInstance("SHA-256").digest("$kind:$sub:$pdus".toByteArray()).joinToString("") { "%02x".format(it) }
        val atomic = AtomicFile(File(directory(context), "$digest.json"))
        val output = atomic.startWrite()
        try { output.write(payload.toString().toByteArray()); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
        return atomic.baseFile
    }

    fun enqueue(context: Context) {
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        WorkManager.getInstance(context).enqueueUniqueWork("receive-messages", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SmsInboxWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }
}

open class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION -> "sms"
            Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION -> "mms"
            else -> return
        }
        val result = goAsync()
        thread(name = "store-incoming-message") {
            try {
                val file = SmsInboxQueue.store(context, intent, kind)
                // Procesa los SMS durante la entrega del sistema, sin esperar al planificador de trabajos.
                if (kind == "sms" && context.getSystemService(UserManager::class.java).isUserUnlocked && SmsRepository.isDefault(context)) {
                    try {
                        val raw = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
                        SmsInboxWorker.persistSms(context, raw, file.nameWithoutExtension)
                        file.delete()
                    } catch (_: Exception) { /* La copia duradera queda para WorkManager. */ }
                }
                SmsInboxQueue.enqueue(context)
            }
            catch (_: Exception) { SmsNotifications.error(context, "No se pudo guardar un mensaje. Revisa el espacio libre y abre Bloqueador.") }
            finally { result.finish() }
        }
    }
}

class MmsDeliveryReceiver : SmsDeliveryReceiver()

class SmsInboxWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!SmsRepository.isDefault(applicationContext)) return Result.success() // Conserva la cola para recuperar al volver a elegir la app.
        return try {
            try { SmsRepository.recoverPending(applicationContext) } catch (_: Exception) { }
            var errors = false
            SmsInboxQueue.files(applicationContext).forEach { file ->
                try {
                val raw = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
                if (raw.getString("kind") == "mms") {
                    val receipt = file.nameWithoutExtension
                    if (!SmsJournal(applicationContext).use { it.received(receipt) }) {
                        MmsInbox.receive(applicationContext, raw)
                        SmsJournal(applicationContext).use { it.recordIncoming(receipt, "mms:queued") }
                    }
                }
                else persistSms(applicationContext, raw, file.nameWithoutExtension)
                check(file.delete()) { "No se pudo retirar el mensaje procesado de la cola" }
                } catch (_: Exception) { errors = true }
            }
            check(!errors) { "Hay mensajes pendientes de procesar" }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 6) Result.retry() else {
                SmsNotifications.error(applicationContext, "Hay mensajes pendientes de guardar. Abre Bloqueador para reintentar; se conservan en la cola local.")
                Result.failure()
            }
        }
    }

    companion object {
    @Synchronized fun persistSms(context: Context, raw: JSONObject, receipt: String) {
        if (SmsJournal(context).use { it.received(receipt) }) return
        val parts = raw.getJSONArray("pdus")
        val messages = (0 until parts.length()).map { index ->
            SmsMessage.createFromPdu(Base64.decode(parts.getString(index), Base64.NO_WRAP), raw.getString("format"))
                ?: error("SMS no interpretable")
        }
        require(messages.isNotEmpty())
        val from = messages.first().originatingAddress ?: error("SMS sin remitente")
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val sent = messages.first().timestampMillis
        val sub = raw.getInt("sub")
        val duplicate = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.BODY}=? AND ${Telephony.Sms.DATE_SENT}=? AND ${Telephony.Sms.SUBSCRIPTION_ID}=?",
            arrayOf(from, body, sent.toString(), sub.toString()), null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
        if (duplicate != null) {
            SmsJournal(context).use { it.recordIncoming(receipt, android.content.ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, duplicate).toString()) }
            return
        }
        check(SmsRepository.isDefault(context))
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, from); put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, raw.getLong("received")); put(Telephony.Sms.DATE_SENT, sent)
            put(Telephony.Sms.SUBSCRIPTION_ID, sub); put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.SERVICE_CENTER, messages.first().serviceCenterAddress)
        }) ?: error("No se pudo guardar SMS en la bandeja")
        SmsJournal(context).use { it.recordIncoming(receipt, uri.toString()) }
        val threadId = SmsRepository.threadFor(context, uri) ?: return
        val store = ArchiveStore(context)
        store.save(store.read().restoreThread(threadId))
        val silence = try { SmsNotifications.shouldSilence(context, from) } catch (_: Exception) { false }
        if (silence) Prefs(context).addSilencedSms(from, body, System.currentTimeMillis())
        else SmsNotifications.incoming(context, threadId, from, body)
    }
    }
}

class SmsRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { SmsInboxQueue.enqueue(context) }
}

class SmsPendingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try { SmsRepository.recoverPending(applicationContext); Result.success() }
    catch (_: Exception) { Result.retry() }
}

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        val part = intent.getIntExtra("part", -1)
        val sentResult = resultCode
        val pending = goAsync()
        thread(name = "sms-sent-result") {
            try {
                val result = SmsJournal(context).use { it.record(token, part, sentResult) } ?: return@thread
                if (!SmsRepository.isDefault(context)) return@thread // Conserva el callback, pero no escribe sin el rol.
                if (result.state != "pending") {
                    val count = context.contentResolver.update(Uri.parse(result.uri), ContentValues().apply {
                        put(Telephony.Sms.TYPE, if (result.state == "sent") Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
                        put(Telephony.Sms.STATUS, if (result.state == "sent") Telephony.Sms.STATUS_NONE else Telephony.Sms.STATUS_FAILED)
                        if (result.state == "sent") put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                        else put(Telephony.Sms.ERROR_CODE, sentResult)
                    }, "${Telephony.Sms.DATE}=?", arrayOf(result.date.toString()))
                    SmsJournal(context).use { it.settle(result.uri, result.date) }
                    if (count > 0 && result.state == "failed") SmsNotifications.error(context, "No se confirmó el envío de un SMS. Revisa el mensaje antes de reenviar; algunas partes pudieron llegar.")
                }
            } catch (_: Exception) { SmsNotifications.error(context, "Hay un estado de envío pendiente de actualizar. Abre Mensajes para revisarlo.") }
            finally { pending.finish() }
        }
    }
}

/** Respuesta rápida desde la pantalla de llamadas; solo el sistema puede iniciar este servicio. */
class SmsRespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManagerActions.RESPOND) { stopSelf(startId); return START_NOT_STICKY }
        thread(name = "sms-quick-response") {
            try {
                val to = intent.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
                val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                val sub = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubscriptionId()))
                SmsRepository.send(this, to, body, sub)
            } catch (_: Exception) { SmsNotifications.error(this, "No se pudo enviar la respuesta rápida. Abre Mensajes para revisarla.") }
            finally { stopSelf(startId) }
        }
        return START_NOT_STICKY
    }
}
private object TelephonyManagerActions { const val RESPOND = "android.intent.action.RESPOND_VIA_MESSAGE" }
