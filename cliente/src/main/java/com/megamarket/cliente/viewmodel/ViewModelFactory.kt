package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.megamarket.cliente.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer,
    private val soloOfertas: Boolean = false
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val modelo: ViewModel = when {
            modelClass.isAssignableFrom(SesionViewModel::class.java) ->
                SesionViewModel(container.sesionRepository)
            modelClass.isAssignableFrom(InicioViewModel::class.java) ->
                InicioViewModel(container.catalogoRepository, container.mediosImagenRepository)
            modelClass.isAssignableFrom(CatalogoViewModel::class.java) ->
                CatalogoViewModel(
                    container.catalogoRepository,
                    soloOfertas,
                    container.mediosImagenRepository
                )
            modelClass.isAssignableFrom(DetalleProductoViewModel::class.java) ->
                DetalleProductoViewModel(
                    extras.createSavedStateHandle(),
                    container.catalogoRepository,
                    container.carritoRepository,
                    container.favoritosRepository,
                    container.mediosImagenRepository
                )
            modelClass.isAssignableFrom(FavoritosViewModel::class.java) ->
                FavoritosViewModel(container.favoritosRepository, container.mediosImagenRepository)
            modelClass.isAssignableFrom(CarritoViewModel::class.java) ->
                CarritoViewModel(container.carritoRepository, container.mediosImagenRepository)
            modelClass.isAssignableFrom(CheckoutViewModel::class.java) ->
                CheckoutViewModel(
                    container.sesionRepository,
                    container.pedidoRepository,
                    container.franjasRepository
                )
            modelClass.isAssignableFrom(ConfirmacionViewModel::class.java) ->
                ConfirmacionViewModel(
                    extras.createSavedStateHandle(),
                    container.pedidoRepository,
                    container.pagosRepository,
                    container.sincronizacionRepository
                )
            modelClass.isAssignableFrom(PerfilViewModel::class.java) ->
                PerfilViewModel(container.sesionRepository,container.pedidoRepository)
            modelClass.isAssignableFrom(SincronizacionViewModel::class.java) ->
                SincronizacionViewModel(container.sincronizacionRepository)
            modelClass.isAssignableFrom(SustitucionesViewModel::class.java) ->
                SustitucionesViewModel(container.sustitucionesRepository)
            modelClass.isAssignableFrom(DetalleSustitucionViewModel::class.java) ->
                DetalleSustitucionViewModel(
                    extras.createSavedStateHandle(),
                    container.sustitucionesRepository
                )
            modelClass.isAssignableFrom(MisPedidosViewModel::class.java) ->
                MisPedidosViewModel(container.misPedidosRepository)
            modelClass.isAssignableFrom(DetallePedidoViewModel::class.java) ->
                DetallePedidoViewModel(
                    extras.createSavedStateHandle(),
                    container.misPedidosRepository,
                    container.sustitucionesRepository
                )
            modelClass.isAssignableFrom(PagoQrViewModel::class.java) ->
                PagoQrViewModel(
                    extras.createSavedStateHandle(),
                    container.pagosRepository,
                    container.billeteraRepository,
                    container.pedidoRepository
                )
            modelClass.isAssignableFrom(BilleteraViewModel::class.java) ->
                BilleteraViewModel(container.billeteraRepository)
            else -> throw IllegalArgumentException("ViewModel no registrado: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return modelo as T
    }
}
