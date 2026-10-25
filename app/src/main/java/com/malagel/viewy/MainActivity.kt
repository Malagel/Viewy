package com.malagel.viewy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.malagel.viewy.ui.ViewyScreen
import com.malagel.viewy.ui.theme.ViewyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViewyTheme {
                ViewyScreen()
            }
        }
    }
}
