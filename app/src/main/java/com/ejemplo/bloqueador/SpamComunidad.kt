package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Tipos de spam que se pueden reportar. El orden debe coincidir con CATEGORIAS de cloudflare/src/lib.js. */
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
    // Debe coincidir con PEPPER de cloudflare/src/lib.js.
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

    /** Tellows México no debe bloquear una llamada extranjera con los mismos últimos 10 dígitos. */
    fun numeroMexicano(numero: String): Boolean {
        val raw = numero.trim()
        val digits = raw.filter { it in '0'..'9' }
        return (digits.length == 12 && digits.startsWith("52")) ||
            (digits.length == 14 && digits.startsWith("0052")) ||
            (!raw.startsWith("+") && digits.length == 10)
    }
}

/** Lista de spam descargada, guardada en el teléfono. Consultarla no envía nada a internet. */
object SpamLista {
    private const val ARCHIVO = "spam_list.json"

    @Volatile private var cache: Map<String, Int>? = null
    @Volatile private var cacheSello = -1L
    @Volatile private var cacheVence = Long.MAX_VALUE
    @Volatile private var cacheTellows: Set<String> = emptySet()

    private fun archivo(context: Context) = File(context.applicationContext.filesDir, ARCHIVO)

    @Synchronized
    internal fun mapa(context: Context, ahora: Long = System.currentTimeMillis()): Map<String, Int> {
        val f = archivo(context)
        if (!f.exists()) return emptyMap()
        val sello = f.lastModified()
        cache?.let { if (sello == cacheSello && ahora < cacheVence) return it }
        val nuevo = try {
            val raw = JSONObject(f.readText())
            val array = raw.getJSONArray("n")
            val tellowsHasta = raw.optLong("tellows_hasta", 0L)
            cacheVence = if (tellowsHasta > ahora) tellowsHasta else Long.MAX_VALUE
            val m = HashMap<String, Int>(array.length() * 2)
            val tellows = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val e = array.getJSONArray(i)
                if (e.optString(3) == "tellows" && ahora >= tellowsHasta) continue
                m[e.getString(0)] = e.optInt(1, 0)
                if (e.optString(3) == "tellows") tellows.add(e.getString(0))
            }
            cacheTellows = tellows
            m
        } catch (_: Exception) {
            emptyMap()
        }
        cache = nuevo
        cacheSello = sello
        return nuevo
    }

    fun total(context: Context) = mapa(context).size

    /** Se muestra la fuente real de la última descarga; una integración pendiente no cuenta como activa. */
    fun estadoTellows(context: Context): String = try {
        val raw = JSONObject(archivo(context).readText())
        val info = raw.optJSONObject("fuentes")?.optJSONObject("tellows")
        val vigente = System.currentTimeMillis() < raw.optLong("tellows_hasta", 0L)
        when {
            info == null || info.optString("estado") == "pendiente" -> "Pendiente de activación"
            !vigente -> "Sin datos vigentes"
            info.optString("estado") == "sin_actualizar" -> "Usando la última lista disponible"
            else -> "Lista disponible"
        }
    } catch (_: Exception) { "Pendiente de activación" }

    /** Categoría reportada para ese número, o null si no está en la lista. */
    @Synchronized
    fun categoria(context: Context, numero: String): CategoriaSpam? {
        val h = SpamRules.huella(numero)
        if (h.isEmpty()) return null
        val entries = mapa(context)
        if (h in cacheTellows && !SpamRules.numeroMexicano(numero)) return null
        return entries[h]?.let { CategoriaSpam.deIndice(it) }
    }

    /** Descarga la lista pública. Devuelve false si no hay red, no hay servidor configurado o el archivo no sirve. */
    fun actualizar(context: Context): Boolean {
        val base = SpamComunidad.servidor(context) ?: return false
        return try {
            val conexion = URL("$base/lista?fuentes=2").openConnection() as HttpURLConnection
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

    /** Identificador aleatorio de esta instalación (no ligado al teléfono ni a la cuenta); el servidor solo guarda su huella. */
    val instalacion: String
        get() = prefs.getString("installation", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).apply()
        }

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

/** Reportes a la comunidad (Cloudflare Worker). Sin dirección en servidor.xml todo queda como "sin configurar". */
object SpamComunidad {
    /** Dirección https del Worker sin diagonal final, o null si no se ha configurado. */
    fun servidor(context: Context): String? {
        val url = context.applicationContext.getString(R.string.spam_server_url).trim().trimEnd('/')
        return url.takeIf { it.startsWith("https://") }
    }

    fun configurado(context: Context): Boolean = servidor(context) != null

    /** Debe llamarse fuera del hilo principal. Devuelve null si salió bien, o el motivo del fallo. */
    fun reportar(context: Context, numero: String, categoria: CategoriaSpam): String? {
        val base = servidor(context) ?: return "Los reportes todavía no están configurados en esta versión."
        if (!SpamRules.reportable(numero)) return "Este número no se puede reportar."
        val store = SpamStore(context)
        if (store.reportado(numero)) return null
        val fallo = "No se pudo enviar el reporte. Revisa tu conexión e inténtalo de nuevo."
        return try {
            val cuerpo = JSONObject()
                .put("numero", numero)
                .put("categoria", categoria.codigo)
                .put("instalacion", store.instalacion)
            val conexion = URL("$base/reportar").openConnection() as HttpURLConnection
            try {
                conexion.requestMethod = "POST"
                conexion.connectTimeout = 15_000
                conexion.readTimeout = 20_000
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
                when (conexion.responseCode) {
                    200 -> { store.marcarReportado(numero); null }
                    429 -> "Hoy ya enviaste muchos reportes. Inténtalo mañana."
                    else -> fallo
                }
            } finally {
                conexion.disconnect()
            }
        } catch (_: Exception) {
            fallo
        }
    }
}

