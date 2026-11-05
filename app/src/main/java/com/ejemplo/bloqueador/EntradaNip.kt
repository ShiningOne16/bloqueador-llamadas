package com.ejemplo.bloqueador

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** Cómo está la sesión actual respecto al NIP de entrada. */
enum class ModoEntrada {
    /** Falta escribir el NIP. */
    BLOQUEADA,

    /** NIP correcto (o función apagada): se ve todo. */
    NORMAL,

    /** NIP incorrecto: la app abre, pero sin los contactos de la lista "a ocultar". */
    FALSO
}

/** Sin dependencias de Android: se prueba con valores concretos. */
object EntradaRules {
    /** Tiempo en segundo plano a partir del cual se vuelve a pedir el NIP. */
    const val RELOCK_MS = 30_000L

    /** [segundoPlanoDesde] es 0 si la app no estuvo en segundo plano. Un reloj que retrocede también bloquea. */
    fun debeBloquear(segundoPlanoDesde: Long, ahora: Long): Boolean =
        segundoPlanoDesde > 0L && (ahora - segundoPlanoDesde) !in 0L until RELOCK_MS

    /**
     * Resultado de escribir un NIP. Con un NIP incorrecto solo se entra en modo falso si hay contactos
     * que ocultar; si la lista está vacía, entrar no ocultaría nada y se rechaza (null).
     */
    fun modoTrasNip(correcto: Boolean, hayContactosAOcultar: Boolean): ModoEntrada? = when {
        correcto -> ModoEntrada.NORMAL
        hayContactosAOcultar -> ModoEntrada.FALSO
        else -> null
    }

    fun claves(persistentes: Set<String>, aOcultar: List<HiddenContact>): Set<String> =
        persistentes + HiddenRules.keys(aOcultar)

    fun contactos(persistentes: List<HiddenContact>, aOcultar: List<HiddenContact>): List<HiddenContact> =
        persistentes + aOcultar
}

/**
 * NIP de entrada (función Premium). El NIP se guarda solo como hash con sal y la lista de contactos a
 * ocultar va cifrada con su propia llave; en disco no queda ningún número ni nombre legible.
 */
class EntradaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("entrada_nip", Context.MODE_PRIVATE)
    private val crypto = HiddenCrypto("bloqueador_entrada_v1")

    /** La función está activa mientras exista un NIP. */
    fun activo() = prefs.contains("pin_hash")

    fun configurarNip(pin: String) {
        require(HiddenRules.validPin(pin)) { "El NIP debe tener ${HiddenRules.PIN_LENGTH} dígitos." }
        val salt = HiddenRules.newSalt()
        val iterations = HiddenRules.iterations()
        val hash = HiddenRules.hashPin(pin, salt, iterations)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("pin_iter", iterations)
            .commit()
    }

    fun verificar(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        val hash = prefs.getString("pin_hash", null) ?: return false
        return HiddenRules.verifyPin(
            pin, Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP),
            prefs.getInt("pin_iter", HiddenRules.iterations())
        )
    }

    /** Apaga la función. La lista de contactos a ocultar se conserva por si se vuelve a activar. */
    fun quitarNip() {
        prefs.edit().remove("pin_salt").remove("pin_hash").remove("pin_iter").commit()
    }

    // ---------------- Contactos a ocultar ----------------

    fun aOcultar(): List<HiddenContact> {
        val blob = prefs.getString("data", null) ?: return emptyList()
        val raw = crypto.decrypt(blob) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val numbers = o.getJSONArray("numbers")
                HiddenContact(
                    name = o.optString("name"),
                    numbers = (0 until numbers.length()).map { numbers.getString(it) },
                    contactId = o.optLong("id", -1L),
                    lookupKey = o.optString("lookup")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun escribir(lista: List<HiddenContact>) {
        val array = JSONArray()
        lista.forEach { c ->
            array.put(
                JSONObject().put("name", c.name).put("numbers", JSONArray(c.numbers))
                    .put("id", c.contactId).put("lookup", c.lookupKey)
            )
        }
        prefs.edit().putString("data", crypto.encrypt(array.toString())).commit()
    }

    fun agregar(contacto: HiddenContact) {
        val actual = aOcultar().filterNot { it.contactId > 0 && it.contactId == contacto.contactId }
        escribir(actual + contacto)
    }

    fun quitar(contacto: HiddenContact) {
        escribir(aOcultar().filterNot { it == contacto })
    }
}

/**
 * Estado de la sesión actual. Vive en memoria: al cerrar la app (o si el sistema la termina) se vuelve
 * a pedir el NIP, y también al volver tras [EntradaRules.RELOCK_MS] en segundo plano.
 */
object SesionEntrada {
    // El estado de Compose hace que la pantalla cambie sola; el campo simple lo leen los hilos de carga.
    private val estado = mutableStateOf(ModoEntrada.BLOQUEADA)
    @Volatile private var modoActual = ModoEntrada.BLOQUEADA
    @Volatile private var aOcultarActivos: List<HiddenContact> = emptyList()
    @Volatile private var iniciada = false
    @Volatile private var segundoPlanoDesde = 0L

    /** Para la interfaz: cambia la composición cuando cambia el modo. */
    val modo: ModoEntrada get() = estado.value

    private fun poner(nuevo: ModoEntrada) {
        modoActual = nuevo
        estado.value = nuevo
    }

    /** Solo la primera vez en este proceso: girar la pantalla no vuelve a pedir el NIP. */
    @Synchronized
    fun iniciar(context: Context) {
        if (iniciada) return
        iniciada = true
        poner(if (EntradaStore(context).activo()) ModoEntrada.BLOQUEADA else ModoEntrada.NORMAL)
    }

    fun alSalir() {
        segundoPlanoDesde = SystemClock.elapsedRealtime()
    }

    fun alVolver(context: Context) {
        val desde = segundoPlanoDesde
        segundoPlanoDesde = 0L
        if (modoActual == ModoEntrada.BLOQUEADA) return
        if (EntradaRules.debeBloquear(desde, SystemClock.elapsedRealtime()) && EntradaStore(context).activo()) {
            aOcultarActivos = emptyList()
            poner(ModoEntrada.BLOQUEADA)
        }
    }

    fun abrirNormal() {
        aOcultarActivos = emptyList()
        poner(ModoEntrada.NORMAL)
    }

    fun entrarFalso(aOcultar: List<HiddenContact>) {
        aOcultarActivos = aOcultar
        poner(ModoEntrada.FALSO)
    }

    /** Contactos que esta sesión debe esconder (vacío salvo en modo falso). */
    fun aOcultar(): List<HiddenContact> = if (modoActual == ModoEntrada.FALSO) aOcultarActivos else emptyList()
}

/**
 * Lo que la interfaz debe esconder: los contactos ocultos de siempre y, en una sesión con NIP incorrecto,
 * también los de la lista "a ocultar". Es solo para mostrar: el bloqueo de llamadas y SMS no la usa, así
 * que esos contactos siguen funcionando como siempre.
 */
object VistaOculta {
    fun claves(context: Context): Set<String> {
        val base = HiddenStore(context).hiddenKeys()
        val extra = SesionEntrada.aOcultar()
        return if (extra.isEmpty()) base else EntradaRules.claves(base, extra)
    }

    fun contactos(context: Context): List<HiddenContact> {
        val base = HiddenStore(context).contacts()
        val extra = SesionEntrada.aOcultar()
        return if (extra.isEmpty()) base else EntradaRules.contactos(base, extra)
    }
}
