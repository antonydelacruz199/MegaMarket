const { test } = require('node:test');
const assert = require('node:assert/strict');
const { cantidadFaltanteDetalle } = require('../src/sustituciones');

test('cantidadFaltanteDetalle: solo unidades no armadas', () => {
  assert.equal(cantidadFaltanteDetalle({ cantidad: 3, cantidad_armada: 2 }), 1);
  assert.equal(cantidadFaltanteDetalle({ cantidad: 3, cantidad_armada: 0 }), 3);
  assert.equal(cantidadFaltanteDetalle({ cantidad: 3, cantidad_armada: 3 }), 0);
  assert.equal(cantidadFaltanteDetalle({ cantidad: 1 }), 1);
  assert.equal(cantidadFaltanteDetalle({ cantidad: 0, cantidad_armada: 0 }), 0);
});
