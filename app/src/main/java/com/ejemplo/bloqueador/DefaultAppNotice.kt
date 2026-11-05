package com.ejemplo.bloqueador

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Aviso breve y pulsable para elegir la app en Android. */
@Composable
fun AvisoAppPredeterminada(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            Modifier.padding(horizontal = Esp.l, vertical = Esp.m),
            horizontalArrangement = Arrangement.spacedBy(Esp.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp))
            Text(texto, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun AvisoFiltroLlamadas(refreshKey: Int) {
    val context = LocalContext.current
    val roles = remember { context.getSystemService(RoleManager::class.java) }
    var revision by remember { mutableIntStateOf(0) }
    val disponible = remember(refreshKey, revision) { roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) }
    val elegido = remember(refreshKey, revision) { roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val solicitud = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
    fun abrirAjustes() {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Abre Ajustes → Apps predeterminadas para elegir Bloqueador.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Abre Ajustes → Apps predeterminadas para elegir Bloqueador.", Toast.LENGTH_LONG).show()
        }
    }

    if (disponible && !elegido) {
        AvisoAppPredeterminada(
            "Establecer Bloqueador para filtrar llamadas",
            onClick = {
                try {
                    solicitud.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                } catch (_: ActivityNotFoundException) {
                    abrirAjustes()
                } catch (_: SecurityException) {
                    abrirAjustes()
                }
            },
            modifier = Modifier.padding(horizontal = Esp.l, vertical = Esp.s)
        )
    }
}
