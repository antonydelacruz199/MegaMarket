/**
 * Multipart (multer) si está instalado; si no, body binario image/*.
 * Tests pueden inyectar req.file directamente.
 */

const { MAX_BYTES } = require('../storage/validateImage');

function extensionDesdeMime(mime) {
  if (mime === 'image/png') return 'png';
  if (mime === 'image/webp') return 'webp';
  return 'jpg';
}

function leerCuerpoBinario(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > MAX_BYTES + 1024) {
        reject(Object.assign(new Error('too large'), { codigo: 'ARCHIVO_DEMASIADO_GRANDE' }));
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function uploadImagen(req, res, next) {
  if (req.file && req.file.buffer) {
    return next();
  }

  const contentType = String(req.headers['content-type'] || '');
  if (contentType.startsWith('multipart/form-data')) {
    let multer;
    try {
      // eslint-disable-next-line global-require
      multer = require('multer');
    } catch (_e) {
      return res.status(500).json({
        error: 'Dependencia multer no instalada. Ejecuta npm install en api-rest/',
        codigo: 'MULTER_AUSENTE'
      });
    }
    const middleware = multer({
      storage: multer.memoryStorage(),
      limits: { fileSize: MAX_BYTES, files: 1 }
    }).single('archivo');

    return middleware(req, res, (err) => {
      if (err) {
        const codigo =
          err.code === 'LIMIT_FILE_SIZE' ? 'ARCHIVO_DEMASIADO_GRANDE' : 'UPLOAD_ERROR';
        const http = err.code === 'LIMIT_FILE_SIZE' ? 413 : 400;
        return res.status(http).json({
          error: err.message || 'Error al recibir el archivo',
          codigo
        });
      }
      return next();
    });
  }

  if (contentType.startsWith('image/')) {
    const mime = contentType.split(';')[0].trim().toLowerCase();
    return leerCuerpoBinario(req)
      .then((buffer) => {
        req.file = {
          buffer,
          mimetype: mime,
          originalname: `upload.${extensionDesdeMime(mime)}`,
          size: buffer.length
        };
        return next();
      })
      .catch((err) => {
        const http = err.codigo === 'ARCHIVO_DEMASIADO_GRANDE' ? 413 : 400;
        return res.status(http).json({
          error: err.message,
          codigo: err.codigo || 'UPLOAD_ERROR'
        });
      });
  }

  return res.status(400).json({
    error: 'Envía multipart campo "archivo" o body image/*',
    codigo: 'ARCHIVO_REQUERIDO'
  });
}

module.exports = uploadImagen;
