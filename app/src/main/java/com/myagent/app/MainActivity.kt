package com.myagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myagent.app.ui.navigation.AICharacterChatNavHost
import com.myagent.app.ui.theme.AICharacterChatAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AICharacterChatAppTheme {
                AICharacterChatNavHost()
            }
        }
    }
}
