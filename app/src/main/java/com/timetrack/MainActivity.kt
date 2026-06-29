package com.timetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.timetrack.ui.TimeTrackRoot
import com.timetrack.ui.theme.TimeTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TimeTrackTheme {
                TimeTrackRoot()
            }
        }
    }
}
