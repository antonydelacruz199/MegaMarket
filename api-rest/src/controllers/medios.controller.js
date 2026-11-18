const crypto = require('crypto');
const pool = require('../database');
const { getStorage } = require('../storage');
const { validarArchivoImagen } = require('../storage/validateImage');
const {
  esUuid,
  esClaveLocal,
  claveProducto,
  claveEntrega,
  perteneceAProducto,
  perteneceAPedido,
  esClaveRemotaEntrega
} = require('../storage/keys');
const { assertRepartidorAsignado } = require('../entregas');

const TTL_PRODUCTO = Number(process.env.MEDIA_URL_TTL_PRODUCTO) || 3600;
const TTL_ENTREGA = Number(process.env.MEDIA_URL_TTL_ENTREGA) || 600;

async function subirProducto(req, res) {
  try {
    const productoId = String(req.params.productoId || '').trim();
    if (!esUuid(productoId)) {
      return res.status(400).json({
        error: 'productoId inválido',
        codigo: 'PRODUCTO_INVALIDO'
      });
    }

    const validacion = validarArchivoImagen(req.file);
    if (!validacion.ok) {
      return res.status(validacion.http).json({
        error: validacion.error,
        codigo: validacion.codigo
      });
    }

    const { rows } = await pool.query(
      `SELECT id, imagen_key FROM productos
       WHERE id = $1 AND deleted_at IS NULL
       LIMIT 1`,
      [productoId]
    );
    const producto = rows[0];
    if (!producto) {
      return res.status(404).json({
        error: 'Producto no encontrado',
        codigo: 'PRODUCTO_NO_ENCONTRADO'
      });
    }

    const nombre = `${crypto.randomUUID()}.${validacion.extension}`;
    const nuevaClave = claveProducto(productoId, nombre);
    const storage = getStorage();

    // Subir primero y solo después actualizar Neon. No se borra la imagen anterior
    // aunque el archivo nuevo ya esté en disco: imagen_key cambia cuando el UPDATE confirma.
    await storage.subir({
      clave: nuevaClave,
      buffer: req.file.buffer,
      contentType: validacion.mime
    });

    const { rows: [upd] } = await pool.query(
      `UPDATE productos
       SET imagen_key = $2
       WHERE id = $1 AND deleted_at IS NULL
       RETURNING id, imagen_key, version, updated_at`,
      [productoId, nuevaClave]
    );

    return res.status(201).json({
      imagenKey: upd.imagen_key,
      imagenAnterior: producto.imagen_key || null,
      eliminadaAnterior: false,
      producto: {
        id: upd.id,
        imagenKey: upd.imagen_key,
        version: Number(upd.version),
        updatedAt: upd.updated_at
      }
    });
  } catch (error) {
    console.error('Error al subir imagen de producto:', error.message);
    return res.status(500).json({
      error: 'No se pudo subir la imagen del producto',
      codigo: error.codigo || 'STORAGE_ERROR'
    });
  }
}

async function obtenerProducto(req, res) {
  try {
    const productoId = String(req.params.productoId || '').trim();
    if (!esUuid(productoId)) {
      return res.status(400).json({
        error: 'productoId inválido',
        codigo: 'PRODUCTO_INVALIDO'
      });
    }

    const { rows } = await pool.query(
      `SELECT id, imagen_key, activo, deleted_at FROM productos WHERE id = $1 LIMIT 1`,
      [productoId]
    );
    const producto = rows[0];
    if (!producto || producto.deleted_at) {
      return res.status(404).json({
        error: 'Producto no encontrado',
        codigo: 'PRODUCTO_NO_ENCONTRADO'
      });
    }
    if (!producto.imagen_key) {
      return res.status(404).json({
        error: 'El producto no tiene imagen',
        codigo: 'IMAGEN_AUSENTE'
      });
    }
    if (esClaveLocal(producto.imagen_key)) {
      return res.status(404).json({
        error: 'La imagen está solo en el dispositivo admin (clave local heredada)',
        codigo: 'IMAGEN_LOCAL',
        imagenKey: producto.imagen_key
      });
    }
    if (!perteneceAProducto(producto.imagen_key, productoId)) {
      return res.status(409).json({
        error: 'imagen_key no pertenece a este producto',
        codigo: 'IMAGEN_KEY_INVALIDA'
      });
    }

    const storage = getStorage();
    if (!(await storage.existe(producto.imagen_key))) {
      return res.status(404).json({
        error: 'Archivo remoto no encontrado',
        codigo: 'OBJETO_NO_ENCONTRADO'
      });
    }

    if (String(req.query.download || '') === '1') {
      const obj = await storage.obtener(producto.imagen_key);
      if (!obj) {
        return res.status(404).json({
          error: 'Archivo remoto no encontrado',
          codigo: 'OBJETO_NO_ENCONTRADO'
        });
      }
      res.setHeader('Content-Type', obj.contentType);
      res.setHeader('Cache-Control', 'private, max-age=60');
      return res.status(200).send(obj.buffer);
    }

    const firmada = await storage.urlFirmada(producto.imagen_key, {
      ttlSeconds: TTL_PRODUCTO
    });
    return res.status(200).json({
      imagenKey: producto.imagen_key,
      url: firmada.url,
      expiraEn: firmada.expiraEn,
      proxy: Boolean(firmada.proxy)
    });
  } catch (error) {
    console.error('Error al obtener imagen de producto:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener la imagen' });
  }
}

async function subirEntrega(req, res) {
  const cliente = await pool.connect();
  try {
    const pedidoId = String(req.params.pedidoId || '').trim();
    if (!esUuid(pedidoId)) {
      return res.status(400).json({
        error: 'pedidoId inválido',
        codigo: 'PEDIDO_INVALIDO'
      });
    }

    const validacion = validarArchivoImagen(req.file);
    if (!validacion.ok) {
      return res.status(validacion.http).json({
        error: validacion.error,
        codigo: validacion.codigo
      });
    }

    await cliente.query('BEGIN');
    const { rows } = await cliente.query(
      `SELECT * FROM pedidos WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`,
      [pedidoId]
    );
    const pedido = rows[0];
    const asig = assertRepartidorAsignado(pedido, req.usuario.id);
    if (!asig.ok) {
      await cliente.query('ROLLBACK');
      return res.status(asig.http).json({ error: asig.error, codigo: asig.codigo });
    }
    if (pedido.estado === 'ENTREGADO') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'El pedido ya fue entregado; no se admite nueva fotografía',
        codigo: 'ENTREGA_CERRADA'
      });
    }
    if (pedido.estado !== 'EN_CAMINO') {
      await cliente.query('ROLLBACK');
      return res.status(409).json({
        error: 'Solo se suben fotos de entrega en EN_CAMINO',
        codigo: 'PEDIDO_NO_EN_CAMINO'
      });
    }

    const nombre = `${crypto.randomUUID()}.${validacion.extension}`;
    const nuevaClave = claveEntrega(pedidoId, nombre);
    const storage = getStorage();
    await storage.subir({
      clave: nuevaClave,
      buffer: req.file.buffer,
      contentType: validacion.mime
    });

    await cliente.query('COMMIT');
    return res.status(201).json({
      fotoReferencia: nuevaClave,
      pedidoId,
      // El cliente debe pasar esta clave a POST /api/entregas/pedidos/:id
      uso: 'Usar fotoReferencia al registrar la entrega'
    });
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al subir foto de entrega:', error.message);
    return res.status(500).json({
      error: 'No se pudo subir la fotografía de entrega',
      codigo: error.codigo || 'STORAGE_ERROR'
    });
  } finally {
    cliente.release();
  }
}

async function autorizarLecturaEntrega(usuario, pedido) {
  if (!pedido) return false;
  if (usuario.rol === 'ADMINISTRADOR') return true;
  if (usuario.rol === 'REPARTIDOR' && pedido.repartidor_id === usuario.id) return true;
  if (usuario.rol === 'CLIENTE' && pedido.cliente_id === usuario.id) return true;
  return false;
}

async function obtenerEntrega(req, res) {
  try {
    const pedidoId = String(req.params.pedidoId || '').trim();
    if (!esUuid(pedidoId)) {
      return res.status(400).json({
        error: 'pedidoId inválido',
        codigo: 'PEDIDO_INVALIDO'
      });
    }

    const { rows: pedidos } = await pool.query(
      `SELECT id, cliente_id, repartidor_id, estado, deleted_at
       FROM pedidos WHERE id = $1 LIMIT 1`,
      [pedidoId]
    );
    const pedido = pedidos[0];
    if (!pedido || pedido.deleted_at) {
      return res.status(404).json({
        error: 'Pedido no encontrado',
        codigo: 'PEDIDO_NO_ENCONTRADO'
      });
    }
    if (!(await autorizarLecturaEntrega(req.usuario, pedido))) {
      return res.status(403).json({
        error: 'No autorizado a ver la fotografía de entrega',
        codigo: 'PERMISO_DENEGADO'
      });
    }

    const { rows: entregas } = await pool.query(
      `SELECT foto_referencia FROM entregas WHERE pedido_id = $1 LIMIT 1`,
      [pedidoId]
    );
    const entrega = entregas[0];
    if (!entrega) {
      return res.status(404).json({
        error: 'No hay entrega registrada',
        codigo: 'ENTREGA_NO_ENCONTRADA'
      });
    }

    const clave = entrega.foto_referencia;
    if (!perteneceAPedido(clave, pedidoId) || !esClaveRemotaEntrega(clave)) {
      // Compat: referencias de prueba no remotas → no exponer públicamente
      return res.status(404).json({
        error: 'Referencia de foto no disponible en almacenamiento remoto',
        codigo: 'FOTO_NO_REMOTA'
      });
    }

    const storage = getStorage();
    if (!(await storage.existe(clave))) {
      return res.status(404).json({
        error: 'Archivo remoto no encontrado',
        codigo: 'OBJETO_NO_ENCONTRADO'
      });
    }

    // Nunca URL pública permanente: proxy autenticado o firmada de corta vida.
    if (String(req.query.download || '') === '1' || getStorage().name === 'memory') {
      const obj = await storage.obtener(clave);
      res.setHeader('Content-Type', obj.contentType);
      res.setHeader('Cache-Control', 'private, no-store');
      return res.status(200).send(obj.buffer);
    }

    const firmada = await storage.urlFirmada(clave, { ttlSeconds: TTL_ENTREGA });
    return res.status(200).json({
      fotoReferencia: clave,
      url: firmada.url,
      expiraEn: firmada.expiraEn,
      privado: true
    });
  } catch (error) {
    console.error('Error al obtener foto de entrega:', error.message);
    return res.status(500).json({ error: 'No se pudo obtener la fotografía' });
  }
}

module.exports = {
  subirProducto,
  obtenerProducto,
  subirEntrega,
  obtenerEntrega
};
