const MAX_BYTES = Number(process.env.MEDIA_MAX_BYTES) || 5 * 1024 * 1024;

const TIPOS = Object.freeze({
  jpeg: { mime: 'image/jpeg', ext: 'jpg' },
  png: { mime: 'image/png', ext: 'png' },
  webp: { mime: 'image/webp', ext: 'webp' }
});

function detectarTipo(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 3) return null;
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return TIPOS.jpeg;
  }
  if (
    buffer.length >= 8 &&
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47
  ) {
    return TIPOS.png;
  }
  if (
    buffer.length >= 12 &&
    buffer.toString('ascii', 0, 4) === 'RIFF' &&
    buffer.toString('ascii', 8, 12) === 'WEBP'
  ) {
    return TIPOS.webp;
  }
  return null;
}

function extensionDeclarada(nombreOriginal) {
  const nombre = String(nombreOriginal || '').toLowerCase();
  const m = nombre.match(/\.([a-z0-9]+)$/);
  if (!m) return null;
  if (m[1] === 'jpeg') return 'jpg';
  return m[1];
}

/**
 * Valida tamaño, magia MIME y coherencia con extensión declarada.
 */
function validarArchivoImagen(file) {
  if (!file || !file.buffer) {
    return {
      ok: false,
      http: 400,
      codigo: 'ARCHIVO_REQUERIDO',
      error: 'Debes enviar un archivo en el campo "archivo"'
    };
  }
  if (file.buffer.length === 0) {
    return {
      ok: false,
      http: 400,
      codigo: 'ARCHIVO_VACIO',
      error: 'El archivo está vacío'
    };
  }
  if (file.buffer.length > MAX_BYTES) {
    return {
      ok: false,
      http: 413,
      codigo: 'ARCHIVO_DEMASIADO_GRANDE',
      error: `El archivo supera el máximo de ${MAX_BYTES} bytes`
    };
  }

  const tipo = detectarTipo(file.buffer);
  if (!tipo) {
    return {
      ok: false,
      http: 400,
      codigo: 'MIME_INVALIDO',
      error: 'Solo se admiten JPEG, PNG o WEBP (contenido real)'
    };
  }

  const ext = extensionDeclarada(file.originalname);
  if (ext && ext !== tipo.ext && !(ext === 'jpeg' && tipo.ext === 'jpg')) {
    return {
      ok: false,
      http: 400,
      codigo: 'EXTENSION_INCOHERENTE',
      error: `La extensión .${ext} no coincide con el contenido ${tipo.mime}`
    };
  }

  const mimeHeader = String(file.mimetype || '').toLowerCase();
  if (
    mimeHeader &&
    mimeHeader !== 'application/octet-stream' &&
    mimeHeader !== tipo.mime &&
    !(mimeHeader === 'image/jpg' && tipo.mime === 'image/jpeg')
  ) {
    return {
      ok: false,
      http: 400,
      codigo: 'MIME_HEADER_INCOHERENTE',
      error: 'Content-Type no coincide con el contenido del archivo'
    };
  }

  return {
    ok: true,
    mime: tipo.mime,
    extension: tipo.ext,
    bytes: file.buffer.length
  };
}

module.exports = {
  MAX_BYTES,
  detectarTipo,
  validarArchivoImagen
};
