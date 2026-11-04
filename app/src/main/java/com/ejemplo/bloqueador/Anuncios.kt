package com.ejemplo.bloqueador

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Anuncios de AdMob. Se piden solo después de que el usuario responda al mensaje de consentimiento
 * (si le corresponde) y nunca se muestran dentro de llamadas, PIN, Contactos ocultos ni Protección.
 * Los anuncios no reciben datos de llamadas, SMS ni contactos.
 */
object Anuncios {
    /** Cuando exista la compra de Premium, ponerlo en true para quitar todos los anuncios. */
    const val PREMIUM_COMPRADO = false
    val activos: Boolean get() = !PREMIUM_COMPRADO

    /** Verdadero cuando el SDK ya se inició y se pueden pedir anuncios. */
    val listo = mutableStateOf(false)

    private var iniciado = false
    private val nativos = HashMap<Int, NativeAd>()
    private val cargando = HashSet<Int>()

    fun iniciar(activity: Activity) {
        if (!activos) return
        val appContext = activity.applicationContext
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { inicializar(appContext) } },
            { inicializar(appContext) }
        )
        // Si ya hay consentimiento de una sesión anterior, no hace falta esperar a la red.
        inicializar(appContext)
    }

    @Synchronized
    private fun inicializar(context: Context) {
        if (iniciado) return
        if (!UserMessagingPlatform.getConsentInformation(context).canRequestAds()) return
        iniciado = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build()
        )
        Thread { MobileAds.initialize(context) { listo.value = true } }.start()
    }

    /** Anuncio nativo ya cargado para ese lugar de la lista, si lo hay (se reutiliza al volver a verlo). */
    fun nativo(lugar: Int): NativeAd? = nativos[lugar]

    fun cargarNativo(context: Context, lugar: Int, alTerminar: (NativeAd?) -> Unit) {
        if (!activos || !listo.value || lugar in cargando) return
        cargando += lugar
        val cargador = AdLoader.Builder(context.applicationContext, context.getString(R.string.admob_native_id))
            .forNativeAd { anuncio ->
                cargando -= lugar
                nativos.remove(lugar)?.destroy()
                nativos[lugar] = anuncio
                alTerminar(anuncio)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    cargando -= lugar
                    alTerminar(null)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        cargador.loadAd(AdRequest.Builder().build())
    }

    fun liberar() {
        nativos.values.forEach { it.destroy() }
        nativos.clear()
        cargando.clear()
    }
}
