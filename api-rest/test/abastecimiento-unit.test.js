const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  puedeTransicionar,
  validarRuc,
  validarCorreo,
  uuidDerivado,
  hashSolicitud,
  esUuid
} = require('../src/abastecimiento');
const banco = require('../src/banco/bancoAndinoSimulado');

test('transiciones de estado de orden de compra', () => {
  assert.equal(puedeTransicionar('SUGERIDA', 'APROBADA'), true);
  assert.equal(puedeTransicionar('SUGERIDA', 'CANCELADA'), true);
  assert.equal(puedeTransicionar('APROBADA', 'PAGADA'), true);
  assert.equal(puedeTransicionar('PAGADA', 'RECIBIDA'), true);
  assert.equal(puedeTransicionar('SUGERIDA', 'PAGADA'), false);
  assert.equal(puedeTransicionar('PAGADA', 'APROBADA'), false);
  assert.equal(puedeTransicionar('RECIBIDA', 'PAGADA'), false);
  assert.equal(puedeTransicionar('CANCELADA', 'APROBADA'), false);
});

test('validación de RUC y correo', () => {
  assert.equal(validarRuc('20123456789').ok, true);
  assert.equal(validarRuc('123').ok, false);
  assert.equal(validarRuc('abcdefghijk').ok, false);
  assert.equal(validarCorreo('a@b.com').ok, true);
  assert.equal(validarCorreo('').ok, true);
  assert.equal(validarCorreo('malo').ok, false);
});

test('uuid derivado es estable y válido', () => {
  const padre = '123e4567-e89b-12d3-a456-426614174000';
  const a = uuidDerivado(padre, 'detalle-1');
  const b = uuidDerivado(padre, 'detalle-1');
  const c = uuidDerivado(padre, 'detalle-2');
  assert.equal(a, b);
  assert.notEqual(a, c);
  assert.equal(esUuid(a), true);
});

test('hash de solicitud detecta payload distinto', () => {
  const h1 = hashSolicitud({ ordenCompraId: 'a', montoCentimos: 100, uuidOperacion: 'u' });
  const h2 = hashSolicitud({ ordenCompraId: 'a', montoCentimos: 200, uuidOperacion: 'u' });
  assert.notEqual(h1, h2);
});

test('adaptador bancario distingue resultados', async () => {
  const base = {
    uuidOperacion: '123e4567-e89b-12d3-a456-426614174000',
    ordenCompraId: '123e4567-e89b-12d3-a456-426614174001',
    numeroOrden: 'OC-00000001',
    montoCentimos: 1000,
    rucProveedor: '20123456789',
    razonSocial: 'Demo SAC',
    cuentaDestino: '001-123'
  };
  for (const estado of ['CONFIRMADA', 'RECHAZADA', 'PENDIENTE', 'DESCONOCIDA']) {
    const r = await banco.transferir({ ...base, forzarResultado: estado });
    assert.equal(r.estado, estado);
    assert.equal(r.simulacion, true);
    assert.equal(r.adaptador, 'BANCO_ANDINO_SIMULADO');
    assert.ok(r.mensaje && r.mensaje.length > 0);
  }
});
