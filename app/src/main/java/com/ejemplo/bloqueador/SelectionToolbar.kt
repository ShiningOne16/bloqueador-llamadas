package com.ejemplo.bloqueador

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SelectionToolbar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit, enabled: Boolean = true, canDelete: Boolean = true) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = enabled) { Icon(Icons.Filled.Close, contentDescription = "Cancelar selección") }
            Text("$count seleccionados", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onSelectAll, enabled = enabled) { Icon(Icons.Filled.SelectAll, contentDescription = "Seleccionar todos los elementos visibles") }
            IconButton(onClick = onDelete, enabled = enabled && canDelete && count > 0) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar seleccionados") }
        }
    }
}
