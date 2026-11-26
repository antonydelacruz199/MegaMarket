/**
 * Contrato de transferencia a proveedores.
 * Una implementación real de Banco Andino debería cumplir esta interfaz.
 * Adaptador de transferencia bancaria (desarrollo / pruebas).
 */

/**
 * @typedef {'CONFIRMADA'|'RECHAZADA'|'PENDIENTE'|'DESCONOCIDA'} ResultadoTransferencia
 */

/**
 * @typedef {object} SolicitudTransferencia
 * @property {string} uuidOperacion
 * @property {string} ordenCompraId
 * @property {string} numeroOrden
 * @property {number} montoCentimos
 * @property {string} rucProveedor
 * @property {string} razonSocial
 * @property {string} cuentaDestino
 * @property {string} [forzarResultado] Solo para forzar resultado en pruebas.
 */

/**
 * @typedef {object} RespuestaTransferencia
 * @property {ResultadoTransferencia} estado
 * @property {string|null} referenciaBanco
 * @property {string|null} numeroOperacionBanco
 * @property {string} mensaje
 * @property {boolean} simulacion
 * @property {string} adaptador
 */

/**
 * @interface AdaptadorTransferencia
 * @property {string} nombre
 * @property {(solicitud: SolicitudTransferencia) => Promise<RespuestaTransferencia>} transferir
 */

module.exports = {};
