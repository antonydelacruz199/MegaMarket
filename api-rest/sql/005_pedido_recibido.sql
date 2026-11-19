-- MegaMarket · migración 005 — estado RECIBIDO (picking antes de CONFIRMADO)
-- Orden: 001 → 002 → 003 → 004 → este archivo.
-- Idempotente. No elimina tablas/columnas/registros históricos.
-- Semántica nueva:
--   RECIBIDO     = pedido creado, disponible para picker (sin cobro ONLINE aún)
--   EN_PICKING   = picker preparando / propuestas de sustitución
--   CONFIRMADO   = preparación validada; ONLINE puede pagar; CONTRAENTREGA pasa a LISTO
--   PAGADO       = cobro ONLINE confirmado; habilita despacho (LISTO)
--   LISTO        = listo para repartidor

BEGIN;

SELECT megamarket_reemplazar_check(
  'pedidos'::regclass,
  ARRAY['ck_pedidos_estado', 'pedidos_estado_check'],
  'ck_pedidos_estado',
  $$estado IN (
    'RECIBIDO','CONFIRMADO','PAGADO','EN_PICKING','LISTO','EN_CAMINO','ENTREGADO','CANCELADO'
  )$$
);

COMMIT;
