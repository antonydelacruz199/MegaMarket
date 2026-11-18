# Fase 3.5 — código de barras y pasillo

No hay tablas nuevas. Se usan `productos.codigo_barras` y `productos.pasillo`, ya creadas por `sql/003_operaciones_extendidas.sql`. El índice único parcial `uq_productos_codigo_barras` sigue siendo la garantía de no duplicar códigos entre productos no eliminados. Varios `NULL` conviven.

## Contrato

`GET /api/productos`, `GET /api/productos/:id`, `POST /api/productos` y `PATCH /api/productos/:id` incluyen:

| JSON | Columna | Regla |
|---|---|---|
| `codigoBarras` | `codigo_barras` | Opcional. Vacío se guarda `NULL`. Si viene, 1 a 32 letras o dígitos, sin espacios. |
| `pasillo` | `pasillo` | Opcional. Vacío se guarda `NULL`. Si viene, 1 a 40 caracteres: letras (con tildes), números, espacios y guiones. |

También se acepta `codigo_barras` en el cuerpo. Un `PATCH` que omite el campo conserva el valor guardado, incluidos productos antiguos. Enviar `null` o blanco lo limpia. Un valor idéntico al almacenado se conserva aunque no cumpla el formato nuevo.

Duplicado de código: `409` con `codigo: CODIGO_BARRAS_DUPLICADO`. El duplicado de nombre sigue en `NOMBRE_DUPLICADO`. Formato inválido: `400`.

`GET /api/picking/pedidos` y `GET /api/picking/pedidos/:id` ya devuelven `pasillo` y `codigoBarras` de la línea. Esta fase no cambia esas reglas.

La subida de imagen (`POST /api/medios/productos/:id`) no toca estas columnas.
