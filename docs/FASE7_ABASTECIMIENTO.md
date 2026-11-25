# Fase 7 — Abastecimiento (proveedores, OC, pago y recepción)

## 1. Requisitos cubiertos

| Código | Descripción | Estado |
|---|---|---|
| HU-A02 | Pago de orden de compra desde la app admin | Cubierto |
| RF-A03 | Pago de órdenes a proveedores | Cubierto (simulador académico) |
| RF-A04 | Registro de entradas de inventario | Cubierto (`ENTRADA_COMPRA`) |
| RN-02 | Stock solo vía movimientos | Cubierto en recepción |
| RN-05 | Pago solo tras aprobación | Cubierto en backend |
| RF-A02 | Órdenes sugeridas por predicción | Estructura lista (`SUGERIDA`); **sin ML** |

## 2. Arquitectura

- **API** (`api-rest`): Express 5 + PostgreSQL. JWT + rol `ADMINISTRADOR`.
- **Android Admin** (`app/`): MVVM + Compose + Retrofit. Sin conexión directa a PostgreSQL.
- **Inventario**: la recepción reutiliza `movimientos_inventario` y actualiza `productos.stock` en la misma transacción. No se debilitó el endpoint de ajustes (`AJUSTE_*` / `SALIDA_VENTA`).
- **Pagos de clientes**: no se modifican. Las transferencias a proveedores usan tabla propia.

## 3. Tablas existentes reutilizadas

- `proveedores`
- `ordenes_compra`
- `orden_compra_detalle`
- `movimientos_inventario` (tipo `ENTRADA_COMPRA` + `orden_compra_detalle_id`)
- `operaciones_idempotentes`
- `productos` / `usuarios`

**No se recrearon** estas tablas.

## 4. Nueva estructura (migración incremental)

Archivo: `api-rest/sql/007_abastecimiento.sql`

- Secuencia `seq_numero_orden_compra` (numeración concurrente segura).
- Tabla `transferencias_proveedor` (intentos PENDIENTE / CONFIRMADA / RECHAZADA / DESCONOCIDA, hash de solicitud, referencias, bandera `simulacion`).

**No ejecutar automáticamente contra Neon.** Aplicar a mano en el SQL Editor del proyecto `supermarket` / base `bd_megamarket`.

## 5. Endpoints

Todos con `Authorization: Bearer` y rol `ADMINISTRADOR`.

### Proveedores

- `GET /api/proveedores`
- `GET /api/proveedores/:id`
- `POST /api/proveedores`
- `PATCH /api/proveedores/:id`
- `GET /api/proveedores/:id/ordenes`

### Órdenes / pago / recepción

- `GET /api/compras/ordenes`
- `GET /api/compras/ordenes/:id`
- `POST /api/compras/ordenes`
- `POST /api/compras/ordenes/:id/aprobar`
- `POST /api/compras/ordenes/:id/cancelar`
- `POST /api/compras/ordenes/:id/pago`
- `POST /api/compras/ordenes/:id/recepciones`
- `GET /api/compras/ordenes/:id/recepciones`
- `GET /api/compras/transferencias/:uuidOperacion`

## 6. Estados y transiciones

```
SUGERIDA → APROBADA → PAGADA → RECIBIDA
SUGERIDA → CANCELADA
APROBADA → CANCELADA
```

- Recepción parcial: estado permanece `PAGADA`; progreso en `cantidad_recibida`.
- `RECIBIDA` solo cuando todos los detalles están completos.
- No existe estado `PARCIAL` en `estado`.

## 7. Transferencias

1. Validar orden `APROBADA`, cuenta bancaria del proveedor y monto.
2. Registrar intento `PENDIENTE` y **cerrar** la transacción.
3. Llamar al adaptador **fuera** de la TX abierta.
4. Persistir resultado; pasar a `PAGADA` solo si `CONFIRMADA`.
5. Idempotencia por `uuidOperacion` + `solicitud_hash`.
6. Si hay intento `PENDIENTE`/`DESCONOCIDA`, no emitir otra transferencia automática con UUID distinto.

## 8. Simulador académico

- Adaptador: `api-rest/src/banco/bancoAndinoSimulado.js` (`BANCO_ANDINO_SIMULADO`).
- Resultados: `CONFIRMADA` | `RECHAZADA` | `PENDIENTE` | `DESCONOCIDA`.
- Campo `forzarResultado` / env `BANCO_ANDINO_MODO` solo para pruebas.
- Respuestas incluyen `simulacion: true` y aviso explícito. **No es dinero real.**

## 9. Recepción de inventario

- Solo órdenes `PAGADA`.
- `SELECT … FOR UPDATE` en orden, detalles y productos (orden determinista).
- Movimiento `ENTRADA_COMPRA` con `orden_compra_detalle_id`.
- UUID de movimiento derivado del UUID de recepción + detalle (idempotente).
- Stock y movimiento en la misma TX.
- No toca reservas del carrito ni descuentos de venta.

## 10. Pruebas y resultados

### Ejecutadas en esta sesión

```bash
cd api-rest
node --test test/abastecimiento-unit.test.js
# + regresiones unitarias sin DB (paginacion, reglas, estados, etc.)
```

Resultado: **30 passed** (incluye 5 de abastecimiento unitario).

### Omitidas (sin `TEST_DATABASE_URL`)

Las 5 pruebas de `test/fase7-abastecimiento.test.js` usan:

```js
const skip = !process.env.TEST_DATABASE_URL;
test('...', { skip }, async () => { ... });
```

Sin esa variable, Node Test Runner las marca `# SKIP`. Compatibles con migración `007` (la suite aplica `001`…`007` en un schema temporal). **No usan `bd_megamarket`.**

Para ejecutarlas: crear una base de pruebas independiente, definir `TEST_DATABASE_URL` en `api-rest/.env` y correr `npm test`. Procedimiento en `api-rest/README.md`.

### Android

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
  --tests "com.megamarket.app.data.repository.ProveedoresRepositoryTest" \
  --tests "com.megamarket.app.data.repository.BilleteraComercioRepositoryTest"
```

Resultado en esta sesión: **BUILD SUCCESSFUL** (mapeo de proveedores + regresión billetera).

## 11. Problemas encontrados

- No hay adaptador bancario real en el repo; se definió contrato + simulador académico.
- Columnas de `ordenes_compra` no bastaban para intentos pendientes → migración `007`.
- `TEST_DATABASE_URL` ausente → integración Fase 7 omitida.
- Solape temporal entre dos implementaciones Android de Fase 7: se unificó en la versión final del módulo `app/` (repos + pantallas + `ApiServiceNoSoportado` para fakes de prueba). Compilación y tests unitarios de proveedores/billetera verificados en verde.

## 12. Correcciones realizadas

- Bloqueo de segunda transferencia automática ante `PENDIENTE`/`DESCONOCIDA`.
- Numeración de OC con secuencia (fallback advisory lock si `007` no está aplicada).
- Recepción parcial sin inventar estado `PARCIAL`.

## 13. Pendientes

- Aplicar `007_abastecimiento.sql` manualmente en Neon.
- Ejecutar `fase7-abastecimiento.test.js` con `TEST_DATABASE_URL`.
- Integración futura RF-A02 / ML-01 (órdenes sugeridas por predicción).
- Conciliación bancaria (Fase 8): no implementada.

## 14. SQL a ejecutar manualmente

Contenido completo en `api-rest/sql/007_abastecimiento.sql`.

Verificación sugerida tras aplicar:

```sql
SELECT to_regclass('public.transferencias_proveedor');
SELECT c.relname
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind = 'S' AND c.relname = 'seq_numero_orden_compra';
```
