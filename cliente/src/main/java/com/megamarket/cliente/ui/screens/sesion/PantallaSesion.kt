package com.megamarket.cliente.ui.screens.sesion

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.BuildConfig
import com.megamarket.cliente.R
import com.megamarket.cliente.data.connectivity.MENSAJE_PERMISO_RED_LOCAL
import com.megamarket.cliente.data.connectivity.PERMISO_RED_LOCAL
import com.megamarket.cliente.data.connectivity.registrarEstadoPermisoRedLocal
import com.megamarket.cliente.data.connectivity.requierePermisoRedLocal
import com.megamarket.cliente.data.connectivity.tienePermisoRedLocal
import com.megamarket.cliente.ui.components.LogoMegaMarket
import com.megamarket.cliente.ui.theme.Espaciado
import com.megamarket.cliente.ui.theme.MegaContenedorPrimario
import com.megamarket.cliente.ui.theme.MegaFondo
import com.megamarket.cliente.viewmodel.SesionViewModel

private const val TAG = "MegaMarketSesion"

@Composable
fun PantallaSesion(viewModel: SesionViewModel) {
    val contexto = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var errorPermiso by rememberSaveable { mutableStateOf<String?>(null) }
    var credencialesPendientes by remember { mutableStateOf<Pair<String, String>?>(null) }
    val mensajeError = errorPermiso ?: estado.error

    val launcherPermiso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        registrarEstadoPermisoRedLocal(concedido)
        val pendientes = credencialesPendientes
        credencialesPendientes = null
        if (concedido && pendientes != null) {
            Log.i(TAG, "Permiso red local OK; continuando login")
            viewModel.iniciarSesion(pendientes.first, pendientes.second)
        } else {
            errorPermiso = MENSAJE_PERMISO_RED_LOCAL
        }
    }

    fun intentarIngresar(nombreUsuario: String, claveAcceso: String) {
        val baseUrl = BuildConfig.MEGAMARKET_API_BASE_URL
        Log.i(TAG, "Login: baseUrl=$baseUrl")
        if (requierePermisoRedLocal(baseUrl) && !tienePermisoRedLocal(contexto)) {
            Log.i(TAG, "Solicitando ACCESS_LOCAL_NETWORK")
            credencialesPendientes = nombreUsuario to claveAcceso
            launcherPermiso.launch(PERMISO_RED_LOCAL)
            return
        }
        errorPermiso = null
        viewModel.iniciarSesion(nombreUsuario, claveAcceso)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MegaContenedorPrimario, MegaFondo)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espaciado.xl, vertical = Espaciado.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoMegaMarket(tamano = 88.dp)
            Spacer(modifier = Modifier.height(Espaciado.md))
            Text(
                text = "MegaMarket Express",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ingresa para comprar en tu barrio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Espaciado.xl))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(Espaciado.xl)) {
                    Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Usa tu usuario para empezar el pedido",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Espaciado.xs, bottom = Espaciado.lg)
                    )
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = {
                            usuario = it
                            errorPermiso = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.usuario)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        isError = mensajeError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(Espaciado.md))
                    OutlinedTextField(
                        value = clave,
                        onValueChange = {
                            clave = it
                            errorPermiso = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contrasena)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = mensajeError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (mensajeError != null) {
                        Spacer(modifier = Modifier.height(Espaciado.sm))
                        Text(
                            text = mensajeError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(Espaciado.lg))
                    Button(
                        onClick = { intentarIngresar(usuario, clave) },
                        enabled = !estado.cargando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (estado.cargando) {
                                stringResource(R.string.ingresando)
                            } else {
                                stringResource(R.string.iniciar_sesion)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
