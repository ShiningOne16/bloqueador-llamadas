package com.ejemplo.bloqueador

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

private class ColoresAnuncio(val fondo: Int, val borde: Int, val texto: Int, val suave: Int, val etiqueta: Int, val boton: Int, val sobreBoton: Int)

/** Anuncio nativo con el aspecto de una tarjeta de la app; siempre marcado como "Anuncio". */
@Composable
fun AnuncioNativo(lugar: Int, modifier: Modifier = Modifier) {
    if (!Anuncios.activos) return
    val context = LocalContext.current
    val listo = Anuncios.listo.value
    var anuncio by remember(lugar) { mutableStateOf(Anuncios.nativo(lugar)) }
    LaunchedEffect(listo, lugar) {
        if (listo && anuncio == null) Anuncios.cargarNativo(context, lugar) { anuncio = it }
    }
    val actual = anuncio ?: return
    val m = MaterialTheme.colorScheme
    val colores = ColoresAnuncio(
        m.surfaceContainerLowest.toArgb(), m.outlineVariant.toArgb(), m.onSurface.toArgb(),
        m.onSurfaceVariant.toArgb(), m.surfaceContainerHigh.toArgb(), LocalColoresExtra.current.marca.toArgb(),
        LocalColoresExtra.current.onMarca.toArgb()
    )
    AndroidView(
        factory = { ctx -> crearVistaNativa(ctx, colores) },
        update = { vista -> poblar(vista, actual) },
        modifier = modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.s)
    )
}

private fun crearVistaNativa(ctx: Context, c: ColoresAnuncio): NativeAdView {
    val d = ctx.resources.displayMetrics.density
    fun dp(v: Int) = (v * d).toInt()
    fun fondo(color: Int, radio: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radio * d }

    val media = MediaView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160))
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }
    val icono = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val etiqueta = TextView(ctx).apply {
        text = "Anuncio"
        textSize = 11f
        setTextColor(c.suave)
        background = fondo(c.etiqueta, 6f)
        setPadding(dp(6), dp(1), dp(6), dp(1))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
    }
    val titulo = TextView(ctx).apply {
        textSize = 16f
        setTextColor(c.texto)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val cuerpo = TextView(ctx).apply {
        textSize = 14f
        setTextColor(c.suave)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val boton = Button(ctx).apply {
        textSize = 14f
        isAllCaps = false
        setTextColor(c.sobreBoton)
        background = fondo(c.boton, 24f)
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    val fila = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(etiqueta)
        addView(titulo)
    }
    val textos = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(fila)
        addView(cuerpo)
    }
    val pie = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(icono)
        addView(textos)
    }
    val accion = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(dp(16), 0, dp(16), dp(12))
        addView(boton)
    }
    val raiz = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(c.fondo); cornerRadius = 20f * d; setStroke(dp(1), c.borde)
        }
        clipToOutline = true
        addView(media)
        addView(pie)
        addView(accion)
    }
    return NativeAdView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(raiz)
        mediaView = media
        iconView = icono
        headlineView = titulo
        bodyView = cuerpo
        callToActionView = boton
    }
}

private fun poblar(vista: NativeAdView, ad: NativeAd) {
    (vista.headlineView as TextView).text = ad.headline
    (vista.bodyView as TextView).apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (vista.callToActionView as Button).apply {
        text = ad.callToAction
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (vista.iconView as ImageView).apply {
        val icono = ad.icon
        if (icono == null) visibility = View.GONE else { setImageDrawable(icono.drawable); visibility = View.VISIBLE }
    }
    vista.mediaView?.setMediaContent(ad.mediaContent)
    vista.setNativeAd(ad)
}

/** Banner adaptable anclado; no ocupa espacio hasta que AdMob responde. */
@Composable
fun AnuncioBanner(modifier: Modifier = Modifier) {
    if (!Anuncios.activos || !Anuncios.listo.value) return
    val ancho = LocalConfiguration.current.screenWidthDp
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, ancho))
                adUnitId = ctx.getString(R.string.admob_banner_id)
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier.fillMaxWidth(),
        onRelease = { it.destroy() }
    )
}
