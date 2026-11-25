const { test } = require('node:test');
const assert = require('node:assert/strict');
const { textoError } = require('../src/erroresDb');

test('textoError incluye los fallos de un AggregateError sin mensaje', () => {
  const error = new AggregateError([
    Object.assign(new Error('connect ETIMEDOUT 15.229.115.3:5432'), { code: 'ETIMEDOUT' }),
    Object.assign(new Error('connect ENETUNREACH 2600:1f1e::1:5432'), { code: 'ENETUNREACH' })
  ]);
  error.code = 'ETIMEDOUT';
  error.message = '';

  const texto = textoError(error);
  assert.match(texto, /ETIMEDOUT/);
  assert.match(texto, /ENETUNREACH/);
  assert.match(texto, /15\.229\.115\.3/);
});
