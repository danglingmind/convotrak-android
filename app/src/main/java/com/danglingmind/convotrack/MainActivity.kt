package com.danglingmind.convotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.danglingmind.convotrack.ui.AppRoot
import com.danglingmind.convotrack.ui.theme.ConvoTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConvoTrackTheme {
                AppRoot()
            }
        }
    }
}
