# MegaMarket Express — Contexto maestro para Cursor

> Este archivo es la fuente de verdad del proyecto. Cursor debe leerlo antes de crear, modificar o refactorizar código.

## 1. Identificación del proyecto

- **Nombre comercial:** MegaMarket Express
- **Nombre del proyecto Android:** MegaMarket
- **Desarrollador:** Antony Marcelo De La Cruz Cardenas
- **Sector:** Retail / Abarrotes
- **Ciudad de referencia:** Huancayo, Junín, Perú
- **Plataforma actual:** Android
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose
- **Diseño:** Material 3
- **Arquitectura:** MVVM + Repository
- **Objetivo académico:** implementar un aplicativo de supermercado funcional, ordenado, demostrable y extensible, cumpliendo al 100 % los requerimientos RF01–RF10 y las mejoras definidas en este documento.

## 2. Principios del proyecto

1. Priorizar primero los requerimientos obligatorios RF01–RF10.
2. Las mejoras RF11–RF24 no deben romper ni reemplazar los requerimientos originales.
3. El código debe ser sencillo de leer, modular y mantenible.
4. No colocar lógica de negocio dentro de composables ni `MainActivity`.
5. No crear funcionalidades ficticias que no estén documentadas aquí sin indicarlo previamente.
6. La UI debe estar en español.
7. El proyecto debe compilar después de cada cambio importante.
8. Evitar sobreingeniería: no introducir patrones, librerías o capas innecesarias.
9. Si existe una decisión entre una solución compleja y otra simple que cumple el mismo requerimiento, preferir la simple y mantenible.
10. Mantener compatibilidad mínima con **API 24 (Android 7.0)** salvo que el proyecto ya haya sido creado con otro mínimo compatible.

---

# 3. Alcance funcional

MegaMarket Express tendrá dos roles:

### Cliente

Puede iniciar sesión, visualizar y buscar productos, filtrar por categoría u oferta, ver detalles, guardar favoritos, administrar un carrito, ingresar una dirección y completar un checkout simulado.

### Administrador

Puede iniciar sesión, visualizar un dashboard, consultar el catálogo administrativo, crear y editar productos, y modificar precio, stock, estado y ofertas.

No se implementarán pagos reales, delivery GPS, pasarela de pago, Firebase, autenticación externa, backend remoto ni mapas reales en la primera versión.

---

# 4. Historias de usuario oficiales

## HU01 — Splash

**Como** usuario, **quiero** visualizar una pantalla Splash, **para** identificar MegaMarket Express al iniciar la aplicación.

## HU02 — Inicio de sesión y rol

**Como** usuario, **quiero** iniciar sesión, **para** acceder a las funciones correspondientes a mi rol de cliente o administrador.

## HU03 — Catálogo

**Como** cliente, **quiero** visualizar el catálogo, **para** conocer los productos disponibles.

## HU04 — Búsqueda

**Como** cliente, **quiero** buscar productos por nombre o marca, **para** encontrarlos rápidamente.

## HU05 — Filtro por categoría

**Como** cliente, **quiero** filtrar productos por categoría, **para** reducir los resultados mostrados.

## HU06 — Ofertas

**Como** cliente, **quiero** visualizar productos en oferta, **para** conocer las promociones de la semana.

## HU07 — Detalle del producto

**Como** cliente, **quiero** consultar el detalle de un producto, **para** conocer su marca, descripción, precio, stock y oferta.

## HU08 — Carrito

**Como** cliente, **quiero** agregar productos y modificar sus cantidades, **para** preparar mi compra.

## HU09 — Totales

**Como** cliente, **quiero** visualizar subtotal y total, **para** conocer el importe de mi compra.

## HU10 — Favoritos

**Como** cliente, **quiero** guardar productos como favoritos, **para** encontrarlos rápidamente en futuras compras.

## HU11 — Dirección

**Como** cliente, **quiero** registrar una dirección de entrega, **para** completar el checkout simulado.

## HU12 — Confirmación

**Como** cliente, **quiero** visualizar una confirmación final, **para** verificar que la compra simulada fue registrada correctamente.

## HU13 — Catálogo administrativo

**Como** administrador, **quiero** visualizar los productos registrados, **para** administrar el catálogo.

## HU14 — Registrar producto

**Como** administrador, **quiero** registrar productos, **para** ampliar el catálogo disponible.

## HU15 — Editar producto

**Como** administrador, **quiero** editar productos, **para** mantener actualizada su información.

## HU16 — Precio y stock

**Como** administrador, **quiero** actualizar precio y stock, **para** reflejar la disponibilidad del supermercado.

## HU17 — Ofertas administrativas

**Como** administrador, **quiero** activar o desactivar ofertas, **para** administrar promociones semanales.

---

# 5. Requerimientos funcionales

## Requerimientos obligatorios de la guía

- **RF01.** Incluir Splash Screen y Login.
- **RF02.** Permitir acceso como cliente o administrador.
- **RF03.** Mostrar catálogo de mínimo 30 productos.
- **RF04.** Filtrar por categoría y oferta semanal.
- **RF05.** Agregar productos y modificar cantidades en carrito.
- **RF06.** Calcular subtotal y total.
- **RF07.** Registrar dirección de entrega en checkout simulado.
- **RF08.** Guardar productos favoritos para recompra.
- **RF09.** Permitir al administrador registrar o editar productos.
- **RF10.** Permitir actualizar stock, precio y oferta.

## Requerimientos mejorados

- **RF11.** Buscar productos por nombre o marca.
- **RF12.** Mostrar detalle del producto.
- **RF13.** Evitar que la cantidad del carrito supere el stock disponible.
- **RF14.** Permitir eliminar productos del carrito.
- **RF15.** Mantener carrito y favoritos almacenados localmente.
- **RF16.** Mostrar los productos agotados como no disponibles y bloquear su agregado al carrito.
- **RF17.** Mostrar precio regular y precio de oferta cuando corresponda.
- **RF18.** Permitir limpiar búsqueda y filtros aplicados.
- **RF19.** Mostrar pantalla de confirmación después del checkout.
- **RF20.** Permitir activar/desactivar productos sin eliminarlos físicamente.
- **RF21.** Identificar productos con stock bajo en el panel administrativo.
- **RF22.** Validar campos obligatorios al crear o editar productos.
- **RF23.** Validar los datos obligatorios de la dirección antes de confirmar el checkout.
- **RF24.** Permitir cerrar sesión y regresar al Login.

---

# 6. Requerimientos no funcionales

- **RNF01.** Usar Kotlin y Jetpack Compose.
- **RNF02.** Implementar MVVM.
- **RNF03.** Utilizar Material 3.
- **RNF04.** Persistir los datos de negocio localmente con Room cuando corresponda.
- **RNF05.** Utilizar `StateFlow`/Flow para exponer estado desde ViewModels.
- **RNF06.** Mantener consistencia visual en colores, tipografía, componentes y espaciado.
- **RNF07.** Validar formularios antes de guardar o confirmar acciones.
- **RNF08.** Separar claramente el flujo Cliente del flujo Administrador.
- **RNF09.** Considerar estados `Loading`, `Content`, `Empty` y `Error` donde tengan sentido.
- **RNF10.** No realizar acceso a repositorios o base de datos directamente desde composables.
- **RNF11.** Evitar bloquear el hilo principal con operaciones de datos.
- **RNF12.** Mantener strings visibles al usuario en recursos `strings.xml` cuando sea práctico.
- **RNF13.** La aplicación debe poder demostrarse sin conexión a Internet.

---

# 7. Stack técnico

## Obligatorio

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- StateFlow / Flow
- Coroutines
- Room

## Recomendado

- Lifecycle Compose (`collectAsStateWithLifecycle`)
- DataStore para preferencias simples o sesión local si es necesario
- Coil únicamente si se necesitan imágenes remotas o URIs; para el catálogo inicial se prefieren recursos locales

## No introducir todavía

- Firebase
- Retrofit / API remota
- Hilt, salvo que se apruebe expresamente o el proyecto haya crecido lo suficiente para justificarlo
- pagos reales
- Google Maps
- servicios de ubicación
- notificaciones push

La primera versión debe funcionar completamente de forma local.

---

# 8. Arquitectura

Flujo esperado:

```text
Compose UI
   ↓ eventos
ViewModel
   ↓
Repository
   ↓
Room / fuente local

Room / fuente local
   ↓ Flow
Repository
   ↓
ViewModel (UiState)
   ↓
Compose UI
```

## Reglas MVVM

- `MainActivity` solo debe inicializar el tema y la navegación principal.
- Cada pantalla obtiene estado de un ViewModel o recibe estado por parámetros.
- Los composables no contienen consultas a Room.
- Los composables no calculan reglas complejas de negocio.
- Los ViewModels coordinan estado y acciones de UI.
- Los repositories abstraen la fuente de datos.
- Las entidades Room no deben filtrarse directamente a toda la UI si luego se necesita separar el modelo de presentación.
- Preferir UI stateless y state hoisting cuando sea posible.

---

# 9. Estructura de paquetes sugerida

```text
com.megamarket.app
│
├── data/
│   ├── local/
│   │   ├── dao/
│   │   ├── database/
│   │   └── entity/
│   ├── mapper/
│   └── repository/
│
├── model/
│   ├── Producto.kt
│   ├── Categoria.kt
│   ├── CarritoItem.kt
│   ├── Pedido.kt
│   ├── Direccion.kt
│   ├── Favorito.kt
│   ├── Usuario.kt
│   └── RolUsuario.kt
│
├── repository/
│   ├── AuthRepository.kt
│   ├── ProductoRepository.kt
│   ├── CarritoRepository.kt
│   ├── FavoritosRepository.kt
│   └── PedidoRepository.kt
│
├── ui/
│   ├── components/
│   ├── navigation/
│   ├── screens/
│   │   ├── splash/
│   │   ├── login/
│   │   ├── home/
│   │   ├── catalogo/
│   │   ├── producto/
│   │   ├── favoritos/
│   │   ├── carrito/
│   │   ├── checkout/
│   │   ├── confirmacion/
│   │   ├── perfil/
│   │   └── admin/
│   │       ├── dashboard/
│   │       ├── productos/
│   │       └── producto_form/
│   └── theme/
│
├── viewmodel/
│   ├── AuthViewModel.kt
│   ├── CatalogoViewModel.kt
│   ├── CarritoViewModel.kt
│   ├── FavoritosViewModel.kt
│   ├── CheckoutViewModel.kt
│   └── AdminProductoViewModel.kt
│
└── MainActivity.kt
```

No crear todas las carpetas vacías de forma anticipada. Crear cada paquete cuando exista una clase real que pertenezca a él.

---

# 10. Modelos de dominio

## Producto

Se recomienda manejar dinero en céntimos (`Long`) para evitar errores de coma flotante.

```kotlin
data class Producto(
    val id: Long,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long? = null,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean = true
)
```

Reglas:

- `precioCentimos > 0`
- `stock >= 0`
- si `esOferta == true`, `precioOfertaCentimos` debe existir y ser menor que `precioCentimos`
- producto con `stock == 0` no puede agregarse al carrito
- producto con `activo == false` no debe aparecer en el catálogo del cliente

## Categoria

```kotlin
data class Categoria(
    val id: Long,
    val nombre: String,
    val iconoKey: String
)
```

Categorías iniciales sugeridas:

- Abarrotes
- Lácteos
- Bebidas
- Limpieza
- Snacks
- Cuidado personal
- Frutas y verduras

## CarritoItem

```kotlin
data class CarritoItem(
    val producto: Producto,
    val cantidad: Int
)
```

Reglas:

- cantidad mínima: 1
- cantidad máxima: stock del producto
- subtotal del ítem = precio vigente × cantidad
- precio vigente = precio de oferta si la oferta está activa; de lo contrario precio regular

## Direccion

```kotlin
data class Direccion(
    val id: Long = 0,
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val referencia: String,
    val telefono: String
)
```

Valores de ejemplo para la demo pueden usar:

- Departamento: Junín
- Provincia: Huancayo

No fijar obligatoriamente el distrito.

## Usuario y rol

```kotlin
enum class RolUsuario {
    CLIENTE,
    ADMINISTRADOR
}

data class Usuario(
    val id: Long,
    val nombre: String,
    val correo: String,
    val rol: RolUsuario
)
```

---

# 11. Autenticación simulada

La primera versión no tendrá backend.

Credenciales demo sugeridas:

### Cliente

- Correo: `cliente@megamarket.com`
- Clave: `123456`

### Administrador

- Correo: `admin@megamarket.com`
- Clave: `admin123`

Reglas:

- Las credenciales no deben validarse directamente dentro del composable.
- `AuthRepository` debe encapsular la validación demo.
- `AuthViewModel` expone el resultado del login y el rol.
- Cliente navega al flujo Cliente.
- Administrador navega al flujo Administrador.
- Debe existir cierre de sesión.

---

# 12. Catálogo inicial

Crear **40 productos iniciales** para superar el mínimo de 30.

Distribución sugerida:

| Categoría | Cantidad aproximada |
|---|---:|
| Abarrotes | 10 |
| Lácteos | 6 |
| Bebidas | 6 |
| Limpieza | 6 |
| Snacks | 5 |
| Cuidado personal | 4 |
| Frutas y verduras | 3 |
| **Total** | **40** |

Los productos deben tener:

- nombre
- marca
- descripción breve
- categoría
- precio
- stock
- imagen
- estado de oferta
- precio de oferta cuando corresponda
- activo/inactivo

Debe haber datos variados para demostrar:

- varios productos en oferta
- al menos 2 productos agotados
- al menos 4 productos con stock bajo
- productos de todas las categorías
- varias marcas

## Imágenes

Para la primera entrega priorizar imágenes locales y estables.

- Colocar assets/drawables con nombres claros.
- Usar un placeholder cuando una imagen no exista.
- No almacenar IDs enteros de `R.drawable` en Room; almacenar una clave/nombre y resolverla en UI.

---

# 13. Navegación

## Flujo principal

```text
Splash → Login

Cliente:
Inicio/Catálogo → Filtros → Detalle → Carrito → Checkout → Confirmación

Administrador:
Dashboard → Productos → Crear/Editar → Stock/Precio/Oferta
```

## Rutas sugeridas

```text
splash
login

home
catalogo
producto/{productoId}
favoritos
carrito
checkout
confirmacion
perfil

admin/dashboard
admin/productos
admin/producto/nuevo
admin/producto/{productoId}/editar
```

Usar rutas constantes o una representación tipada si la versión de Navigation utilizada en el proyecto lo permite sin aumentar innecesariamente la complejidad.

## Restricción por rol

- Un cliente no debe navegar mediante UI a pantallas administrativas.
- Un administrador debe iniciar en `admin/dashboard`.
- No duplicar pantallas si una pantalla reutilizable resuelve ambos casos correctamente.

---

# 14. Navigation Drawer y navegación inferior

El proyecto debe incorporar **Navigation Drawer con iconos**.

## Cliente — Bottom Navigation

Destinos principales:

1. Inicio
2. Catálogo
3. Favoritos
4. Carrito

Mostrar badge de cantidad en Carrito cuando existan productos.

## Cliente — Navigation Drawer

- Inicio
- Catálogo
- Ofertas
- Favoritos
- Carrito
- Perfil
- Cerrar sesión

Todos deben usar iconos de Material Icons.

## Administrador — Navigation Drawer

- Dashboard
- Productos
- Stock bajo
- Cerrar sesión

No es obligatorio utilizar Bottom Navigation en el panel de administrador.

---

# 15. Pantallas

## SplashScreen

Debe mostrar:

- identidad de MegaMarket Express
- logo o icono de carrito/bolsa de compra
- nombre de la app
- transición breve al Login

No utilizar esperas excesivas. Una demora simulada de aproximadamente 1–2 segundos es suficiente si se necesita demostrar la pantalla.

## LoginScreen

Debe incluir:

- logo/nombre
- correo
- contraseña
- mostrar/ocultar contraseña
- botón Ingresar
- validación de campos vacíos
- mensaje de credenciales incorrectas

El rol se obtiene de la cuenta autenticada. No pedir al usuario que seleccione manualmente su rol si las credenciales ya lo determinan.

## HomeScreen

Debe incluir como mínimo:

- top bar con MegaMarket Express
- acceso al Drawer
- acceso visible al carrito
- buscador o acceso rápido al catálogo
- banner de ofertas
- categorías
- productos destacados/ofertas

## CatalogoScreen

Debe incluir:

- búsqueda
- categorías
- filtro “Ofertas”
- opción limpiar filtros
- lista/grid de productos
- estado sin resultados
- acceso a detalle
- acción para favorito
- acción para agregar al carrito

## ProductoDetalleScreen

Debe mostrar:

- imagen
- nombre
- marca
- descripción
- categoría
- precio
- oferta si existe
- stock
- control de cantidad
- agregar al carrito
- favorito

## FavoritosScreen

Debe permitir:

- listar favoritos
- quitar favorito
- abrir detalle
- agregar directamente al carrito si hay stock
- estado vacío con mensaje claro

## CarritoScreen

Debe permitir:

- listar ítems
- aumentar cantidad
- disminuir cantidad
- eliminar producto
- mostrar subtotal por ítem
- mostrar subtotal general
- mostrar total
- continuar al checkout
- estado carrito vacío

Para la primera versión simulada, `total == subtotal`, salvo que luego se agregue delivery/descuento de forma explícita.

## CheckoutScreen

Debe incluir formulario:

- departamento
- provincia
- distrito
- dirección
- referencia
- teléfono
- resumen del carrito
- total
- botón Confirmar compra

No permitir confirmar si:

- carrito vacío
- dirección vacía
- provincia vacía
- distrito vacío
- teléfono inválido

## ConfirmacionScreen

Debe mostrar:

- icono de éxito
- mensaje “Compra simulada registrada correctamente” o equivalente
- resumen breve
- acción Volver al inicio

Al confirmar correctamente, vaciar el carrito.

## AdminDashboardScreen

Indicadores mínimos:

- productos activos
- productos en oferta
- productos con stock bajo
- productos agotados

Debe permitir navegar a la administración de productos.

## AdminProductosScreen

Debe permitir:

- listar productos
- buscar por nombre/marca
- mostrar precio
- mostrar stock
- indicar oferta
- indicar inactivo
- editar
- crear nuevo producto

## AdminProductoFormScreen

Campos mínimos:

- nombre
- marca
- descripción
- categoría
- precio
- stock
- oferta sí/no
- precio oferta si corresponde
- activo sí/no

Validaciones:

- nombre obligatorio
- marca obligatoria
- categoría obligatoria
- precio > 0
- stock >= 0
- precio oferta > 0
- precio oferta < precio regular

---

# 16. ViewModels

## AuthViewModel

Responsabilidades:

- estado de correo/clave si se decide centralizar formulario
- login
- resultado del login
- usuario actual
- rol
- logout

## CatalogoViewModel

Responsabilidades:

- lista de productos
- búsqueda
- categoría seleccionada
- filtro ofertas
- limpiar filtros
- combinación reactiva de filtros
- producto seleccionado, si es necesario

Los filtros deben poder combinarse. Ejemplo: “Lácteos” + “Solo ofertas” + búsqueda “Gloria”.

## CarritoViewModel

Responsabilidades:

- agregar producto
- eliminar producto
- incrementar/decrementar cantidad
- validar stock
- subtotal por ítem
- subtotal general
- total
- vaciar carrito después de confirmar

## FavoritosViewModel

Responsabilidades:

- listar favoritos
- alternar favorito
- quitar favorito
- consultar si un producto es favorito

## CheckoutViewModel

Responsabilidades:

- estado del formulario de dirección
- validación
- confirmación simulada
- generación del pedido local si se implementa
- estado de éxito/error

## AdminProductoViewModel

Responsabilidades:

- listar productos para administración
- buscar
- crear
- editar
- actualizar precio
- actualizar stock
- actualizar oferta
- activar/desactivar
- detectar stock bajo
- validar formulario

---

# 17. Repositories

## AuthRepository

- autenticar usuario demo
- recuperar usuario por credenciales
- cerrar sesión local si se persiste sesión

## ProductoRepository

- observar productos activos
- observar todos los productos para administrador
- obtener por ID
- buscar/filtrar cuando corresponda
- crear
- editar
- actualizar stock/precio/oferta

## CarritoRepository

- observar carrito
- agregar/modificar/eliminar ítems
- limpiar carrito

## FavoritosRepository

- observar favoritos
- agregar/quitar
- consultar estado

## PedidoRepository

- registrar checkout simulado si se implementa persistencia de pedidos
- recuperar pedido para confirmación si es necesario

---

# 18. Persistencia Room

Tablas mínimas sugeridas:

```text
productos
categorias
carrito_items
favoritos
pedidos              (opcional para primera iteración, recomendado para confirmación)
pedido_detalle       (opcional si se persisten pedidos)
direcciones          (opcional si se desea guardar direcciones)
```

## Consideraciones

- Utilizar claves primarias claras.
- Definir índices donde aporten valor, no por defecto.
- Favoritos debe evitar duplicados por producto.
- Carrito debe tener como máximo un registro por producto y actualizar cantidad.
- Sembrar catálogo/categorías solo cuando la base esté vacía.
- No recrear los productos iniciales en cada ejecución.

---

# 19. Reglas de negocio

1. No agregar al carrito productos con stock 0.
2. La cantidad no puede superar el stock.
3. Disminuir desde cantidad 1 puede eliminar el producto o bloquear en 1; preferir bloquear en 1 y usar acción explícita eliminar.
4. El precio aplicado al carrito debe ser el precio vigente del producto.
5. El subtotal de un ítem es `precioVigente × cantidad`.
6. El subtotal general es la suma de subtotales.
7. En la primera versión `total = subtotal` si no existe cargo adicional documentado.
8. No permitir checkout con carrito vacío.
9. No permitir checkout sin dirección válida.
10. Un favorito no debe duplicarse.
11. Un producto inactivo no aparece en catálogo cliente.
12. Un administrador puede reactivar un producto.
13. Stock bajo: usar por defecto `stock in 1..5` salvo que se defina otro umbral centralizado.
14. Agotado: `stock == 0`.
15. Oferta válida: `esOferta && precioOfertaCentimos != null && precioOfertaCentimos < precioCentimos`.

---

# 20. UI/UX

## Línea visual sugerida

MegaMarket debe verse como supermercado moderno, limpio y accesible.

Paleta inicial propuesta:

- Primary: `#0F7B4B` — verde supermercado
- OnPrimary: `#FFFFFF`
- Secondary: `#F5A623` — ámbar para promociones
- Background: `#F7F8F5`
- Surface: `#FFFFFF`
- Error: usar color Material 3 compatible

Si ya existe un tema creado, conservar consistencia y no cambiarlo arbitrariamente.

## Componentes reutilizables

Crear cuando realmente sean reutilizados:

- `ProductCard`
- `CategoryChip` / `CategoryItem`
- `OfferBadge`
- `PriceView`
- `QuantitySelector`
- `EmptyState`
- `LoadingState`
- `AppTopBar`
- `MegaMarketDrawer`
- `CartBadge`
- `AdminStatCard`

## Reglas visuales

- Material 3.
- Espaciado consistente en múltiplos de 4/8 dp.
- Cards con jerarquía clara, sin exceso de sombras.
- No saturar la interfaz con demasiados colores.
- Mostrar precio de oferta con mayor jerarquía y precio anterior tachado.
- Mantener botones principales visibles.
- Evitar textos diminutos.
- Usar iconos Material donde corresponda.
- Soportar scroll correctamente y evitar contenido cortado.

---

# 21. Estados de UI

Las pantallas de datos deben contemplar cuando aplique:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

No es obligatorio aplicar el mismo genérico a todas las pantallas si una `data class XxxUiState` resulta más clara.

Ejemplos de estados vacíos:

- “No se encontraron productos”
- “Todavía no tienes favoritos”
- “Tu carrito está vacío”
- “No hay productos con stock bajo”

---

# 22. Manejo de dinero

Preferir `Long` en céntimos.

Ejemplo:

```kotlin
val precioCentimos = 480L // S/ 4.80
```

Crear una función reutilizable de formato:

```kotlin
fun Long.toSoles(): String
```

Resultado esperado:

```text
S/ 4.80
```

No realizar sumas de dinero con `Float`.

---

# 23. Convenciones de código

- Clases/interfaces/objetos: `PascalCase`.
- Funciones/variables: `camelCase`.
- Constantes: `UPPER_SNAKE_CASE` cuando corresponda.
- Un archivo principal por clase pública importante.
- Nombres en código preferentemente en español para mantener coherencia académica del proyecto, salvo APIs/librerías o convenciones Android.
- No abreviar nombres sin necesidad.
- Evitar `!!` salvo caso justificado.
- Evitar números mágicos; centralizar umbrales como stock bajo.
- Composables de pantalla terminan en `Screen`.
- Componentes reutilizables con nombres descriptivos.
- Eventos de UI pueden representarse mediante funciones explícitas o `sealed interface` si realmente aporta claridad.
- Usar `viewModelScope` para coroutines desde ViewModel.
- En Compose, preferir `collectAsStateWithLifecycle()`.
- No iniciar coroutines arbitrarias en composables si una API declarativa como `LaunchedEffect` resuelve el caso.
- No crear ViewModels manualmente dentro de cada recomposición.

---

# 24. Strings y recursos

Textos visibles importantes deben ir en `res/values/strings.xml` cuando sea viable.

Ejemplos:

```xml
<string name="app_name">MegaMarket Express</string>
<string name="login_title">Iniciar sesión</string>
<string name="cart_title">Mi carrito</string>
<string name="checkout_title">Checkout</string>
<string name="favorites_title">Favoritos</string>
```

Recursos gráficos:

- nombres en minúsculas con `_`
- ejemplo: `product_leche_gloria.png`
- proporcionar placeholder genérico

---

# 25. Accesibilidad básica

- Agregar `contentDescription` a iconos con significado.
- Iconos puramente decorativos pueden usar `contentDescription = null`.
- No comunicar estados solo mediante color.
- Mantener contraste legible.
- Botones y controles táctiles deben tener tamaño adecuado.

---

# 26. Seguridad y datos

Esta es una aplicación académica con autenticación simulada.

- No presentar las credenciales demo como un sistema seguro real.
- No almacenar contraseñas reales.
- No registrar contraseñas en logs.
- No incluir claves privadas o secretos.
- Si en el futuro se integra backend, reemplazar completamente la autenticación demo.

---

# 27. Validaciones

## Login

- correo obligatorio
- clave obligatoria
- credenciales válidas

## Producto administrativo

- nombre obligatorio
- marca obligatoria
- categoría obligatoria
- precio > 0
- stock >= 0
- si es oferta, precio oferta obligatorio y menor al precio normal

## Carrito

- cantidad >= 1
- cantidad <= stock
- producto activo
- stock > 0

## Checkout

- carrito no vacío
- departamento obligatorio
- provincia obligatoria
- distrito obligatorio
- dirección obligatoria
- teléfono obligatorio
- teléfono con formato razonable para Perú (validación básica, sin pretender verificar existencia real)

---

# 28. Datos de demostración

El proyecto debe poder demostrarse apenas se inicia, sin configuración externa.

Asegurar:

- cuentas demo cliente/admin
- categorías creadas
- 40 productos
- ofertas visibles
- agotados visibles
- stock bajo visible en administrador
- productos de diferentes marcas
- carrito inicialmente vacío
- favoritos inicialmente vacíos

No llenar favoritos o carrito automáticamente.

---

# 29. Estrategia de implementación

## Fase 1 — Base

- Crear/verificar proyecto Android
- Material 3
- tema MegaMarket
- Navigation Compose
- estructura MVVM mínima

## Fase 2 — Autenticación

- Splash
- Login
- `AuthRepository`
- `AuthViewModel`
- navegación por rol
- logout

## Fase 3 — Catálogo

- modelos
- Room
- seed de categorías/productos
- catálogo
- búsqueda
- filtros
- ofertas
- detalle

## Fase 4 — Carrito

- persistencia carrito
- agregar/eliminar
- cantidades
- validación stock
- subtotal/total

## Fase 5 — Favoritos

- persistencia
- alternar favorito
- pantalla Favoritos
- agregar al carrito

## Fase 6 — Checkout

- dirección
- validaciones
- resumen
- confirmación
- limpiar carrito

## Fase 7 — Administrador

- dashboard
- lista productos
- nuevo producto
- editar
- stock
- precio
- oferta
- activar/desactivar

## Fase 8 — Calidad

- estados vacíos
- mensajes de error
- accesibilidad básica
- pruebas
- pulido UI/UX

No saltar directamente a la fase 7 antes de que el flujo principal del cliente sea estable.

---

# 30. Pruebas mínimas

## Unitarias

Crear pruebas para reglas críticas:

- cálculo de subtotal
- cálculo de total
- no exceder stock
- filtros de catálogo
- combinación búsqueda + categoría + oferta
- validación de oferta
- validación de formulario administrativo
- autenticación por rol

## UI / instrumentadas

Prioridad:

1. Splash → Login
2. Login cliente → Home
3. Login administrador → Dashboard
4. agregar producto → carrito
5. modificar cantidad
6. checkout válido
7. crear/editar producto administrativo

No es obligatorio automatizar absolutamente toda la app; priorizar flujos evaluables.

---

# 31. Definition of Done

Una funcionalidad se considera terminada cuando:

- compila sin errores
- cumple su RF/HU
- la lógica no está dentro del composable
- tiene validaciones necesarias
- maneja estado vacío/error cuando aplica
- respeta el tema visual
- no rompe navegación existente
- no genera warnings nuevos evitables
- se probó manualmente en emulador Android
- si contiene lógica crítica, tiene una prueba unitaria razonable

---

# 32. Reglas específicas para Cursor

Cursor debe seguir estas instrucciones al generar código:

1. Antes de modificar arquitectura, revisar este archivo.
2. No reescribir archivos completos si basta un cambio localizado.
3. No borrar código funcional sin explicar el motivo.
4. No instalar dependencias sin justificar su necesidad.
5. Antes de agregar una dependencia, comprobar si Jetpack/Android estándar ya cubre el caso.
6. No crear APIs, endpoints o backend ficticios.
7. No usar Firebase salvo instrucción explícita.
8. No usar XML para pantallas; la UI principal es Jetpack Compose.
9. No mezclar Views/XML con Compose salvo necesidad técnica documentada.
10. No colocar toda la aplicación en `MainActivity.kt`.
11. No colocar catálogos hardcodeados dentro de composables.
12. No llamar DAOs directamente desde composables.
13. No manejar navegación desde repositories.
14. No usar `GlobalScope`.
15. No usar `runBlocking` en UI.
16. No guardar `Context` en ViewModel salvo caso técnicamente necesario y justificado.
17. No almacenar IDs de `R.drawable` como datos persistentes de negocio.
18. Mantener nombres coherentes y evitar duplicar modelos.
19. Si ya existe una clase equivalente, reutilizar/refactorizar antes de crear otra.
20. Mantener el proyecto compilable después de cada bloque de trabajo.
21. Si falta una decisión de producto, no inventar una funcionalidad grande: usar la opción mínima compatible con este documento.
22. Mostrar cambios por archivo cuando se solicite código.
23. Si se modifica Gradle, indicar exactamente qué dependencia se agrega y por qué.
24. Si un cambio implica migración Room, crear la migración o explicar por qué puede usarse `fallbackToDestructiveMigration` únicamente durante prototipado; no usarlo silenciosamente en una versión final.
25. Priorizar claridad sobre abstracciones excesivas.

---

# 33. Funcionalidades fuera de alcance actual

No implementar sin solicitud explícita:

- pagos reales
- Yape/Plin/Visa/Mastercard reales
- delivery en tiempo real
- tracking GPS
- mapa de repartidores
- backend REST
- Firebase Authentication
- push notifications
- lector de códigos de barras
- facturación electrónica
- comprobantes SUNAT
- multi-sucursal real
- inventario distribuido
- analítica avanzada
- recomendaciones con IA

Estas funciones pueden ser evoluciones futuras.

---

# 34. Prioridad de evaluación

La prioridad absoluta es que la demostración permita comprobar de forma clara:

```text
RF01 Splash + Login
RF02 Cliente / Administrador
RF03 30+ productos
RF04 Categorías + ofertas
RF05 Carrito + cantidades
RF06 Subtotal + total
RF07 Dirección checkout
RF08 Favoritos
RF09 Admin crea/edita producto
RF10 Admin actualiza stock/precio/oferta
```

Las mejoras RF11–RF24 deben fortalecer el proyecto, no ocultar ni dificultar la evaluación de RF01–RF10.

---

# 35. Resultado esperado de la primera versión

Al finalizar, MegaMarket Express debe permitir realizar esta demostración completa:

```text
Abrir app
→ Splash
→ Login cliente
→ Ver catálogo
→ Buscar producto
→ Filtrar categoría/oferta
→ Abrir producto
→ Marcar favorito
→ Agregar al carrito
→ Cambiar cantidad
→ Revisar subtotal/total
→ Checkout
→ Registrar dirección
→ Confirmar compra
→ Volver al inicio
→ Cerrar sesión
→ Login administrador
→ Dashboard
→ Ver productos
→ Crear producto
→ Editarlo
→ Cambiar stock
→ Cambiar precio
→ Activar oferta
→ Ver cambios persistidos
→ Cerrar sesión
```

Ese flujo es la referencia principal para decisiones de implementación.
