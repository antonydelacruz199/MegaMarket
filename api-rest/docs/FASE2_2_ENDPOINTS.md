# Fase 2.2 — Endpoints entregados

Implementación según `FASE2_1_CHECKOUT_INVENTARIO.md`. Checkout v3 activo en `POST /api/pedidos`. Sin Banco Andino.

## Endpoints

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/franjas` | Hoy/mañana, `cupoRestante` |
| GET | `/api/franjas/:id` | Detalle + ocupación |
| GET | `/api/pedidos` | Filtro opcional `?estado=` |
| GET | `/api/pedidos/:id` | Detalle + pago si existe |
| PATCH | `/api/pedidos/:id/estado` | Body: `{ "estado": "EN_PICKING", "pickerId"?: "..." }` |
| POST | `/api/pedidos` | `contratoVersion: 2` (legado) o `3` (franja + modalidad) |

## Checkout v3 (`contratoVersion: 3`)

Body adicional: `franjaEntregaId`, `modalidadPago` (`ONLINE`|`CONTRAENTREGA`). Opcional: `preferenciaSustitucion`.

- Cupo con `asegurarCupoFranja` + `INSERT` pedido en la misma TX.
- Pedido inicial `CONFIRMADO`; fila `pagos` en `PENDIENTE` (nunca `CONFIRMADO` aquí).
- Una `SALIDA_VENTA` por línea; reintento idempotente no consume cupo ni stock.
- Hash de idempotencia incluye franja y modalidad.

## Reglas aplicadas

- Cupo: `FOR UPDATE` + `COUNT` pedidos no `CANCELADO` ≤ `capacidad` (máx. 20).
- `PAGADO` rechazado en API (`PAGO_REQUIERE_BANCO`).
- ONLINE pendiente → no `EN_PICKING`; CONTRAENTREGA y legado sin `pagos` → sí.
- Transiciones no emiten `SALIDA_VENTA`; cancelación restituye con `AJUSTE_ENTRADA` idempotente.
- Pedidos con `pagos.estado = CONFIRMADO` o `pedidos.estado = PAGADO` no se cancelan (`CANCELACION_REQUIERE_DEVOLUCION`).
