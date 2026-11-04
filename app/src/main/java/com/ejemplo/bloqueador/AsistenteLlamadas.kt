package com.ejemplo.bloqueador

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/** Qué parece ser quien respondió, según palabras clave de su mensaje. */
enum class ClaseRespuesta(val etiqueta: String) {
    LEGITIMA("Parece legítimo (entrega o cita)"),
    SPAM("Parece publicidad o spam"),
    INCIERTA("No se pudo clasificar")
}

/** Sin dependencias de Android: se prueba con valores concretos. */
object AsistenteRules {
    const val MAX_NOMBRE = 30
    /** Tras avisar a un número, no se le vuelve a escribir (y si insiste llamando, suena). */
    const val ENFRIAMIENTO_MS = 24 * 60 * 60 * 1000L
    /** Tiempo en que su respuesta se considera respuesta al asistente. */
    const val VENTANA_RESPUESTA_MS = 48 * 60 * 60 * 1000L
    const val MAX_POR_HORA = 5
    const val HORA_MS = 60 * 60 * 1000L

    fun limpiarNombre(texto: String) = texto.trim().replace(Regex("\\s+"), " ").take(MAX_NOMBRE)

    /** Sin acentos en el texto fijo para que quepa en un solo SMS (alfabeto GSM de 160 caracteres). */
    fun mensaje(nombre: String): String {
        val n = limpiarNombre(nombre)
        val quien = if (n.isEmpty()) "de esta linea" else "de $n"
        return "Hola, soy el asistente de llamadas $quien. Ahora no puedo atender tu llamada. " +
            "Responde con tu nombre y el motivo de tu llamada."
    }

    fun puedeEnviar(enviadosUltimaHora: Int) = enviadosUltimaHora < MAX_POR_HORA

    fun enFuerza(desde: Long, ahora: Long, ventana: Long) = desde > 0 && ahora - desde in 0 until ventana

    private val LEGITIMAS = listOf(
        "paquete", "paqueteria", "entrega", "repartidor", "envio", "guia de", "pedido", "mensajeria", "estafeta",
        "dhl", "fedex", "ups", "correos", "mercado libre", "amazon", "rappi", "didi", "uber", "domicilio",
        "cita", "consultorio", "clinica", "hospital", "doctor", "escuela", "maestra", "maestro", "colegio",
        "mi hijo", "mi hija", "urgente"
    )
    private val SPAM = listOf(
        "promocion", "oferta", "descuento", "ganaste", "ganador", "premio", "felicidades", "sorteo", "gratis",
        "credito", "prestamo", "tarjeta", "inversion", "bitcoin", "gana dinero", "encuesta", "suscripcion",
        "cobranza", "adeudo", "deuda", "venta de", "vendo", "seguro de", "plan de", "http", "www.", "bit.ly",
        "haz clic", "da clic", "click"
    )

    fun clasificar(texto: String): ClaseRespuesta {
        val t = simplificar(texto)
        val legitima = LEGITIMAS.any { it in t }
        val spam = SPAM.any { it in t }
        return when {
            legitima && !spam -> ClaseRespuesta.LEGITIMA
            spam && !legitima -> ClaseRespuesta.SPAM
            else -> ClaseRespuesta.INCIERTA
        }
    }
}

data class RegistroAsistente(val numero: String, val enviado: Long, val respondido: Long = 0L)

/** Guarda los ajustes y los números a los que ya se escribió, solo en el teléfono. */
class AsistenteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("call_assistant", Context.MODE_PRIVATE)

    var activo: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var nombre: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) { prefs.edit().putString("name", AsistenteRules.limpiarNombre(value)).apply() }

    private fun leer(): MutableMap<String, RegistroAsistente> {
        val raw = prefs.getString("log", null) ?: return mutableMapOf()
        return try {
            val array = JSONArray(raw)
            val mapa = LinkedHashMap<String, RegistroAsistente>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val r = RegistroAsistente(o.optString("number"), o.optLong("sent"), o.optLong("replied"))
                val clave = PremiumRules.numberKey(r.numero)
                if (clave.isNotEmpty()) mapa[clave] = r
            }
            mapa
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun escribir(mapa: Map<String, RegistroAsistente>, ahora: Long) {
        val array = JSONArray()
        // Se descartan los registros viejos: no hace falta recordar a quién se escribió hace semanas.
        mapa.values.filter { ahora - it.enviado < 7 * 24 * 60 * 60 * 1000L }.forEach {
            array.put(JSONObject().put("number", it.numero).put("sent", it.enviado).put("replied", it.respondido))
        }
        prefs.edit().putString("log", array.toString()).commit()
    }

    fun registro(numero: String): RegistroAsistente? = leer()[PremiumRules.numberKey(numero)]

    fun enviadosDesde(desde: Long): Int = leer().values.count { it.enviado >= desde }

    @Synchronized
    fun anotarEnvio(numero: String, ahora: Long) {
        val m = leer()
        m[PremiumRules.numberKey(numero)] = RegistroAsistente(numero, ahora)
        escribir(m, ahora)
    }

    @Synchronized
    fun olvidar(numero: String, ahora: Long) {
        val m = leer()
        if (m.remove(PremiumRules.numberKey(numero)) != null) escribir(m, ahora)
    }

    /** Marca la primera respuesta; devuelve false si no había una pregunta vigente. */
    @Synchronized
    fun anotarRespuesta(numero: String, ahora: Long): Boolean {
        val m = leer()
        val clave = PremiumRules.numberKey(numero)
        val r = m[clave] ?: return false
        if (r.respondido > 0 || !AsistenteRules.enFuerza(r.enviado, ahora, AsistenteRules.VENTANA_RESPUESTA_MS)) return false
        m[clave] = r.copy(respondido = ahora)
        escribir(m, ahora)
        return true
    }

    fun borrarClaves(claves: Set<String>) {
        val m = leer()
        if (claves.any { m.remove(it) != null }) escribir(m, System.currentTimeMillis())
    }
}

object Asistente {
    private const val CANAL = "asistente"
    const val ACCION_IGNORAR = "com.ejemplo.bloqueador.ASISTENTE_IGNORAR"
    const val ACCION_BLOQUEAR = "com.ejemplo.bloqueador.ASISTENTE_BLOQUEAR"

    /**
     * Intenta atender una llamada de un desconocido: manda el SMS y devuelve true para que la llamada
     * se silencie. Si algo falla (sin permiso, sin SIM, límite por hora) devuelve false y la llamada
     * sigue las reglas normales de bloqueo.
     */
    fun atender(context: Context, numero: String): Boolean {
        val store = AsistenteStore(context)
        if (!store.activo) return false
        if (SmsPolicy.recipient(numero) == null || !PremiumRules.validNumber(numero)) return false
        if (!PhoneData.has(context, Manifest.permission.SEND_SMS)) return false
        val ahora = System.currentTimeMillis()
        if (!AsistenteRules.puedeEnviar(store.enviadosDesde(ahora - AsistenteRules.HORA_MS))) return false
        val sub = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (!SubscriptionManager.isValidSubscriptionId(sub)) return false
        val texto = AsistenteRules.mensaje(store.nombre)
        store.anotarEnvio(numero, ahora) // antes de enviar: evita un segundo SMS si llega otra llamada a la vez
        return try {
            if (SmsRepository.isDefault(context)) SmsRepository.send(context, numero, texto, sub)
            else SmsRepository.manager(context, sub).sendTextMessage(numero, null, texto, null, null)
            true
        } catch (_: Exception) {
            store.olvidar(numero, ahora)
            false
        }
    }

    /** Ya se le escribió hace poco: si vuelve a llamar, se deja sonar. */
    fun yaAtendido(context: Context, numero: String): Boolean {
        val store = AsistenteStore(context)
        if (!store.activo) return false
        val r = store.registro(numero) ?: return false
        return AsistenteRules.enFuerza(r.enviado, System.currentTimeMillis(), AsistenteRules.ENFRIAMIENTO_MS)
    }

    /** Llamar cuando llega un SMS. Devuelve true si era la respuesta al asistente y ya se avisó al usuario. */
    fun alRecibir(context: Context, remitente: String, texto: String): Boolean {
        if (!PremiumRules.validNumber(remitente)) return false
        val store = AsistenteStore(context)
        if (!store.activo || !store.anotarRespuesta(remitente, System.currentTimeMillis())) return false
        avisar(context, remitente, texto)
        return true
    }

    private fun avisar(context: Context, numero: String, texto: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CANAL, "Asistente de llamadas", NotificationManager.IMPORTANCE_HIGH))
        val clase = AsistenteRules.clasificar(texto)
        val codigo = PremiumRules.numberKey(numero).hashCode()
        val bandera = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val responder = PendingIntent.getActivity(
            context, codigo,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:$numero")),
            bandera
        )
        fun accion(nombre: String, desfase: Int) = PendingIntent.getBroadcast(
            context, codigo + desfase,
            Intent(context, AsistenteAccionReceiver::class.java).setAction(nombre).putExtra("numero", numero), bandera
        )
        val cuerpo = "${clase.etiqueta}\n\"$texto\""
        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Respondió a tu asistente: $numero")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(responder)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Responder", responder)
            .addAction(0, "Ignorar", accion(ACCION_IGNORAR, 1))
            .addAction(0, "Bloquear", accion(ACCION_BLOQUEAR, 2))
            .build()
        try { manager.notify("asistente:${PremiumRules.numberKey(numero)}", 1, notificacion) } catch (_: SecurityException) { }
    }
}

/** Botones Ignorar y Bloquear de la notificación del asistente. */
class AsistenteAccionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val numero = intent.getStringExtra("numero") ?: return
        if (intent.action == Asistente.ACCION_BLOQUEAR) {
            try { Prefs(context).setManualBlock(numero, true) } catch (_: Exception) { }
        } else if (intent.action != Asistente.ACCION_IGNORAR) return
        context.getSystemService(NotificationManager::class.java).cancel("asistente:${PremiumRules.numberKey(numero)}", 1)
    }
}
