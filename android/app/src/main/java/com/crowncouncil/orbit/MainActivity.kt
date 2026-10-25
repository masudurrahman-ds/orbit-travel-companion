package com.crowncouncil.orbit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.crowncouncil.orbit.ui.I18n
import com.crowncouncil.orbit.ui.OrbitApp
import com.crowncouncil.orbit.ui.OrbitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(this)
        enableEdgeToEdge()
        setContent { OrbitTheme { OrbitApp() } }
    }
}
