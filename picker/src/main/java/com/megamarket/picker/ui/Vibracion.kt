package com.megamarket.picker.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

fun vibrarBreve(contexto: Context) {
    val vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val administrador = contexto.getSystemService(VibratorManager::class.java) ?: return
        administrador.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        contexto.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    }
    if (!vibrador.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrador.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrador.vibrate(180)
    }
}
