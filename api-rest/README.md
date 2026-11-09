# MegaMarket API REST

API pequeña en Node.js + Express + PostgreSQL (Neon).

## Arranque

1. Instalar dependencias:

```bash
npm install
```

2. Copiar variables de entorno:

```bash
copy .env.example .env
```

En Linux/macOS:

```bash
cp .env.example .env
```

3. Completar en `.env`:

- `DATABASE_URL` — cadena de conexión de Neon
- `JWT_SECRET` — secreto para firmar tokens
- `PORT` — por defecto `8000`

4. Ejecutar en desarrollo:

```bash
npm run dev
```

5. Probar salud:

```bash
GET http://localhost:8000/api/health
```

Respuesta esperada cuando Neon responde:

```json
{
  "ok": true,
  "database": "connected"
}
```

No coloques credenciales reales en este README.

## Actualización a reservas

Esta versión requiere Node.js 24 o posterior y un cambio de esquema antes de arrancar:

- Base existente: ejecutar `sql/002_reservas.sql` después de revisar nombres duplicados.
- Base nueva: ejecutar `sql/001_esquema_inicial.sql` y después `sql/002_reservas.sql`; cargar usuarios con hashes bcrypt, categorías y productos apropiados para el entorno.
- Los scripts no se ejecutan automáticamente al iniciar el servidor.
- Configurar `PORT=8010` para coincidir con la URL Android predeterminada, o configurar ambos lados con el mismo puerto.

Los pedidos antiguos enviados con el protocolo anterior reciben HTTP 426. Actualizar API y aplicativos de forma coordinada.

## Operaciones

Todas requieren JWT, salvo login y health.

| Ruta | Acceso | Función |
|---|---|---|
| POST /api/auth/login | Público | Autenticación |
| GET /api/auth/me | Autenticado | Perfil |
| GET /api/categorias | Autenticado | Categorías |
| GET /api/productos | Autenticado | Cliente: disponibles; administrador: activos e inactivos |
| POST, PATCH, DELETE /api/productos | Administrador | Gestión de productos y control de versión |
| PUT /api/carritos/:id | Cliente propietario | Instantánea de reserva con revisión creciente |
| POST /api/pedidos | Cliente propietario | Confirmación, validación de precio y consumo de reserva |
| POST /api/inventario/movimientos | Administrador | Ajustes que respetan las reservas existentes |

Ejemplo de reserva:

```json
{"revision":1,"items":[{"productoId":"UUID_PRODUCTO","cantidad":2}]}
```

Una lista vacía libera la reserva. El UUID del carrito se reutiliza como `clientUuid` del pedido. El pedido requiere `contratoVersion: 2`, dirección e ítems con `precioEsperadoCentimos`. Un reintento conserva UUID y contenido.

## Pruebas

```bash
npm test
```

Las pruebas de controladores utilizan transacciones SQLite en memoria con un adaptador SQL. No verifican los bloqueos de PostgreSQL.

Para probar migraciones, índice de nombres y reservas concurrentes en PostgreSQL, proporcionar explícitamente `TEST_DATABASE_URL` de una base de pruebas y ejecutar `npm test`. La prueba crea un esquema aislado aleatorio y lo elimina al terminar; no utiliza `DATABASE_URL`. Sin esa variable, esa prueba se omite.

Detalles de despliegue, migraciones y comprobación en dispositivos: `../docs/IMPLEMENTACION_RESERVAS.md`.
