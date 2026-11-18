/**
 * Backend de almacenamiento de medios (sin Firebase).
 * MEDIA_STORAGE=memory (pruebas y omisión) o disk (disco local, MEDIA_ROOT).
 */

let instancia = null;

function crearStorage() {
  const modo = String(process.env.MEDIA_STORAGE || 'memory').trim().toLowerCase();
  if (modo === 'memory') {
    // eslint-disable-next-line global-require
    return require('./memoryStorage');
  }
  if (modo === 'disk') {
    // eslint-disable-next-line global-require
    return require('./diskStorage').crearDiskStorage();
  }
  const error = new Error(`MEDIA_STORAGE no soportado: ${modo}`);
  error.codigo = 'STORAGE_CONFIG';
  throw error;
}

function getStorage() {
  if (!instancia) instancia = crearStorage();
  return instancia;
}

/** Solo pruebas: fuerza un backend concreto. */
function setStorageForTests(storage) {
  instancia = storage;
}

function resetStorageForTests() {
  instancia = null;
}

module.exports = {
  getStorage,
  setStorageForTests,
  resetStorageForTests
};
