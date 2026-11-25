package com.megamarket.app.ui.screens.admin.proveedores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.data.repository.Proveedor
import com.megamarket.app.state.FiltroProveedor
import com.megamarket.app.ui.components.BarraBusqueda
import com.megamarket.app.ui.components.CargandoCentrado
import com.megamarket.app.ui.components.EstadoVacio
import com.megamarket.app.ui.components.EstructuraAdmin
import com.megamarket.app.ui.components.InsigniaEstado
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.ui.theme.MegaContenedorError
import com.megamarket.app.ui.theme.MegaContenedorPrimario
import com.megamarket.app.ui.theme.MegaError
import com.megamarket.app.ui.theme.MegaPrimario
import com.megamarket.app.viewmodel.ProveedoresViewModel

@Composable
fun PantallaProveedores(
    viewModel: ProveedoresViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alDetalle: (String) -> Unit,
    alNuevo: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargar()
        onPauseOrDispose { }
    }

    EstructuraAdmin(
        rutaActual = Ruta.ProveedoresAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Proveedores",
        botonFlotante = {
            FloatingActionButton(
                onClick = alNuevo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Nuevo proveedor")
            }
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                BarraBusqueda(
                    consulta = estado.consulta,
                    alCambiarConsulta = viewModel::actualizarConsulta,
                    marcador = "Buscar por RUC o razón social"
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FiltroProveedor.entries, key = { it.name }) { filtro ->
                        FilterChip(
                            selected = estado.filtro == filtro,
                            onClick = { viewModel.seleccionarFiltro(filtro) },
                            label = { Text(filtro.etiqueta) }
                        )
                    }
                }
            }

            when {
                estado.cargando && estado.proveedores.isEmpty() -> CargandoCentrado(Modifier.weight(1f))

                estado.error != null && estado.proveedores.isEmpty() -> EstadoVacio(
                    icono = Icons.Default.LocalShipping,
                    titulo = "No se pudo cargar",
                    descripcion = estado.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    etiquetaAccion = "Reintentar",
                    alPulsarAccion = viewModel::cargar
                )

                estado.proveedores.isEmpty() -> EstadoVacio(
                    icono = Icons.Default.LocalShipping,
                    titulo = if (estado.consulta.isBlank() && estado.filtro == FiltroProveedor.TODOS) {
                        "Sin proveedores"
                    } else {
                        "Sin resultados"
                    },
                    descripcion = if (estado.consulta.isBlank() && estado.filtro == FiltroProveedor.TODOS) {
                        "Registra el primer proveedor con el botón +"
                    } else {
                        "No hay proveedores con esa búsqueda o filtro"
                    },
                    modifier = Modifier.weight(1f)
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        estado.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    items(estado.proveedores, key = { it.id }) { proveedor ->
                        TarjetaProveedor(proveedor, alPulsar = { alDetalle(proveedor.id) })
                    }
                    if (estado.hayMas) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::cargarMas,
                                enabled = !estado.cargandoMas,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (estado.cargandoMas) "Cargando…" else "Cargar más") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaProveedor(proveedor: Proveedor, alPulsar: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = proveedor.razonSocial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                if (proveedor.activo) {
                    InsigniaEstado("Activo", MegaContenedorPrimario, MegaPrimario)
                } else {
                    InsigniaEstado("Inactivo", MegaContenedorError, MegaError)
                }
            }
            Text("RUC ${proveedor.ruc}", style = MaterialTheme.typography.bodyMedium)
            proveedor.telefono?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            proveedor.correo?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
