package com.myagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.myagent.app.ui.theme.MyAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAgentTheme {
                // 占位界面，正式 UI 在第 9~14 步实现
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("MyAgent")
                }
            }
        }
    }
}
