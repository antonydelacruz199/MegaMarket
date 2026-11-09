const { test } = require('node:test');
const assert = require('node:assert/strict');
const { agruparItems, huella } = require('../src/reglas');
const id = '123e4567-e89b-42d3-a456-426614174000';
test('suma cantidades repetidas antes de validar stock', () => {
  assert.deepEqual(agruparItems([{productoId:id,cantidad:4},{productoId:id,cantidad:4}]), [{productoId:id,cantidad:8}]);
});
test('no acepta cantidades negativas, fraccionarias ni cadenas', () => {
  for (const cantidad of [-1,0,1.2,'2',Infinity]) assert.throws(()=>agruparItems([{productoId:id,cantidad}]));
});
test('exige precio esperado coherente por producto', () => {
  assert.throws(()=>agruparItems([{productoId:id,cantidad:1}],true));
  assert.throws(()=>agruparItems([{productoId:id,cantidad:1,precioEsperadoCentimos:100},{productoId:id,cantidad:1,precioEsperadoCentimos:200}],true));
});
test('huella distingue contenidos y permite reintentos idénticos', () => {
  assert.equal(huella({cantidad:2}),huella({cantidad:2}));
  assert.notEqual(huella({cantidad:2}),huella({cantidad:3}));
});
