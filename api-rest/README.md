# MegaMarket API REST

API pequeña en Node.js + Express + PostgreSQL (Neon).

## Arranque

1. Instalar dependencias:

```bash
npm install
```

2. Copiar variables de entorno:

```bash
copy .env.example .env
```

En Linux/macOS:

```bash
cp .env.example .env
```

3. Completar en `.env`:

- `DATABASE_URL` — cadena de conexión de Neon
- `JWT_SECRET` — secreto para firmar tokens
- `PORT` — por defecto `8000`
- `MEDIA_STORAGE` — `disk` guarda fotos en disco; las pruebas de endpoints siguen usando memoria
- `MEDIA_ROOT` — carpeta de esas fotos (`data/media` o una ruta absoluta). No se sube a Git

4. Ejecutar en desarrollo:

```bash
npm run dev
```

5. Probar salud:

```bash
GET http://localhost:8000/api/health
```

Respuesta esperada cuando Neon responde:

```json
{
  "ok": true,
  "database": "connected"
}
```

No coloques credenciales reales en este README.

## Migraciones SQL

Esta versión requiere Node.js 24 o posterior. Los scripts **no** se ejecutan al iniciar el servidor ni contra Neon desde la API.

Orden:

1. Base nueva: `sql/001_esquema_inicial.sql`
2. Reservas de carrito: `sql/002_reservas.sql` (en bases existentes, solo este tras revisar nombres duplicados)
3. Operaciones extendidas (alineada al esquema Neon): `sql/003_operaciones_extendidas.sql`
4. Billetera simulada (académica): `sql/004_billetera_simulada.sql`

La migración `003` refleja nombres reales de Neon (`franja_entrega_id`, `productos.pasillo`, `productos.codigo_barras`, `preferencia_sustitucion`, etc.), es idempotente (`IF NOT EXISTS`) y conserva filas/`CONFIRMADO`. No cambia el checkout; ver `docs/FASE1_CHECKOUT_ANALISIS.md`.

La migración `004` añade `cuentas_billetera` y `movimientos_billetera` (céntimos enteros, sin saldos negativos). Semilla la cuenta comercial MegaMarket en S/ 500,000 y, si ya existen clientes, les asigna S/ 1,000,000,000. No conecta bancos reales.

Configurar `PORT=8010` para coincidir con la URL Android predeterminada, o alinear ambos lados.

El API ejecuta un job interno que aplica `preferencia_sustitucion` a propuestas con `fecha_limite` vencida (`SUSTITUCIONES_JOB`, `SUSTITUCIONES_JOB_MS` en `.env`). No depende de las apps Android.

Los pedidos antiguos enviados con el protocolo anterior reciben HTTP 426. Actualizar API y aplicativos de forma coordinada.

## Operaciones

Todas requieren JWT, salvo login y health.

| Ruta | Acceso | Función |
|---|---|---|
| POST /api/auth/login | Público | Autenticación |
| GET /api/auth/me | Autenticado | Perfil |
| GET /api/categorias | Autenticado | Categorías |
| GET /api/productos | Autenticado | Cliente: disponibles; administrador: activos e inactivos |
| POST, PATCH, DELETE /api/productos | Administrador | Gestión de productos, `codigoBarras`, `pasillo` y control de versión |
| GET /api/franjas | Autenticado | Franjas de hoy/mañana con cupo restante |
| GET /api/franjas/:id | Autenticado | Detalle de franja (lectura con bloqueo de cupo) |
| PUT /api/carritos/:id | Cliente propietario | Instantánea de reserva con revisión creciente |
| POST /api/pedidos | Cliente propietario | Checkout `contratoVersion: 2` o `3` (v3: franja + modalidad) |
| GET /api/pedidos | Autenticado (por rol) | Listado según propietario o asignación |
| GET /api/pedidos/:id | Autenticado (por rol) | Detalle autorizado |
| PATCH /api/pedidos/:id/estado | Autenticado (por rol) | Transiciones; `PAGADO` no desde apps |
| GET /api/picking/pedidos | Picker asignado | Pedidos `EN_PICKING` con líneas ordenadas por pasillo |
| GET /api/picking/pedidos/:id | Picker asignado | Detalle de preparación |
| POST /api/picking/pedidos/:id/detalles/:detalleId/escaneos | Picker asignado | Escaneo idempotente (`uuidOperacion`); sin redecontar stock |
| POST /api/picking/pedidos/:id/detalles/:detalleId/faltante | Picker asignado | Marca línea como `FALTANTE` |
| GET /api/picking/pedidos/:id/detalles/:detalleId/sustitutos | Picker asignado | Candidatos de sustitución (categoría, ±20 %, stock) |
| POST /api/picking/pedidos/:id/detalles/:detalleId/sustituciones | Picker asignado | Propone sustitución pendiente (5 min) |
| GET /api/sustituciones | Cliente | Sustituciones de sus pedidos |
| GET /api/sustituciones/:id | Cliente / picker / admin | Detalle de propuesta |
| POST /api/sustituciones/:id/aceptar \| rechazar | Cliente propietario | Decisión idempotente |
| POST /api/sustituciones/resolver-expiradas | Admin / picker | Respaldo manual; el job de servidor ya resuelve vencidas |
| GET /api/entregas/pedidos | Repartidor asignado | Pedidos `EN_CAMINO` / `ENTREGADO` |
| GET /api/entregas/pedidos/:id | Repartidor asignado | Detalle de entrega |
| POST /api/entregas/pedidos/:id | Repartidor asignado | Evidencia (foto ref. + GPS + uuid) → `ENTREGADO` |
| POST /api/medios/productos/:productoId | Administrador | Sube imagen (API); actualiza `imagen_key` |
| GET /api/medios/productos/:productoId | Autenticado | Recupera imagen (URL firmada o `?download=1`) |
| POST /api/medios/entregas/:pedidoId | Repartidor asignado | Sube foto privada; devuelve `fotoReferencia` |
| GET /api/medios/entregas/:pedidoId | Admin / cliente / repartidor | Lectura privada de evidencia |
| POST /api/inventario/movimientos | Administrador | Ajustes que respetan las reservas existentes |
| GET /api/billetera/saldo | Cliente / Admin | Saldo simulado propio (cliente) o comercial (admin) |
| GET /api/billetera/movimientos | Cliente / Admin | Historial ficticio de movimientos |
| GET /api/billetera/comercio | Administrador | Alias del saldo comercial MegaMarket |
| POST /api/pagos/qr | Cliente / Repartidor / Admin | Genera QR informativo (`SIMULACIÓN`); no confirma cobro |
| GET /api/pagos/pedido/:pedidoId | Autenticado (por rol) | Estado del pago del pedido |
| GET /api/pagos/:id | Autenticado (por rol) | Detalle de pago |
| POST /api/pagos/:id/confirmar | Cliente / Repartidor / Admin | Confirma cobro simulado (saldo + idempotencia) |
| POST /api/pagos/ajustes/:id/confirmar | Cliente / Admin | Confirma devolución/descuento pendiente de forma controlada |

Ejemplo de reserva:

```json
{"revision":1,"items":[{"productoId":"UUID_PRODUCTO","cantidad":2}]}
```

Una lista vacía libera la reserva. El UUID del carrito se reutiliza como `clientUuid` del pedido. El pedido requiere `contratoVersion: 2` o `3`, dirección e ítems con `precioEsperadoCentimos`. Un reintento conserva UUID y contenido.

Checkout v3 además exige `franjaEntregaId` y `modalidadPago` (`ONLINE` | `CONTRAENTREGA`). Crea el pedido en `CONFIRMADO` con pago `PENDIENTE` (sin confirmación bancaria), copia `preferencia_sustitucion` del cliente y reserva cupo de franja en la misma transacción (`FOR UPDATE`).

Fase 3: el QR es solo informativo. El cobro se confirma con `POST /api/pagos/:id/confirmar` (`uuidOperacion`), que debita la billetera del cliente, acredita MegaMarket y pasa el pago a `CONFIRMADO` (referencias `SIM-*`). Mostrar el QR no marca `PAGADO`.

## Pruebas

```bash
npm test
```

Las pruebas de controladores utilizan transacciones SQLite en memoria con un adaptador SQL. No verifican los bloqueos de PostgreSQL.

Para probar migraciones, índice de nombres y reservas concurrentes en PostgreSQL, proporcionar explícitamente `TEST_DATABASE_URL` de una base de pruebas y ejecutar `npm test`. La prueba crea un esquema aislado aleatorio y lo elimina al terminar; no utiliza `DATABASE_URL`. Sin esa variable, esa prueba se omite.

`test/fase2-disk-storage.test.js` ejercita el disco en un directorio temporal. No escribe en `data/media` ni borra fotos ya guardadas. La suite de endpoints de medios sigue fijando `memoryStorage`.

Detalles de despliegue, migraciones y comprobación en dispositivos: `../docs/IMPLEMENTACION_RESERVAS.md`.
