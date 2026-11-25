package com.megamarket.picker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.megamarket.picker.ui.navigation.NavegacionPicker
import com.megamarket.picker.ui.theme.TemaMegaMarket
import com.megamarket.picker.worker.SincronizacionPickingWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaMegaMarket {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavegacionPicker()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SincronizacionPickingWorker.programar(this)
    }
}
