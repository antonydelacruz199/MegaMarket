# Fase 2.6 — Entregas

Evidencia de entrega con la tabla Neon `entregas`. Sin bytes de foto en PostgreSQL; sin confirmar pagos desde la app.

## Endpoints

| Método | Ruta | Rol | Función |
|---|---|---|---|
| GET | `/api/entregas/pedidos` | REPARTIDOR | Pool `LISTO` + asignados `EN_CAMINO` / `ENTREGADO` |
| GET | `/api/entregas/pedidos/:id` | REPARTIDOR asignado | Detalle + dirección + entrega |
| POST | `/api/entregas/pedidos/:id` | REPARTIDOR asignado | Registra evidencia e `ENTREGADO` |

### Body de registro

```json
{
  "uuidOperacion": "UUID",
  "fotoReferencia": "entregas/…/clave.jpg",
  "latitud": -12.0464,
  "longitud": -77.0428,
  "fechaEntrega": "2026-11-11T20:00:00.000Z"
}
```

`fechaEntrega` es opcional (default `now()`). `fotoReferencia` es clave/URL remota futura.

## Reglas

- Listado: `LISTO` (sin asignar o propios) + `EN_CAMINO`/`ENTREGADO` propios. Tomar con `PATCH …/estado` → `EN_CAMINO`.
- Registro de evidencia: solo `EN_CAMINO` + repartidor asignado.
- GPS válido; foto no vacía; `uuid_operacion` único; un `entregas` por pedido.
- Misma TX: `INSERT entregas` + `pedidos.estado = ENTREGADO`.
- Pago: exige `pagos.CONFIRMADO` con `numero_operacion_banco` + `fecha_confirmacion` (ONLINE y CONTRAENTREGA).
- Compatibilidad: solo históricos sin fila `pagos` y `solicitud_hash IS NULL`. Pedidos nuevos sin pagar no usan la excepción.
- `PATCH /api/pedidos/:id/estado` con `ENTREGADO` → `ENTREGA_REQUIERE_EVIDENCIA`.
- Sin cambios de stock ni checkout.

## Pendiente

Subida real de fotografías a almacenamiento remoto, cobro/QR Banco Andino en contraentrega, firma del cliente.
