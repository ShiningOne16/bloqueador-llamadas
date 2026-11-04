package com.ejemplo.bloqueador

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Tipos de spam que se pueden reportar. El orden debe coincidir con CATEGORIAS de firebase/functions/lib.js. */
enum class CategoriaSpam(val codigo: String, val etiqueta: String) {
    OTRO("otro", "Otro"),
    COBRANZA("cobranza", "Cobranza"),
    VENTAS("ventas", "Ventas o publicidad"),
    ESTAFA("estafa", "Estafa o fraude"),
    ENCUESTA("encuesta", "Encuesta");

    companion object {
        fun deIndice(i: Int) = entries.getOrElse(i) { OTRO }
    }
}

/** Sin dependencias de Android: se prueba con valores concretos. */
object SpamRules {
    // Debe coincidir con PEPPER de firebase/functions/lib.js.
    private const val PEPPER = "bloqueador-spam-v1:"
    const val MAX_LISTA_BYTES = 8 * 1024 * 1024

    /** Huella de 12 caracteres de los últimos 10 dígitos: la lista no guarda números legibles. */
    fun huella(numero: String): String {
        val clave = PremiumRules.numberKey(numero)
        if (clave.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest((PEPPER + clave).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** Un número se puede reportar si parece un teléfono real (7 a 15 dígitos). */
    fun reportable(numero: String) = PremiumRules.validNumber(numero)
}

/** Lista de spam descargada, guardada en el teléfono. Consultarla no envía nada a internet. */
object SpamLista {
    private const val ARCHIVO = "spam_list.json"

    @Volatile private var cache: Map<String, Int>? = null
    @Volatile private var cacheSello = -1L

    private fun archivo(context: Context) = File(context.applicationContext.filesDir, ARCHIVO)

    private fun mapa(context: Context): Map<String, Int> {
        val f = archivo(context)
        if (!f.exists()) return emptyMap()
        val sello = f.lastModified()
        cache?.let { if (sello == cacheSello) return it }
        val nuevo = try {
            val raw = JSONObject(f.readText())
            val array = raw.getJSONArray("n")
            val m = HashMap<String, Int>(array.length() * 2)
            for (i in 0 until array.length()) {
                val e = array.getJSONArray(i)
                m[e.getString(0)] = e.optInt(1, 0)
            }
            m
        } catch (_: Exception) {
            emptyMap()
        }
        cache = nuevo
        cacheSello = sello
        return nuevo
    }

    fun total(context: Context) = mapa(context).size

    /** Categoría reportada para ese número, o null si no está en la lista. */
    fun categoria(context: Context, numero: String): CategoriaSpam? {
        val h = SpamRules.huella(numero)
        if (h.isEmpty()) return null
        return mapa(context)[h]?.let { CategoriaSpam.deIndice(it) }
    }

    /** Descarga la lista pública. Devuelve false si no hay red, no hay servidor configurado o el archivo no sirve. */
    fun actualizar(context: Context): Boolean {
        val bucket = try { FirebaseApp.getInstance().options.storageBucket } catch (_: Exception) { null }
        if (bucket.isNullOrBlank()) return false
        return try {
            val url = URL("https://firebasestorage.googleapis.com/v0/b/$bucket/o/spam_list.json?alt=media")
            val conexion = url.openConnection() as HttpURLConnection
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 30_000
            conexion.useCaches = false
            try {
                if (conexion.responseCode != 200) return false
                val salida = java.io.ByteArrayOutputStream()
                conexion.inputStream.use { entrada ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        if (salida.size() > SpamRules.MAX_LISTA_BYTES) return false
                    }
                }
                val texto = salida.toString(Charsets.UTF_8.name())
                JSONObject(texto).getJSONArray("n") // si no es una lista válida, se descarta sin tocar la anterior
                val temporal = File(archivo(context).parentFile, "$ARCHIVO.tmp")
                temporal.writeText(texto)
                if (!temporal.renameTo(archivo(context))) { temporal.delete(); return false }
                SpamStore(context).ultimaActualizacion = System.currentTimeMillis()
                true
            } finally {
                conexion.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }
}

/** Ajustes locales de la función: si está activa, últimos datos y qué se reportó o se marcó "no es spam". */
class SpamStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("spam_community", Context.MODE_PRIVATE)

    /** Apagado por defecto: la persona decide si quiere que la lista bloquee llamadas. */
    var activo: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var ultimaActualizacion: Long
        get() = prefs.getLong("updated", 0L)
        set(value) { prefs.edit().putLong("updated", value).apply() }

    private fun conjunto(nombre: String): Set<String> = prefs.getStringSet(nombre, emptySet()).orEmpty().toSet()

    fun reportado(numero: String) = SpamRules.huella(numero).let { it.isNotEmpty() && it in conjunto("reported") }
    fun marcarReportado(numero: String) {
        val h = SpamRules.huella(numero)
        if (h.isNotEmpty()) prefs.edit().putStringSet("reported", conjunto("reported") + h).apply()
    }
    val totalReportados: Int get() = conjunto("reported").size

    /** "No es spam": la lista deja de afectar a ese número en este teléfono. */
    fun permitido(numero: String) = SpamRules.huella(numero).let { it.isNotEmpty() && it in conjunto("allowed") }
    fun permitir(numero: String, permitir: Boolean) {
        val h = SpamRules.huella(numero)
        if (h.isEmpty()) return
        prefs.edit().putStringSet("allowed", if (permitir) conjunto("allowed") + h else conjunto("allowed") - h).apply()
    }
}

/** Reportes a la comunidad (Firebase). Sin google-services.json no hay servidor y todo queda como "sin configurar". */
object SpamComunidad {
    fun configurado(context: Context): Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

    /** Debe llamarse fuera del hilo principal. Devuelve null si salió bien, o el motivo del fallo. */
    fun reportar(context: Context, numero: String, categoria: CategoriaSpam): String? {
        if (!configurado(context)) return "Los reportes todavía no están configurados en esta versión."
        if (!SpamRules.reportable(numero)) return "Este número no se puede reportar."
        val store = SpamStore(context)
        if (store.reportado(numero)) return null
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) Tasks.await(auth.signInAnonymously())
            Tasks.await(
                FirebaseFunctions.getInstance().getHttpsCallable("reportar")
                    .call(mapOf("numero" to numero, "categoria" to categoria.codigo))
            )
            store.marcarReportado(numero)
            null
        } catch (e: Exception) {
            "No se pudo enviar el reporte. Revisa tu conexión e inténtalo de nuevo."
        }
    }
}
