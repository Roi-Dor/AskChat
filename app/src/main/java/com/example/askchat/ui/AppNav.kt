package com.example.askchat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.askchat.ui.chat.ChatListScreen
import com.example.askchat.ui.chat.ChatScreen

/**
 * App navigation graph.
 *
 * @param initialDeepLinkChatId If not null, we immediately navigate to this chat.
 */
@Composable
fun AppNav(
    initialDeepLinkChatId: String? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // If launched via a deep-link / notification, open the chat once.
    LaunchedEffect(initialDeepLinkChatId) {
        initialDeepLinkChatId?.let { target ->
            navController.navigate("chat/$target")
        }
    }

    NavHost(
        navController = navController,
        startDestination = "chats",
        modifier = modifier
    ) {
        // Chat list (DM picker + AskChat row)
        composable(route = "chats") {
            ChatListScreen(
                onOpenChat = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }

        // Single chat screen
        composable(
            route = "chat/{chatId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() }
                    )
        }
    }
}
