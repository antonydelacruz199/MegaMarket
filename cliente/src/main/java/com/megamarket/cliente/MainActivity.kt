package com.megamarket.cliente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.megamarket.cliente.ui.navigation.NavegacionCliente
import com.megamarket.cliente.ui.theme.TemaMegaMarket

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaMegaMarket {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavegacionCliente()
                }
            }
        }
    }
}
