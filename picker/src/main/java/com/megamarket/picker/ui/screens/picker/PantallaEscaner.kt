package com.megamarket.picker.ui.screens.picker

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
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
    alConfirmarPedido: () -> Unit = {}
) {
    val contexto = LocalContext.current
    var acceso by remember { mutableStateOf(EstadoCamaraUi()) }
    var permisoConcedido by remember { mutableStateOf(false) }
    var ingresoManualElegido by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(estado.resultado) {
        if (estado.resultado == TipoResultadoEscaneo.CORRECTO ||
            estado.resultado == TipoResultadoEscaneo.PENDIENTE_LOCAL
        ) {
            codigoManual = ""
        }
    }

    val puedeVolverACamara = ingresoManualElegido && permisoConcedido && acceso.decidido
    val mostrarManual = ingresoManualElegido || (acceso.decidido && !acceso.camaraActiva)
    val titulo = if (mostrarManual && !estado.pedidoListoParaConfirmar && !estado.lineaCompleta) {
        stringResource(R.string.ingresar_codigo_manual)
    } else {
        stringResource(R.string.escaner_titulo)
    }

    fun cerrarManualOVolver() {
        if (puedeVolverACamara) {
            ingresoManualElegido = false
            acceso = estadoTrasPermiso(true)
        } else {
            alVolver()
        }
    }

    BackHandler {
        cerrarManualOVolver()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = { cerrarManualOVolver() }) {
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
            val flujoActivo = !estado.lineaCompleta &&
                !estado.pedidoListoParaConfirmar &&
                !estado.pedidoConfirmado
            // Solo en cámara activa: los paneles de cierre/línea completa ya traen EncabezadoLinea.
            if (!mostrarManual && flujoActivo) {
                EncabezadoLinea(estado, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                !acceso.decidido && !ingresoManualElegido -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.preparando_camara))
                        }
                    }
                }
                estado.pedidoListoParaConfirmar || estado.pedidoConfirmado -> {
                    PanelCierrePedido(
                        estado = estado,
                        alConfirmarPedido = alConfirmarPedido,
                        alVolver = alVolver,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
                estado.lineaCompleta -> {
                    PanelLineaCompleta(
                        estado = estado,
                        alVolver = alVolver,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
                mostrarManual -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ingresar_codigo_manual),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!ingresoManualElegido) {
                            Spacer(Modifier.height(8.dp))
                            Text(textoMotivo(acceso.motivo))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = codigoManual,
                            onValueChange = { codigoManual = it },
                            label = { Text(stringResource(R.string.codigo_manual)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (codigoManual.isNotBlank() && !estado.enviando) {
                                        alManual(codigoManual)
                                    }
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !estado.enviando && !estado.lineaCompleta
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { alManual(codigoManual) },
                            enabled = codigoManual.isNotBlank() &&
                                !estado.enviando &&
                                !estado.lineaCompleta,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.registrar_codigo))
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelResultadoCompacto(estado, alReintentar, alContinuar)
                        Spacer(Modifier.height(16.dp))
                        PanelProgresoPedido(estado)
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        VistaPreviaCodigo(
                            activo = !estado.enviando,
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
                        PanelResultadoCompacto(estado, alReintentar, alContinuar)
                        TextButton(
                            onClick = {
                                ingresoManualElegido = true
                                acceso = estadoManual()
                            },
                            enabled = !estado.enviando
                        ) {
                            Text(stringResource(R.string.ingresar_codigo_manual))
                        }
                        TextButton(
                            onClick = alMarcarFaltante,
                            enabled = !estado.enviando &&
                                !estado.faltantePendiente &&
                                !estado.estadoLinea.equals("FALTANTE", true)
                        ) {
                            Text(stringResource(R.string.marcar_faltante))
                        }
                        if (estado.faltantePendiente) {
                            Text(
                                text = stringResource(R.string.sustitucion_faltante_pendiente),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelCierrePedido(
    estado: EscanerUiState,
    alConfirmarPedido: () -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EncabezadoLinea(estado)
        Spacer(Modifier.height(24.dp))
        if (estado.pedidoConfirmado) {
            Text(
                text = stringResource(R.string.pedido_listo_ok),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = alVolver, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.volver))
            }
        } else {
            Text(
                text = stringResource(R.string.linea_completa),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = alConfirmarPedido,
                enabled = !estado.confirmandoPedido,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (estado.confirmandoPedido) {
                        stringResource(R.string.confirmando_pedido)
                    } else {
                        stringResource(R.string.confirmar_pedido)
                    }
                )
            }
            if (!estado.mensaje.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PanelLineaCompleta(
    estado: EscanerUiState,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EncabezadoLinea(estado)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.linea_completa),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = alVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.volver_preparacion))
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
private fun PanelProgresoPedido(estado: EscanerUiState) {
    Text(
        text = stringResource(R.string.progreso_escaneados),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    if (estado.lineasEscaneadas.isEmpty()) {
        Text(
            text = stringResource(R.string.progreso_vacio_escaneados),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        estado.lineasEscaneadas.forEach { linea ->
            Text(
                text = stringResource(
                    R.string.progreso_linea,
                    linea.nombre,
                    linea.cantidadArmada,
                    linea.cantidadSolicitada
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.progreso_pendientes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    if (estado.lineasPendientes.isEmpty()) {
        Text(
            text = stringResource(R.string.progreso_vacio_pendientes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        estado.lineasPendientes.forEach { linea ->
            Text(
                text = stringResource(
                    R.string.progreso_linea,
                    linea.nombre,
                    linea.cantidadArmada,
                    linea.cantidadSolicitada
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PanelResultadoCompacto(
    estado: EscanerUiState,
    alReintentar: () -> Unit,
    alContinuar: () -> Unit
) {
    if (estado.enviando) {
        Text(stringResource(R.string.registrando_escaneo))
        Spacer(Modifier.height(8.dp))
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
    val mostrarSeguir = resultado != null &&
        resultado != TipoResultadoEscaneo.CORRECTO &&
        resultado != TipoResultadoEscaneo.PENDIENTE_LOCAL &&
        resultado != TipoResultadoEscaneo.SESION_VENCIDA &&
        !estado.enviando &&
        !estado.lineaCompleta
    if (mostrarSeguir) {
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
private fun textoMotivo(motivo: MotivoManual): String = when (motivo) {
    MotivoManual.SIN_CAMARA -> stringResource(R.string.camara_no_disponible)
    MotivoManual.PERMISO_DENEGADO -> stringResource(R.string.permiso_camara_denegado)
    MotivoManual.ELECCION, MotivoManual.NINGUNO -> stringResource(R.string.ingresar_codigo_manual)
}
