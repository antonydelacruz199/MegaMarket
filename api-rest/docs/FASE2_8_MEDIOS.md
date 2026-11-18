# Fase 2.8 — Medios (sin Firebase)

Neon PostgreSQL es la única base de datos. Las referencias viven en `productos.imagen_key` y `entregas.foto_referencia`. **No** se usa Firebase / Firestore / Auth.

## Endpoints (conservados)

| Método | Ruta | Rol | Función |
|---|---|---|---|
| POST | `/api/medios/productos/:productoId` | ADMIN | Valida y sube; actualiza `imagen_key`; no borra la anterior |
| GET | `/api/medios/productos/:productoId` | Autenticado | Proxy/`?download=1` o metadatos de acceso |
| POST | `/api/medios/entregas/:pedidoId` | REPARTIDOR asignado | Foto en `EN_CAMINO` → `fotoReferencia` |
| GET | `/api/medios/entregas/:pedidoId` | Admin / dueño / repartidor | Lectura **privada** |

Validaciones vigentes: MIME real, extensión, tamaño, JWT/rol, pertenencia de clave al recurso, claves locales heredadas (`IMAGEN_LOCAL`).

## Almacén

| `MEDIA_STORAGE` | Adaptador | Uso |
|---|---|---|
| `memory` (omisión) | `memoryStorage` | Pruebas automatizadas de endpoints. No sobrevive a un reinicio. |
| `disk` | `diskStorage` | Disco local. Sobrevive al reinicio del proceso en la misma máquina. |

`MEDIA_ROOT` es obligatorio con `disk`. Puede ser absoluta o relativa a `api-rest/` (ejemplo Windows: `data/media`). Neon sigue guardando solo `productos.imagen_key` y `entregas.foto_referencia`.

La subida escribe un temporal en la misma carpeta, hace `fsync` y publica el archivo con enlace duro o `rename`. Si la escritura falla, se borra solo el temporal. `subir()` no elimina la imagen anterior: el `UPDATE` de Neon confirma la clave nueva y la foto previa permanece.

No hay `express.static` sobre `MEDIA_ROOT`. Las fotos de entrega se leen por GET autenticado (`?download=1` devuelve bytes; sin ese parámetro la URL es `disk://`, no una ruta del disco).

## Directorio persistente del servidor

### Idea

- Ruta base configurable, p. ej. `MEDIA_ROOT=/var/lib/megamarket/media`.
- Objetos en disco con las mismas claves: `productos/{id}/{uuid}.ext`, `entregas/{id}/{uuid}.ext`.
- La API sigue siendo el único punto de lectura/escritura (sin exponer el filesystem por HTTP estático público).
- Neon solo guarda la clave; nunca bytes.

### Persistencia

| Aspecto | Evaluación |
|---|---|
| Reinicio del proceso | OK si el volumen/disco es persistente |
| Varias instancias API | Requiere disco compartido (NFS/SMB) o un solo nodo de escritura |
| Backups | Incluir `MEDIA_ROOT` junto con políticas de Neon |
| Contenedores | Montar volumen nombrado o bind mount; no usar capa efímera del contenedor |

### Seguridad

| Riesgo | Mitigación |
|---|---|
| Path traversal (`../`) | Resolver path bajo `MEDIA_ROOT` y rechazar escapes |
| Lectura pública de entregas | Solo GET autenticado con autorización; no `express.static` sobre entregas |
| Productos | Proxy autenticado (`?download=1`). `MEDIA_ROOT` no se publica |
| Cuota/DoS | `MEDIA_MAX_BYTES`, límites multer, cuotas por disco |
| Permisos OS | Usuario del proceso con escritura solo en `MEDIA_ROOT` |

### Viabilidad de despliegue

- **VPS / VM única:** alta (simple, barato, alineado a un API Node).
- **Neon + API en PaaS sin disco (p. ej. algunos serverless):** baja → hace falta volumen o object storage no-Firebase.
- **Docker Compose académico:** alta con volumen `./data/media`.
- **Kubernetes:** media (PVC ReadWriteMany o un réplica).

### Comprobar persistencia local

1. En `api-rest/.env`: `MEDIA_STORAGE=disk` y `MEDIA_ROOT=data/media`.
2. Arrancar con `npm run dev` y confirmar el log `Almacén de medios: disk`.
3. Subir una imagen con `POST /api/medios/productos/:productoId` (administrador).
4. Detener el proceso. El archivo sigue en `api-rest/data/media/productos/{productoId}/{uuid}.jpg` (o `.png` / `.webp`).
5. Volver a arrancar y pedir `GET /api/medios/productos/:productoId?download=1`.

Las fotos de entrega quedan en `data/media/entregas/{pedidoId}/{uuid}.ext` y solo se descargan con JWT y rol autorizado. Esa carpeta no se versiona.

### Hecho en esta fase

1. Topología asumida: un proceso Node en Windows, un disco local.
2. `diskStorage` con la misma interfaz que `memoryStorage` (`subir`, `existe`, `obtener`, `urlFirmada`).
3. La imagen anterior no se borra al reemplazar.
4. Pruebas de path traversal, errores de disco, reinicio y PostgreSQL de laboratorio.

## Pendiente Android

Sin cambios en las aplicaciones. La integración posterior sigue en MVVM contra `/api/medios`, sin SDKs de Firebase.
