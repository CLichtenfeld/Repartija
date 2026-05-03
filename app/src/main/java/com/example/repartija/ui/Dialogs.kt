package com.example.repartija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.Payment
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult

@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var description by remember { mutableStateOf(expense.description) }
    var amountStr by remember { mutableStateOf(expense.amount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Gasto") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (description.isNotBlank() && amount > 0) {
                    onConfirm(description, amount)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EditPaymentDialog(
    payment: Payment,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountStr by remember { mutableStateOf(payment.amount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Pago") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    onConfirm(amount)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    members: List<Profile>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, Map<String, Double>) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var paidByUserId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
    val shares = remember { mutableStateMapOf<String, Double>() }

    // Initialize shares equally
    LaunchedEffect(members) {
        members.forEach { shares[it.id] = 1.0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Gasto") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Monto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pagado por:", style = MaterialTheme.typography.titleSmall)
                }

                items(members) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paidByUserId == member.id,
                            onClick = { paidByUserId = member.id }
                        )
                        Text(member.displayName)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Dividir entre (proporción):", style = MaterialTheme.typography.titleSmall)
                }

                items(members) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(member.displayName, modifier = Modifier.weight(1f))
                        var shareStr by remember { mutableStateOf("1") }
                        OutlinedTextField(
                            value = shareStr,
                            onValueChange = {
                                shareStr = it
                                shares[member.id] = it.toDoubleOrNull() ?: 0.0
                            },
                            modifier = Modifier.width(60.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (description.isNotBlank() && amount > 0 && paidByUserId.isNotBlank()) {
                    val totalShares = shares.values.sum()
                    if (totalShares > 0) {
                        val finalShares = shares.mapValues { (_, s) -> (s / totalShares) * amount }
                        onConfirm(description, amount, paidByUserId, finalShares)
                    }
                }
            }) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddMemberDialog(
    searchResult: DataResult<Profile>?,
    onSearch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var emailQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar por Email") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = emailQuery,
                    onValueChange = { emailQuery = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSearch(emailQuery) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Buscar") }

                Spacer(modifier = Modifier.height(16.dp))

                when (searchResult) {
                    is DataResult.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    is DataResult.Error -> Text("Error: ${searchResult.message}", color = MaterialTheme.colorScheme.error)
                    is DataResult.Success -> {
                        val profile = searchResult.data
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.displayName, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onAdd(profile.id) }) {
                                Icon(Icons.Default.Add, "Agregar")
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
