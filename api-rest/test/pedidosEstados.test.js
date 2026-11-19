const { test } = require('node:test');
const assert = require('node:assert/strict');
const { evaluarTransicion, puedeVerPedido } = require('../src/pedidosEstados');

const pedidoBase = {
  id: 'p1',
  cliente_id: 'cli',
  picker_id: null,
  repartidor_id: null,
  estado: 'RECIBIDO'
};

test('RECIBIDO inicia picking sin exigir pago ONLINE', () => {
  const online = evaluarTransicion({
    desde: 'RECIBIDO',
    hacia: 'EN_PICKING',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: pedidoBase,
    pago: { modalidad: 'ONLINE', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(online.ok, true);

  const contra = evaluarTransicion({
    desde: 'RECIBIDO',
    hacia: 'EN_PICKING',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: pedidoBase,
    pago: { modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(contra.ok, true);
});

test('EN_PICKING cierra a CONFIRMADO; no a LISTO directo', () => {
  const conf = evaluarTransicion({
    desde: 'EN_PICKING',
    hacia: 'CONFIRMADO',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { ...pedidoBase, estado: 'EN_PICKING', picker_id: 'pick-1' },
    pago: { modalidad: 'ONLINE', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(conf.ok, true);

  const listo = evaluarTransicion({
    desde: 'EN_PICKING',
    hacia: 'LISTO',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { ...pedidoBase, estado: 'EN_PICKING', picker_id: 'pick-1' },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(listo.ok, false);
  assert.equal(listo.codigo, 'TRANSICION_INVALIDA');
});

test('PAGADO solo tras origen bancario desde CONFIRMADO ONLINE', () => {
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
      pedido: { ...pedidoBase, cliente_id: 'u', estado: 'CONFIRMADO' },
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
    pedido: { ...pedidoBase, estado: 'CONFIRMADO' },
    pago: pagoOk,
    origenInternoBancario: true
  });
  assert.equal(interno.ok, true);
});

test('ONLINE CONFIRMADO no pasa a LISTO sin cobro', () => {
  const r = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'LISTO',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: { ...pedidoBase, estado: 'CONFIRMADO' },
    pago: { modalidad: 'ONLINE', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'LISTO_REQUIERE_PAGO');
});

test('cliente cancela RECIBIDO/EN_PICKING/CONFIRMADO; no en ruta', () => {
  const okRecibido = evaluarTransicion({
    desde: 'RECIBIDO',
    hacia: 'CANCELADO',
    rol: 'CLIENTE',
    usuarioId: 'cli',
    pedido: pedidoBase,
    pago: { modalidad: 'ONLINE', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(okRecibido.ok, true);

  const enCamino = evaluarTransicion({
    desde: 'EN_CAMINO',
    hacia: 'CANCELADO',
    rol: 'CLIENTE',
    usuarioId: 'cli',
    pedido: { ...pedidoBase, estado: 'EN_CAMINO' },
    pago: { modalidad: 'CONTRAENTREGA', estado: 'PENDIENTE' },
    origenInternoBancario: false
  });
  assert.equal(enCamino.ok, false);
  assert.equal(enCamino.codigo, 'CANCELACION_NO_PERMITIDA');
});

test('pago confirmado bloquea cancelación sin devolución', () => {
  const conPago = evaluarTransicion({
    desde: 'CONFIRMADO',
    hacia: 'CANCELADO',
    rol: 'ADMINISTRADOR',
    usuarioId: 'adm',
    pedido: { ...pedidoBase, estado: 'CONFIRMADO' },
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

  const conDevolucion = evaluarTransicion({
    desde: 'PAGADO',
    hacia: 'CANCELADO',
    rol: 'CLIENTE',
    usuarioId: 'cli',
    pedido: { ...pedidoBase, estado: 'PAGADO' },
    pago: {
      modalidad: 'ONLINE',
      estado: 'CONFIRMADO',
      numero_operacion_banco: 'OP-9',
      fecha_confirmacion: new Date().toISOString()
    },
    origenInternoBancario: false,
    devolucionLiquidada: true
  });
  assert.equal(conDevolucion.ok, true);
});

test('confirmar cobro no puede retroceder EN_CAMINO/LISTO a PAGADO', () => {
  const pagoOk = {
    modalidad: 'CONTRAENTREGA',
    estado: 'CONFIRMADO',
    numero_operacion_banco: 'OP-1',
    fecha_confirmacion: new Date().toISOString()
  };
  for (const desde of ['EN_PICKING', 'LISTO', 'EN_CAMINO']) {
    const r = evaluarTransicion({
      desde,
      hacia: 'PAGADO',
      rol: 'ADMINISTRADOR',
      usuarioId: 'adm',
      pedido: { ...pedidoBase, estado: desde },
      pago: pagoOk,
      origenInternoBancario: true
    });
    assert.equal(r.ok, false);
    assert.equal(r.codigo, 'TRANSICION_INVALIDA');
  }
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
