const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const sqlDir = path.join(__dirname, '../sql');
const migracion = fs.readFileSync(path.join(sqlDir, '003_operaciones_extendidas.sql'), 'utf8');

test('003 declara las tablas operativas de Neon sin DROP TABLE', () => {
  const tablas = [
    'franjas_entrega',
    'picking_escaneos',
    'sustituciones_pedido',
    'pagos',
    'ajustes_pago',
    'proveedores',
    'ordenes_compra',
    'orden_compra_detalle',
    'entregas',
    'conciliaciones_bancarias',
    'conciliacion_detalle',
    'solicitudes_credito'
  ];
  for (const tabla of tablas) {
    assert.match(migracion, new RegExp(`CREATE TABLE IF NOT EXISTS ${tabla}\\b`));
  }
  assert.doesNotMatch(migracion, /\bDROP TABLE\b/i);
  assert.doesNotMatch(migracion, /\bTRUNCATE\b/i);
  assert.doesNotMatch(migracion, /\bpasillos\b/);
  assert.doesNotMatch(migracion, /\bproducto_codigos_barras\b/);
});

test('003 usa los nombres de columnas confirmados en Neon', () => {
  assert.match(migracion, /franja_entrega_id/);
  assert.doesNotMatch(migracion, /\bfranja_id\b/);
  assert.match(migracion, /ADD COLUMN IF NOT EXISTS pasillo varchar/);
  assert.match(migracion, /ADD COLUMN IF NOT EXISTS codigo_barras varchar/);
  assert.doesNotMatch(migracion, /pasillo_id/);
  assert.match(migracion, /preferencia_sustitucion/);
  assert.match(migracion, /estado_picking/);
  assert.match(migracion, /cantidad_armada/);
  assert.match(migracion, /orden_compra_detalle_id/);
  assert.match(migracion, /PICKER/);
  assert.match(migracion, /REPARTIDOR/);
  assert.match(migracion, /'CONFIRMADO'/);
  assert.match(migracion, /idx_ajustes_pago_estado/);
  assert.match(migracion, /uq_ajustes_pago_sustitucion_tipo/);
  assert.match(migracion, /idx_conciliacion_detalle_conciliacion/);
});

test('cadena de migraciones versionadas está completa en sql/', () => {
  for (const file of [
    '001_esquema_inicial.sql',
    '002_reservas.sql',
    '003_operaciones_extendidas.sql'
  ]) {
    assert.ok(fs.existsSync(path.join(sqlDir, file)), file);
  }
});
