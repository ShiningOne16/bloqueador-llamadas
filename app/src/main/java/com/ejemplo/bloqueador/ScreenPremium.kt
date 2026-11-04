package com.ejemplo.bloqueador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Presentación de Premium mientras se definen sus funciones y la oferta de pago. */
@Composable
fun PantallaPremium(onCerrar: () -> Unit) {
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(end = Esp.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCerrar, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Premium", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Esp.xl, vertical = Esp.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Esp.l)
        ) {
            IconoCirculo(
                Icons.Filled.WorkspacePremium,
                fondo = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary,
                tamano = 96.dp
            )
            Text(
                "Bloqueador Premium",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text("Próximamente", Modifier.padding(horizontal = Esp.l, vertical = Esp.s),
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Estamos preparando nuevas funciones para darte más control sobre tus llamadas y mensajes.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tarjeta {
                Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                    Text("Funciones en preparación", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aquí podrás conocer las funciones incluidas y su precio cuando Premium esté disponible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "Puedes seguir usando las funciones actuales de Bloqueador.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Volver a Bloqueador")
            }
        }
    }
}
