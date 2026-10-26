package com.malagel.viewy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.malagel.viewy.ui.OrientationDiagnosticsPanel
import com.malagel.viewy.ui.OrientationDiagnosticsViewModel
import com.malagel.viewy.ui.ViewyScreen
import com.malagel.viewy.ui.theme.ViewyTheme

class MainActivity : ComponentActivity() {
    private val diagnostics by lazy { ViewModelProvider(this)[OrientationDiagnosticsViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViewyTheme {
                if (BuildConfig.DEBUG) {
                    val state by diagnostics.state.collectAsStateWithLifecycle()
                    ViewyScreen(diagnostics = {
                        OrientationDiagnosticsPanel(state, diagnostics::capture, diagnostics::restart, diagnostics::clearCaptures)
                    })
                } else {
                    ViewyScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) diagnostics.start()
    }

    override fun onPause() {
        if (BuildConfig.DEBUG) diagnostics.stop()
        super.onPause()
    }
}
