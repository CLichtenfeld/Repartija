package com.example.repartija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    version: String,
    progress: Float,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Actualización disponible") },
        text = {
            Column {
                Text("Hay una nueva versión disponible: $version")
                if (progress > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${(progress * 100).toInt()}%", modifier = Modifier.align(Alignment.End))
                }
            }
        },
        confirmButton = {
            if (progress == 0f) {
                Button(onClick = onUpdate) { Text("Actualizar ahora") }
            }
        }
    )
}
