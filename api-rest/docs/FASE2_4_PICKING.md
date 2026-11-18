# Fase 2.4 — Módulo Picking (API REST)

Preparación de pedidos para la app Picker. **No** descuenta stock ni emite `SALIDA_VENTA`.

## Endpoints

| Método | Ruta | Rol | Función |
|---|---|---|---|
| GET | `/api/picking/pedidos` | PICKER | Pedidos `EN_PICKING` asignados; líneas por `pasillo` |
| GET | `/api/picking/pedidos/:id` | PICKER | Detalle de preparación (debe estar asignado) |
| POST | `/api/picking/pedidos/:id/detalles/:detalleId/escaneos` | PICKER | Escaneo idempotente por `uuidOperacion` |
| POST | `/api/picking/pedidos/:id/detalles/:detalleId/faltante` | PICKER | Marca línea `FALTANTE` |

### Escaneo — body

```json
{
  "uuidOperacion": "UUID",
  "codigoBarras": "770001",
  "cantidad": 1
}
```

- Código correcto → incrementa `cantidad_armada`; al completar → `ARMADO`.
- Código incorrecto → registra `picking_escaneos.resultado = INCORRECTO` (HTTP 409); no incrementa.
- Mismo `uuidOperacion` → HTTP 200 idempotente; no segundo incremento.
- Exceder cantidad solicitada → `CANTIDAD_EXCEDIDA`.

### Restricciones aplicadas

- Solo picker asignado (`picker_id`).
- Solo estado pedido `EN_PICKING`.
- ONLINE con pago no `CONFIRMADO` → `PICKING_REQUIERE_PAGO`.
- Sin propuestas de sustitución ni decisiones del cliente.

## Pendiente (fases posteriores)

- Sustituciones / aceptación del cliente.
- Transición automática a `LISTO` cuando todas las líneas estén `ARMADO` / `SUSTITUIDO` / `ELIMINADO` (también vía `PATCH /api/pedidos/:id/estado`).
- Banco Andino / confirmación de pagos ONLINE.
