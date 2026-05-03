package com.example.repartija.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.repartija.R
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult
import kotlin.math.abs

@Composable
fun BalancesScreen(viewModel: DebtViewModel, onMemberClick: (String) -> Unit) {
    val debts by viewModel.currentDebts.collectAsState()
    val membersRes by viewModel.currentMembers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

    val members = (membersRes as? DataResult.Success)?.data ?: emptyList()
    val memberMap = members.associateBy { it.id }

    var paymentToUserId by remember { mutableStateOf<String?>(null) }
    var paymentAmount by remember { mutableStateOf(0.0) }

    val otherMembers = members.filter { it.id != currentUserId }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Balances con el grupo", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (otherMembers.isEmpty()) {
            item { Text("Agrega otros miembros para ver balances.") }
        }

        items(otherMembers) { member ->
            val owesMe = debts.find { it.fromUserId == member.id && it.toUserId == currentUserId }?.amount ?: 0.0
            val iOwe = debts.find { it.fromUserId == currentUserId && it.toUserId == member.id }?.amount ?: 0.0
            val netBalance = owesMe - iOwe

            BalanceCard(
                member = member,
                netBalance = netBalance,
                onMemberClick = { onMemberClick(member.id) },
                onPayClick = {
                    paymentToUserId = member.id
                    paymentAmount = abs(netBalance)
                }
            )
        }
    }

    if (paymentToUserId != null) {
        val targetMember = memberMap[paymentToUserId]
        PaymentConfirmationDialog(
            memberName = targetMember?.displayName ?: "Miembro",
            amount = paymentAmount,
            onDismiss = { paymentToUserId = null },
            onConfirm = {
                viewModel.makePayment(paymentToUserId!!, paymentAmount)
                paymentToUserId = null
            }
        )
    }
}

@Composable
private fun BalanceCard(
    member: Profile,
    netBalance: Double,
    onMemberClick: () -> Unit,
    onPayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onMemberClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.member_avatar),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                val balanceColor = if (netBalance > 0.01) MaterialTheme.colorScheme.primary
                               else if (netBalance < -0.01) MaterialTheme.colorScheme.error
                               else Color.Gray
                val balanceLabel = if (netBalance > 0.01) "Te debe"
                                else if (netBalance < -0.01) "Le debés"
                                else "Al día"

                Text(
                    text = "$balanceLabel: $ ${String.format("%.2f", abs(netBalance))}",
                    color = balanceColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (netBalance < -0.01) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onPayClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Payment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pagar deuda")
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentConfirmationDialog(
    memberName: String,
    amount: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Pago") },
        text = { Text("¿Saldar deuda de $ ${String.format("%.2f", amount)} con $memberName?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confirmar Pago") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
