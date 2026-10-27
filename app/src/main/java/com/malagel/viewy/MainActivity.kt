package com.malagel.viewy

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.malagel.viewy.ui.MeasurementViewModel
import com.malagel.viewy.ui.ViewyScreen
import com.malagel.viewy.ui.theme.ViewyTheme

class MainActivity : ComponentActivity() {
    private val measurement by lazy { ViewModelProvider(this)[MeasurementViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ViewyTheme {
                val state by measurement.state.collectAsStateWithLifecycle()
                ViewyScreen(state, measurement)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        measurement.resume()
    }

    override fun onPause() {
        measurement.pause()
        super.onPause()
    }
}
