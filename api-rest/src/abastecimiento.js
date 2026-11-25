/**
 * Utilidades compartidas del módulo de abastecimiento (Fase 7).
 */

const crypto = require('node:crypto');

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const ESTADOS_ORDEN = new Set([
  'SUGERIDA',
  'APROBADA',
  'PAGADA',
  'RECIBIDA',
  'CANCELADA'
]);

/** Transiciones autorizadas. */
const TRANSICIONES = {
  SUGERIDA: new Set(['APROBADA', 'CANCELADA']),
  APROBADA: new Set(['PAGADA', 'CANCELADA']),
  PAGADA: new Set(['RECIBIDA']),
  RECIBIDA: new Set(),
  CANCELADA: new Set()
};

const CORREO_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

function valor(body, camel, snake, defecto) {
  if (body && body[camel] !== undefined) return body[camel];
  if (body && body[snake] !== undefined) return body[snake];
  return defecto;
}

function hashSolicitud(partes) {
  return crypto.createHash('sha256').update(JSON.stringify(partes)).digest('hex');
}

/**
 * UUID determinista (v5-like) a partir de un padre + clave.
 * Sirve para movimientos de recepción idempotentes por línea.
 */
function uuidDerivado(uuidPadre, clave) {
  const digest = crypto
    .createHash('sha256')
    .update(`megamarket:${uuidPadre}:${clave}`)
    .digest();
  const bytes = Buffer.from(digest.subarray(0, 16));
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

function puedeTransicionar(desde, hacia) {
  const origen = String(desde || '').toUpperCase();
  const destino = String(hacia || '').toUpperCase();
  if (!ESTADOS_ORDEN.has(origen) || !ESTADOS_ORDEN.has(destino)) return false;
  return TRANSICIONES[origen].has(destino);
}

function mapearProveedor(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    ruc: fila.ruc,
    razonSocial: fila.razon_social,
    telefono: fila.telefono || null,
    correo: fila.correo || null,
    cuentaBancaria: fila.cuenta_bancaria || null,
    activo: Boolean(fila.activo),
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function mapearDetalle(fila) {
  if (!fila) return null;
  const cantidad = Number(fila.cantidad);
  const recibida = Number(fila.cantidad_recibida || 0);
  return {
    id: fila.id,
    ordenCompraId: fila.orden_compra_id,
    productoId: fila.producto_id,
    nombreProducto: fila.nombre_producto || fila.producto_nombre || null,
    cantidad,
    precioUnitarioCentimos: Number(fila.precio_unitario_centimos),
    subtotalCentimos: Number(fila.subtotal_centimos),
    cantidadRecibida: recibida,
    cantidadPendiente: Math.max(0, cantidad - recibida)
  };
}

function mapearOrden(fila, extras = {}) {
  if (!fila) return null;
  return {
    id: fila.id,
    proveedorId: fila.proveedor_id,
    administradorId: fila.administrador_id || null,
    numeroOrden: fila.numero_orden,
    estado: fila.estado,
    totalCentimos: Number(fila.total_centimos),
    numeroOperacionBanco: fila.numero_operacion_banco || null,
    fechaAprobacion: fila.fecha_aprobacion || null,
    fechaPago: fila.fecha_pago || null,
    fechaRecepcion: fila.fecha_recepcion || null,
    createdAt: fila.created_at,
    updatedAt: fila.updated_at,
    proveedor: extras.proveedor || undefined,
    detalles: extras.detalles || undefined,
    transferencia: extras.transferencia || undefined
  };
}

function mapearTransferencia(fila) {
  if (!fila) return null;
  return {
    id: fila.id,
    uuidOperacion: fila.uuid_operacion,
    ordenCompraId: fila.orden_compra_id,
    administradorId: fila.administrador_id,
    montoCentimos: Number(fila.monto_centimos),
    cuentaDestino: fila.cuenta_destino,
    estado: fila.estado,
    referenciaBanco: fila.referencia_banco || null,
    numeroOperacionBanco: fila.numero_operacion_banco || null,
    mensaje: fila.mensaje || null,
    simulacion: Boolean(fila.simulacion),
    adaptador: fila.adaptador || 'BANCO_ANDINO_SIMULADO',
    fechaSolicitud: fila.fecha_solicitud,
    fechaRespuesta: fila.fecha_respuesta || null,
    createdAt: fila.created_at,
    updatedAt: fila.updated_at
  };
}

function validarRuc(ruc) {
  const valorRuc = String(ruc || '').trim();
  if (!/^\d{11}$/.test(valorRuc)) {
    return { ok: false, error: 'El RUC debe tener exactamente 11 dígitos numéricos' };
  }
  return { ok: true, ruc: valorRuc };
}

function validarCorreo(correo) {
  if (correo === null || correo === undefined || String(correo).trim() === '') {
    return { ok: true, correo: null };
  }
  const limpio = String(correo).trim();
  if (!CORREO_RE.test(limpio)) {
    return { ok: false, error: 'El correo no tiene un formato válido' };
  }
  return { ok: true, correo: limpio };
}

async function buscarOperacionIdempotente(cliente, uuidOperacion) {
  if (!uuidOperacion) return null;
  const consulta = await cliente.query(
    `SELECT codigo_http, respuesta, tipo_operacion, entidad_id
     FROM operaciones_idempotentes
     WHERE uuid_operacion = $1
     LIMIT 1`,
    [uuidOperacion]
  );
  return consulta.rows[0] || null;
}

async function registrarOperacionIdempotente(
  cliente,
  { uuidOperacion, usuarioId, tipoOperacion, recurso, entidadId, codigoHttp, respuesta }
) {
  await cliente.query(
    `INSERT INTO operaciones_idempotentes (
       uuid_operacion, usuario_id, tipo_operacion, recurso, entidad_id,
       estado, codigo_http, respuesta
     ) VALUES ($1, $2, $3, $4, $5, 'PROCESADA', $6, $7::jsonb)
     ON CONFLICT (uuid_operacion) DO NOTHING`,
    [
      uuidOperacion,
      usuarioId,
      tipoOperacion,
      recurso,
      entidadId || null,
      codigoHttp,
      JSON.stringify(respuesta)
    ]
  );
}

/**
 * Genera número de orden único con secuencia + reintento ante colisión.
 */
async function generarNumeroOrden(cliente) {
  for (let intento = 0; intento < 5; intento += 1) {
    let n;
    try {
      const seq = await cliente.query(`SELECT nextval('seq_numero_orden_compra') AS n`);
      n = Number(seq.rows[0].n);
    } catch (_error) {
      // Fallback si 007 aún no está aplicada: candado advisory + conteo.
      await cliente.query(`SELECT pg_advisory_xact_lock(872314001)`);
      const contador = await cliente.query(
        `SELECT COUNT(*)::bigint AS n FROM ordenes_compra`
      );
      n = Number(contador.rows[0].n) + 1 + intento;
    }
    const numero = `OC-${String(n).padStart(8, '0')}`;
    const existe = await cliente.query(
      `SELECT 1 FROM ordenes_compra WHERE numero_orden = $1 LIMIT 1`,
      [numero]
    );
    if (!existe.rows[0]) return numero;
  }
  throw new Error('No se pudo generar un número de orden único');
}

async function cargarOrdenCompleta(cliente, ordenId, { forUpdate = false } = {}) {
  const bloqueo = forUpdate ? 'FOR UPDATE' : '';
  const ordenRes = await cliente.query(
    `SELECT * FROM ordenes_compra WHERE id = $1 ${bloqueo}`,
    [ordenId]
  );
  const orden = ordenRes.rows[0];
  if (!orden) return null;

  const proveedorRes = await cliente.query(
    `SELECT * FROM proveedores WHERE id = $1`,
    [orden.proveedor_id]
  );
  const detallesRes = await cliente.query(
    `SELECT d.*, p.nombre AS nombre_producto
     FROM orden_compra_detalle d
     JOIN productos p ON p.id = d.producto_id
     WHERE d.orden_compra_id = $1
     ORDER BY d.id ASC`,
    [orden.id]
  );
  const transferenciaRes = await cliente.query(
    `SELECT * FROM transferencias_proveedor
     WHERE orden_compra_id = $1
     ORDER BY created_at DESC
     LIMIT 1`,
    [orden.id]
  ).catch(() => ({ rows: [] }));

  return {
    orden,
    proveedor: proveedorRes.rows[0] || null,
    detalles: detallesRes.rows,
    transferencia: transferenciaRes.rows[0] || null
  };
}

function respuestaOrdenCompleta(paquete) {
  return {
    orden: mapearOrden(paquete.orden, {
      proveedor: mapearProveedor(paquete.proveedor),
      detalles: paquete.detalles.map(mapearDetalle),
      transferencia: mapearTransferencia(paquete.transferencia)
    })
  };
}

module.exports = {
  esUuid,
  valor,
  hashSolicitud,
  uuidDerivado,
  puedeTransicionar,
  ESTADOS_ORDEN,
  TRANSICIONES,
  mapearProveedor,
  mapearDetalle,
  mapearOrden,
  mapearTransferencia,
  validarRuc,
  validarCorreo,
  buscarOperacionIdempotente,
  registrarOperacionIdempotente,
  generarNumeroOrden,
  cargarOrdenCompleta,
  respuestaOrdenCompleta
};
