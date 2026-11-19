package com.myagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myagent.app.ui.navigation.MyAgentNavHost
import com.myagent.app.ui.theme.MyAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAgentTheme {
                MyAgentNavHost()
            }
        }
    }
}
