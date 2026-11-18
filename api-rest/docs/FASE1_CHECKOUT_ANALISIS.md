# Fase 1 — Análisis de checkout (`POST /api/pedidos`)

**Alcance:** solo análisis. El comportamiento actual del checkout **no cambia** en esta fase.
No se integra Banco Andino ni se añaden endpoints de pago.

Esquema de referencia: Neon (`sql/003_operaciones_extendidas.sql` alineada por introspección).

## Comportamiento actual (conservado)

Archivo: `src/controllers/pedidos.controller.js` · ruta: `src/routes/pedidos.routes.js`.

1. Exige `contratoVersion: 2`, `clientUuid`, dirección e ítems con `precioEsperadoCentimos`.
2. Idempotencia por `client_uuid` + `solicitud_hash` (y rama histórica si `solicitud_hash` es NULL).
3. En una sola transacción: valida stock frente a otras reservas, valida precios, inserta pedido con `estado = 'CONFIRMADO'`, dirección, detalle, **descuenta `productos.stock`**, registra `SALIDA_VENTA`, cierra carrito y libera reservas.
4. Roles: solo `CLIENTE`. Aísla pedidos ajenos por `cliente_id`.

## Separación conceptual (siguiente fase)

En Neon, `pedidos.estado` ya admite:
`CONFIRMADO`, `PAGADO`, `EN_PICKING`, `LISTO`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO`.

| Concepto | Hoy | Objetivo sobre el esquema Neon |
|---|---|---|
| Confirmación comercial | `CONFIRMADO` al crear | Mantener o transicionar según franja/`pagos` |
| Confirmación bancaria | Implícita (simulada) | Tabla `pagos` (`modalidad` ONLINE/CONTRAENTREGA); `CONFIRMADO` en pago solo con `numero_operacion_banco` + `fecha_confirmacion` |
| Preparación | No usada por la API | `EN_PICKING` / `LISTO` + `pedido_detalle.estado_picking` + `picking_escaneos` |
| Descuento de inventario | Al crear el pedido | Una sola `SALIDA_VENTA`; picking no debe duplicarla; recepción usa `ENTRADA_COMPRA` |

Columnas relevantes ya en Neon: `pedidos.franja_entrega_id`, `preferencia_sustitucion`, `picker_id`, `repartidor_id`.

## Integración futura: QR y contraentrega

- **ONLINE:** crear fila en `pagos` (`medio_pago = 'QR'`, `modalidad = 'ONLINE'`).
- **CONTRAENTREGA:** `modalidad = 'CONTRAENTREGA'`; el teléfono no marca el pedido como pagado.
- Ajustes por faltante/sustitución: `ajustes_pago` (`DESCUENTO`/`DEVOLUCION`) ligados a `pago_id` y opcionalmente `sustitucion_id`.

## Compatibilidad con `CONFIRMADO` histórico

- El check de estado en Neon **incluye** `CONFIRMADO`.
- Los tres (o N) pedidos históricos siguen válidos; `preferencia_sustitucion` nace con default `RECHAZAR` al añadir la columna.
- La rama `solicitud_hash IS NULL` del controlador permanece válida.

## Cambios de código previstos (no hechos aquí)

1. Aceptar `franjaEntregaId` y modalidad de pago sin romper `contratoVersion: 2`.
2. Cupo de franja por conteo de pedidos (Neon no tiene columna `ocupados`; capacidad 1–20).
3. Transiciones de `pedidos.estado` y escritura en `pagos` tras verificación bancaria.
4. GET de pedidos/estados; tests de no doble descuento.
