/**
 * Utilidades de pago QR simulado (sin banco real).
 * Las referencias SIM-* son ficticias y académicas.
 */

const crypto = require('node:crypto');

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

function mapearPago(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    pedidoId: fila.pedido_id,
    montoCentimos: Number(fila.monto_centimos),
    moneda: fila.moneda,
    medioPago: fila.medio_pago,
    modalidad: fila.modalidad,
    estado: fila.estado,
    referenciaBanco: fila.referencia_banco || null,
    numeroOperacionBanco: fila.numero_operacion_banco || null,
    qrContenido: fila.qr_contenido || null,
    fechaExpiracionQr: fila.fecha_expiracion_qr || null,
    fechaConfirmacion: fila.fecha_confirmacion || null,
    simulacion: true,
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearAjuste(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    pagoId: fila.pago_id,
    sustitucionId: fila.sustitucion_id || null,
    tipo: fila.tipo,
    motivo: fila.motivo,
    montoCentimos: Number(fila.monto_centimos),
    estado: fila.estado,
    referenciaBanco: fila.referencia_banco || null,
    numeroOperacionBanco: fila.numero_operacion_banco || null,
    fechaConfirmacion: fila.fecha_confirmacion || null,
    observaciones: fila.observaciones || null,
    simulacion: true,
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function construirQrContenido({ pago, pedidoId, referencia, generadoEn }) {
  return [
    'MEGAMARKET-SIMULACION',
    'v=1',
    `pedido=${pedidoId}`,
    `pago=${pago.id}`,
    `ref=${referencia}`,
    `monto_centimos=${pago.monto_centimos}`,
    `moneda=${pago.moneda || 'PEN'}`,
    `modalidad=${pago.modalidad}`,
    `generado=${generadoEn}`,
    'estado=PENDIENTE',
    'aviso=NO ES UN PAGO BANCARIO REAL'
  ].join('\n');
}

function nuevaReferenciaSimulada(pagoId) {
  const corto = String(pagoId).replace(/-/g, '').slice(0, 12).toUpperCase();
  const aleatorio = crypto.randomBytes(4).toString('hex').toUpperCase();
  return `SIM-REF-${corto}-${aleatorio}`;
}

function nuevoNumeroOperacionSimulado(uuidOperacion) {
  const corto = String(uuidOperacion).replace(/-/g, '').slice(0, 16).toUpperCase();
  return `SIM-OP-${corto}`;
}

module.exports = {
  esUuid,
  mapearPago,
  mapearAjuste,
  construirQrContenido,
  nuevaReferenciaSimulada,
  nuevoNumeroOperacionSimulado
};
