# Fase 2.3 — Checkout `contratoVersion: 3`

## Contrato `POST /api/pedidos`

| Campo | v2 | v3 |
|---|---|---|
| `contratoVersion` | `2` | `3` |
| `clientUuid` | sí | sí |
| `direccion` | sí | sí |
| `items[].productoId/cantidad/precioEsperadoCentimos` | sí | sí |
| `franjaEntregaId` | no | obligatorio (UUID) |
| `modalidadPago` | no | `ONLINE` \| `CONTRAENTREGA` |
| `preferenciaSustitucion` | no | opcional; default perfil |

## Comportamiento v3

1. Idempotencia por `client_uuid` + hash (ítems, dirección, franja, modalidad).
2. `FOR UPDATE` usuario → `asegurarCupoFranja` → carrito/productos.
3. Pedido `CONFIRMADO` con `franja_entrega_id` y `preferencia_sustitucion`.
4. Una `SALIDA_VENTA` por línea; stock descontado una vez.
5. `pagos` en `PENDIENTE` (`medio_pago=QR`, sin `numero_operacion_banco` ni confirmación).
6. v2 sin cambios de forma ni de tablas usadas.

## Pendiente (Banco Andino / fases siguientes)

- Generación/expiración de QR y webhooks bancarios.
- Confirmar pago → `pagos.CONFIRMADO` / pedido `PAGADO`.
- Picking, sustituciones y entregas operativas.
