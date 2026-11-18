# Fase 2.1 — Diseño del checkout y control de inventario

**Tipo:** diseño técnico (sin implementación).  
**Fecha de referencia:** 2026-11-11.  
**Esquema:** Neon / `sql/003_operaciones_extendidas.sql` (validado en Fase 1).  
**Código actual:** `POST /api/pedidos` (`src/controllers/pedidos.controller.js`), `PUT /api/carritos/:id`, `POST /api/inventario/movimientos`.

---

## 1. Situación actual (baseline)

### 1.1 Checkout vigente (`contratoVersion: 2`)

1. El cliente reserva unidades con `PUT /api/carritos/:id` → `reservas_carrito` (disponible = `stock − reservas ajenas`).
2. `POST /api/pedidos` exige `clientUuid`, dirección e ítems con `precioEsperadoCentimos`.
3. En una sola transacción:
   - idempotencia por `client_uuid` + `solicitud_hash` (rama histórica si `solicitud_hash IS NULL`);
   - valida precio servidor;
   - inserta `pedidos.estado = 'CONFIRMADO'`;
   - **descuenta `productos.stock`**;
   - inserta `movimientos_inventario` (`SALIDA_VENTA`);
   - borra reservas y cierra el carrito.
4. No usa `franja_entrega_id`, `pagos` ni modalidades QR.

### 1.2 Inventario

| Mecanismo | Efecto sobre stock físico | Efecto sobre disponible |
|---|---|---|
| Reserva de carrito | Ninguno | Reduce disponible |
| `SALIDA_VENTA` en checkout | Reduce `productos.stock` | Liberar reserva + bajar físico (neto una vez) |
| `AJUSTE_*` admin | Delta explícito | Respeta reservas ajenas |
| Picking / escaneo (aún no en API) | **No debe** tocar stock de venta | Solo avance logístico |

### 1.3 Esquema ya disponible (sin tablas nuevas obligatorias)

- `franjas_entrega(capacidad 1..20)`, `pedidos.franja_entrega_id`
- `pagos` (`medio_pago='QR'`, `modalidad` ONLINE|CONTRAENTREGA, estados de cobro)
- `pedidos.estado` ∈ CONFIRMADO, PAGADO, EN_PICKING, LISTO, EN_CAMINO, ENTREGADO, CANCELADO
- `pedido_detalle.estado_picking`, `picking_escaneos`
- Índice único parcial: un solo `pagos` CONFIRMADO por pedido

---

## 2. Flujo propuesto de creación de pedidos

### 2.1 Principios

1. **Reserva en carrito → consumo único al confirmar el pedido comercial.**  
   Pagar, pickear y entregar **no** emiten otra `SALIDA_VENTA` por las mismas unidades.
2. **La verdad del cobro vive en `pagos`.** El teléfono no marca pagado. Solo el servidor, tras verificar con el banco (fase posterior), pone `pagos.estado = CONFIRMADO` y puede avanzar `pedidos.estado` a `PAGADO` cuando corresponda.
3. **`CONFIRMADO` histórico se conserva** como estado terminal/legado de pedidos ya creados con el protocolo parcial (stock ya descontado, sin fila de pago).
4. **Contraentrega permite picking con cobro pendiente** (alineado al plan académico).
5. **Franja:** el cupo se ocupa al crear el pedido y se libera solo con `CANCELADO` (conteo atómico; sin columna `ocupados`).

### 2.2 Diagrama de alto nivel

```text
[Carrito] --PUT reservas--> reservas_carrito
                |
                v
        POST /api/pedidos  (contrato v3 o v2+campos)
                |
    +-----------+-----------+
    |                       |
 ONLINE                  CONTRAENTREGA
    |                       |
 pedidos.CONFIRMADO         pedidos.CONFIRMADO
 + pagos PENDIENTE/         + pagos PENDIENTE
   QR_GENERADO                modalidad=CONTRAENTREGA
 stock: SALIDA_VENTA        stock: SALIDA_VENTA
 franja: cupo++             franja: cupo++
    |                       |
 banco OK ------------------+----> (en puerta / app)
    v                       v
 pedidos.PAGADO          (puede EN_PICKING sin PAGADO)
    |                       |
    +---------> EN_PICKING -> LISTO -> EN_CAMINO -> ENTREGADO
                (escaneos: sin SALIDA_VENTA)
```

### 2.3 Pasos transaccionales de `POST /api/pedidos` (nuevo comportamiento)

Todo en un `BEGIN` con orden de bloqueo estable:

1. `SELECT usuarios.id … FOR UPDATE` (dueño).
2. Idempotencia por `client_uuid` (igual que hoy; ampliar hash — §6).
3. `INSERT carritos …` / `SELECT carritos … FOR UPDATE`; validar dueño y no cerrado.
4. `SELECT franjas_entrega … FOR UPDATE` donde `id = franja_entrega_id`, `activo`, fecha hoy/mañana.
5. Contar cupo (§5); si lleno → `409` `FRANJA_LLENA`.
6. Por cada producto (ordenados por id): `FOR UPDATE`, validar activo, stock vs otras reservas, `precioEsperadoCentimos`.
7. Insertar `pedidos`:
   - `estado = 'CONFIRMADO'` (aceptación comercial + inventario comprometido);
   - `franja_entrega_id`, `preferencia_sustitucion` (body o default usuario);
   - `solicitud_hash` ampliado.
8. Insertar `direcciones`, `pedido_detalle` (`estado_picking = PENDIENTE`, `cantidad_armada = 0`).
9. Por línea: `UPDATE productos.stock`, `INSERT movimientos_inventario (SALIDA_VENTA, uuid_operacion)`.
10. `DELETE reservas_carrito`; `carritos.cerrado = true`.
11. Insertar `pagos` inicial:
    - ONLINE → `modalidad=ONLINE`, `estado=PENDIENTE` (QR se genera en endpoint de pagos);
    - CONTRAENTREGA → `modalidad=CONTRAENTREGA`, `estado=PENDIENTE`.
12. `COMMIT`. Respuesta 201 con pedido + detalle + dirección + `pago` resumen.

**No** se llama a Banco Andino en este paso hasta existir contrato real; el diseño deja el hueco en `POST /api/pagos/qr` y verificación.

---

## 3. Estados y transiciones

### 3.1 `pedidos.estado` (pipeline comercial/logístico)

| Estado | Significado |
|---|---|
| `CONFIRMADO` | Pedido aceptado: franja ocupada, stock descontado, pago aún no acreditado **o** legado parcial |
| `PAGADO` | Existe cobro verificado en `pagos` (`estado=CONFIRMADO` + datos banco) para el flujo online; o cobro de contraentrega ya verificado |
| `EN_PICKING` | Asignado / en preparación |
| `LISTO` | Picking completo, listo para salida |
| `EN_CAMINO` | En ruta de entrega |
| `ENTREGADO` | Entrega registrada |
| `CANCELADO` | Anulado; libera cupo de franja; restituye stock (§4) |

### 3.2 Matriz de transiciones permitidas

| Desde ↓ / Hacia → | CONFIRMADO | PAGADO | EN_PICKING | LISTO | EN_CAMINO | ENTREGADO | CANCELADO |
|---|---|---|---|---|---|---|---|
| **CONFIRMADO** | — | ✓ (pago verificado) | ✓ solo CONTRAENTREGA **o** legado | — | — | — | ✓ (reglas §4.3) |
| **PAGADO** | — | — | ✓ | — | — | — | ✓ (política por definir) |
| **EN_PICKING** | — | ✓ (contraentrega cobrada en ruta) | — | ✓ | — | — | ✓ (política) |
| **LISTO** | — | ✓ | — | — | ✓ | — | ✓ (política) |
| **EN_CAMINO** | — | ✓ | — | — | — | ✓ | ✓ (excepcional) |
| **ENTREGADO** | — | ✓ (regularización) | — | — | — | — | — |
| **CANCELADO** | — | — | — | — | — | — | — |

Notas:

- **ONLINE:** no entrar a `EN_PICKING` sin `PAGADO` (salvo decisión explícita en §10).
- **CONTRAENTREGA:** `CONFIRMADO → EN_PICKING` con `pagos` aún no CONFIRMADO.
- **Históricos `CONFIRMADO` sin fila en `pagos`:** se tratan como legado; no se les exige cobro QR. Transiciones posteriores (picking/entrega) son opcionales para operación futura; no se reescribe su stock.

### 3.3 `pagos.estado` (independiente)

`PENDIENTE → QR_GENERADO → CONFIRMADO`  
también: `RECHAZADO`, `EXPIRADO`, `CANCELADO`.

Regla dura ya en esquema: `CONFIRMADO` exige `numero_operacion_banco` + `fecha_confirmacion`.  
Único pago CONFIRMADO por pedido (`uq_pagos_confirmado_pedido`).

### 3.4 Picking de línea (no es descuento de stock)

`pedido_detalle.estado_picking`: PENDIENTE → ARMADO | FALTANTE | SUSTITUIDO | ELIMINADO.  
`picking_escaneos` registra intentos (`CORRECTO`/`INCORRECTO`) con `uuid_operacion`.  
**Ningún escaneo ejecuta `UPDATE productos.stock` ni `SALIDA_VENTA`.**

---

## 4. Reglas de inventario

### 4.1 Momentos

| Evento | Reserva carrito | Stock físico | Movimiento |
|---|---|---|---|
| Agregar/quitar carrito | Crear/actualizar/borrar `reservas_carrito` | Sin cambio | — |
| Checkout exitoso | Borrar reservas del carrito | `−cantidad` | `SALIDA_VENTA` (1 por línea, `uuid_operacion` propio) |
| Picking / escaneo | — | **Sin cambio** | — |
| Generar QR / marcar PAGADO | — | **Sin cambio** | — |
| Entrega | — | **Sin cambio** (salvo políticas futuras de merma) | — |
| Cancelación con stock ya descontado | — | `+cantidad` vendible | `AJUSTE_ENTRADA` (o tipo futuro documentado) idempotente |
| Sustitución aceptada (fase posterior) | Reserva puntual del sustituto si aplica | Compensar original/sustituto | Pares de ajuste; **no** segunda `SALIDA_VENTA` de la venta original |
| Recepción OC | — | `+cantidad` | `ENTRADA_COMPRA` + `orden_compra_detalle_id` |

### 4.2 Garantía anti–doble descuento

1. Checkout es el **único** productor de `SALIDA_VENTA` ligado a la venta del pedido.
2. Endpoints de picking/escaneo/faltante **prohíben** llamar a la rutina de descuento de venta.
3. Invariante de prueba: tras checkout, `SUM(movimientos SALIDA_VENTA where pedido_id)` = unidades vendidas; tras N escaneos, esa suma **no cambia**.
4. Reintentos de checkout: idempotencia por `client_uuid` (sin segundo descuento).

### 4.3 Cancelación y restitución

- Solo si el pedido no está `ENTREGADO`.
- Si ya hubo `SALIDA_VENTA`, restituir con movimiento idempotente (`uuid_operacion` de cancelación).
- `estado → CANCELADO` excluye el pedido del cupo de franja.
- Si había `pagos` no CONFIRMADO → `CANCELADO`/`EXPIRADO` en pago; si ya CONFIRMADO → queda pendiente diseño de devolución bancaria (`ajustes_pago`), fuera de 2.1 implementación.

### 4.4 Disponibilidad

Sigue siendo: `disponible = stock − SUM(reservas_carrito)`.  
Tras checkout, el físico ya bajó y la reserva del carrito desapareció → no hay doble resta.

---

## 5. Capacidad atómica de franjas (máx. 20)

### 5.1 Modelo actual (suficiente)

- `franjas_entrega.capacidad` CHECK 1..20, default 20.
- **No** hay columna `ocupados` → el cupo es:

```sql
SELECT COUNT(*) FROM pedidos
 WHERE franja_entrega_id = $1
   AND estado <> 'CANCELADO';
```

### 5.2 Protocolo concurrente

Dentro de la misma transacción del checkout:

1. `SELECT * FROM franjas_entrega WHERE id = $1 AND activo AND deleted_at IS NULL FOR UPDATE`.
2. Validar ventana (hoy/mañana según reglas de negocio).
3. `COUNT` de pedidos no cancelados en esa franja.
4. Si `count >= capacidad` → `ROLLBACK` + `409 FRANJA_LLENA`.
5. Insertar pedido con `franja_entrega_id`.

Dos checkouts concurrentes por el último cupo: el `FOR UPDATE` de la franja serializa; uno gana y el otro ve `count >= capacidad`.

### 5.3 Liberación

Cancelar pedido (`CANCELADO`) reduce el conteo automáticamente. No requiere trigger.

### 5.4 Offline cliente

Un carrito offline no reserva franja. Al sincronizar `POST /api/pedidos`, si la franja llenó → rechazo 409; el cliente debe elegir otra. No inventar reserva de franja sin columna/tabla adicional.

---

## 6. Idempotencia y compatibilidad de contrato

### 6.1 Conservar

- `client_uuid` único = id del carrito.
- Respuesta idempotente 200 si mismo dueño y mismo contenido.
- Rama `solicitud_hash IS NULL` (históricos).
- `precioEsperadoCentimos` y agrupación de ítems (`reglas.js`).
- HTTP 426 si `contratoVersion` ausente/antiguo incompatible.

### 6.2 Ampliación del hash

Incluir en la huella (orden canónico):

- ítems + dirección (como hoy);
- `franjaEntregaId`;
- `modalidadPago` (`ONLINE` | `CONTRAENTREGA`);
- `preferenciaSustitucion` si viaja en el body.

Así un reintento con otra franja/modalidad bajo el mismo UUID no silencia el conflicto (409).

### 6.3 Estrategia de versión de contrato (recomendación)

| Versión | Comportamiento |
|---|---|
| `2` (actual apps) | **Compatibilidad:** mismo flujo de hoy (`CONFIRMADO` + `SALIDA_VENTA`, sin exigir franja/pago). Permite no romper Android en el primer despliegue API. |
| `3` (nuevo) | Exige `franjaEntregaId` + `modalidadPago`; crea `pagos`; ocupa franja; estados/pago según §2–3. |

Alternativa más estricta: deprecar v2 con 426 tras ventana corta. **Decisión pendiente §10.**

Campos nuevos en body v3 (camelCase / snake_case como el resto):

- `franjaEntregaId` / `franja_entrega_id` (UUID)
- `modalidadPago` / `modalidad_pago`: `ONLINE` | `CONTRAENTREGA`
- `preferenciaSustitucion` opcional: `ACEPTAR` | `RECHAZAR`

---

## 7. Endpoints: modificar vs crear

### 7.1 Modificar (existentes)

| Endpoint | Cambio |
|---|---|
| `POST /api/pedidos` | Rama v3: franja, modalidad, `pagos`, hash ampliado; v2 intacto hasta deprecación |
| `PUT /api/carritos/:id` | Sin cambio de contrato; opcional validar que no se reserve sobre productos inactivos (ya lo hace) |
| `GET /api/auth/me` | Opcional: exponer `preferencia_sustitucion` |
| `POST /api/inventario/movimientos` | Seguir restringido a admin; **no** usar para venta de pedido desde apps |

### 7.2 Crear (API MegaMarket)

| Endpoint | Rol | Función |
|---|---|---|
| `GET /api/franjas` | CLIENTE (+ ops) | Franjas hoy/mañana con cupo restante |
| `GET /api/pedidos` / `GET /api/pedidos/:id` | CLIENTE (propios); ADMIN/PICKER/REPARTIDOR según asignación | Seguimiento y ops |
| `POST /api/pagos/qr` | CLIENTE / REPARTIDOR | Generar/regenerar QR (`QR_GENERADO`); **adaptador banco cuando exista contrato** |
| `POST /api/pagos/:id/verificar` o job interno | Servidor | Consulta banco → `pagos.CONFIRMADO` + `pedidos.PAGADO` si aplica |
| `POST /api/pedidos/:id/cancelar` | CLIENTE/ADMIN | Cancelación + restitución stock + liberar franja |
| `POST /api/pedidos/:id/asignar-picker` | ADMIN | `picker_id`, transición hacia picking |
| `GET/POST …/picking/...` | PICKER | Lista/escaneo/faltante (**sin** stock venta) — fase posterior |
| `POST /api/entregas` | REPARTIDOR | Evidencia; cobro contraentrega vía pagos — fase posterior |

### 7.3 No implementar aún

- Llamadas reales a `/banco/api/...` hasta contrato Banco Andino.
- Cambios en módulos Android (`app/`, `cliente/`, `:picker`).

---

## 8. ¿Hace falta ajustar el esquema Neon?

### 8.1 No indispensable (usar lo existente)

| Necesidad | Soporte actual |
|---|---|
| Franja y cupo ≤ 20 | `franjas_entrega` + `COUNT` + `FOR UPDATE` |
| QR online / contraentrega | `pagos.modalidad`, estados, uniques |
| Pipeline pedido | `pedidos.estado` + checks |
| Preferencia sustitución | columnas en `usuarios` / `pedidos` |
| Picking sin stock | `picking_escaneos`, `estado_picking` |
| Anti doble pago confirmado | `uq_pagos_confirmado_pedido` |

**Conclusión:** no se requieren tablas/columnas nuevas para programar checkout+franja+esqueleto de pagos.

### 8.2 Ajustes opcionales (solo si una decisión de §10 lo exige)

| Posible cambio | Cuándo justificarlo |
|---|---|
| Estado `PENDIENTE_PAGO` en `pedidos` | Si se rechaza reutilizar `CONFIRMADO` como “aceptado sin pagar” |
| Columna `ocupados` en franjas | Solo si el `COUNT` no cumple SLA; hoy no es necesario |
| Tipo movimiento `REPOSICION_CANCELACION` | Si no se quiere reutilizar `AJUSTE_ENTRADA` para cancelaciones |
| Caducidad de reservas de carrito | Política de negocio aún abierta (plan §Etapa 1) |

Ninguno de estos se introduce en 2.1 sin decisión explícita.

---

## 9. Plan de implementación incremental (código posterior)

| Paso | Contenido | Criterio de hecho |
|---|---|---|
| **2.1a** | Documento (este) + decisiones §10 cerradas | Acuerdo de equipo/docente |
| **2.1b** | `GET /api/franjas` + tests de cupo concurrente | Dos clients, un cupo: 201 + 409 |
| **2.1c** | `POST /api/pedidos` v3 (franja, modalidad, `pagos` PENDIENTE, stock una vez) manteniendo v2 | Tests idempotencia + no doble `SALIDA_VENTA` |
| **2.1d** | `GET` pedidos + cancelación con restitución | Cupo se libera; stock vuelve con movimiento |
| **2.1e** | Stub `POST /api/pagos/qr` + verificar (simulador etiquetado) | Ningún client marca PAGADO; unique cobro |
| **2.1f** | Transiciones ONLINE vs CONTRAENTREGA hacia picking | Matriz §3.2 cubierta por tests |
| **2.2+** | Picking/entregas/Android/Banco real | Fuera de 2.1 |

Orden de bloqueo a documentar en código: usuario → franja → carrito → productos (ids ordenados).

---

## 10. Decisiones que deben quedar definidas antes de programar

1. **¿`contratoVersion` 3 obligatorio de inmediato o coexistencia prolongada con v2?**  
   Impacta apps Android y ventana de despliegue.

2. **¿`CONFIRMADO` en pedidos nuevos significa “aceptado comercialmente con stock ya descontado, pago pendiente”?**  
   Recomendación de este diseño: **sí**, para no añadir estado. Alternativa: nuevo valor en CHECK (migración).

3. **¿ONLINE puede entrar a `EN_PICKING` sin `PAGADO`?**  
   Recomendación: **no**. Contraentrega: **sí**.

4. **Plazo máximo para pagar ONLINE antes de cancelación automática** (minutos/horas) y si libera franja/stock automáticamente.

5. **Política de cancelación tras `PAGADO`:** ¿devolución bancaria obligatoria vía `ajustes_pago` antes de restituir stock?

6. **Ventana de franjas:** solo hoy+mañana; huso horario (tienda); ¿pedidos `CONFIRMADO` legado cuentan en cupo? (recomendación: sí si tienen `franja_entrega_id`; los sin franja no cuentan).

7. **Duración de reservas de carrito** (hoy no caducan): ¿TTL antes del checkout?

8. **Simulador de banco en 2.1e:** ¿permitido con flag `PAGO_SIMULADO=true` claramente separado del núcleo docente, o bloquear hasta contrato real?  
   Restricción actual del usuario: no implementar Banco Andino sin contrato → el stub no debe fingir ser el banco real.

9. **Restitución por cancelación:** ¿reutilizar `AJUSTE_ENTRADA` o ampliar CHECK de `movimientos_inventario.tipo`?

10. **Exposición de `preferencia_sustitucion`:** ¿solo en pedido al checkout o también editable en perfil (`usuarios`)?

---

## 11. Cierre de la Fase 2.1 (diseño)

Este documento fija:

- flujo QR ONLINE vs contraentrega;
- matriz de estados conservando `CONFIRMADO` histórico;
- cuándo reservar / descontar / liberar / restituir stock;
- picking sin redecontar;
- cupo de franja atómico por `FOR UPDATE` + `COUNT`;
- compatibilidad idempotente del `POST /api/pedidos`;
- mapa de endpoints;
- esquema Neon **suficiente** sin tablas nuevas obligatorias.

**No se ha modificado código, Neon ni aplicaciones Android.**  
La programación debe esperar al cierre de las decisiones de la §10 (como mínimo 1–4 y 8).
