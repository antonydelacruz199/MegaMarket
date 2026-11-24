const { test } = require('node:test');
const assert = require('node:assert/strict');
const { resolverFiltroListado } = require('../src/controllers/pedidos.controller');

test('resolverFiltroListado: filtros nombrados de Mis Pedidos', () => {
  assert.deepEqual(resolverFiltroListado({ filtro: 'PENDIENTES_PAGO' }), {
    tipo: 'pendientes_pago'
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'EN_PREPARACION' }), {
    tipo: 'varios',
    estados: ['RECIBIDO', 'EN_PICKING', 'PAGADO']
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'LISTOS' }), {
    tipo: 'unico',
    estados: ['LISTO']
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'EN_CAMINO' }), {
    tipo: 'unico',
    estados: ['EN_CAMINO']
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'ENTREGADOS' }), {
    tipo: 'unico',
    estados: ['ENTREGADO']
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'CANCELADOS' }), {
    tipo: 'unico',
    estados: ['CANCELADO']
  });
  assert.deepEqual(resolverFiltroListado({ filtro: 'TODOS' }), { tipo: 'ninguno' });
});

test('resolverFiltroListado: estado / estados legacy del picker', () => {
  assert.deepEqual(resolverFiltroListado({ estado: 'LISTO' }), {
    tipo: 'unico',
    estados: ['LISTO']
  });
  assert.deepEqual(resolverFiltroListado({ estados: 'RECIBIDO,EN_PICKING' }), {
    tipo: 'varios',
    estados: ['RECIBIDO', 'EN_PICKING']
  });
  assert.deepEqual(resolverFiltroListado({}), { tipo: 'ninguno' });
});
