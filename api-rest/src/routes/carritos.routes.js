const router = require('express').Router();
const pool = require('../database');
const { UUID, agruparItems } = require('../reglas');
router.use(require('../middleware/verificarToken'), require('../middleware/verificarRol')('CLIENTE'));
router.put('/:id', async (req, res) => {
  let items;
  try {
    if (!UUID.test(req.params.id) || !Number.isSafeInteger(req.body.revision) || req.body.revision < 1)
      throw new Error('Carrito o revisión inválidos');
    items = agruparItems(req.body.items);
  } catch (e) { return res.status(400).json({error:e.message}); }
  const db = await pool.connect();
  try {
    await db.query('BEGIN');
    // Un único orden de bloqueo por usuario y producto en carrito y checkout.
    await db.query('SELECT id FROM usuarios WHERE id=$1 FOR UPDATE', [req.usuario.id]);
    await db.query('INSERT INTO carritos(id,usuario_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [req.params.id,req.usuario.id]);
    const {rows:[cart]} = await db.query('SELECT * FROM carritos WHERE id=$1 FOR UPDATE',[req.params.id]);
    if (cart.usuario_id !== req.usuario.id) { await db.query('ROLLBACK'); return res.status(409).json({error:'Carrito no disponible'}); }
    if(cart.cerrado) { await db.query('COMMIT'); return res.json({revision:Number(cart.revision),cerrado:true}); }
    if (Number(cart.revision) >= req.body.revision) { await db.query('COMMIT'); return res.json({revision:Number(cart.revision)}); }
    const old = await db.query('SELECT producto_id FROM reservas_carrito WHERE carrito_id=$1',[cart.id]);
    const ids = [...new Set([...old.rows.map(x=>x.producto_id), ...items.map(x=>x.productoId)])].sort();
    for (const id of ids) {
      const {rows:[p]} = await db.query('SELECT stock,activo,deleted_at FROM productos WHERE id=$1 FOR UPDATE',[id]);
      const cantidad = items.find(x=>x.productoId===id)?.cantidad || 0;
      const {rows:[r]} = await db.query('SELECT COALESCE(SUM(cantidad),0) AS total FROM reservas_carrito WHERE producto_id=$1 AND carrito_id<>$2',[id,cart.id]);
      if (cantidad && (!p || !p.activo || p.deleted_at || cantidad > Number(p.stock)-Number(r.total))) {
        await db.query('ROLLBACK'); return res.status(409).json({error:'Stock insuficiente o producto inactivo',codigo:'STOCK_INSUFICIENTE'});
      }
    }
    await db.query('DELETE FROM reservas_carrito WHERE carrito_id=$1',[cart.id]);
    for (const item of items) await db.query('INSERT INTO reservas_carrito VALUES($1,$2,$3)',[cart.id,item.productoId,item.cantidad]);
    await db.query('UPDATE carritos SET revision=$2 WHERE id=$1',[cart.id,req.body.revision]);
    await db.query('COMMIT'); res.json({revision:req.body.revision});
  } catch(e) { await db.query('ROLLBACK'); console.error(e.message); res.status(500).json({error:'No se pudo reservar el carrito'}); }
  finally {db.release();}
});
module.exports = router;
