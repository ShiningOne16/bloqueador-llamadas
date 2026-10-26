package com.ejemplo.bloqueador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

enum class Seccion(val titulo: String, val icono: ImageVector) {
    RECIENTES("Recientes", Icons.Default.Refresh),
    CONTACTOS("Contactos", Icons.Default.Person),
    TECLADO("Teclado", Icons.Default.Call),
    MENSAJES("Mensajes", Icons.Default.Email),
    BLOQUEO("Bloqueo", Icons.Default.Lock)
}

class MainActivity : ComponentActivity() {

    // Se incrementa al volver a la app para recargar registro, contactos y mensajes
    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(refresh.intValue) { refresh.intValue++ }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }
}

@Composable
fun App(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var seccion by rememberSaveable { mutableStateOf(Seccion.RECIENTES) }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val pedirPermisos = {
        val faltan = PhoneData.missing(context)
        if (faltan.isEmpty()) onChanged() else pedir.launch(faltan.toTypedArray())
    }

    // Al abrir por primera vez, pide todos los permisos de una vez
    var yaPedido by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!yaPedido) {
            yaPedido = true
            if (PhoneData.missing(context).isNotEmpty()) pedirPermisos()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Seccion.entries.forEach { s ->
                    NavigationBarItem(
                        selected = seccion == s,
                        onClick = { seccion = s },
                        icon = { Icon(s.icono, contentDescription = null) },
                        label = { Text(s.titulo) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (seccion) {
                Seccion.RECIENTES -> PantallaRecientes(refreshKey, pedirPermisos)
                Seccion.CONTACTOS -> PantallaContactos(refreshKey, pedirPermisos)
                Seccion.TECLADO -> PantallaTeclado(refreshKey)
                Seccion.MENSAJES -> PantallaMensajes(refreshKey, pedirPermisos)
                Seccion.BLOQUEO -> PantallaBloqueo(refreshKey, onChanged)
            }
        }
    }
}
