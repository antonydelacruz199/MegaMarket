package com.megamarket.cliente.ui.screens.sesion

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoMegaMarket()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MegaMarket Express",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Ingresa para comprar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = usuario,
            onValueChange = {
                usuario = it
                errorPermiso = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.usuario)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = mensajeError != null
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = clave,
            onValueChange = {
                clave = it
                errorPermiso = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.contrasena)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = mensajeError != null
        )
        if (mensajeError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mensajeError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { intentarIngresar(usuario, clave) },
            enabled = !estado.cargando,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (estado.cargando) {
                    stringResource(R.string.ingresando)
                } else {
                    stringResource(R.string.iniciar_sesion)
                }
            )
        }
    }
}
