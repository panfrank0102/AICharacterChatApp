package com.myagent.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myagent.app.ui.cardimport.CardImportScreen
import com.myagent.app.ui.chat.ChatScreen
import com.myagent.app.ui.conversationlist.ConversationListScreen
import com.myagent.app.ui.sessionsettings.SessionSettingsScreen
import com.myagent.app.ui.sessionsettings.WorldbookEntryEditScreen
import com.myagent.app.ui.sessionsettings.WorldbookListScreen
import com.myagent.app.ui.settings.GlobalSettingsScreen
import com.myagent.app.ui.settings.PresetEditScreen
import com.myagent.app.ui.settings.PresetListScreen
import com.myagent.app.ui.settings.PromptEditScreen

/**
 * 应用导航图：注册全部页面路由与页面间的跳转回调。
 */
@Composable
fun MyAgentNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONVERSATION_LIST) {

        composable(Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.GLOBAL_SETTINGS) },
                onImportCard = { navController.navigate(Routes.CARD_IMPORT) },
            )
        }

        composable(Routes.CARD_IMPORT) {
            CardImportScreen(
                onBack = { navController.popBackStack() },
                onImported = { id ->
                    navController.navigate(Routes.chat(id)) {
                        popUpTo(Routes.CONVERSATION_LIST) { inclusive = false }
                    }
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.ARG_CONVERSATION_ID) ?: -1L
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenSessionSettings = { navController.navigate(Routes.sessionSettings(id)) },
            )
        }

        composable(Routes.GLOBAL_SETTINGS) {
            GlobalSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPresetList = { navController.navigate(Routes.PRESET_LIST) },
                onOpenPromptEdit = { conversationId -> navController.navigate(Routes.promptEdit(conversationId)) },
            )
        }

        composable(Routes.PRESET_LIST) {
            PresetListScreen(
                onBack = { navController.popBackStack() },
                onAddPreset = { navController.navigate(Routes.presetEdit(null)) },
                onEditPreset = { id -> navController.navigate(Routes.presetEdit(id)) },
            )
        }

        composable(
            route = Routes.PRESET_EDIT,
            arguments = listOf(navArgument(Routes.ARG_PRESET_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) {
            PresetEditScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.SESSION_SETTINGS,
            arguments = listOf(navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.ARG_CONVERSATION_ID) ?: -1L
            SessionSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPromptEdit = { conversationId -> navController.navigate(Routes.promptEdit(conversationId)) },
                onOpenPostHistoryEdit = { conversationId ->
                    navController.navigate(Routes.promptEdit(conversationId, Routes.PROMPT_KIND_POST_HISTORY))
                },
                onOpenWorldbook = { navController.navigate(Routes.worldbook(id)) },
            )
        }

        composable(
            route = Routes.PROMPT_EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(Routes.ARG_PROMPT_KIND) {
                    type = NavType.StringType
                    defaultValue = Routes.PROMPT_KIND_SESSION
                },
            ),
        ) {
            PromptEditScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.WORLDBOOK,
            arguments = listOf(navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.ARG_CONVERSATION_ID) ?: -1L
            WorldbookListScreen(
                onBack = { navController.popBackStack() },
                onAddEntry = { navController.navigate(Routes.worldbookEntryEdit(id, null)) },
                onEditEntry = { entryId -> navController.navigate(Routes.worldbookEntryEdit(id, entryId)) },
            )
        }

        composable(
            route = Routes.WORLDBOOK_ENTRY_EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_ENTRY_ID) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            WorldbookEntryEditScreen(onBack = { navController.popBackStack() })
        }
    }
}
