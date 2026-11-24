/**
 * Control de cupo de franjas_entrega.
 * Ocupación = COUNT(pedidos WHERE franja_entrega_id = id AND estado <> 'CANCELADO').
 *
 * La ventana del cliente es hoy y mañana. Estos horarios se crean si faltan
 * y se reactivan si quedaron apagados, para que el checkout siempre pueda elegir.
 */
const HORARIOS_FRANJA = [
  ['08:00', '10:00'],
  ['10:00', '12:00'],
  ['12:00', '14:00'],
  ['14:00', '16:00'],
  ['16:00', '18:00'],
  ['18:00', '20:00']
];

async function asegurarFranjasVentana(db) {
  const valores = HORARIOS_FRANJA
    .map(([inicio, fin]) => `(TIME '${inicio}', TIME '${fin}')`)
    .join(',\n         ');
  await db.query(
    `INSERT INTO franjas_entrega (fecha, hora_inicio, hora_fin, capacidad, activo)
     SELECT dias.fecha, horarios.hora_inicio, horarios.hora_fin, 20, true
     FROM (
       SELECT CURRENT_DATE AS fecha
       UNION ALL
       SELECT CURRENT_DATE + 1
     ) AS dias
     CROSS JOIN (
       VALUES
         ${valores}
     ) AS horarios(hora_inicio, hora_fin)
     ON CONFLICT (fecha, hora_inicio, hora_fin)
     DO UPDATE SET activo = true
     WHERE franjas_entrega.activo = false`
  );
}

/** Hora de tienda. Las franjas se guardan en este reloj, no en UTC. */
const ZONA_HORARIA_TIENDA = 'America/Lima';

function sqlHorarioPasado(alias) {
  const prefijo = alias ? `${alias}.` : '';
  return `((${prefijo}fecha + ${prefijo}hora_fin) <= (CURRENT_TIMESTAMP AT TIME ZONE '${ZONA_HORARIA_TIENDA}'))`;
}

function partesEnTienda(instante) {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: ZONA_HORARIA_TIENDA,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(instante);
  const valor = {};
  for (const parte of partes) {
    if (parte.type !== 'literal') valor[parte.type] = parte.value;
  }
  return valor;
}

function fechaIso(valor) {
  if (valor instanceof Date && !Number.isNaN(valor.getTime())) {
    const mes = String(valor.getMonth() + 1).padStart(2, '0');
    const dia = String(valor.getDate()).padStart(2, '0');
    return `${valor.getFullYear()}-${mes}-${dia}`;
  }
  const texto = String(valor ?? '').trim();
  const base = texto.slice(0, 10);
  if (/^\d{4}-\d{2}-\d{2}$/.test(base)) return base;
  // Presentación Perú dd/MM/yyyy → ISO
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(base)) {
    const [dia, mes, anio] = base.split('/');
    return `${anio}-${mes}-${dia}`;
  }
  return null;
}

function horaIso(valor) {
  const texto = String(valor ?? '').trim();
  const base = texto.split('.')[0];
  const conSegundos = base.length === 5 ? `${base}:00` : base;
  return /^\d{2}:\d{2}:\d{2}$/.test(conSegundos.slice(0, 8)) ? conSegundos.slice(0, 8) : null;
}

/**
 * La franja ya no se puede elegir cuando la hora actual de tienda alcanzó su fin.
 * `ahora` se inyecta en pruebas; en producción es el reloj del proceso.
 */
function horarioYaTermino(fecha, horaFin, ahora = new Date()) {
  const dia = fechaIso(fecha);
  const fin = horaIso(horaFin);
  if (!dia || !fin) return false;
  const reloj = partesEnTienda(ahora);
  const actual = `${reloj.year}-${reloj.month}-${reloj.day}T${reloj.hour}:${reloj.minute}:${reloj.second}`;
  return actual >= `${dia}T${fin}`;
}

async function bloquearFranja(cliente, franjaId) {
  const { rows } = await cliente.query(
    `SELECT id, fecha, hora_inicio, hora_fin, capacidad, activo, created_at,
            ${sqlHorarioPasado()} AS horario_pasado
     FROM franjas_entrega
     WHERE id = $1
     FOR UPDATE`,
    [franjaId]
  );
  return rows[0] || null;
}

async function contarPedidosActivosEnFranja(cliente, franjaId) {
  const { rows } = await cliente.query(
    `SELECT COUNT(*)::int AS total
     FROM pedidos
     WHERE franja_entrega_id = $1
       AND estado <> 'CANCELADO'
       AND deleted_at IS NULL`,
    [franjaId]
  );
  return Number(rows[0].total);
}

/**
 * Debe llamarse dentro de una transacción abierta.
 * Lanza error con codigo FRANJA_* si no hay cupo o la franja no sirve.
 */
async function asegurarCupoFranja(cliente, franjaId, { exigirHoyManana = true } = {}) {
  const franja = await bloquearFranja(cliente, franjaId);
  if (!franja || !franja.activo) {
    const error = new Error('Franja no disponible');
    error.codigo = 'FRANJA_NO_DISPONIBLE';
    error.http = 404;
    throw error;
  }

  if (exigirHoyManana) {
    const { rows } = await cliente.query(
      `SELECT ($1::date = CURRENT_DATE OR $1::date = CURRENT_DATE + 1) AS valida`,
      [franja.fecha]
    );
    if (!rows[0].valida) {
      const error = new Error('Solo se admiten franjas de hoy o mañana');
      error.codigo = 'FRANJA_FUERA_VENTANA';
      error.http = 409;
      throw error;
    }
  }

  const horarioPasado = typeof franja.horario_pasado === 'boolean'
    ? franja.horario_pasado
    : horarioYaTermino(franja.fecha, franja.hora_fin);
  if (horarioPasado) {
    const error = new Error('Esa franja ya pasó. Elige un horario vigente.');
    error.codigo = 'FRANJA_HORARIO_PASADO';
    error.http = 409;
    throw error;
  }

  const ocupados = await contarPedidosActivosEnFranja(cliente, franjaId);
  const capacidad = Number(franja.capacidad);
  if (ocupados >= capacidad) {
    const error = new Error('La franja no tiene cupo disponible');
    error.codigo = 'FRANJA_LLENA';
    error.http = 409;
    throw error;
  }

  return {
    franja,
    ocupados,
    capacidad,
    cupoRestante: capacidad - ocupados
  };
}

/** Presentación Perú: dd/MM/yyyy. Internamente seguimos usando ISO para lógica. */
function fechaPresentacion(valor) {
  const iso = fechaIso(valor);
  if (!iso) return valor;
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

function mapearFranja(fila) {
  const capacidad = Number(fila.capacidad);
  const ocupados = Number(fila.ocupados || 0);
  const horarioPasado = fila.horario_pasado === undefined || fila.horario_pasado === null
    ? horarioYaTermino(fila.fecha, fila.hora_fin)
    : Boolean(fila.horario_pasado);
  return {
    id: fila.id,
    fecha: fechaPresentacion(fila.fecha),
    horaInicio: fila.hora_inicio,
    horaFin: fila.hora_fin,
    capacidad,
    ocupados,
    cupoRestante: Math.max(0, capacidad - ocupados),
    activa: Boolean(fila.activo),
    horarioPasado,
    createdAt: fila.created_at
  };
}

module.exports = {
  HORARIOS_FRANJA,
  ZONA_HORARIA_TIENDA,
  sqlHorarioPasado,
  horarioYaTermino,
  asegurarFranjasVentana,
  bloquearFranja,
  contarPedidosActivosEnFranja,
  asegurarCupoFranja,
  mapearFranja
};
