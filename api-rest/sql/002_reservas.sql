-- Ejecutar sobre el esquema actual, antes de desplegar la nueva API.
-- No fusiona duplicados: el índice falla y revierte si requieren revisión manual.
BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS productos_nombre_normalizado_unique ON productos
 (lower(regexp_replace(trim(nombre), '\s+', ' ', 'g'))) WHERE deleted_at IS NULL;
CREATE TABLE IF NOT EXISTS carritos (
 id uuid PRIMARY KEY,
 usuario_id uuid NOT NULL REFERENCES usuarios(id),
 revision bigint NOT NULL DEFAULT 0,
 cerrado boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS reservas_carrito (
 carrito_id uuid NOT NULL REFERENCES carritos(id),
 producto_id uuid NOT NULL REFERENCES productos(id),
 cantidad integer NOT NULL CHECK (cantidad > 0),
 PRIMARY KEY(carrito_id, producto_id)
);
CREATE INDEX IF NOT EXISTS reservas_por_producto ON reservas_carrito(producto_id);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS solicitud_hash text;
COMMIT;
