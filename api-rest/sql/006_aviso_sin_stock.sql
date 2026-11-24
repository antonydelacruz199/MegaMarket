-- Aviso al cliente cuando no hay sustituto en almacén (SIN_STOCK).
-- producto_sustituto_id puede ser NULL; precio_sustituto_centimos = 0.

ALTER TABLE sustituciones_pedido
  ALTER COLUMN producto_sustituto_id DROP NOT NULL;

ALTER TABLE sustituciones_pedido
  DROP CONSTRAINT IF EXISTS ck_sustituciones_productos;

ALTER TABLE sustituciones_pedido
  ADD CONSTRAINT ck_sustituciones_productos CHECK (
    producto_sustituto_id IS NULL OR producto_original_id <> producto_sustituto_id
  );

ALTER TABLE sustituciones_pedido
  DROP CONSTRAINT IF EXISTS ck_sustituciones_precios;

ALTER TABLE sustituciones_pedido
  ADD CONSTRAINT ck_sustituciones_precios CHECK (
    precio_original_centimos > 0
    AND (
      (producto_sustituto_id IS NULL AND precio_sustituto_centimos = 0)
      OR (producto_sustituto_id IS NOT NULL AND precio_sustituto_centimos > 0)
    )
  );

ALTER TABLE sustituciones_pedido
  DROP CONSTRAINT IF EXISTS ck_sustituciones_estado;

ALTER TABLE sustituciones_pedido
  ADD CONSTRAINT ck_sustituciones_estado CHECK (estado IN (
    'PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'CANCELADA', 'SIN_STOCK'
  ));

CREATE UNIQUE INDEX IF NOT EXISTS uq_sustituciones_sin_stock
  ON sustituciones_pedido (pedido_detalle_id)
  WHERE estado = 'SIN_STOCK';
