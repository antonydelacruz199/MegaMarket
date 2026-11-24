const test = require('node:test');
const assert = require('node:assert/strict');
const { paginar, metaPaginacion } = require('../src/paginacion');

test('paginar usa defaults y acota el límite', () => {
  assert.deepEqual(paginar({ query: {} }), { page: 1, limit: 20, offset: 0 });
  assert.deepEqual(paginar({ query: { page: '2', limit: '10' } }), {
    page: 2,
    limit: 10,
    offset: 10
  });
  assert.equal(paginar({ query: { limit: '999' } }).limit, 50);
  assert.equal(paginar({ query: { page: '0' } }).page, 1);
});

test('metaPaginacion calcula hayMas', () => {
  assert.deepEqual(metaPaginacion(45, { page: 2, limit: 20 }), {
    pagina: 2,
    limite: 20,
    total: 45,
    hayMas: true
  });
  assert.equal(metaPaginacion(40, { page: 2, limit: 20 }).hayMas, false);
});
