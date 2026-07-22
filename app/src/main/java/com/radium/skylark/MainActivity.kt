package com.radium.skylark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.radium.skylark.ui.navigation.SkylarkApp
import com.radium.skylark.ui.theme.SkylarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SkylarkTheme {
                SkylarkApp()
            }
        }
    }
}
