package com.example.repartija

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.Group
import com.example.repartija.data.model.Payment
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult
import com.example.repartija.data.repository.SessionRepository
import com.example.repartija.ui.DebtViewModel
import com.example.repartija.ui.auth.AuthViewModel
import com.example.repartija.ui.auth.LoginScreen
import com.example.repartija.ui.auth.RegisterScreen
import com.example.repartija.ui.theme.RepartijaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DebtViewModel = hiltViewModel()

            RepartijaTheme {
                val currentUserInfo by sessionRepository.currentUser.collectAsState()

                if (currentUserInfo == null) {
                    var showRegister by remember { mutableStateOf(false) }
                    val authViewModel: AuthViewModel = hiltViewModel()

                    if (showRegister) {
                        RegisterScreen(
                            viewModel = authViewModel,
                            onNavigateToLogin = { showRegister = false }
                        )
                    } else {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToRegister = { showRegister = true }
                        )
                    }
                } else {
                    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
                    val groupsRes by viewModel.allGroups.collectAsState()

                    if (selectedGroupId == null) {
                        GroupsScreen(viewModel, groupsRes)
                    } else {
                        MainAppScaffold(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(viewModel: DebtViewModel, groupsRes: DataResult<List<Group>>) {
    var showAddGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Mis Grupos") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Grupo")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when (groupsRes) {
                is DataResult.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DataResult.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${groupsRes.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is DataResult.Success -> {
                    val groups = groupsRes.data
                    if (groups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tenés grupos. ¡Creá uno!")
                        }
                    } else {
                        LazyColumn {
                            items(groups) { group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable { viewModel.selectGroup(group.id) },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(group.name, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddGroupDialog) {
            var groupName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddGroupDialog = false },
                title = { Text("Nuevo Grupo") },
                text = {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Nombre del grupo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.addGroup(groupName)
                            showAddGroupDialog = false
                        }
                    }) { Text("Crear") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: DebtViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var showExpenseDialog by remember { mutableStateOf(false) }

    val membersRes by viewModel.currentMembers.collectAsState()
    val groupsRes by viewModel.allGroups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val groups = (groupsRes as? DataResult.Success)?.data ?: emptyList()
    val members = (membersRes as? DataResult.Success)?.data ?: emptyList()

    val selectedGroup = groups.find { it.id == selectedGroupId }
    val currentUser = members.find { it.id == currentUserId }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(selectedGroup?.name ?: "Repartija", style = MaterialTheme.typography.titleMedium)
                            if (currentUser != null) {
                                Text("Tú: ${currentUser.displayName}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.selectGroup(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        SyncIndicator(isSyncing = isSyncing)
                        val authViewModel: AuthViewModel = hiltViewModel()
                        IconButton(onClick = { authViewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                        }
                    }
                )
                // Subtle progress bar below the top bar
                AnimatedVisibility(
                    visible = isSyncing,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(400))
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = Color.Transparent
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.AccountBalance, null) },
                    label = { Text("Saldos") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Miembros") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(onClick = { showExpenseDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Gasto")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> BalancesScreen(viewModel)
                1 -> HistoryScreen(viewModel)
                2 -> MembersScreen(viewModel)
            }
        }

        if (showExpenseDialog) {
            ExpenseDialog(
                members = members,
                onDismiss = { showExpenseDialog = false },
                onConfirm = { desc, amount, paidBy, shares ->
                    viewModel.addNewExpense(desc, amount, paidBy, shares)
                    showExpenseDialog = false
                }
            )
        }
    }
}

@Composable
fun BalancesScreen(viewModel: DebtViewModel) {
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

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column {
                        Text(member.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        val balanceColor = if (netBalance > 0.01) Color(0xFF388E3C)
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
                                onClick = {
                                    paymentToUserId = member.id
                                    paymentAmount = abs(netBalance)
                                },
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
    }

    if (paymentToUserId != null) {
        AlertDialog(
            onDismissRequest = { paymentToUserId = null },
            title = { Text("Registrar Pago") },
            text = { Text("¿Saldar deuda de $ ${String.format("%.2f", paymentAmount)} con ${memberMap[paymentToUserId]?.displayName}?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.makePayment(paymentToUserId!!, paymentAmount)
                    paymentToUserId = null
                }) { Text("Confirmar Pago") }
            }
        )
    }
}

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

@Composable
fun HistoryScreen(viewModel: DebtViewModel) {
    val expensesRes by viewModel.currentExpenses.collectAsState()
    val paymentsRes by viewModel.currentPayments.collectAsState()
    val membersRes by viewModel.currentMembers.collectAsState()
    val debts by viewModel.currentDebts.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Historial", style = MaterialTheme.typography.headlineMedium)
            val balanceColor = if (globalBalance > 0.01) Color(0xFF388E3C) else if (globalBalance < -0.01) MaterialTheme.colorScheme.error else Color.Gray
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

        Spacer(modifier = Modifier.height(16.dp))

        if (movements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay movimientos registrados.")
            }
        }

        LazyColumn {
            items(movements) { move ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (move) {
                                    is Movement.Exp -> Icons.AutoMirrored.Filled.ReceiptLong
                                    is Movement.Pay -> Icons.Default.CheckCircle
                                }
                                val color = when (move) {
                                    is Movement.Exp -> if (move.expense.paidBy == currentUserId) Color(0xFF388E3C) else Color.Gray
                                    is Movement.Pay -> MaterialTheme.colorScheme.primary
                                }

                                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = move.description, fontWeight = FontWeight.Bold)
                                    val subText = when (move) {
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
                                    Text(text = subText, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$ ${String.format("%.2f", move.amount)}",
                                        color = if (move is Movement.Pay) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = move.date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
fun MembersScreen(viewModel: DebtViewModel) {
    val membersRes by viewModel.currentMembers.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Integrantes", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { /* TODO: Invite via deep link */ }) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Invitar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (membersRes) {
            is DataResult.Loading -> CircularProgressIndicator()
            is DataResult.Error -> Text("Error cargando miembros", color = MaterialTheme.colorScheme.error)
            is DataResult.Success -> {
                LazyColumn {
                    items((membersRes as DataResult.Success<List<Profile>>).data) { member ->
                        ListItem(
                            headlineContent = { Text(member.displayName) },
                            supportingContent = { Text(member.email, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(member.displayName.take(1).uppercase())
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

enum class SplitMethod { EQUAL, PERCENTAGE }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    members: List<Profile>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, Map<String, Double>) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var totalAmountStr by remember { mutableStateOf("") }
    var singlePayerId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
    var splitMethod by remember { mutableStateOf(SplitMethod.EQUAL) }
    var participants by remember { mutableStateOf(members.map { it.id }.toSet()) }
    var splitPercentages by remember { mutableStateOf(emptyMap<String, String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Gasto") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("¿Qué compraste?") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = totalAmountStr, onValueChange = { totalAmountStr = it }, label = { Text("Monto") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¿Quién pagó?", style = MaterialTheme.typography.titleSmall)
                    var expanded by remember { mutableStateOf(false) }
                    val selectedPayer = members.find { it.id == singlePayerId }
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedPayer?.displayName ?: "Seleccionar")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(text = { Text(member.displayName) }, onClick = { singlePayerId = member.id; expanded = false })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¿Cómo se reparte?", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(selected = splitMethod == SplitMethod.EQUAL, onClick = { splitMethod = SplitMethod.EQUAL }, label = { Text("Equitativo") })
                        FilterChip(selected = splitMethod == SplitMethod.PERCENTAGE, onClick = { splitMethod = SplitMethod.PERCENTAGE }, label = { Text("%") })
                    }
                }

                if (splitMethod == SplitMethod.EQUAL) {
                    item {
                        Text("Participantes:", style = MaterialTheme.typography.labelSmall)
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            members.forEach { member ->
                                FilterChip(
                                    selected = participants.contains(member.id),
                                    onClick = { participants = if (participants.contains(member.id)) participants - member.id else participants + member.id },
                                    label = { Text(member.displayName) }
                                )
                            }
                        }
                    }
                } else {
                    items(members) { member ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(member.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = splitPercentages[member.id] ?: "",
                                onValueChange = { splitPercentages = splitPercentages + (member.id to it) },
                                suffix = { Text("%") },
                                modifier = Modifier.width(90.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val totalAmount = totalAmountStr.toDoubleOrNull() ?: 0.0
                if (description.isBlank() || totalAmount <= 0) return@Button
                val shares = when (splitMethod) {
                    SplitMethod.EQUAL -> {
                        if (participants.isEmpty()) return@Button
                        val equalShare = totalAmount / participants.size
                        participants.associateWith { equalShare }
                    }
                    SplitMethod.PERCENTAGE -> {
                        splitPercentages.mapValues { (it.value.toDoubleOrNull() ?: 0.0) / 100.0 * totalAmount }.filterValues { it > 0 }
                    }
                }
                onConfirm(description, totalAmount, singlePayerId, shares)
            }) { Text("Registrar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun SyncIndicator(isSyncing: Boolean) {
    AnimatedVisibility(
        visible = isSyncing,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sync_alpha"
        )

        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Sincronizando",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
                .alpha(alpha)
        )
    }
}
