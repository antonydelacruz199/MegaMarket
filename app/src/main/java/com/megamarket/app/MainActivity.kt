package com.megamarket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.megamarket.app.ui.navigation.MegaMarketNavHost
import com.megamarket.app.ui.theme.MegaMarketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MegaMarketTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MegaMarketNavHost()
                }
            }
        }
    }
}
