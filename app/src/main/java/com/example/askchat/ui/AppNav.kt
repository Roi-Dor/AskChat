package com.example.askchat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.askchat.ui.chat.ChatScreen
import com.example.askchat.ui.chat.ChatListScreen

@Composable
fun AppNav(
    modifier: Modifier = Modifier,
    initialDeepLinkChatId: String? // pass from MainActivity if notification tapped
) {
    val nav = rememberNavController()

    LaunchedEffect(initialDeepLinkChatId) {
        initialDeepLinkChatId?.let { nav.navigate("chat/$it") }
    }

    NavHost(navController = nav, startDestination = "chats", modifier = modifier) {
        composable("chats") {
            ChatListScreen(
                onOpenChat = { chatId -> nav.navigate("chat/$chatId") }
            )
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId"){ type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments!!.getString("chatId")!!
            ChatScreen(chatId = chatId, onBack = { nav.popBackStack() })
        }
    }
}
