package com.ejemplo.bloqueador

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.concurrent.thread

data class MmsItem(val id: Long, val date: Long, val sender: String, val text: String, val pending: Boolean, val parts: List<MmsPart>)
data class MmsPart(val id: Long, val mime: String)

/** Usa el transporte MMS del sistema y conserva los fallos para poder reintentar sin descartar PDUs. */
object MmsInbox {
    private fun directory(context: Context) = File(context.filesDir, "mms_downloads").apply { mkdirs() }

    @Synchronized fun receive(context: Context, raw: JSONObject) {
        val bytes = Base64.decode(raw.getJSONArray("pdus").getString(0), Base64.NO_WRAP)
        val pdu = PduParser(bytes, true).parse() ?: error("MMS no interpretable")
        if (pdu !is NotificationInd) return // Informes de estado no son mensajes de contenido.
        val location = String(pdu.contentLocation, Charsets.ISO_8859_1)
        require(Uri.parse(location).scheme in listOf("http", "https"))
        val key = MessageDigest.getInstance("SHA-256").digest(location.toByteArray()).joinToString("") { "%02x".format(it) }
        val metadata = File(directory(context), "$key.json")
        if (!metadata.exists()) {
            val existing = context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id"), "ct_l=?", arrayOf(location), null)?.use {
                if (it.moveToFirst()) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, it.getLong(0)) else null
            }
            val notification = existing ?: PduPersister.getPduPersister(context).persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, raw.getInt("sub"))
            write(metadata, JSONObject().put("location", location).put("notification", notification.toString())
                .put("sub", raw.getInt("sub")).put("started", 0))
        }
        start(context, metadata)
    }

    @Synchronized private fun start(context: Context, metadata: File) {
        if (!SmsRepository.isDefault(context)) return
        val data = JSONObject(metadata.readText())
        if (System.currentTimeMillis() - data.optLong("started") < 5 * 60_000L) return
        val file = File(directory(context), metadata.nameWithoutExtension + ".pdu")
        if (file.exists() && file.length() > 0) {
            try { persist(context, metadata, file); return } catch (_: Exception) { /* Vuelve a descargar si la respuesta anterior estaba incompleta. */ }
        }
        file.writeBytes(byteArrayOf())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.message_files", file)
        context.grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val callback = PendingIntent.getBroadcast(context, 0,
            Intent(context, MmsDownloadReceiver::class.java).setData(Uri.parse("mms-result://${metadata.nameWithoutExtension}")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        write(metadata, data.put("started", System.currentTimeMillis()))
        try { SmsRepository.manager(context, data.getInt("sub")).downloadMultimediaMessage(context, data.getString("location"), uri, null, callback) }
        catch (e: Exception) { write(metadata, data.put("started", 0)); throw e }
    }

    @Synchronized fun complete(context: Context, key: String, success: Boolean) {
        require(key.matches(Regex("[a-f0-9]{64}")))
        val metadata = File(directory(context), "$key.json")
        if (!metadata.exists()) return
        val data = JSONObject(metadata.readText())
        val file = File(directory(context), "$key.pdu")
        if (!success) {
            write(metadata, data.put("started", 0))
            SmsNotifications.error(context, "Hay un MMS pendiente. Activa los datos móviles y usa Reintentar en Multimedia.")
            return
        }
        persist(context, metadata, file)
    }

    private fun persist(context: Context, metadata: File, file: File) {
        check(SmsRepository.isDefault(context))
        require(file.length() in 1..50_000_000)
        val data = JSONObject(metadata.readText())
        val pdu = PduParser(file.readBytes(), true).parse() as? RetrieveConf ?: error("Respuesta MMS no interpretable")
        val messageId = pdu.messageId?.let { String(it, Charsets.ISO_8859_1) }
        val duplicate = messageId != null && context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id"), "m_id=?", arrayOf(messageId), null)?.use { it.moveToFirst() } == true
        if (!duplicate) {
            val uri = PduPersister.getPduPersister(context).persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, data.getInt("sub"))
            context.contentResolver.update(uri, ContentValues().apply { put("read", 0); put("seen", 0); put("sub_id", data.getInt("sub")) }, null, null)
            SmsNotifications.error(context, "MMS recibido. Puedes ver su texto y adjuntos en Mensajes → Multimedia.")
        }
        context.contentResolver.delete(Uri.parse(data.getString("notification")), "m_type=?", arrayOf("130"))
        context.revokeUriPermission(FileProvider.getUriForFile(context, "${context.packageName}.message_files", file), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        metadata.delete(); file.delete()
    }

    fun retry(context: Context) { directory(context).listFiles()?.filter { it.extension == "json" }?.forEach { start(context, it) } }

    @Synchronized fun delete(context: Context, item: MmsItem) {
        check(SmsRepository.isDefault(context))
        directory(context).listFiles()?.filter { it.extension == "json" }?.forEach { metadata ->
            val data = JSONObject(metadata.readText())
            if (Uri.parse(data.getString("notification")).lastPathSegment == item.id.toString()) {
                val file = File(directory(context), metadata.nameWithoutExtension + ".pdu")
                context.revokeUriPermission(FileProvider.getUriForFile(context, "${context.packageName}.message_files", file), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                metadata.delete(); file.delete()
            }
        }
        context.contentResolver.delete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, item.id), "date=?", arrayOf((item.date / 1000).toString()))
    }

    fun list(context: Context): List<MmsItem> {
        val output = mutableListOf<MmsItem>()
        context.contentResolver.query(Telephony.Mms.Inbox.CONTENT_URI, arrayOf("_id", "date", "m_type"), null, null, "date DESC")?.use { rows ->
            while (rows.moveToNext() && output.size < 200) {
                val id = rows.getLong(0)
                val sender = context.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type=?", arrayOf("137"), null)?.use { if (it.moveToFirst()) it.getString(0) else "MMS" } ?: "MMS"
                val text = mutableListOf<String>()
                val parts = mutableListOf<MmsPart>()
                context.contentResolver.query(Uri.parse("content://mms/$id/part"), arrayOf("_id", "ct", "text"), null, null, null)?.use { part ->
                    while (part.moveToNext()) {
                        val mime = part.getString(1).orEmpty()
                        if (mime == "text/plain") text += part.getString(2).orEmpty()
                        else if (mime != "application/smil") parts += MmsPart(part.getLong(0), mime)
                    }
                }
                output += MmsItem(id, rows.getLong(1) * 1000, sender, text.joinToString("\n"), rows.getInt(2) == 130, parts)
            }
        }
        return output
    }

    fun attachmentIntent(context: Context, part: MmsPart): Intent {
        val directory = File(context.cacheDir, "message_attachments").apply { mkdirs() }
        val file = File(directory, "attachment-${part.id}")
        context.contentResolver.openInputStream(Uri.parse("content://mms/part/${part.id}"))!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.message_files", file), part.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun write(file: File, value: JSONObject) {
        val atomic = AtomicFile(file); val output = atomic.startWrite()
        try { output.write(value.toString().toByteArray()); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }
}

class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.data?.host ?: return
        val success = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        thread(name = "persist-mms") {
            try { MmsInbox.complete(context, key, success) }
            catch (_: Exception) { SmsNotifications.error(context, "No se pudo guardar un MMS. Su descarga se conserva; abre Multimedia para reintentar.") }
            finally { pending.finish() }
        }
    }
}
