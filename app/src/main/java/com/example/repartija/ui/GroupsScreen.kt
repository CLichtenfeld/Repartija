package com.example.repartija.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.repartija.R
import com.example.repartija.data.model.Group
import com.example.repartija.data.repository.DataResult
import com.example.repartija.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    groupsRes: DataResult<List<Group>>,
    onGroupClick: (String) -> Unit
) {
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var deletingGroup by remember { mutableStateOf<Group?>(null) }
    var deleteBlockedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.15f
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { GroupsTopBar() },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Grupo")
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                GroupsContent(
                    groupsRes = groupsRes,
                    viewModel = viewModel,
                    onGroupClick = onGroupClick,
                    onEdit = { editingGroup = it },
                    onDelete = { group ->
                        scope.launch {
                            val canDelete = viewModel.areAllBalancesZero(group.id)
                            if (canDelete) {
                                deletingGroup = group
                            } else {
                                deleteBlockedMessage = "No se puede eliminar \"${group.name}\": hay saldos pendientes."
                            }
                        }
                    }
                )
            }

            GroupsDialogs(
                showAddGroupDialog = showAddGroupDialog,
                editingGroup = editingGroup,
                deletingGroup = deletingGroup,
                deleteBlockedMessage = deleteBlockedMessage,
                onDismissAdd = { showAddGroupDialog = false },
                onDismissEdit = { editingGroup = null },
                onDismissDelete = { deletingGroup = null },
                onDismissBlocked = { deleteBlockedMessage = null },
                onConfirmAdd = { name ->
                    viewModel.addGroup(name)
                    showAddGroupDialog = false
                },
                onConfirmEdit = { id, name ->
                    viewModel.renameGroup(id, name)
                    editingGroup = null
                },
                onConfirmDelete = { id ->
                    viewModel.deleteGroup(id)
                    deletingGroup = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "REPARTIJA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = "Cuentas claras, mates compartidos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        actions = {
            val authViewModel: AuthViewModel = hiltViewModel()
            IconButton(onClick = { authViewModel.logout() }) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
            }
        }
    )
}

@Composable
private fun GroupsContent(
    groupsRes: DataResult<List<Group>>,
    viewModel: GroupsViewModel,
    onGroupClick: (String) -> Unit,
    onEdit: (Group) -> Unit,
    onDelete: (Group) -> Unit
) {
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
                        GroupCard(
                            group = group,
                            onClick = { onGroupClick(group.id) },
                            onEdit = { onEdit(group) },
                            onDelete = { onDelete(group) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.group_avatar),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(group.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { menuExpanded = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsDialogs(
    showAddGroupDialog: Boolean,
    editingGroup: Group?,
    deletingGroup: Group?,
    deleteBlockedMessage: String?,
    onDismissAdd: () -> Unit,
    onDismissEdit: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissBlocked: () -> Unit,
    onConfirmAdd: (String) -> Unit,
    onConfirmEdit: (String, String) -> Unit,
    onConfirmDelete: (String) -> Unit
) {
    if (showAddGroupDialog) {
        var groupNameValue by remember { mutableStateOf(TextFieldValue("")) }
        val groupName = groupNameValue.text
        AlertDialog(
            onDismissRequest = onDismissAdd,
            title = { Text("Nuevo Grupo") },
            text = {
                OutlinedTextField(
                    value = groupNameValue,
                    onValueChange = { groupNameValue = it },
                    label = { Text("Nombre del grupo") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused) {
                            groupNameValue = groupNameValue.copy(selection = TextRange(0, groupNameValue.text.length))
                        }
                    }
                )
            },
            confirmButton = {
                Button(onClick = { if (groupName.isNotBlank()) onConfirmAdd(groupName) }) { Text("Crear") }
            }
        )
    }

    editingGroup?.let { group ->
        var newNameValue by remember(group.id) { mutableStateOf(TextFieldValue(group.name)) }
        val newName = newNameValue.text
        AlertDialog(
            onDismissRequest = onDismissEdit,
            title = { Text("Renombrar Grupo") },
            text = {
                OutlinedTextField(
                    value = newNameValue,
                    onValueChange = { newNameValue = it },
                    label = { Text("Nuevo nombre") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused) {
                            newNameValue = newNameValue.copy(selection = TextRange(0, newNameValue.text.length))
                        }
                    }
                )
            },
            confirmButton = {
                Button(onClick = { if (newName.isNotBlank()) onConfirmEdit(group.id, newName) }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = onDismissEdit) { Text("Cancelar") }
            }
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar \"${group.name}\"?") },
            text = { Text("Esta acción no se puede deshacer. Se eliminarán todos los datos del grupo.") },
            confirmButton = {
                Button(
                    onClick = { onConfirmDelete(group.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("Cancelar") }
            }
        )
    }

    deleteBlockedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissBlocked,
            icon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("No se puede eliminar") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = onDismissBlocked) { Text("Entendido") }
            }
        )
    }
}
