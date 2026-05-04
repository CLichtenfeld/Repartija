package com.example.repartija.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.repartija.R
import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.ExpensePayer
import com.example.repartija.data.model.ExpenseSplit
import com.example.repartija.data.model.Payment
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

sealed class Movement {
    abstract val date: String
    abstract val amount: Double
    abstract val description: String

    data class Exp(val expense: Expense) : Movement() {
        override val date = expense.date
        override val amount = expense.amount
        override val description = expense.description
    }

    data class Pay(val payment: Payment) : Movement() {
        override val date = payment.date
        override val amount = payment.amount
        override val description = "Pago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: DebtViewModel) {
    val expensesRes by viewModel.currentExpenses.collectAsState()
    val paymentsRes by viewModel.currentPayments.collectAsState()
    val membersRes by viewModel.currentMembers.collectAsState()
    val debts by viewModel.currentDebts.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val allSplitsRes by viewModel.currentSplits.collectAsState()
    val allPayersRes by viewModel.currentPayers.collectAsState()

    val expenses = (expensesRes as? DataResult.Success)?.data ?: emptyList()
    val payments = (paymentsRes as? DataResult.Success)?.data ?: emptyList()
    val members = (membersRes as? DataResult.Success)?.data ?: emptyList()
    val memberMap = members.associateBy { it.id }

    val movements = remember(expenses, payments, currentUserId) {
        val list = mutableListOf<Movement>()
        expenses.forEach { list.add(Movement.Exp(it)) }
        payments.filter {
            it.fromUser == currentUserId || it.toUser == currentUserId
        }.forEach { list.add(Movement.Pay(it)) }
        list.sortedByDescending { it.date }
    }

    val totalOwedToMe = debts.filter { it.toUserId == currentUserId }.sumOf { it.amount }
    val totalIOwe = debts.filter { it.fromUserId == currentUserId }.sumOf { it.amount }
    val globalBalance = totalOwedToMe - totalIOwe

    var selectedMovement by remember { mutableStateOf<Movement?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var movementToDelete by remember { mutableStateOf<Movement?>(null) }
    var movementToEdit by remember { mutableStateOf<Movement?>(null) }
    var showNoPermissionAlert by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HistoryHeader(globalBalance)
        Spacer(modifier = Modifier.height(16.dp))

        if (movements.isEmpty()) {
            EmptyHistoryMessage()
        } else {
            MovementList(
                movements = movements,
                currentUserId = currentUserId,
                memberMap = memberMap,
                viewModel = viewModel,
                onMoveClick = {
                    selectedMovement = it
                    showActionSheet = true
                }
            )
        }
    }

    // Sheet and Dialogs logic
    if (showActionSheet && selectedMovement != null) {
        val move = selectedMovement!!
        val isCreator = isUserCreator(move, currentUserId)

        ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
            MovementActionSheetContent(
                move = move,
                isCreator = isCreator,
                onEdit = { 
                    showActionSheet = false
                    movementToEdit = move 
                },
                onDelete = { 
                    showActionSheet = false
                    movementToDelete = move 
                },
                onNoPermission = {
                    showActionSheet = false
                    showNoPermissionAlert = true
                }
            )
        }
    }

    if (movementToDelete != null) {
        DeleteMovementDialog(
            onDismiss = { movementToDelete = null },
            onConfirm = {
                val move = movementToDelete!!
                when (move) {
                    is Movement.Exp -> viewModel.deleteExpense(move.expense.id)
                    is Movement.Pay -> viewModel.deletePayment(move.payment.id)
                }
                movementToDelete = null
            }
        )
    }

    if (showNoPermissionAlert) {
        NoPermissionDialog(onDismiss = { showNoPermissionAlert = false })
    }

    movementToEdit?.let { move ->
        when (move) {
            is Movement.Exp -> {
                val expSplits = (allSplitsRes as? DataResult.Success)?.data?.filter { it.expenseId == move.expense.id } ?: emptyList()
                val expPayers = (allPayersRes as? DataResult.Success)?.data?.filter { it.expenseId == move.expense.id } ?: emptyList()
                
                EditExpenseDialog(
                    expense = move.expense,
                    members = members,
                    currentPayers = expPayers,
                    currentSplits = expSplits,
                    onDismiss = { movementToEdit = null },
                    onConfirm = { desc, amount, payers, splits, type ->
                        viewModel.updateExpense(move.expense, desc, amount, payers, splits, type)
                        movementToEdit = null
                    }
                )
            }
            is Movement.Pay -> {
                EditPaymentDialog(
                    payment = move.payment,
                    onDismiss = { movementToEdit = null },
                    onConfirm = { amount ->
                        viewModel.updatePayment(move.payment, amount)
                        movementToEdit = null
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(globalBalance: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Historial", style = MaterialTheme.typography.headlineMedium)
        val balanceColor = if (globalBalance > 0.01) MaterialTheme.colorScheme.primary 
                        else if (globalBalance < -0.01) MaterialTheme.colorScheme.error 
                        else Color.Gray
        Column(horizontalAlignment = Alignment.End) {
            Text("Balance Global", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(
                text = "$ ${String.format("%.2f", abs(globalBalance))}",
                color = balanceColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyHistoryMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No hay movimientos registrados.")
    }
}

@Composable
private fun MovementList(
    movements: List<Movement>,
    currentUserId: String?,
    memberMap: Map<String, Profile>,
    viewModel: DebtViewModel,
    onMoveClick: (Movement) -> Unit
) {
    LazyColumn {
        items(movements) { move ->
            MovementCard(move, currentUserId, memberMap, viewModel, onMoveClick)
        }
    }
}

@Composable
private fun MovementCard(
    move: Movement,
    currentUserId: String?,
    memberMap: Map<String, Profile>,
    viewModel: DebtViewModel,
    onMoveClick: (Movement) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onMoveClick(move) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                MovementCardHeader(move, currentUserId, memberMap, viewModel)
                MovementCardFooter(move)
            }
        }
    }
}

@Composable
private fun MovementCardHeader(
    move: Movement,
    currentUserId: String?,
    memberMap: Map<String, Profile>,
    viewModel: DebtViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (userId, icon) = when (move) {
            is Movement.Exp -> move.expense.paidBy to Icons.AutoMirrored.Filled.ReceiptLong
            is Movement.Pay -> move.payment.fromUser to Icons.Default.CheckCircle
        }
        val profile = memberMap[userId]
        val avatarUrl = viewModel.getAvatarUrl(userId, profile?.avatarUrl)

        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            error = painterResource(id = R.drawable.group_avatar)
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = move.description, fontWeight = FontWeight.Bold)
            val subText = getMovementSubtext(move, currentUserId, memberMap)
            Text(text = subText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MovementCardFooter(move: Movement) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$ ${String.format("%.2f", move.amount)}",
                color = if (move is Movement.Pay) MaterialTheme.colorScheme.primary else Color.Unspecified,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val formattedDate = remember(move.date) {
                try {
                    if (move.date.contains("T")) {
                        val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
                        val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val dt = ZonedDateTime.parse(move.date)
                        dt.format(outputFormatter)
                    } else {
                        // For simple YYYY-MM-DD
                        val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
                        val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                        val dt = java.time.LocalDate.parse(move.date)
                        dt.format(outputFormatter)
                    }
                } catch (e: Exception) {
                    move.date
                }
            }
            Text(text = formattedDate, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun MovementActionSheetContent(
    move: Movement,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNoPermission: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()) {
        Text(
            text = move.description,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        ListItem(
            modifier = Modifier.clickable { if (isCreator) onEdit() else onNoPermission() },
            headlineContent = { Text("Editar") },
            leadingContent = { Icon(Icons.Default.Edit, null) }
        )
        ListItem(
            modifier = Modifier.clickable { if (isCreator) onDelete() else onNoPermission() },
            headlineContent = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

private fun isUserCreator(move: Movement, currentUserId: String?): Boolean {
    return when (move) {
        is Movement.Exp -> move.expense.paidBy == currentUserId
        is Movement.Pay -> move.payment.fromUser == currentUserId
    }
}

private fun getMovementSubtext(
    move: Movement,
    currentUserId: String?,
    memberMap: Map<String, Profile>
): String {
    return when (move) {
        is Movement.Exp -> {
            val payer = memberMap[move.expense.paidBy]?.displayName ?: "Alguien"
            "Pagado por $payer"
        }
        is Movement.Pay -> {
            val fromName = memberMap[move.payment.fromUser]?.displayName ?: "Alguien"
            val toName = memberMap[move.payment.toUser]?.displayName ?: "Alguien"
            if (move.payment.fromUser == currentUserId) "Pagaste a $toName"
            else "Te pagó $fromName"
        }
    }
}

@Composable
private fun DeleteMovementDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar movimiento?") },
        text = { Text("Esta acción eliminará el registro permanentemente.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun NoPermissionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sin permiso") },
        text = { Text("Solo el creador del movimiento puede editarlo o eliminarlo.") },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Entendido") }
        }
    )
}
