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
