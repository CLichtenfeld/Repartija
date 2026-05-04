package com.example.repartija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.ExpensePayer
import com.example.repartija.data.model.ExpenseSplit
import com.example.repartija.data.model.Payment
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult

enum class SplitType {
    EQUALLY, PERCENTAGE, EXACT
}

@Composable
fun EditExpenseDialog(
    expense: Expense,
    members: List<Profile>,
    currentPayers: List<ExpensePayer>,
    currentSplits: List<ExpenseSplit>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, List<ExpensePayer>, List<ExpenseSplit>, SplitType) -> Unit
) {
    ExpenseDialog(
        members = members,
        initialExpense = expense,
        initialPayers = currentPayers,
        initialSplits = currentSplits,
        onDismiss = onDismiss,
        onConfirm = { desc, amount, _, splits, type, payers ->
            onConfirm(desc, amount, payers, splits, type)
        },
        title = "Editar Gasto"
    )
}

@Composable
fun EditPaymentDialog(
    payment: Payment,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountValue by remember { mutableStateOf(TextFieldValue(
        text = String.format(java.util.Locale.US, "%.2f", payment.amount)
    )) }
    val amountStr = amountValue.text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Pago") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = it },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused) {
                            amountValue = amountValue.copy(selection = TextRange(0, amountValue.text.length))
                        }
                    }
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
    initialExpense: Expense? = null,
    initialPayers: List<ExpensePayer>? = null,
    initialSplits: List<ExpenseSplit>? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, List<ExpenseSplit>, SplitType, List<ExpensePayer>) -> Unit,
    title: String = "Nuevo Gasto"
) {
    var descriptionValue by remember { mutableStateOf(TextFieldValue(initialExpense?.description ?: "")) }
    val description = descriptionValue.text
    var amountValue by remember { mutableStateOf(TextFieldValue(
        text = initialExpense?.amount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
    )) }
    val amountStr = amountValue.text
    var splitType by remember { mutableStateOf(
        initialExpense?.splitType?.let { SplitType.valueOf(it) } ?: SplitType.EQUALLY
    ) }

    // Multi-payer state
    val payersMap = remember { mutableStateMapOf<String, Double>() }
    // Split state
    val splitsMap = remember { mutableStateMapOf<String, Double>() }
    val splitParticipation = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(members, initialExpense, initialPayers, initialSplits) {
        if (initialExpense == null) {
            // New expense defaults
            members.forEach {
                splitsMap[it.id] = 0.0
                splitParticipation[it.id] = true
                payersMap[it.id] = 0.0
            }
            if (members.isNotEmpty()) {
                payersMap[members.first().id] = 0.0 // Will be set when amount is typed
            }
        } else {
            // Edit mode
            members.forEach { m ->
                val payer = initialPayers?.find { it.userId == m.id }
                payersMap[m.id] = payer?.amount ?: 0.0
                
                val split = initialSplits?.find { it.userId == m.id }
                splitsMap[m.id] = split?.amount ?: 0.0
                splitParticipation[m.id] = split != null
            }
        }
    }

    val totalAmount = amountStr.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(
                        value = descriptionValue,
                        onValueChange = { descriptionValue = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) {
                                descriptionValue = descriptionValue.copy(selection = TextRange(0, descriptionValue.text.length))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountValue,
                        onValueChange = { 
                            amountValue = it
                            val amount = it.text.replace(",", ".").toDoubleOrNull() ?: 0.0
                            // If single payer and it was 0 or old amount, update it
                            if (payersMap.values.count { it > 0 } <= 1) {
                                val currentPayer = payersMap.entries.find { it.value > 0 }?.key 
                                    ?: members.firstOrNull()?.id
                                if (currentPayer != null) {
                                    payersMap[currentPayer] = amount
                                }
                            }
                        },
                        label = { Text("Monto Total") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) {
                                amountValue = amountValue.copy(selection = TextRange(0, amountValue.text.length))
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pagado por:", style = MaterialTheme.typography.titleSmall)
                }

                items(members) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = payersMap[member.id]?.let { it > 0 } ?: false,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (payersMap.values.sum() == 0.0) {
                                        payersMap[member.id] = totalAmount
                                    } else {
                                        payersMap[member.id] = 0.01 // Small non-zero
                                    }
                                } else {
                                    payersMap[member.id] = 0.0
                                }
                            }
                        )
                        Text(member.displayName, modifier = Modifier.weight(1f))
                        if (payersMap[member.id]?.let { it > 0 } == true) {
                            var pAmountValue by remember(payersMap[member.id]) { 
                                mutableStateOf(TextFieldValue(
                                    text = String.format(java.util.Locale.US, "%.2f", payersMap[member.id] ?: 0.0)
                                )) 
                            }
                            OutlinedTextField(
                                value = pAmountValue,
                                onValueChange = {
                                    pAmountValue = it
                                    payersMap[member.id] = it.text.replace(",", ".").toDoubleOrNull() ?: 0.0
                                },
                                modifier = Modifier.width(100.dp).onFocusChanged {
                                    if (it.isFocused) {
                                        pAmountValue = pAmountValue.copy(selection = TextRange(0, pAmountValue.text.length))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                label = { Text("$", fontSize = 10.sp) }
                            )
                        }
                    }
                }

                item {
                    val totalPaid = payersMap.values.sum()
                    val paidDiff = totalAmount - totalPaid
                    val isPaidOk = Math.abs(paidDiff) < 0.01

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPaidOk) "Pagos: OK ✓" else "Faltan pagar: $ ${String.format("%.2f", paidDiff)}",
                            color = if (isPaidOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Modo de división:", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = splitType == SplitType.EQUALLY,
                            onClick = { splitType = SplitType.EQUALLY },
                            label = { Text("Igual") }
                        )
                        FilterChip(
                            selected = splitType == SplitType.PERCENTAGE,
                            onClick = { splitType = SplitType.PERCENTAGE },
                            label = { Text("%") }
                        )
                        FilterChip(
                            selected = splitType == SplitType.EXACT,
                            onClick = { splitType = SplitType.EXACT },
                            label = { Text("Monto") }
                        )
                    }
                    
                    val currentSplitTotal = when(splitType) {
                        SplitType.EQUALLY -> totalAmount
                        SplitType.PERCENTAGE -> splitsMap.values.sum()
                        SplitType.EXACT -> splitsMap.values.sum()
                    }
                    val splitDiff = if (splitType == SplitType.PERCENTAGE) 100.0 - currentSplitTotal else totalAmount - currentSplitTotal
                    val isSplitOk = splitType == SplitType.EQUALLY || Math.abs(splitDiff) < 0.01

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSplitOk) "División: OK ✓" else "Diferencia: ${if (splitType == SplitType.PERCENTAGE) String.format("%.1f%%", splitDiff) else String.format("$ %.2f", splitDiff)}",
                            color = if (isSplitOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(members) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = splitParticipation[member.id] ?: false,
                            onCheckedChange = { splitParticipation[member.id] = it }
                        )
                        Text(member.displayName, modifier = Modifier.weight(1f))
                        
                        if (splitParticipation[member.id] == true && splitType != SplitType.EQUALLY) {
                            var sValueValue by remember(splitsMap[member.id], splitType) { 
                                mutableStateOf(TextFieldValue(
                                    text = String.format(java.util.Locale.US, "%.2f", splitsMap[member.id] ?: 0.0)
                                )) 
                            }
                            OutlinedTextField(
                                value = sValueValue,
                                onValueChange = {
                                    sValueValue = it
                                    splitsMap[member.id] = it.text.replace(",", ".").toDoubleOrNull() ?: 0.0
                                },
                                modifier = Modifier.width(100.dp).onFocusChanged {
                                    if (it.isFocused) {
                                        sValueValue = sValueValue.copy(selection = TextRange(0, sValueValue.text.length))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                label = { Text(if (splitType == SplitType.PERCENTAGE) "%" else "$", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalAmount = amountStr.toDoubleOrNull() ?: 0.0
                val totalPaid = payersMap.values.sum()
                
                if (description.isNotBlank() && finalAmount > 0 && Math.abs(totalPaid - finalAmount) < 0.1) {
                    val participatingMembers = members.filter { splitParticipation[it.id] == true }
                    if (participatingMembers.isNotEmpty()) {
                        val finalSplits = when (splitType) {
                            SplitType.EQUALLY -> {
                                val share = finalAmount / participatingMembers.size
                                participatingMembers.map { ExpenseSplit("", it.id, share) }
                            }
                            SplitType.PERCENTAGE -> {
                                participatingMembers.map { 
                                    val percent = splitsMap[it.id] ?: 0.0
                                    ExpenseSplit("", it.id, (percent / 100.0) * finalAmount)
                                }
                            }
                            SplitType.EXACT -> {
                                participatingMembers.map { 
                                    ExpenseSplit("", it.id, splitsMap[it.id] ?: 0.0)
                                }
                            }
                        }
                        
                        val finalPayers = payersMap.filter { it.value > 0 }.map { 
                            ExpensePayer("", it.key, it.value)
                        }
                        
                        // We take the first payer as the "main" one for the legacy field
                        val mainPayerId = finalPayers.firstOrNull()?.userId ?: members.first().id
                        
                        onConfirm(description, finalAmount, mainPayerId, finalSplits, splitType, finalPayers)
                    }
                }
            }) { Text(if (initialExpense == null) "Agregar" else "Guardar") }
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
    var emailQueryValue by remember { mutableStateOf(TextFieldValue("")) }
    val emailQuery = emailQueryValue.text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar por Email") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = emailQueryValue,
                    onValueChange = { emailQueryValue = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused) {
                            emailQueryValue = emailQueryValue.copy(selection = TextRange(0, emailQueryValue.text.length))
                        }
                    }
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
