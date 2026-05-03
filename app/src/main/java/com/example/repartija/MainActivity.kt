package com.example.repartija

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.repartija.data.Debt
import com.example.repartija.data.Group
import com.example.repartija.data.Member
import com.example.repartija.data.Payment
import com.example.repartija.ui.DebtViewModel
import com.example.repartija.ui.theme.RepartijaTheme
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = (application as RepartijaApplication).repository
            val viewModel: DebtViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return DebtViewModel(repository) as T
                    }
                }
            )

            RepartijaTheme {
                val selectedGroupId by viewModel.selectedGroupId.collectAsState()
                val groups by viewModel.allGroups.collectAsState()
                val currentMemberId by viewModel.currentMemberId.collectAsState()
                val members by viewModel.currentMembers.collectAsState()

                if (selectedGroupId == null) {
                    GroupsScreen(viewModel, groups)
                } else if (currentMemberId == null) {
                    SelectUserScreen(viewModel, members)
                } else {
                    MainAppScaffold(viewModel)
                }
            }
        }
    }
}

@Composable
fun SelectUserScreen(viewModel: DebtViewModel, members: List<Member>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("¿Quién eres tú?", style = MaterialTheme.typography.headlineMedium)
        Text("Selecciona tu perfil en este grupo", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        
        if (members.isEmpty()) {
            Text("No hay miembros en este grupo.")
            Button(onClick = { viewModel.selectGroup(null) }) { Text("Volver") }
        }

        members.forEach { member ->
            Button(
                onClick = { viewModel.setCurrentMember(member.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(member.name)
            }
        }
        
        TextButton(onClick = { viewModel.selectGroup(null) }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Cancelar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(viewModel: DebtViewModel, groups: List<Group>) {
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
    val members by viewModel.currentMembers.collectAsState()
    val groups by viewModel.allGroups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val currentMemberId by viewModel.currentMemberId.collectAsState()
    val selectedGroup = groups.find { it.id == selectedGroupId }
    val currentUser = members.find { it.id == currentMemberId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(selectedGroup?.name ?: "Repartija", style = MaterialTheme.typography.titleMedium)
                        Text("Usuario: ${currentUser?.name}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.selectGroup(null); viewModel.setCurrentMember(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
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
                onConfirm = { desc, paidAmounts, shares ->
                    viewModel.addNewExpense(desc, paidAmounts, shares)
                    showExpenseDialog = false
                }
            )
        }
    }
}

@Composable
fun BalancesScreen(viewModel: DebtViewModel) {
    val debts by viewModel.currentDebts.collectAsState()
    val members by viewModel.currentMembers.collectAsState()
    val currentMemberId by viewModel.currentMemberId.collectAsState()
    val memberMap = members.associateBy { it.id }
    
    var paymentDebt by remember { mutableStateOf<Debt?>(null) }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }

    LaunchedEffect(Unit) { viewModel.updateAllInterests() }

    val otherMembers = members.filter { it.id != currentMemberId }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Balances con el grupo", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (otherMembers.isEmpty()) {
            item { Text("Agrega otros miembros para ver balances.") }
        }

        items(otherMembers) { member ->
            val heOwesMeDebts = debts.filter { it.fromMemberId == member.id && it.toMemberId == currentMemberId && it.remainingBalance > 0.01 }
            val iOweHimDebts = debts.filter { it.fromMemberId == currentMemberId && it.toMemberId == member.id && it.remainingBalance > 0.01 }
            
            val totalHeOwesMe = heOwesMeDebts.sumOf { it.remainingBalance }
            val totalIOweHim = iOweHimDebts.sumOf { it.remainingBalance }
            val netBalance = totalHeOwesMe - totalIOweHim

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        var showMovesMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMovesMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(expanded = showMovesMenu, onDismissRequest = { showMovesMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Editar Movimientos") },
                                onClick = { 
                                    editingDebt = (heOwesMeDebts + iOweHimDebts).firstOrNull()
                                    showMovesMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                        }
                    }

                    Column {
                        Text(member.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        
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
                                onClick = { paymentDebt = iOweHimDebts.maxByOrNull { it.remainingBalance } },
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

    editingDebt?.let { debt ->
        ExpenseDialog(
            members = members,
            initialDebt = debt,
            onDismiss = { editingDebt = null },
            onConfirm = { desc, paidAmounts, shares ->
                viewModel.deleteExpense(debt)
                viewModel.addNewExpense(desc, paidAmounts, shares)
                editingDebt = null
            }
        )
    }

    paymentDebt?.let { debt ->
        AlertDialog(
            onDismissRequest = { paymentDebt = null },
            title = { Text("Registrar Pago") },
            text = { Text("¿Saldar deuda de $ ${String.format("%.2f", debt.remainingBalance)} con ${memberMap[debt.toMemberId]?.name}?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.makePayment(debt.id, debt.remainingBalance)
                    paymentDebt = null
                }) { Text("Confirmar Pago") }
            }
        )
    }
}

sealed class Movement {
    abstract val date: LocalDate
    abstract val amount: Double
    abstract val description: String

    data class Expense(val debt: Debt) : Movement() {
        override val date = debt.startDate
        override val amount = debt.originalAmount
        override val description = debt.description
    }

    data class Pay(val payment: Payment, val debt: Debt?) : Movement() {
        override val date = payment.date
        override val amount = payment.amount
        override val description = debt?.description ?: "Pago"
    }
}

@Composable
fun HistoryScreen(viewModel: DebtViewModel) {
    val debts by viewModel.currentDebts.collectAsState()
    val payments by viewModel.currentPayments.collectAsState()
    val members by viewModel.currentMembers.collectAsState()
    val currentMemberId by viewModel.currentMemberId.collectAsState()
    val memberMap = members.associateBy { it.id }
    val debtMap = debts.associateBy { it.id }

    var editingDebt by remember { mutableStateOf<Debt?>(null) }

    val movements = remember(debts, payments, currentMemberId) {
        val list = mutableListOf<Movement>()
        debts.filter { it.fromMemberId == currentMemberId || it.toMemberId == currentMemberId }
            .forEach { list.add(Movement.Expense(it)) }
        payments.filter { 
            val debt = debtMap[it.debtId]
            debt != null && (debt.fromMemberId == currentMemberId || debt.toMemberId == currentMemberId)
        }.forEach { list.add(Movement.Pay(it, debtMap[it.debtId])) }
        list.sortedByDescending { it.date }
    }

    val totalHeOwesMe = debts.filter { it.toMemberId == currentMemberId }.sumOf { it.remainingBalance }
    val totalIOweHim = debts.filter { it.fromMemberId == currentMemberId }.sumOf { it.remainingBalance }
    val globalBalance = totalHeOwesMe - totalIOweHim

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
                Text("No hay movimientos registrados para ti.")
            }
        }

        LazyColumn {
            items(movements) { move ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.MoreVert, null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                when(move) {
                                    is Movement.Expense -> {
                                        DropdownMenuItem(
                                            text = { Text("Editar Gasto") },
                                            onClick = { editingDebt = move.debt; showMenu = false },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Eliminar Gasto") },
                                            onClick = { viewModel.deleteExpense(move.debt); showMenu = false },
                                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                                        )
                                    }
                                    is Movement.Pay -> {
                                        DropdownMenuItem(
                                            text = { Text("Eliminar Pago") },
                                            onClick = { viewModel.deletePayment(move.payment); showMenu = false },
                                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                                        )
                                    }
                                }
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when(move) {
                                    is Movement.Expense -> Icons.AutoMirrored.Filled.ReceiptLong
                                    is Movement.Pay -> Icons.Default.CheckCircle
                                }
                                val color = when(move) {
                                    is Movement.Expense -> if (move.debt.toMemberId == currentMemberId) Color(0xFF388E3C) else Color.Gray
                                    is Movement.Pay -> MaterialTheme.colorScheme.primary
                                }
                                
                                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = move.description, fontWeight = FontWeight.Bold)
                                    val subText = when(move) {
                                        is Movement.Expense -> {
                                            if (move.debt.toMemberId == currentMemberId) "Prestaste a ${memberMap[move.debt.fromMemberId]?.name}"
                                            else "Te prestó ${memberMap[move.debt.toMemberId]?.name}"
                                        }
                                        is Movement.Pay -> {
                                            val fromName = memberMap[move.debt?.fromMemberId]?.name ?: "Alguien"
                                            val toName = memberMap[move.debt?.toMemberId]?.name ?: "Alguien"
                                            if (move.debt?.fromMemberId == currentMemberId) "Pagaste a $toName"
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
                                    Text(text = move.date.toString(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingDebt?.let { debt ->
        ExpenseDialog(
            members = members,
            initialDebt = debt,
            onDismiss = { editingDebt = null },
            onConfirm = { desc, paidAmounts, shares ->
                viewModel.deleteExpense(debt)
                viewModel.addNewExpense(desc, paidAmounts, shares)
                editingDebt = null
            }
        )
    }
}

@Composable
fun MembersScreen(viewModel: DebtViewModel) {
    val members by viewModel.currentMembers.collectAsState()
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Integrantes", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { showAddMemberDialog = true }) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(members) { member ->
                ListItem(
                    headlineContent = { Text(member.name) },
                    leadingContent = {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text(member.name.take(1).uppercase())
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { editingMember = member }) {
                            Icon(Icons.Default.Edit, "Editar Miembro")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddMemberDialog) {
        var memberName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Nuevo Integrante") },
            text = {
                OutlinedTextField(value = memberName, onValueChange = { memberName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    if (memberName.isNotBlank()) {
                        viewModel.addMember(memberName)
                        showAddMemberDialog = false
                    }
                }) { Text("Agregar") }
            }
        )
    }

    editingMember?.let { member ->
        var newName by remember { mutableStateOf(member.name) }
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Editar Integrante") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nuevo Nombre") })
            },
            confirmButton = {
                Button(onClick = { viewModel.updateMember(member, newName); editingMember = null }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteMember(member); editingMember = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                    Text("Eliminar")
                }
            }
        )
    }
}

enum class SplitMethod { EQUAL, FULL_DEBT, PERCENTAGE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseDialog(
    members: List<Member>,
    initialDebt: Debt? = null,
    onDismiss: () -> Unit, 
    onConfirm: (String, Map<Int, Double>, Map<Int, Double>) -> Unit
) {
    var description by remember { mutableStateOf(initialDebt?.description ?: "") }
    var totalAmountStr by remember { mutableStateOf(initialDebt?.originalAmount?.toString() ?: "") }
    var singlePayerId by remember { mutableStateOf(initialDebt?.toMemberId ?: members.firstOrNull()?.id ?: 0) }
    var splitMethod by remember { mutableStateOf(if (initialDebt != null) SplitMethod.FULL_DEBT else SplitMethod.EQUAL) }
    var participants by remember { mutableStateOf(members.map { it.id }.toSet()) }
    var fullDebtorId by remember { mutableStateOf(initialDebt?.fromMemberId ?: members.firstOrNull { it.id != singlePayerId }?.id ?: members.firstOrNull()?.id ?: 0) }
    var splitPercentages by remember { mutableStateOf(emptyMap<Int, String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDebt == null) "Nuevo Gasto" else "Editar Gasto") },
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
                        Text(selectedPayer?.name ?: "Seleccionar")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(text = { Text(member.name) }, onClick = { singlePayerId = member.id; expanded = false })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("¿Cómo se reparte?", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(selected = splitMethod == SplitMethod.EQUAL, onClick = { splitMethod = SplitMethod.EQUAL }, label = { Text("Equitativo") })
                        FilterChip(selected = splitMethod == SplitMethod.FULL_DEBT, onClick = { splitMethod = SplitMethod.FULL_DEBT }, label = { Text("Deuda Total") })
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
                                    label = { Text(member.name) }
                                )
                            }
                        }
                    }
                } else if (splitMethod == SplitMethod.FULL_DEBT) {
                    item {
                        Text("El total lo debe:", style = MaterialTheme.typography.labelSmall)
                        var expanded by remember { mutableStateOf(false) }
                        val selectedDebtor = members.find { it.id == fullDebtorId }
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedDebtor?.name ?: "Seleccionar")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            members.forEach { member ->
                                DropdownMenuItem(text = { Text(member.name) }, onClick = { fullDebtorId = member.id; expanded = false })
                            }
                        }
                    }
                } else {
                    items(members) { member ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
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
                    SplitMethod.FULL_DEBT -> mapOf(fullDebtorId to totalAmount)
                    SplitMethod.PERCENTAGE -> {
                        splitPercentages.mapValues { (it.value.toDoubleOrNull() ?: 0.0) / 100.0 * totalAmount }.filterValues { it > 0 }
                    }
                }
                onConfirm(description, mapOf(singlePayerId to totalAmount), shares)
            }) { Text(if (initialDebt == null) "Registrar" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
