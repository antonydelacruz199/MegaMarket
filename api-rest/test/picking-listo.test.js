const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  lineasListasParaSalida,
  evaluarPreparacionCompleta,
  evaluarEliminacionLinea
} = require('../src/picking');
const { evaluarTransicion } = require('../src/pedidosEstados');

test('lineasListasParaSalida exige todas ARMADO/SUSTITUIDO/ELIMINADO', () => {
  assert.equal(lineasListasParaSalida([]), false);
  assert.equal(lineasListasParaSalida(['ARMADO']), true);
  assert.equal(lineasListasParaSalida(['ARMADO', 'SUSTITUIDO']), true);
  assert.equal(lineasListasParaSalida(['ARMADO', 'ELIMINADO']), true);
  assert.equal(lineasListasParaSalida(['ARMADO', 'PENDIENTE']), false);
  assert.equal(lineasListasParaSalida(['ARMADO', 'FALTANTE']), false);
});

test('preparación completa: pedido totalmente preparado', () => {
  const r = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ARMADO', cantidad: 2, cantidad_armada: 2 },
    { id: 'd2', estado_picking: 'SUSTITUIDO', cantidad: 1, cantidad_armada: 1 }
  ], [
    { id: 's1', pedido_detalle_id: 'd2', estado: 'ACEPTADA' }
  ]);
  assert.equal(r.ok, true);
});

test('preparación completa: bloquea PENDIENTE y FALTANTE', () => {
  const pend = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'PENDIENTE', cantidad: 1, cantidad_armada: 0 }
  ]);
  assert.equal(pend.ok, false);
  assert.equal(pend.codigo, 'PICKING_INCOMPLETO');
  assert.equal(pend.impedimentos[0].codigo, 'LINEA_PENDIENTE');

  const falt = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'FALTANTE', cantidad: 1, cantidad_armada: 0 }
  ]);
  assert.equal(falt.ok, false);
  assert.equal(falt.impedimentos[0].codigo, 'LINEA_FALTANTE');
});

test('preparación completa: cantidad incompleta en ARMADO', () => {
  const r = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ARMADO', cantidad: 3, cantidad_armada: 2 }
  ]);
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'CANTIDAD_INCOMPLETA');
});

test('preparación completa: sustitución PENDIENTE bloquea cierre', () => {
  const r = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'FALTANTE', cantidad: 1, cantidad_armada: 0 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'PENDIENTE' }
  ]);
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'SUSTITUCION_PENDIENTE');
});

test('preparación completa: sustituto ACEPTADA sin escanear', () => {
  const r = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'FALTANTE', cantidad: 2, cantidad_armada: 0 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'ACEPTADA' }
  ]);
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'SUSTITUTO_SIN_ESCANEAR');
});

test('preparación completa: sustituto escaneado completamente', () => {
  const r = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'SUSTITUIDO', cantidad: 2, cantidad_armada: 2 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'ACEPTADA' }
  ]);
  assert.equal(r.ok, true);
});

test('preparación completa: ELIMINADO solo con RECHAZADA', () => {
  const sin = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ELIMINADO', cantidad: 1, cantidad_armada: 0 }
  ]);
  assert.equal(sin.ok, false);
  assert.equal(sin.codigo, 'ELIMINADO_SIN_RESOLUCION');

  const ok = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ELIMINADO', cantidad: 1, cantidad_armada: 0 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'RECHAZADA' }
  ]);
  assert.equal(ok.ok, true);

  const conAceptada = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ELIMINADO', cantidad: 1, cantidad_armada: 0 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'RECHAZADA' },
    { id: 's2', pedido_detalle_id: 'd1', estado: 'ACEPTADA' }
  ]);
  assert.equal(conAceptada.ok, false);
  assert.equal(conAceptada.codigo, 'SUSTITUTO_SIN_ESCANEAR');

  const armada = evaluarPreparacionCompleta([
    { id: 'd1', estado_picking: 'ELIMINADO', cantidad: 1, cantidad_armada: 1 }
  ], [
    { id: 's1', pedido_detalle_id: 'd1', estado: 'RECHAZADA' }
  ]);
  assert.equal(armada.ok, false);
  assert.equal(armada.codigo, 'ELIMINADO_SIN_RESOLUCION');
});

test('eliminación: solo FALTANTE con rechazo y sin aceptación', () => {
  const base = { id: 'd1', estado_picking: 'FALTANTE', cantidad: 1, cantidad_armada: 0 };
  const sin = evaluarEliminacionLinea(base, []);
  assert.equal(sin.ok, false);
  assert.equal(sin.codigo, 'ELIMINADO_SIN_RESOLUCION');

  const pendiente = evaluarEliminacionLinea(base, [
    { id: 's1', estado: 'RECHAZADA', fecha_respuesta: '2026-11-17T10:00:00Z' },
    { id: 's2', estado: 'PENDIENTE', fecha_respuesta: null }
  ]);
  assert.equal(pendiente.ok, false);
  assert.equal(pendiente.codigo, 'SUSTITUCION_PENDIENTE');

  const aceptada = evaluarEliminacionLinea(base, [
    { id: 's1', estado: 'ACEPTADA', fecha_respuesta: '2026-11-17T10:00:00Z' }
  ]);
  assert.equal(aceptada.ok, false);
  assert.equal(aceptada.codigo, 'SUSTITUCION_ACEPTADA');

  const ok = evaluarEliminacionLinea(base, [
    { id: 's-vieja', estado: 'RECHAZADA', fecha_respuesta: '2026-11-17T09:00:00Z' },
    { id: 's-nueva', estado: 'RECHAZADA', fecha_respuesta: '2026-11-17T11:00:00Z' }
  ]);
  assert.equal(ok.ok, true);
  assert.equal(ok.yaResuelta, false);
  assert.equal(ok.sustitucionId, 's-nueva');

  const repetida = evaluarEliminacionLinea(
    { ...base, estado_picking: 'ELIMINADO' },
    [{ id: 's1', estado: 'RECHAZADA', fecha_respuesta: '2026-11-17T11:00:00Z' }]
  );
  assert.equal(repetida.ok, true);
  assert.equal(repetida.yaResuelta, true);
});

test('picker sin asignación no puede cerrar a LISTO', () => {
  const r = evaluarTransicion({
    desde: 'EN_PICKING',
    hacia: 'LISTO',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { id: 'p1', picker_id: null, cliente_id: 'c', repartidor_id: null },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(r.ok, false);
  assert.equal(r.codigo, 'PEDIDO_NO_ASIGNADO');
});

test('picker asignado puede intentar LISTO (validación de líneas aparte)', () => {
  const r = evaluarTransicion({
    desde: 'EN_PICKING',
    hacia: 'LISTO',
    rol: 'PICKER',
    usuarioId: 'pick-1',
    pedido: { id: 'p1', picker_id: 'pick-1', cliente_id: 'c', repartidor_id: null },
    pago: null,
    origenInternoBancario: false
  });
  assert.equal(r.ok, true);
});
