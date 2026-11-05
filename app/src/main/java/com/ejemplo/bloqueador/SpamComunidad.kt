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
    const val VIGENCIA_LISTA_MS = 7 * 24 * 60 * 60 * 1000L

    /** Huella de 12 caracteres de los últimos 10 dígitos: la lista no guarda números legibles. */
    fun huella(numero: String): String {
        val clave = PremiumRules.numberKey(numero)
        if (clave.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest((PEPPER + clave).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** Esta comunidad cubre México; no mezcla países por sus últimos diez dígitos. */
    fun reportable(numero: String) = PremiumRules.validNumber(numero) && numeroMexicano(numero)

    /** Solo propone revisar en el teléfono. Nunca decide ni envía un reporte. */
    fun sugerirRevision(nombre: String?, etiqueta: String?, perdida: Boolean): Boolean {
        val texto = simplificar(listOfNotNull(nombre, etiqueta).joinToString(" "))
        return (nombre == null && perdida) || listOf("spam", "estafa", "fraude").any { it in texto }
    }

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
            val generado = raw.optLong("generado", 0L)
            val comunidadHasta = if (generado > 0) {
                raw.optLong("comunidad_hasta", generado + SpamRules.VIGENCIA_LISTA_MS)
                    .coerceAtMost(generado + SpamRules.VIGENCIA_LISTA_MS)
            } else 0L
            cacheVence = listOf(tellowsHasta, comunidadHasta).filter { it > ahora }.minOrNull() ?: Long.MAX_VALUE
            val m = HashMap<String, Int>(array.length() * 2)
            val tellows = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val e = array.getJSONArray(i)
                if (e.optString(3) == "tellows" && ahora >= tellowsHasta) continue
                if (e.optString(3) != "tellows" && ahora >= comunidadHasta) continue
                if (e.optString(3) != "tellows" && e.optInt(2, 0) < 3) continue
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
        if (!SpamRules.numeroMexicano(numero)) return null
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
            val conexion = URL("$base/lista?fuentes=3").openConnection() as HttpURLConnection
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

    /** Identificador aleatorio de instalación; es seudónimo, no una garantía de anonimato. */
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
    fun retirarReportado(numero: String) {
        prefs.edit().putStringSet("reported", conjunto("reported") - SpamRules.huella(numero)).apply()
    }
    val totalReportados: Int get() = conjunto("reported").size
    fun tieneReporteLocal(numero: String) = SpamRules.huella(numero) in conjunto("local_reports")
    fun marcarReporteLocal(numero: String) {
        val h = SpamRules.huella(numero)
        if (h.isNotEmpty()) prefs.edit().putStringSet("local_reports", conjunto("local_reports") + h).apply()
    }

    /** "No es spam": la lista deja de afectar a ese número en este teléfono. */
    fun permitido(numero: String) = SpamRules.huella(numero).let { it.isNotEmpty() && it in conjunto("allowed") }
    fun permitir(numero: String, permitir: Boolean) {
        val h = SpamRules.huella(numero)
        if (h.isEmpty()) return
        val editor = prefs.edit().putStringSet("allowed", if (permitir) conjunto("allowed") + h else conjunto("allowed") - h)
        if (permitir) editor.putStringSet("local_reports", conjunto("local_reports") - h)
        editor.apply()
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
    fun errorReporte(context: Context, numero: String): String? {
        if (!SpamRules.reportable(numero)) return "Solo se pueden reportar números de México de 10 dígitos."
        return try {
            val ocultos = HiddenStore(context).contactsForReporting() + SesionEntrada.aOcultar()
            if (HiddenRules.matchesSender(ocultos, numero)) "Los números ocultos no se envían a la comunidad." else null
        } catch (_: Exception) { "No se pudo comprobar la privacidad del número. No se enviará." }
    }

    fun reportar(context: Context, numero: String, categoria: CategoriaSpam,
        conectar: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): String? {
        errorReporte(context, numero)?.let { return it }
        Prefs(context).setManualBlock(numero, true)
        SpamStore(context).permitir(numero, false)
        SpamStore(context).marcarReporteLocal(numero)
        val base = servidor(context) ?: return "Los reportes todavía no están configurados en esta versión."
        val store = SpamStore(context)
        if (store.reportado(numero)) return null
        val fallo = "No se pudo enviar el reporte. Revisa tu conexión e inténtalo de nuevo."
        return try {
            val cuerpo = JSONObject()
                .put("numero", numero)
                .put("categoria", categoria.codigo)
                .put("instalacion", store.instalacion)
            val conexion = conectar(URL("$base/reportar"))
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

    fun noEsSpamLocal(context: Context, numero: String) {
        SpamStore(context).permitir(numero, true)
        Prefs(context).setManualBlock(numero, false)
    }

    /** Retira solo el voto de esta instalación; "No es spam" ya se aplica localmente aunque falle la red. */
    fun retirar(context: Context, numero: String,
        conectar: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): String? {
        errorReporte(context, numero)?.let { return it }
        val store = SpamStore(context)
        if (!store.reportado(numero)) return null
        val base = servidor(context) ?: return "El servidor de reportes no está configurado."
        return try {
            val conexion = conectar(URL("$base/retirar"))
            try {
                conexion.requestMethod = "POST"
                conexion.connectTimeout = 15_000
                conexion.readTimeout = 20_000
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val cuerpo = JSONObject().put("numero", numero).put("instalacion", store.instalacion)
                conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
                if (conexion.responseCode != 200) return "No se pudo retirar tu reporte. Puedes reintentarlo."
                store.retirarReportado(numero)
                null
            } finally { conexion.disconnect() }
        } catch (_: Exception) { "No se pudo retirar tu reporte. Puedes reintentarlo." }
    }
}

