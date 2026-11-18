# Fase 2.5 — Sustituciones

Usa `sustituciones_pedido` y `ajustes_pago` existentes. Sin confirmaciones bancarias inventadas.

## Endpoints

| Método | Ruta | Rol | Función |
|---|---|---|---|
| GET | `/api/picking/pedidos/:id/detalles/:detalleId/sustitutos` | PICKER | Candidatos misma categoría, stock y ±20 % |
| POST | `/api/picking/pedidos/:id/detalles/:detalleId/sustituciones` | PICKER | Propone sustitución (`productoSustitutoId`) |
| GET | `/api/sustituciones` | CLIENTE | Lista (resuelve expiradas del cliente) |
| GET | `/api/sustituciones/:id` | CLIENTE/PICKER/ADMIN | Detalle |
| POST | `/api/sustituciones/:id/aceptar` | CLIENTE propietario | Acepta (idempotente) |
| POST | `/api/sustituciones/:id/rechazar` | CLIENTE propietario | Rechaza (idempotente) |
| POST | `/api/sustituciones/resolver-expiradas` | ADMIN/PICKER | Disparo manual de respaldo (misma lógica que el job) |

## Automatización (servidor)

Mecanismo: **`setInterval` en el proceso Node** (`src/jobs/sustitucionesExpiradas.js`), arrancado desde `server.js`.

- Intervalo: `SUSTITUCIONES_JOB_MS` (default 15000). Desactivar: `SUSTITUCIONES_JOB=0`.
- Reutiliza `resolverExpiradas` (preferencia, inventario, ajustes).
- Concurrencia multi-instancia: `pg_try_advisory_lock`.
- Pedidos `CANCELADO` / `ENTREGADO` / no `EN_PICKING|LISTO` → propuesta `CANCELADA` (sin inventario).
- Decisión manual previa (`ACEPTADA`/`RECHAZADA`) no se sobrescribe.
- No requiere Android ni sesión de administrador.

## Reglas

- Línea en `FALTANTE`; una sola propuesta `PENDIENTE` por detalle.
- `fecha_limite = now() + 5 minutes`; al vencer → `pedidos.preferencia_sustitucion` (`ACEPTAR`/`RECHAZAR`) con `respuesta_automatica`.
- Aceptación: marca `sustituciones_pedido = ACEPTADA`, deja la línea en `FALTANTE` con `cantidad_armada = 0` (sin cambiar importes históricos ni `producto_id`). **No** implica preparado.
- Escaneo posterior: valida el `codigo_barras` del **sustituto** aceptado; al completar cantidad → `SUSTITUIDO` y entonces `AJUSTE_ENTRADA`/`AJUSTE_SALIDA` (UUID determinista por sustitución; **no** `SALIDA_VENTA`).
- Precio sustituto > original → `ajustes_pago` `DESCUENTO` `PENDIENTE`; menor → `DEVOLUCION` `PENDIENTE`. Nunca `CONFIRMADO` sin banco.

## Pendiente

Confirmación bancaria de devoluciones/descuentos, UI Android, sustitución parcial multi-producto avanzada.
