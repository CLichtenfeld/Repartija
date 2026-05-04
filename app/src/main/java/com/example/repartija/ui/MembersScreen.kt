package com.example.repartija.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import com.example.repartija.R
import com.example.repartija.data.model.GroupInvite
import com.example.repartija.data.model.Profile
import com.example.repartija.data.repository.DataResult
import kotlinx.coroutines.launch
import java.io.InputStream

@Composable
fun MembersScreen(viewModel: DebtViewModel) {
    val membersRes by viewModel.currentMembers.collectAsState()
    val invitesRes by viewModel.currentInvites.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var removingMember by remember { mutableStateOf<Profile?>(null) }
    var removeBlockedMessage by remember { mutableStateOf<String?>(null) }
    var showChangeAvatarDialog by remember { mutableStateOf<Profile?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        MembersHeader(
            onSearchClick = { showAddMemberDialog = true },
            onInviteClick = {
                scope.launch {
                    val url = viewModel.generateInviteLink()
                    if (url != null) shareInviteLink(context, url)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MembersListContent(
            membersRes = membersRes,
            invitesRes = invitesRes,
            currentUserId = currentUserId,
            viewModel = viewModel,
            onRemoveClick = { member, canRemove ->
                if (canRemove) {
                    removingMember = member
                } else {
                    removeBlockedMessage = "No se puede quitar a ${member.displayName}: tiene saldos pendientes."
                }
            },
            onChangeAvatar = { showChangeAvatarDialog = it }
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            searchResult = searchResult,
            onSearch = { viewModel.searchUserByEmail(it) },
            onAdd = { userId -> viewModel.addMemberToCurrentGroup(userId) },
            onDismiss = { showAddMemberDialog = false; viewModel.clearSearchResult() }
        )
    }

    MembersDialogs(
        removingMember = removingMember,
        removeBlockedMessage = removeBlockedMessage,
        showChangeAvatarDialog = showChangeAvatarDialog,
        viewModel = viewModel,
        onDismissRemove = { removingMember = null },
        onDismissBlocked = { removeBlockedMessage = null },
        onDismissAvatar = { showChangeAvatarDialog = null },
        onConfirmRemove = { member ->
            viewModel.removeMemberFromCurrentGroup(member.id)
            removingMember = null
        }
    )
}

@Composable
private fun MembersHeader(onSearchClick: () -> Unit, onInviteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Integrantes", style = MaterialTheme.typography.headlineMedium)
        Row {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.PersonSearch, null)
            }
            Button(onClick = onInviteClick) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Invitar")
            }
        }
    }
}

@Composable
private fun MembersListContent(
    membersRes: DataResult<List<Profile>>,
    invitesRes: DataResult<List<GroupInvite>>,
    currentUserId: String?,
    viewModel: DebtViewModel,
    onRemoveClick: (Profile, Boolean) -> Unit,
    onChangeAvatar: (Profile) -> Unit
) {
    val context = LocalContext.current
    when (membersRes) {
        is DataResult.Loading -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DataResult.Error -> Text("Error cargando miembros", color = MaterialTheme.colorScheme.error)
        is DataResult.Success -> {
            val members = membersRes.data
            LazyColumn {
                items(members) { member ->
                    val isCurrentUser = member.id == currentUserId
                    val canRemove = !isCurrentUser && viewModel.memberHasZeroBalance(member.id)
                    MemberListItem(member, isCurrentUser, canRemove, onRemoveClick, onChangeAvatar, viewModel)
                    HorizontalDivider()
                }

                if (invitesRes is DataResult.Success) {
                    val pendingInvites = invitesRes.data.filter { !it.used }
                    if (pendingInvites.isNotEmpty()) {
                        item {
                            Text("Invitaciones Pendientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp))
                        }
                        items(pendingInvites) { invite ->
                            InviteListItem(invite, context, onShare = { shareInviteLink(context, it) }, onDelete = { viewModel.deleteInvite(it) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberListItem(
    member: Profile,
    isCurrentUser: Boolean,
    canRemove: Boolean,
    onRemoveClick: (Profile, Boolean) -> Unit,
    onChangeAvatar: (Profile) -> Unit,
    viewModel: DebtViewModel
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.displayName, fontWeight = FontWeight.Medium)
                if (isCurrentUser) {
                    Spacer(Modifier.width(8.dp))
                    Text("(tú)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        supportingContent = { Text(member.email, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            val avatarUrl = viewModel.getAvatarUrl(member.id, member.avatarUrl)
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                error = painterResource(id = R.drawable.member_avatar)
            )
        },
        trailingContent = {
            if (isCurrentUser) {
                IconButton(onClick = { onChangeAvatar(member) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Cambiar Foto")
                }
            } else {
                IconButton(onClick = { onRemoveClick(member, canRemove) }) {
                    Icon(
                        Icons.Default.PersonRemove,
                        contentDescription = "Quitar",
                        tint = if (canRemove) MaterialTheme.colorScheme.error else Color.Gray
                    )
                }
            }
        }
    )
}

@Composable
private fun InviteListItem(
    invite: GroupInvite,
    context: Context,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text("Token: ${invite.token.take(8)}...") },
        supportingContent = { Text("Expira: ${invite.expiresAt.replace("T", " ")}") },
        leadingContent = {
            Icon(Icons.Default.HourglassEmpty, null, tint = MaterialTheme.colorScheme.secondary)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onShare("repartija://join?token=${invite.token}") }) {
                    Icon(Icons.Default.Share, contentDescription = "Reenviar")
                }
                IconButton(onClick = { onDelete(invite.id) }) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancelar invitación", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun MembersDialogs(
    removingMember: Profile?,
    removeBlockedMessage: String?,
    showChangeAvatarDialog: Profile?,
    viewModel: DebtViewModel,
    onDismissRemove: () -> Unit,
    onDismissBlocked: () -> Unit,
    onDismissAvatar: () -> Unit,
    onConfirmRemove: (Profile) -> Unit
) {
    removingMember?.let { member ->
        AlertDialog(
            onDismissRequest = onDismissRemove,
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Quitar a ${member.displayName}?") },
            text = { Text("Se eliminará del grupo. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { onConfirmRemove(member) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemove) { Text("Cancelar") }
            }
        )
    }

    removeBlockedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissBlocked,
            icon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("No se puede quitar") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = onDismissBlocked) { Text("Entendido") }
            }
        )
    }

    showChangeAvatarDialog?.let { profile ->
        var newAvatarUrlValue by remember { mutableStateOf(TextFieldValue(profile.avatarUrl ?: "")) }
        val newAvatarUrl = newAvatarUrlValue.text
        val context = LocalContext.current
        
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                uri?.let {
                    val bytes = context.contentResolver.openInputStream(it)?.use { input ->
                        input.readBytes()
                    }
                    if (bytes != null) {
                        viewModel.uploadAvatar(profile.id, bytes)
                        onDismissAvatar()
                    }
                }
            }
        )

        AlertDialog(
            onDismissRequest = onDismissAvatar,
            title = { Text("Cambiar Foto de Perfil") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val currentPreview = viewModel.getAvatarUrl(profile.id, newAvatarUrl.ifBlank { null })
                    AsyncImage(
                        model = currentPreview,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        error = painterResource(id = R.drawable.member_avatar)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir de la Galería")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("O ingresá una URL:", style = MaterialTheme.typography.labelSmall)
                    
                    OutlinedTextField(
                        value = newAvatarUrlValue,
                        onValueChange = { newAvatarUrlValue = it },
                        label = { Text("URL de la imagen") },
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) {
                                newAvatarUrlValue = newAvatarUrlValue.copy(selection = TextRange(0, newAvatarUrlValue.text.length))
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateProfileAvatar(profile.id, newAvatarUrl)
                    onDismissAvatar()
                }) { Text("Guardar URL") }
            },
            dismissButton = {
                TextButton(onClick = onDismissAvatar) { Text("Cancelar") }
            }
        )
    }
}

private fun shareInviteLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "¡Unite a mi grupo en Repartija! Cuentas claras, mates compartidos. $url")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir invitación"))
}
