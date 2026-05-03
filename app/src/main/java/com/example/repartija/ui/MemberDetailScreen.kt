package com.example.repartija.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.MemberDetailState
import com.example.repartija.data.repository.TransactionType
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    groupId: String,
    currentUserId: String,
    member: Profile,
    onBack: () -> Unit,
    viewModel: MemberDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(member.id) {
        viewModel.loadDetails(groupId, currentUserId, member.id)
    }
    
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(member.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.loadDetails(groupId, currentUserId, member.id)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    item {
                        // Current Balance Header
                        Text(
                            text = "Balance Actual",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        val balanceColor = if (state.currentBalance > 0.01) Color(0xFF388E3C)
                        else if (state.currentBalance < -0.01) MaterialTheme.colorScheme.error
                        else Color.Gray
                        
                        val balanceLabel = if (state.currentBalance > 0.01) "Te debe"
                        else if (state.currentBalance < -0.01) "Le debés"
                        else "Al día"

                        Text(
                            text = "$balanceLabel: $ ${String.format("%.2f", abs(state.currentBalance))}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = balanceColor
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Chart Section
                        Text(
                            text = "Evolución últimos 30 días",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        CanvasDebtChart(
                            state = state,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Transactions Section
                        Text(
                            text = "Historial con ${member.displayName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (state.transactions.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No hay transacciones previas.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(state.transactions) { tx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = if (tx.type == TransactionType.EXPENSE) Icons.Default.ReceiptLong else Icons.Default.CheckCircle
                                    val color = if (tx.effectOnBalance > 0) Color(0xFF388E3C) else MaterialTheme.colorScheme.error

                                    Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = tx.description, fontWeight = FontWeight.Bold)
                                        Text(text = tx.date.take(10), style = MaterialTheme.typography.bodySmall)
                                    }

                                    Text(
                                        text = "${if (tx.effectOnBalance > 0) "+" else "-"}$ ${String.format("%.2f", abs(tx.amount))}",
                                        color = color,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasDebtChart(state: MemberDetailState, modifier: Modifier) {
    if (state.dailyBalances.isEmpty()) return

    val positiveColor = Color(0xFF388E3C)
    val negativeColor = MaterialTheme.colorScheme.error
    val zeroColor = Color.Gray.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val balances = state.dailyBalances.map { it.balance.toFloat() }
        val maxVal = balances.maxOrNull() ?: 0f
        val minVal = balances.minOrNull() ?: 0f
        
        // Add a bit of padding to min/max so lines don't hug the very top/bottom
        val padding = kotlin.math.max(abs(maxVal), abs(minVal)) * 0.1f
        val adjustedMax = if (maxVal == minVal) maxVal + 10f else maxVal + padding
        val adjustedMin = if (maxVal == minVal) minVal - 10f else minVal - padding

        val range = adjustedMax - adjustedMin

        // Calculate zero Y position
        val zeroY = height - ((0f - adjustedMin) / range) * height

        // Draw zero line
        drawLine(
            color = zeroColor,
            start = Offset(0f, zeroY),
            end = Offset(width, zeroY),
            strokeWidth = 2f
        )

        if (balances.size < 2) return@Canvas

        val stepX = width / (balances.size - 1)

        for (i in 0 until balances.size - 1) {
            val y1 = height - ((balances[i] - adjustedMin) / range) * height
            val y2 = height - ((balances[i + 1] - adjustedMin) / range) * height
            val x1 = i * stepX
            val x2 = (i + 1) * stepX

            // Check if line crosses zero
            if ((balances[i] >= 0 && balances[i + 1] < 0) || (balances[i] < 0 && balances[i + 1] >= 0)) {
                // Find intersection X
                val ratio = abs(balances[i]) / (abs(balances[i]) + abs(balances[i+1]))
                val crossX = x1 + (x2 - x1) * ratio
                val crossY = zeroY

                // Draw first segment
                drawLine(
                    color = if (balances[i] >= 0) positiveColor else negativeColor,
                    start = Offset(x1, y1),
                    end = Offset(crossX, crossY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                // Draw second segment
                drawLine(
                    color = if (balances[i + 1] >= 0) positiveColor else negativeColor,
                    start = Offset(crossX, crossY),
                    end = Offset(x2, y2),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            } else {
                // Does not cross zero, single color
                val color = if (balances[i] >= 0 && balances[i+1] >= 0) positiveColor else negativeColor
                drawLine(
                    color = color,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
