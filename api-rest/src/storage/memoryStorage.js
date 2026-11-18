/**
 * Almacén en memoria para pruebas / desarrollo sin bucket.
 * No sube a producción.
 */

const objetos = new Map();

async function subir({ clave, buffer, contentType }) {
  objetos.set(clave, {
    buffer: Buffer.from(buffer),
    contentType,
    updatedAt: new Date().toISOString()
  });
  return { clave };
}

async function existe(clave) {
  return objetos.has(clave);
}

async function obtener(clave) {
  const obj = objetos.get(clave);
  if (!obj) return null;
  return { buffer: Buffer.from(obj.buffer), contentType: obj.contentType };
}

async function urlFirmada(clave, { ttlSeconds = 900 } = {}) {
  if (!objetos.has(clave)) {
    const error = new Error('Objeto no encontrado');
    error.codigo = 'OBJETO_NO_ENCONTRADO';
    throw error;
  }
  const expira = Date.now() + ttlSeconds * 1000;
  // URL sintética; la API sirve el binario por proxy autenticado.
  return {
    url: `memory://megamarket/${encodeURIComponent(clave)}?exp=${expira}`,
    expiraEn: new Date(expira).toISOString(),
    proxy: true
  };
}

function _reset() {
  objetos.clear();
}

function _keys() {
  return [...objetos.keys()];
}

module.exports = {
  name: 'memory',
  subir,
  existe,
  obtener,
  urlFirmada,
  _reset,
  _keys
};
