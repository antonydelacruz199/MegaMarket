-- ---------------------------------------------------------------------------
-- 007_abastecimiento.sql
-- Fase 7: transferencias a proveedores y numeración concurrente de OC.
-- NO recrea proveedores / ordenes_compra / orden_compra_detalle.
-- Aplicar manualmente en Neon SQL Editor sobre bd_megamarket.
-- NO ejecutar automáticamente desde la API.
-- ---------------------------------------------------------------------------

-- Numeración segura frente a concurrencia (evita MAX(numero)+1).
CREATE SEQUENCE IF NOT EXISTS seq_numero_orden_compra START WITH 1 INCREMENT BY 1;

-- Libro de intentos de transferencia a proveedores (simulador académico).
-- No reutiliza la tabla `pagos` (cobros de clientes).
CREATE TABLE IF NOT EXISTS transferencias_proveedor (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  orden_compra_id uuid NOT NULL REFERENCES ordenes_compra(id),
  administrador_id uuid NOT NULL REFERENCES usuarios(id),
  monto_centimos bigint NOT NULL,
  cuenta_destino varchar NOT NULL,
  solicitud_hash varchar NOT NULL,
  estado varchar NOT NULL DEFAULT 'PENDIENTE',
  referencia_banco varchar,
  numero_operacion_banco varchar,
  mensaje text,
  simulacion boolean NOT NULL DEFAULT true,
  adaptador varchar NOT NULL DEFAULT 'BANCO_ANDINO_SIMULADO',
  fecha_solicitud timestamptz NOT NULL DEFAULT now(),
  fecha_respuesta timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT transferencias_proveedor_uuid_key UNIQUE (uuid_operacion),
  CONSTRAINT ck_transferencias_monto CHECK (monto_centimos > 0),
  CONSTRAINT ck_transferencias_cuenta CHECK (TRIM(BOTH FROM cuenta_destino) <> ''),
  CONSTRAINT ck_transferencias_estado CHECK (estado IN (
    'PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'DESCONOCIDA'
  ))
);

CREATE INDEX IF NOT EXISTS idx_transferencias_orden
  ON transferencias_proveedor (orden_compra_id);

CREATE INDEX IF NOT EXISTS idx_transferencias_estado
  ON transferencias_proveedor (estado);

-- Como máximo una transferencia CONFIRMADA por orden.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transferencia_confirmada_orden
  ON transferencias_proveedor (orden_compra_id)
  WHERE estado = 'CONFIRMADA';

CREATE UNIQUE INDEX IF NOT EXISTS uq_transferencias_numero_operacion
  ON transferencias_proveedor (numero_operacion_banco)
  WHERE numero_operacion_banco IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Verificación sugerida (ejecutar tras aplicar):
--
-- SELECT to_regclass('public.transferencias_proveedor') AS tabla;
-- SELECT pg_get_serial_sequence('ordenes_compra', 'numero_orden'); -- puede ser NULL
-- SELECT nextval('seq_numero_orden_compra'); -- consume un valor; solo en prueba
-- SELECT column_name, data_type
--   FROM information_schema.columns
--  WHERE table_name = 'transferencias_proveedor'
--  ORDER BY ordinal_position;
-- ---------------------------------------------------------------------------
