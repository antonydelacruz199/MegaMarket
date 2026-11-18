package com.megamarket.picker.ui.screens.picker

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.megamarket.picker.R
import com.megamarket.picker.camera.EstadoCamaraUi
import com.megamarket.picker.camera.MotivoManual
import com.megamarket.picker.camera.estadoAlAbrir
import com.megamarket.picker.camera.estadoManual
import com.megamarket.picker.camera.estadoTrasPermiso
import com.megamarket.picker.data.repository.TipoResultadoEscaneo
import com.megamarket.picker.state.EscanerUiState
import com.megamarket.picker.ui.components.ImagenProductoRemota
import com.megamarket.picker.ui.textoEstadoLinea
import com.megamarket.picker.ui.vibrarBreve
import com.megamarket.picker.ui.camera.VistaPreviaCodigo
import com.megamarket.picker.ui.camera.tieneCamara

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEscaner(
    estado: EscanerUiState,
    alVolver: () -> Unit,
    alDetectar: (String?) -> Unit,
    alManual: (String) -> Unit,
    alReintentar: () -> Unit,
    alContinuar: () -> Unit,
    alMarcarFaltante: () -> Unit,
    alSustituir: () -> Unit = {}
) {
    val contexto = LocalContext.current
    var acceso by remember { mutableStateOf(EstadoCamaraUi()) }
    var permisoConcedido by remember { mutableStateOf(false) }
    var codigoManual by rememberSaveable { mutableStateOf("") }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permisoConcedido = concedido
        acceso = estadoTrasPermiso(concedido)
    }

    LaunchedEffect(Unit) {
        val hayCamara = tieneCamara(contexto)
        val concedido = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        permisoConcedido = concedido
        val decision = estadoAlAbrir(hayCamara, concedido)
        acceso = decision
        if (decision.pedirPermiso) {
            permiso.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(estado.idVibracion) {
        if (estado.idVibracion > 0) vibrarBreve(contexto)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.escaner_titulo)) },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EncabezadoLinea(estado, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            when {
                !acceso.decidido -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.preparando_camara))
                        }
                    }
                }
                acceso.camaraActiva -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        VistaPreviaCodigo(
                            activo = true,
                            alDetectar = alDetectar,
                            alVacio = { alDetectar(null) },
                            alSinCamara = {
                                acceso = estadoAlAbrir(tieneCamara = false, permisoConcedido = false)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 240.dp, height = 140.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        )
                    }
                    Column(Modifier.padding(16.dp)) {
                        PanelResultado(estado, alReintentar, alContinuar)
                        TextButton(onClick = { acceso = estadoManual() }) {
                            Text(stringResource(R.string.ingresar_codigo_manual))
                        }
                        AccionesFaltanteSustitucion(
                            estado = estado,
                            alMarcarFaltante = alMarcarFaltante,
                            alSustituir = alSustituir
                        )
                        ListaOperaciones(estado)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(textoMotivo(acceso.motivo))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = codigoManual,
                            onValueChange = { codigoManual = it },
                            label = { Text(stringResource(R.string.codigo_manual)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { alManual(codigoManual) },
                            enabled = codigoManual.isNotBlank() && !estado.enviando,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.registrar_codigo))
                        }
                        if (permisoConcedido) {
                            TextButton(onClick = { acceso = estadoTrasPermiso(true) }) {
                                Text(stringResource(R.string.usar_camara))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelResultado(estado, alReintentar, alContinuar)
                        AccionesFaltanteSustitucion(
                            estado = estado,
                            alMarcarFaltante = alMarcarFaltante,
                            alSustituir = alSustituir
                        )
                        ListaOperaciones(estado)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccionesFaltanteSustitucion(
    estado: EscanerUiState,
    alMarcarFaltante: () -> Unit,
    alSustituir: () -> Unit
) {
    TextButton(
        onClick = alMarcarFaltante,
        enabled = !estado.enviando && !estado.faltantePendiente
    ) {
        Text(stringResource(R.string.marcar_faltante))
    }
    if (estado.faltantePendiente) {
        Text(
            text = stringResource(R.string.sustitucion_faltante_pendiente),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    } else if (estado.estadoLinea.equals("FALTANTE", ignoreCase = true)) {
        TextButton(
            onClick = alSustituir,
            enabled = !estado.enviando
        ) {
            Text(stringResource(R.string.proponer_sustituto))
        }
    }
}

@Composable
private fun EncabezadoLinea(estado: EscanerUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ImagenProductoRemota(
            url = estado.imagenUrl,
            descripcion = estado.nombreProducto,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (estado.nombreProducto.isNotBlank()) {
                Text(estado.nombreProducto, fontWeight = FontWeight.SemiBold)
            }
            if (estado.cantidadSolicitada > 0 || estado.cantidadArmada > 0) {
                Text(
                    stringResource(
                        R.string.armada_de_solicitada,
                        estado.cantidadArmada,
                        estado.cantidadSolicitada
                    )
                )
            }
            if (estado.cantidadPendienteLocal > 0) {
                Text(stringResource(R.string.pendiente_local, estado.cantidadPendienteLocal))
            }
            if (estado.faltantePendiente) {
                Text(stringResource(R.string.faltante_pendiente))
            }
            if (estado.estadoLinea.isNotBlank()) {
                Text(stringResource(R.string.estado_producto, textoEstadoLinea(estado.estadoLinea)))
            }
        }
    }
}

@Composable
private fun ListaOperaciones(estado: EscanerUiState) {
    if (estado.operaciones.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    estado.operaciones.take(8).forEach { op ->
        Text(
            text = "${textoEstadoOperacion(op.estado, op.resultado)} · ${op.operacion}" +
                (op.codigoBarras?.let { " · $it" } ?: "") +
                (op.ultimoError?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PanelResultado(
    estado: EscanerUiState,
    alReintentar: () -> Unit,
    alContinuar: () -> Unit
) {
    if (estado.enviando) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.registrando_escaneo))
    }
    val resultado = estado.resultado
    if (resultado != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = tituloResultado(resultado),
            color = when (resultado) {
                TipoResultadoEscaneo.CORRECTO,
                TipoResultadoEscaneo.PENDIENTE_LOCAL -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.SemiBold
        )
    }
    if (!estado.mensaje.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(estado.mensaje)
    }
    if (estado.puedeReintentar) {
        Spacer(Modifier.height(8.dp))
        Button(onClick = alReintentar, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reintentar))
        }
    }
    val hayResultado = resultado != null || !estado.mensaje.isNullOrBlank()
    if (!estado.enviando && hayResultado && resultado != TipoResultadoEscaneo.SESION_VENCIDA) {
        TextButton(onClick = alContinuar) {
            Text(stringResource(R.string.seguir_escaneando))
        }
    }
}

@Composable
private fun tituloResultado(tipo: TipoResultadoEscaneo): String = when (tipo) {
    TipoResultadoEscaneo.CORRECTO -> stringResource(R.string.resultado_correcto)
    TipoResultadoEscaneo.INCORRECTO -> stringResource(R.string.resultado_incorrecto)
    TipoResultadoEscaneo.CANTIDAD_EXCEDIDA -> stringResource(R.string.resultado_cantidad_excedida)
    TipoResultadoEscaneo.PEDIDO_NO_ASIGNADO -> stringResource(R.string.resultado_pedido_no_asignado)
    TipoResultadoEscaneo.SESION_VENCIDA -> stringResource(R.string.resultado_sesion_vencida)
    TipoResultadoEscaneo.PENDIENTE_LOCAL -> stringResource(R.string.resultado_pendiente_local)
    TipoResultadoEscaneo.RECHAZADO -> stringResource(R.string.resultado_rechazado)
}

@Composable
private fun textoEstadoOperacion(estado: String, resultado: String?): String = when {
    estado == "PENDIENTE" || estado == "ENVIANDO" -> stringResource(R.string.operacion_pendiente)
    estado == "ERROR" -> stringResource(R.string.operacion_rechazada)
    resultado == "INCORRECTO" -> stringResource(R.string.operacion_incorrecta)
    estado == "SINCRONIZADO" -> stringResource(R.string.operacion_confirmada)
    else -> estado
}

@Composable
private fun textoMotivo(motivo: MotivoManual): String = when (motivo) {
    MotivoManual.SIN_CAMARA -> stringResource(R.string.camara_no_disponible)
    MotivoManual.PERMISO_DENEGADO -> stringResource(R.string.permiso_camara_denegado)
    MotivoManual.ELECCION, MotivoManual.NINGUNO -> stringResource(R.string.ingresar_codigo_manual)
}
