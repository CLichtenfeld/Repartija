package com.example.repartija

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.util.Consumer
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.repartija.data.repository.DataResult
import com.example.repartija.data.repository.SessionRepository
import com.example.repartija.ui.*
import com.example.repartija.ui.auth.AuthViewModel
import com.example.repartija.ui.auth.LoginScreen
import com.example.repartija.ui.auth.RegisterScreen
import com.example.repartija.ui.theme.RepartijaTheme
import com.example.repartija.util.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        val updateManager = UpdateManager(applicationContext)
        
        setContent {
            val viewModel: DebtViewModel = hiltViewModel()

            HandleIntent(viewModel)
            HandleUpdate(updateManager)

            RepartijaTheme {
                val currentUserInfo by sessionRepository.currentUser.collectAsState()
                val navController = rememberNavController()

                LaunchedEffect(currentUserInfo) {
                    if (currentUserInfo == null) {
                        navController.navigate("login") { popUpTo(0) }
                    } else if (navController.currentDestination?.route == "login") {
                        navController.navigate("groups") { popUpTo("login") { inclusive = true } }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = if (currentUserInfo == null) "login" else "groups"
                ) {
                    composable("login") {
                        val authViewModel: AuthViewModel = hiltViewModel()
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToRegister = { navController.navigate("register") }
                        )
                    }
                    composable("register") {
                        val authViewModel: AuthViewModel = hiltViewModel()
                        RegisterScreen(
                            viewModel = authViewModel,
                            onNavigateToLogin = { navController.popBackStack() }
                        )
                    }
                    composable("groups") {
                        val groupsViewModel: GroupsViewModel = hiltViewModel()
                        val groupsRes by groupsViewModel.allGroups.collectAsState()
                        GroupsScreen(
                            viewModel = groupsViewModel,
                            groupsRes = groupsRes,
                            onGroupClick = { groupId -> navController.navigate("group/$groupId") }
                        )
                    }
                    composable(
                        route = "group/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                        LaunchedEffect(groupId) { viewModel.selectGroup(groupId) }

                        MainAppScaffold(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onMemberClick = { memberId ->
                                navController.navigate("member_detail/$groupId/$memberId")
                            }
                        )
                    }
                    composable(
                        route = "member_detail/{groupId}/{memberId}",
                        arguments = listOf(
                            navArgument("groupId") { type = NavType.StringType },
                            navArgument("memberId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                        val memberId = backStackEntry.arguments?.getString("memberId") ?: return@composable
                        val currentUserId by viewModel.currentUserId.collectAsState()
                        val membersRes by viewModel.currentMembers.collectAsState()
                        
                        val members = (membersRes as? DataResult.Success)?.data ?: emptyList()
                        val member = members.find { it.id == memberId }

                        if (member != null && currentUserId != null) {
                            MemberDetailScreen(
                                groupId = groupId,
                                currentUserId = currentUserId!!,
                                member = member,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HandleIntent(viewModel: DebtViewModel) {
        LaunchedEffect(Unit) {
            val data = intent?.data
            if (data?.host == "join") {
                data.getQueryParameter("token")?.let { viewModel.setPendingJoinToken(it) }
            }
        }

        DisposableEffect(Unit) {
            val listener = Consumer<Intent> { newIntent ->
                val data = newIntent.data
                if (data?.host == "join") {
                    data.getQueryParameter("token")?.let { viewModel.setPendingJoinToken(it) }
                }
            }
            addOnNewIntentListener(listener)
            onDispose { removeOnNewIntentListener(listener) }
        }
    }

    @Composable
    private fun HandleUpdate(updateManager: UpdateManager) {
        LaunchedEffect(Unit) {
            updateManager.checkForUpdates()
        }

        val updateInfo by updateManager.updateAvailable.collectAsState()
        val downloadProgress by updateManager.downloadProgress.collectAsState()

        updateInfo?.let { info ->
            UpdateDialog(
                version = info.version,
                progress = downloadProgress ?: 0f,
                onUpdate = { updateManager.downloadAndInstall(info) }
            )
        }
    }
}
