package com.ejemplo.bloqueador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class VistaTelefono(val titulo: String, val icono: ImageVector) {
    RECIENTES("Recientes", Icons.Outlined.History),
    CONTACTOS("Contactos", Icons.Outlined.Contacts),
    FAVORITOS("Favoritos", Icons.Outlined.FavoriteBorder),
    MARCAR("Marcar", Icons.Outlined.Dialpad)
}

/**
 * Submenú del teléfono: tres accesos agrupados en una barra tipo píldora y el marcador aparte.
 * Igual que Google Teléfono, Samsung y iOS, cada acceso lleva ícono y texto (no solo ícono).
 */
@Composable
fun PantallaTelefono(refreshKey: Int, pedirPermisos: () -> Unit, irAProteccion: () -> Unit) {
    var vista by rememberSaveable { mutableStateOf(VistaTelefono.RECIENTES) }
    val pantallas = rememberSaveableStateHolder()

    BackHandler(enabled = vista != VistaTelefono.RECIENTES) { vista = VistaTelefono.RECIENTES }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            pantallas.SaveableStateProvider(vista.name) {
                when (vista) {
                    VistaTelefono.RECIENTES -> PantallaRecientes(refreshKey, pedirPermisos, irAProteccion)
                    VistaTelefono.CONTACTOS -> PantallaContactos(refreshKey, pedirPermisos)
                    VistaTelefono.FAVORITOS -> PantallaContactos(refreshKey, pedirPermisos, soloFavoritos = true)
                    VistaTelefono.MARCAR -> PantallaTeclado(refreshKey)
                }
            }
        }

        Row(
            Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Esp.l, vertical = Esp.s)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Esp.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 3.dp
            ) {
                Row(Modifier.padding(Esp.xs), horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
                    listOf(VistaTelefono.RECIENTES, VistaTelefono.CONTACTOS, VistaTelefono.FAVORITOS).forEach { destino ->
                        val activo = vista == destino
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (activo) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = if (activo) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .selectable(selected = activo, role = Role.Tab, onClick = { vista = destino })
                        ) {
                            Column(
                                Modifier.padding(horizontal = Esp.xs, vertical = Esp.s),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(destino.icono, contentDescription = null, modifier = Modifier.size(24.dp))
                                Text(
                                    destino.titulo,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 64.dp).clip(RoundedCornerShape(24.dp))
                    .selectable(selected = vista == VistaTelefono.MARCAR, role = Role.Tab, onClick = {
                        vista = if (vista == VistaTelefono.MARCAR) VistaTelefono.RECIENTES else VistaTelefono.MARCAR
                    })
            ) {
                Column(
                    Modifier.padding(horizontal = Esp.s, vertical = Esp.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.Dialpad, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        VistaTelefono.MARCAR.titulo,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
