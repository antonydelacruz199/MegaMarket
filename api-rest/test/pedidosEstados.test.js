const { test } = require('node:test');
const assert = require('node:assert/strict');
const { evaluarTransicion, puedeVerPedido } = require('../src/pedidosEstados');

const pedidoBase = {
  id: 'p1',
  cliente_id: 'cli',
  picker_id: null,
  repartidor_id: null,
  estado: 'CONFIRMADO'
};

test('ONLINE pendiente no inicia picking; contraentrega y legado sí', () => {
  const online = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'EN_PICKING',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: { modalidad: 'ONLINE', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(online.ok, false);
  assert.equal(online.codigo, 'PICKING_REQUIERE_PAGO');

  const contra = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'EN_PICKING',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: { modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(contra.ok, true);

  const legado = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'EN_PICKING',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(legado.ok, true);
});

test('PAGADO no se puede fijar desde Android ni admin sin origen bancario', () => {
  const pagoOk = {
    modalidad: 'ONLINE',
    estado: 'CONFIRMADO',
    numero_operacion_banco: 'OP-1',
    fecha_confirmacion: new Date().toISOString()
  };
  for (const rol of ['CLIENTE', 'ADMINISTRADOR', 'PICKER', 'REPARTIDOR']) {
    const r = evaluarTransicion({
      desde: 'CONFIRMADO',
      hacia: 'PAGADO',
      rol,
      usuarioId: 'u',
      pedido: { ...pedidoBase, cliente_id: 'u' },
      pago: pagoOk,
      origenInternoBancario: false
    });
    assert.equal(r.ok, false);
    assert.equal(r.codigo, 'PAGO_REQUIERE_BANCO');
  }
  const interno = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'PAGADO',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: pagoOk,
    origenInternoBancario: true
  });
  assert.equal(interno.ok, true);
});

test('transiciones estructurales inválidas se rechazan', () => {
  const r = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'ENTREGADO',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'TRANSICION_INVALIDA');
});

test('cliente solo cancela propios pedidos tempranos; picker/repartidor respetan asignación', () => {
  const cancelAjeno = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'CANCELADO',
    rol: 'CLIENTE',
    usuarioId: 'otro',
    pedido: pedidoBase,
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(cancelAjeno.ok, false);

  const pickerAjeno = evaluarTransicion({
    desde: 'PAGADO',
    hacia: 'EN_PICKING',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { ...pedidoBase, estado: 'PAGADO', picker_id: 'pick-2' },
    pago: { modalidad: 'ONLINE', estado: 'CONFIRMADO' },
    origenInternoBancario: false
  });
  assert.equal(pickerAjeno.codigo, 'PEDIDO_NO_ASIGNADO');

  assert.equal(puedeVerPedido({ id: 'cli', rol: 'CLIENTE' }, pedidoBase), true);
  assert.equal(puedeVerPedido({ id: 'x', rol: 'CLIENTE' }, pedidoBase), false);
});

test('pago confirmado o estado PAGADO bloquea cancelación sin devolución', () => {
  const conPago = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'CANCELADO',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: pedidoBase,
    pago: {
      modalidad: 'ONLINE',
      estado: 'CONFIRMADO',
      numero_operacion_banco: 'OP-9',
      fecha_confirmacion: new Date().toISOString()
    },
    origenInternoBancario: false
  });
  assert.equal(conPago.ok, false);
  assert.equal(conPago.codigo, 'CANCELACION_REQUIERE_DEVOLUCION');

  const desdePagado = evaluarTransicion({
    desde: 'PAGADO',
    hacia: 'CANCELADO',
    rol: 'CLIENTE',
    usuarioId: 'cli',
    pedido: pedidoBase,
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(desdePagado.ok, false);
  assert.equal(desdePagado.codigo, 'CANCELACION_REQUIERE_DEVOLUCION');
});

test('un solo repartidor toma un LISTO; el segundo y el picker no pueden', () => {
  const libre = evaluarTransicion({
    desde: 'LISTO',
    hacia: 'EN_CAMINO',
    rol: 'REPARTIDOR',
    usuarioId: 'rep-1',
    pedido: { ...pedidoBase, estado: 'LISTO', repartidor_id: null },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(libre.ok, true);

  const segundo = evaluarTransicion({
    desde: 'LISTO',
    hacia: 'EN_CAMINO',
    rol: 'REPARTIDOR',
    usuarioId: 'rep-2',
    pedido: { ...pedidoBase, estado: 'LISTO', repartidor_id: 'rep-1' },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(segundo.ok, false);
  assert.equal(segundo.http, 403);
  assert.equal(segundo.codigo, 'PEDIDO_NO_ASIGNADO');

  const picker = evaluarTransicion({
    desde: 'LISTO',
    hacia: 'EN_CAMINO',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { ...pedidoBase, estado: 'LISTO', repartidor_id: null },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(picker.ok, false);
  assert.equal(picker.codigo, 'PERMISO_DENEGADO');

  assert.equal(
    puedeVerPedido(
      { id: 'rep-2', rol: 'REPARTIDOR' },
      { ...pedidoBase, estado: 'LISTO', repartidor_id: 'rep-1' }
    ),
    false
  );
});
