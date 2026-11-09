const crypto = require('crypto');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function agruparItems(items, precios = false) {
  if (!Array.isArray(items) || items.length > 500) throw new Error('Lista de productos inválida');
  const mapa = new Map();
  for (const item of items) {
    const id = item.productoId || item.producto_id;
    const cantidad = item.cantidad;
    if (!UUID.test(String(id)) || !Number.isSafeInteger(cantidad) || cantidad <= 0)
      throw new Error('Producto o cantidad inválidos');
    const precio = item.precioEsperadoCentimos;
    if (precios && (!Number.isSafeInteger(precio) || precio <= 0)) throw new Error('Precio esperado obligatorio');
    const anterior = mapa.get(id);
    if (anterior && precios && anterior.precioEsperadoCentimos !== precio) throw new Error('Precios contradictorios');
    const total = (anterior?.cantidad || 0) + cantidad;
    if (!Number.isSafeInteger(total) || total > 2147483647) throw new Error('Cantidad excesiva');
    mapa.set(id, { productoId: id, cantidad: total, ...(precios ? {precioEsperadoCentimos: precio} : {}) });
  }
  return [...mapa.values()].sort((a,b) => a.productoId.localeCompare(b.productoId));
}
function huella(valor) { return crypto.createHash('sha256').update(JSON.stringify(valor)).digest('hex'); }
module.exports = { UUID, agruparItems, huella };
