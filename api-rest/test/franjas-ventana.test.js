const { test } = require('node:test');
const assert = require('node:assert/strict');
const { HORARIOS_FRANJA, asegurarFranjasVentana, horarioYaTermino, mapearFranja } = require('../src/franjas');

test('la ventana de entrega conserva los seis horarios de dos horas', () => {
  assert.deepEqual(HORARIOS_FRANJA, [
    ['08:00', '10:00'],
    ['10:00', '12:00'],
    ['12:00', '14:00'],
    ['14:00', '16:00'],
    ['16:00', '18:00'],
    ['18:00', '20:00']
  ]);
});

test('asegurarFranjasVentana crea hoy y mañana y reactiva las apagadas', async () => {
  const consultas = [];
  const db = {
    async query(sql) {
      consultas.push(sql);
      return { rows: [] };
    }
  };

  await asegurarFranjasVentana(db);

  assert.equal(consultas.length, 1);
  const sql = consultas[0];
  assert.match(sql, /CURRENT_DATE \+ 1/);
  assert.match(sql, /ON CONFLICT \(fecha, hora_inicio, hora_fin\)/);
  assert.match(sql, /SET activo = true/);
  for (const [inicio, fin] of HORARIOS_FRANJA) {
    assert.match(sql, new RegExp(`TIME '${inicio}', TIME '${fin}'`));
  }
});

test('una franja se bloquea al llegar su hora de fin en Lima', () => {
  const ahora = new Date('2026-11-22T16:02:00.000Z');
  assert.equal(horarioYaTermino('2026-11-22', '10:00:00', ahora), true);
  assert.equal(horarioYaTermino('2026-11-22', '12:00:00', ahora), false);
  assert.equal(horarioYaTermino('2026-11-23', '08:00:00', ahora), false);

  const pasada = mapearFranja({
    id: 'f1',
    fecha: '2026-11-22',
    hora_inicio: '08:00:00',
    hora_fin: '10:00:00',
    capacidad: 20,
    ocupados: 0,
    activo: true,
    horario_pasado: true
  });
  assert.equal(pasada.horarioPasado, true);
  assert.equal(pasada.fecha, '22/11/2026');

  const vigente = mapearFranja({
    id: 'f2',
    fecha: '2026-11-22',
    hora_inicio: '10:00:00',
    hora_fin: '12:00:00',
    capacidad: 20,
    ocupados: 0,
    activo: true,
    horario_pasado: false
  });
  assert.equal(vigente.horarioPasado, false);
  assert.equal(vigente.fecha, '22/11/2026');
});
