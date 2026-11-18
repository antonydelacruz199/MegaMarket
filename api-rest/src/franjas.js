/**
 * Control de cupo de franjas_entrega.
 * Ocupación = COUNT(pedidos WHERE franja_entrega_id = id AND estado <> 'CANCELADO').
 */

async function bloquearFranja(cliente, franjaId) {
  const { rows } = await cliente.query(
    `SELECT id, fecha, hora_inicio, hora_fin, capacidad, activo, created_at
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

function mapearFranja(fila) {
  const capacidad = Number(fila.capacidad);
  const ocupados = Number(fila.ocupados || 0);
  return {
    id: fila.id,
    fecha: fila.fecha,
    horaInicio: fila.hora_inicio,
    horaFin: fila.hora_fin,
    capacidad,
    ocupados,
    cupoRestante: Math.max(0, capacidad - ocupados),
    activa: Boolean(fila.activo),
    createdAt: fila.created_at
  };
}

module.exports = {
  bloquearFranja,
  contarPedidosActivosEnFranja,
  asegurarCupoFranja,
  mapearFranja
};
