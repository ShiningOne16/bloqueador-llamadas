package com.ejemplo.bloqueador

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    // Se incrementa al volver a la app para refrescar estado e historial
    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    PantallaPrincipal(refresh.intValue) { refresh.intValue++ }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }
}

@Composable
fun PantallaPrincipal(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    val tieneRol = remember(refreshKey) { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val tieneContactos = remember(refreshKey) {
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val bloqueadas = remember(refreshKey) { prefs.blockedCalls() }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var blockHidden by remember { mutableStateOf(prefs.blockHidden) }
    var allowRepeat by remember { mutableStateOf(prefs.allowRepeat) }

    val pedirRol = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    val listo = tieneRol && tieneContactos

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Bloqueador de llamadas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Solo suenan las llamadas de tus contactos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (listo && enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            !listo -> "Falta configurar"
                            enabled -> "Protección activa"
                            else -> "Protección en pausa"
                        },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    if (!tieneRol) {
                        Text("1. Elige esta app como tu app de filtrado de llamadas.")
                        Button(onClick = {
                            pedirRol.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        }) { Text("Activar filtrado") }
                    }
                    if (!tieneContactos) {
                        Text("2. Permite leer contactos para saber quién es conocido. Sin esto no se bloquea nada.")
                        Button(onClick = { pedirContactos.launch(Manifest.permission.READ_CONTACTS) }) {
                            Text("Permitir contactos")
                        }
                    }
                    if (listo) Text("Todo listo. Puedes cerrar la app; el filtro sigue funcionando.")
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Ajuste("Bloquear desconocidos", "Interruptor principal", enabled) {
                        enabled = it; prefs.enabled = it
                    }
                    Ajuste("Bloquear números ocultos", "Privados o sin identificador", blockHidden) {
                        blockHidden = it; prefs.blockHidden = it
                    }
                    Ajuste(
                        "Dejar pasar si insisten",
                        "Si el mismo número vuelve a llamar en menos de 3 min, suena (por si es urgente)",
                        allowRepeat
                    ) { allowRepeat = it; prefs.allowRepeat = it }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bloqueadas (${bloqueadas.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (bloqueadas.isNotEmpty()) {
                    TextButton(onClick = { prefs.clearLog(); onChanged() }) { Text("Borrar") }
                }
            }
        }

        if (bloqueadas.isEmpty()) {
            item {
                Text(
                    "Aún no se ha bloqueado ninguna llamada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(bloqueadas) { call ->
            ListItem(
                headlineContent = { Text(call.number) },
                supportingContent = {
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(call.timestamp)))
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun Ajuste(titulo: String, detalle: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = { Text(detalle) },
        trailingContent = { Switch(checked = valor, onCheckedChange = onCambio) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
