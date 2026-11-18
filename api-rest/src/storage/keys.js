const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** Claves locales históricas del admin Android: `{timestamp}.jpg` */
function esClaveLocal(clave) {
  return Boolean(clave && /^\d+\.jpg$/i.test(String(clave).trim()));
}

function esUuid(valor) {
  return Boolean(valor && UUID_RE.test(String(valor)));
}

function claveProducto(productoId, nombreArchivo) {
  return `productos/${productoId}/${nombreArchivo}`;
}

function claveEntrega(pedidoId, nombreArchivo) {
  return `entregas/${pedidoId}/${nombreArchivo}`;
}

function perteneceAProducto(clave, productoId) {
  const k = String(clave || '').trim();
  return k.startsWith(`productos/${productoId}/`) && !k.includes('..');
}

function perteneceAPedido(clave, pedidoId) {
  const k = String(clave || '').trim();
  return k.startsWith(`entregas/${pedidoId}/`) && !k.includes('..');
}

/**
 * Acepta null, legado local, o ruta remota del propio producto.
 * Rechaza rutas de otros productos u objetos arbitrarios.
 */
function imagenKeyProductoAceptable(imagenKey, productoId) {
  if (imagenKey === null || imagenKey === undefined || imagenKey === '') {
    return { ok: true, valor: null };
  }
  const clave = String(imagenKey).trim();
  if (esClaveLocal(clave)) {
    return { ok: true, valor: clave, legadoLocal: true };
  }
  if (productoId && perteneceAProducto(clave, productoId)) {
    return { ok: true, valor: clave };
  }
  return {
    ok: false,
    error: 'imagenKey no pertenece a este producto o no es una clave remota válida',
    codigo: 'IMAGEN_KEY_INVALIDA'
  };
}

function esClaveRemotaProducto(clave) {
  return /^productos\/[0-9a-f-]{36}\/[0-9a-f-]{36}\.(jpe?g|png|webp)$/i.test(
    String(clave || '').trim()
  );
}

function esClaveRemotaEntrega(clave) {
  return /^entregas\/[0-9a-f-]{36}\/[0-9a-f-]{36}\.(jpe?g|png|webp)$/i.test(
    String(clave || '').trim()
  );
}

module.exports = {
  UUID_RE,
  esUuid,
  esClaveLocal,
  claveProducto,
  claveEntrega,
  perteneceAProducto,
  perteneceAPedido,
  imagenKeyProductoAceptable,
  esClaveRemotaProducto,
  esClaveRemotaEntrega
};
