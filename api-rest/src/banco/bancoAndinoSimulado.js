/**
 * Adaptador de transferencia Banco Andino (entorno de desarrollo / pruebas).
 */

const crypto = require('node:crypto');

const RESULTADOS = new Set(['CONFIRMADA', 'RECHAZADA', 'PENDIENTE', 'DESCONOCIDA']);
const ADAPTADOR = 'BANCO_ANDINO_SIMULADO';

/**
 * @param {import('./contratoTransferencia').SolicitudTransferencia} solicitud
 * @returns {Promise<import('./contratoTransferencia').RespuestaTransferencia>}
 */
async function transferir(solicitud) {
  const forzado = String(
    solicitud.forzarResultado || process.env.BANCO_ANDINO_MODO || 'CONFIRMADA'
  )
    .trim()
    .toUpperCase();

  const estado = RESULTADOS.has(forzado) ? forzado : 'CONFIRMADA';
  const corto = String(solicitud.uuidOperacion || '')
    .replace(/-/g, '')
    .slice(0, 12)
    .toUpperCase();
  const aleatorio = crypto.randomBytes(3).toString('hex').toUpperCase();

  if (estado === 'CONFIRMADA') {
    return {
      estado: 'CONFIRMADA',
      referenciaBanco: `SIM-REF-${corto}-${aleatorio}`,
      numeroOperacionBanco: `SIM-OP-${corto}${aleatorio}`,
      mensaje: 'Transferencia confirmada.',
      simulacion: true,
      adaptador: ADAPTADOR
    };
  }

  if (estado === 'RECHAZADA') {
    return {
      estado: 'RECHAZADA',
      referenciaBanco: `SIM-REF-${corto}-${aleatorio}`,
      numeroOperacionBanco: null,
      mensaje: 'Transferencia rechazada (fondos o datos inválidos).',
      simulacion: true,
      adaptador: ADAPTADOR
    };
  }

  if (estado === 'PENDIENTE') {
    return {
      estado: 'PENDIENTE',
      referenciaBanco: `SIM-REF-${corto}-${aleatorio}`,
      numeroOperacionBanco: null,
      mensaje: 'Transferencia pendiente de confirmación. No reintentar automáticamente.',
      simulacion: true,
      adaptador: ADAPTADOR
    };
  }

  return {
    estado: 'DESCONOCIDA',
    referenciaBanco: `SIM-REF-${corto}-${aleatorio}`,
    numeroOperacionBanco: null,
    mensaje:
      'Resultado desconocido (timeout o respuesta ambigua). No emitir segunda transferencia automática.',
    simulacion: true,
    adaptador: ADAPTADOR
  };
}

module.exports = {
  nombre: ADAPTADOR,
  transferir,
  RESULTADOS
};
