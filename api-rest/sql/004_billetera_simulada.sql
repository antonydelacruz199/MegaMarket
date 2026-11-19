-- MegaMarket · migración 004 — billetera simulada (académica, sin banco real)
-- Orden: 001 → 002 → 003 → este archivo.
-- Idempotente. No elimina tablas/columnas/registros.
-- Montos en céntimos enteros (bigint). Prohíbe saldos negativos.
-- No se ejecuta al arrancar la API. No modificar Neon desde pruebas.

BEGIN;

-- ---------------------------------------------------------------------------
-- cuentas_billetera: saldo por cliente + cuenta comercial MegaMarket
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cuentas_billetera (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo varchar NOT NULL,
  usuario_id uuid REFERENCES usuarios(id),
  codigo varchar,
  saldo_centimos bigint NOT NULL,
  moneda varchar NOT NULL DEFAULT 'PEN',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_cuentas_billetera_tipo CHECK (tipo IN ('CLIENTE', 'COMERCIO')),
  CONSTRAINT ck_cuentas_billetera_moneda CHECK (moneda = 'PEN'),
  CONSTRAINT ck_cuentas_billetera_saldo CHECK (saldo_centimos >= 0),
  CONSTRAINT ck_cuentas_billetera_cliente CHECK (
    (tipo = 'CLIENTE' AND usuario_id IS NOT NULL AND codigo IS NULL)
    OR (tipo = 'COMERCIO' AND usuario_id IS NULL AND codigo IS NOT NULL)
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuentas_billetera_cliente
  ON cuentas_billetera (usuario_id)
  WHERE tipo = 'CLIENTE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuentas_billetera_comercio
  ON cuentas_billetera (codigo)
  WHERE tipo = 'COMERCIO';

-- ---------------------------------------------------------------------------
-- movimientos_billetera: historial ficticio con idempotencia por uuid_operacion
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS movimientos_billetera (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  cuenta_id uuid NOT NULL REFERENCES cuentas_billetera(id),
  tipo varchar NOT NULL,
  sentido varchar NOT NULL,
  monto_centimos bigint NOT NULL,
  saldo_posterior_centimos bigint NOT NULL,
  pago_id uuid REFERENCES pagos(id),
  ajuste_pago_id uuid REFERENCES ajustes_pago(id),
  pedido_id uuid REFERENCES pedidos(id),
  descripcion text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT movimientos_billetera_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT ck_movimientos_billetera_tipo CHECK (tipo IN (
    'ABONO_INICIAL',
    'COBRO_PEDIDO',
    'ACREDITACION_COMERCIO',
    'DEVOLUCION_CLIENTE',
    'DEBITO_COMERCIO_DEVOLUCION',
    'AJUSTE_DESCUENTO_CLIENTE',
    'AJUSTE_ACREDITACION_COMERCIO'
  )),
  CONSTRAINT ck_movimientos_billetera_sentido CHECK (sentido IN ('DEBITO', 'CREDITO')),
  CONSTRAINT ck_movimientos_billetera_monto CHECK (monto_centimos > 0),
  CONSTRAINT ck_movimientos_billetera_saldo_post CHECK (saldo_posterior_centimos >= 0)
);

CREATE INDEX IF NOT EXISTS idx_movimientos_billetera_cuenta
  ON movimientos_billetera (cuenta_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_movimientos_billetera_pago
  ON movimientos_billetera (pago_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_billetera_ajuste
  ON movimientos_billetera (ajuste_pago_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_billetera_pedido
  ON movimientos_billetera (pedido_id);

-- Una sola liquidación de cobro por pago (evita doble débito).
CREATE UNIQUE INDEX IF NOT EXISTS uq_movimientos_cobro_pago
  ON movimientos_billetera (pago_id)
  WHERE tipo = 'COBRO_PEDIDO';

-- Una sola liquidación por ajuste confirmado.
CREATE UNIQUE INDEX IF NOT EXISTS uq_movimientos_ajuste
  ON movimientos_billetera (ajuste_pago_id)
  WHERE ajuste_pago_id IS NOT NULL
    AND tipo IN ('DEVOLUCION_CLIENTE', 'AJUSTE_DESCUENTO_CLIENTE');

-- ---------------------------------------------------------------------------
-- Semilla: cuenta comercial MegaMarket S/ 500,000 (= 50_000_000 céntimos)
-- ---------------------------------------------------------------------------
INSERT INTO cuentas_billetera (tipo, codigo, saldo_centimos, moneda)
SELECT 'COMERCIO', 'MEGAMARKET', 50000000, 'PEN'
WHERE NOT EXISTS (
  SELECT 1 FROM cuentas_billetera WHERE tipo = 'COMERCIO' AND codigo = 'MEGAMARKET'
);

-- Semilla diferida de clientes: la API crea saldo S/ 1_000_000_000 al primer acceso.
-- Aquí se inicializan clientes ya existentes sin cuenta (instalaciones previas).
INSERT INTO cuentas_billetera (tipo, usuario_id, saldo_centimos, moneda)
SELECT 'CLIENTE', u.id, 100000000000, 'PEN'
FROM usuarios u
WHERE u.rol = 'CLIENTE'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM cuentas_billetera c
    WHERE c.tipo = 'CLIENTE' AND c.usuario_id = u.id
  );

COMMIT;
