/**
 * Almacén persistente en disco local, sin Firebase y sin publicar la carpeta por HTTP.
 * Las claves son relativas: productos/{id}/{uuid}.ext y entregas/{id}/{uuid}.ext.
 * subir() no borra imágenes anteriores.
 */

const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { esClaveRemotaProducto, esClaveRemotaEntrega } = require('./keys');

const API_ROOT = path.resolve(__dirname, '..', '..');
const MIME_POR_EXTENSION = Object.freeze({
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp'
});

function errorAlmacen(mensaje, codigo) {
  const error = new Error(mensaje);
  error.codigo = codigo;
  return error;
}

function envolverDisco(causa, accion) {
  const error = new Error(`No se pudo ${accion} la imagen en disco`);
  error.codigo = 'STORAGE_ERROR';
  error.causaCodigo = causa && causa.code ? causa.code : undefined;
  return error;
}

function rutaContenida(raiz, candidato) {
  const base = path.resolve(raiz);
  const destino = path.resolve(candidato);
  const relativo = path.relative(base, destino);
  if (!relativo || path.isAbsolute(relativo)) return false;
  return !relativo.split(path.sep).includes('..');
}

function normalizarClave(clave) {
  if (typeof clave !== 'string') return '';
  if (clave.includes('\0') || clave.includes('\\') || clave.includes('..')) return '';
  const limpia = clave.trim();
  if (!esClaveRemotaProducto(limpia) && !esClaveRemotaEntrega(limpia)) return '';
  return limpia;
}

function mimeDeClave(clave) {
  const coincidencia = String(clave).toLowerCase().match(/\.(jpeg|jpg|png|webp)$/);
  if (!coincidencia) return null;
  return MIME_POR_EXTENSION[coincidencia[1]];
}

function crearDiskStorage(opciones = {}) {
  const io = opciones.io || fsp;
  const indicado = Object.prototype.hasOwnProperty.call(opciones, 'root')
    ? opciones.root
    : process.env.MEDIA_ROOT;
  if (indicado === undefined || indicado === null || !String(indicado).trim()) {
    throw errorAlmacen(
      'MEDIA_ROOT es obligatorio cuando el almacén es disco',
      'STORAGE_CONFIG'
    );
  }

  const root = path.resolve(API_ROOT, String(indicado).trim());
  if (path.resolve(root) === path.resolve(path.parse(root).root)) {
    throw errorAlmacen('MEDIA_ROOT no puede ser la raíz del disco', 'STORAGE_CONFIG');
  }

  const maxConfigurado = Number(
    opciones.maxBytes !== undefined ? opciones.maxBytes : process.env.MEDIA_MAX_BYTES
  );
  const maxBytes = Number.isFinite(maxConfigurado) && maxConfigurado > 0
    ? maxConfigurado
    : 5 * 1024 * 1024;

  function resolverDestino(clave) {
    const limpia = normalizarClave(clave);
    if (!limpia) {
      throw errorAlmacen('Referencia de imagen inválida', 'CLAVE_INVALIDA');
    }
    const destino = path.resolve(root, ...limpia.split('/'));
    if (!rutaContenida(root, destino)) {
      throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
    }
    return { clave: limpia, destino };
  }

  async function asegurarRaiz() {
    try {
      await io.mkdir(root, { recursive: true });
    } catch (error) {
      if (error.codigo) throw error;
      throw envolverDisco(error, 'preparar la carpeta');
    }
    let estado;
    try {
      estado = await io.lstat(root);
    } catch (error) {
      if (error.codigo) throw error;
      throw envolverDisco(error, 'preparar la carpeta');
    }
    if (estado.isSymbolicLink() || !estado.isDirectory()) {
      throw errorAlmacen('MEDIA_ROOT no es un directorio real', 'STORAGE_CONFIG');
    }
    try {
      return await io.realpath(root);
    } catch (error) {
      if (error.codigo) throw error;
      throw envolverDisco(error, 'preparar la carpeta');
    }
  }

  async function asegurarDirectorio(dir) {
    const realRoot = await asegurarRaiz();
    if (!rutaContenida(root, dir)) {
      throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
    }

    let actual = root;
    for (const parte of path.relative(root, dir).split(path.sep)) {
      actual = path.join(actual, parte);
      let estado;
      try {
        estado = await io.lstat(actual);
      } catch (error) {
        if (error.codigo) throw error;
        if (error.code !== 'ENOENT') throw envolverDisco(error, 'crear la carpeta');
        try {
          await io.mkdir(actual);
        } catch (mkdirError) {
          if (mkdirError.codigo) throw mkdirError;
          if (mkdirError.code !== 'EEXIST') throw envolverDisco(mkdirError, 'crear la carpeta');
        }
        try {
          estado = await io.lstat(actual);
        } catch (statError) {
          if (statError.codigo) throw statError;
          throw envolverDisco(statError, 'crear la carpeta');
        }
      }
      if (estado.isSymbolicLink() || !estado.isDirectory()) {
        throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
      }
      let realActual;
      try {
        realActual = await io.realpath(actual);
      } catch (error) {
        if (error.codigo) throw error;
        throw envolverDisco(error, 'comprobar la carpeta');
      }
      if (!rutaContenida(realRoot, realActual)) {
        throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
      }
    }
  }

  async function limpiarParciales(dir) {
    let entradas;
    try {
      entradas = await io.readdir(dir, { withFileTypes: true });
    } catch (error) {
      if (error.code === 'ENOENT' || error.code === 'ENOTDIR') return;
      throw envolverDisco(error, 'revisar archivos temporales');
    }
    for (const entrada of entradas) {
      if (entrada.isSymbolicLink()) continue;
      const ruta = path.join(dir, entrada.name);
      if (entrada.isDirectory()) {
        await limpiarParciales(ruta);
      } else if (
        entrada.isFile() &&
        entrada.name.startsWith('.') &&
        entrada.name.endsWith('.partial')
      ) {
        await io.unlink(ruta).catch(() => {});
      }
    }
  }

  async function preparar() {
    await asegurarRaiz();
    await limpiarParciales(root);
  }

  async function publicar(tmp, destino) {
    try {
      await io.link(tmp, destino);
    } catch (error) {
      if (error.codigo) throw error;
      if (error.code === 'EEXIST') {
        throw errorAlmacen('La imagen ya existe', 'OBJETO_EXISTENTE');
      }
      try {
        await io.lstat(destino);
        throw errorAlmacen('La imagen ya existe', 'OBJETO_EXISTENTE');
      } catch (existe) {
        if (existe.codigo) throw existe;
        if (existe.code !== 'ENOENT') throw envolverDisco(existe, 'guardar');
      }
      try {
        await io.rename(tmp, destino);
        return;
      } catch (renombrado) {
        if (renombrado.code === 'EEXIST' || renombrado.code === 'EPERM') {
          try {
            await io.lstat(destino);
            throw errorAlmacen('La imagen ya existe', 'OBJETO_EXISTENTE');
          } catch (existe) {
            if (existe.codigo) throw existe;
            if (existe.code !== 'ENOENT') throw envolverDisco(existe, 'guardar');
          }
        }
        throw envolverDisco(renombrado, 'guardar');
      }
    }
    await io.unlink(tmp).catch(() => {});
  }

  async function subir({ clave, buffer, contentType }) {
    if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
      throw errorAlmacen('El archivo de imagen no es válido', 'ARCHIVO_INVALIDO');
    }
    if (buffer.length > maxBytes) {
      throw errorAlmacen('El archivo supera el máximo permitido', 'ARCHIVO_DEMASIADO_GRANDE');
    }

    const { clave: limpia, destino } = resolverDestino(clave);
    const mime = mimeDeClave(limpia);
    const declarado = String(contentType || '').split(';')[0].trim().toLowerCase();
    if (!declarado || declarado !== mime) {
      throw errorAlmacen('Content-Type no coincide con la clave', 'MIME_INVALIDO');
    }

    await asegurarDirectorio(path.dirname(destino));
    try {
      await io.lstat(destino);
      throw errorAlmacen('La imagen ya existe', 'OBJETO_EXISTENTE');
    } catch (error) {
      if (error.codigo) throw error;
      if (error.code !== 'ENOENT') throw envolverDisco(error, 'comprobar');
    }

    const tmp = path.join(
      path.dirname(destino),
      `.${path.basename(destino)}.${crypto.randomUUID()}.partial`
    );
    let publicado = false;
    try {
      const handle = await io.open(tmp, 'wx', 0o600);
      try {
        await handle.writeFile(buffer);
        await handle.sync();
      } finally {
        await handle.close();
      }
      await publicar(tmp, destino);
      publicado = true;
      return { clave: limpia };
    } catch (error) {
      if (!publicado) await io.unlink(tmp).catch(() => {});
      if (error.codigo) throw error;
      throw envolverDisco(error, 'guardar');
    }
  }

  async function existe(clave) {
    const { destino } = resolverDestino(clave);
    try {
      const estado = await io.lstat(destino);
      if (estado.isSymbolicLink()) {
        throw errorAlmacen('Referencia de imagen inválida', 'CLAVE_INVALIDA');
      }
      if (!estado.isFile()) return false;
      const realRoot = await io.realpath(root);
      const realDestino = await io.realpath(destino);
      if (!rutaContenida(realRoot, realDestino)) {
        throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
      }
      return true;
    } catch (error) {
      if (error.codigo) throw error;
      if (error.code === 'ENOENT') return false;
      throw envolverDisco(error, 'comprobar');
    }
  }

  async function obtener(clave) {
    const { clave: limpia, destino } = resolverDestino(clave);
    try {
      const estado = await io.lstat(destino);
      if (estado.isSymbolicLink() || !estado.isFile()) {
        throw errorAlmacen('Referencia de imagen inválida', 'CLAVE_INVALIDA');
      }
      const realRoot = await io.realpath(root);
      const realDestino = await io.realpath(destino);
      if (!rutaContenida(realRoot, realDestino)) {
        throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
      }
      const buffer = await io.readFile(realDestino);
      return { buffer, contentType: mimeDeClave(limpia) };
    } catch (error) {
      if (error.codigo) throw error;
      if (error.code === 'ENOENT') return null;
      throw envolverDisco(error, 'leer');
    }
  }

  async function urlFirmada(clave, { ttlSeconds = 900 } = {}) {
    const { clave: limpia } = resolverDestino(clave);
    if (!(await existe(limpia))) {
      throw errorAlmacen('Objeto no encontrado', 'OBJETO_NO_ENCONTRADO');
    }
    const ttl = Number(ttlSeconds) > 0 ? Number(ttlSeconds) : 900;
    const expira = Date.now() + ttl * 1000;
    return {
      url: `disk://megamarket/${encodeURIComponent(limpia)}?exp=${expira}`,
      expiraEn: new Date(expira).toISOString(),
      proxy: true
    };
  }

  /**
   * Borra solo el archivo de esa clave, dentro de MEDIA_ROOT.
   * No lo usa subir(): la imagen anterior se conserva hasta una eliminación explícita.
   */
  async function eliminar(clave) {
    const { clave: limpia, destino } = resolverDestino(clave);
    try {
      const estado = await io.lstat(destino);
      if (estado.isSymbolicLink() || !estado.isFile()) {
        throw errorAlmacen('Referencia de imagen inválida', 'CLAVE_INVALIDA');
      }
      const realRoot = await io.realpath(root);
      const realDestino = await io.realpath(destino);
      if (!rutaContenida(realRoot, realDestino)) {
        throw errorAlmacen('La referencia sale de MEDIA_ROOT', 'PATH_TRAVERSAL');
      }
      await io.unlink(realDestino);
      return { clave: limpia, eliminado: true };
    } catch (error) {
      if (error.codigo) throw error;
      if (error.code === 'ENOENT') {
        throw errorAlmacen('Objeto no encontrado', 'OBJETO_NO_ENCONTRADO');
      }
      throw envolverDisco(error, 'eliminar');
    }
  }

  return {
    name: 'disk',
    preparar,
    subir,
    existe,
    obtener,
    urlFirmada,
    eliminar
  };
}

module.exports = {
  crearDiskStorage,
  rutaContenida
};
