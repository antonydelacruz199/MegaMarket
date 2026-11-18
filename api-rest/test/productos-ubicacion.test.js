const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  leerUbicacion,
  esCodigoBarrasDuplicado,
  textoAlmacenado
} = require('../src/productosUbicacion');

test('alta con código y pasillo', () => {
  const ubicacion = leerUbicacion({
    codigoBarras: ' 770001 ',
    pasillo: '  Lácteos  2 '
  });
  assert.deepEqual(ubicacion.errores, []);
  assert.equal(ubicacion.codigoBarras, '770001');
  assert.equal(ubicacion.pasillo, 'Lácteos 2');
});

test('acepta los nombres de PostgreSQL', () => {
  const ubicacion = leerUbicacion({ codigo_barras: 'ABC12', pasillo: 'A-1' });
  assert.equal(ubicacion.codigoBarras, 'ABC12');
  assert.equal(ubicacion.pasillo, 'A-1');
});

test('producto antiguo sin código queda en null y no pisa un valor omitido', () => {
  const vacio = leerUbicacion({ codigoBarras: '   ', pasillo: '' });
  assert.equal(vacio.codigoBarras, null);
  assert.equal(vacio.pasillo, null);

  const omitido = leerUbicacion(
    { nombre: 'Arroz' },
    { codigoBarras: '770001', pasillo: 'A' }
  );
  assert.equal(omitido.codigoBarras, '770001');
  assert.equal(omitido.pasillo, 'A');
  assert.equal(textoAlmacenado(''), null);
});

test('rechaza longitud y caracteres no permitidos', () => {
  const largo = leerUbicacion({ codigoBarras: '1'.repeat(33), pasillo: 'A' });
  assert.equal(largo.errores.length, 1);
  assert.match(largo.errores[0], /32/);

  const simbolo = leerUbicacion({ codigoBarras: '7700 01', pasillo: 'A/1' });
  assert.equal(simbolo.errores.length, 2);
});

test('conserva un código ya guardado aunque la edición lo reenvíe', () => {
  const igual = leerUbicacion(
    { codigoBarras: '77-01', pasillo: '***' },
    { codigoBarras: '77-01', pasillo: '***' }
  );
  assert.deepEqual(igual.errores, []);
  assert.equal(igual.codigoBarras, '77-01');
  assert.equal(igual.pasillo, '***');
});

test('el índice único de código no se confunde con el de nombre', () => {
  assert.equal(
    esCodigoBarrasDuplicado({ code: '23505', constraint: 'uq_productos_codigo_barras' }),
    true
  );
  assert.equal(
    esCodigoBarrasDuplicado({
      code: '23505',
      constraint: 'productos_nombre_normalizado_unique'
    }),
    false
  );
});
