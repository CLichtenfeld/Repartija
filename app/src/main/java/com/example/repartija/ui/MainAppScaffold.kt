package com.example.repartija.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.repartija.R
import com.example.repartija.data.repository.DataResult
import com.example.repartija.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: DebtViewModel,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) }
    var showExpenseDialog by remember { mutableStateOf(false) }

    val membersRes by viewModel.currentMembers.collectAsState()
    val selectedGroupRes by viewModel.selectedGroup.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val members = (membersRes as? DataResult.Success)?.data ?: emptyList()
    val selectedGroup = (selectedGroupRes as? DataResult.Success)?.data

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            MainTopBar(
                title = selectedGroup?.name?.uppercase() ?: "REPARTIJA",
                isSyncing = isSyncing,
                onBack = onBack
            )
        },
        bottomBar = {
            MainNavigationBar(
                currentTab = currentTab,
                onTabSelect = { currentTab = it }
            )
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = { showExpenseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Gasto")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentTab) {
                0 -> BalancesScreen(viewModel, onMemberClick = onMemberClick)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    isSyncing: Boolean,
    onBack: () -> Unit
) {
    Column {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text("Cuentas claras, mates compartidos", style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
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
        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun SyncIndicator(isSyncing: Boolean) {
    AnimatedVisibility(visible = isSyncing) {
        Icon(
            Icons.Default.Sync,
            contentDescription = "Sincronizando",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun MainNavigationBar(
    currentTab: Int,
    onTabSelect: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        NavigationBarItem(
            selected = currentTab == 0,
            onClick = { onTabSelect(0) },
            icon = { NavIcon(R.drawable.nav_saldos, "Saldos", currentTab == 0) },
            label = { Text("Saldos") }
        )
        NavigationBarItem(
            selected = currentTab == 1,
            onClick = { onTabSelect(1) },
            icon = { NavIcon(R.drawable.nav_historial, "Historial", currentTab == 1) },
            label = { Text("Historial") }
        )
        NavigationBarItem(
            selected = currentTab == 2,
            onClick = { onTabSelect(2) },
            icon = { NavIcon(R.drawable.nav_miembros, "Miembros", currentTab == 2) },
            label = { Text("Miembros") }
        )
    }
}

@Composable
private fun NavIcon(resId: Int, contentDescription: String, isSelected: Boolean) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = Modifier.size(28.dp),
        alpha = if (isSelected) 1f else 0.5f
    )
}
