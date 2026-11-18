const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {Pool}=require('pg');

// Requiere una base de pruebas explícita. Nunca utiliza DATABASE_URL.
test('PostgreSQL: migraciones e inventario concurrente', {skip:!process.env.TEST_DATABASE_URL}, async()=>{
  const schema='megamarket_test_'+crypto.randomBytes(8).toString('hex');
  const admin=new Pool({connectionString:process.env.TEST_DATABASE_URL});
  let pool;
  let creado=false;
  try {
    await admin.query(`CREATE SCHEMA "${schema}"`);creado=true;
    pool=new Pool({connectionString:process.env.TEST_DATABASE_URL,options:`-c search_path=${schema}`});
    for(const file of ['001_esquema_inicial.sql','002_reservas.sql','003_operaciones_extendidas.sql']) await pool.query(fs.readFileSync(path.join(__dirname,'../sql',file),'utf8'));
    // Reaplicar 003 debe ser idempotente.
    await pool.query(fs.readFileSync(path.join(__dirname,'../sql/003_operaciones_extendidas.sql'),'utf8'));
    const {rows:[categoria]}=await pool.query("INSERT INTO categorias(nombre) VALUES('Prueba') RETURNING id");
    const {rows:[producto]}=await pool.query("INSERT INTO productos(nombre,marca,categoria_id,precio_centimos,stock) VALUES('Arroz','Marca',$1,100,1) RETURNING id",[categoria.id]);
    await assert.rejects(pool.query("INSERT INTO productos(nombre,marca,categoria_id,precio_centimos,stock) VALUES('  ARROZ  ','Otra',$1,100,1)",[categoria.id]),{code:'23505'});
    const tablasNuevas=['franjas_entrega','pagos','ajustes_pago','picking_escaneos','sustituciones_pedido','proveedores','ordenes_compra','orden_compra_detalle','entregas','conciliaciones_bancarias','conciliacion_detalle','solicitudes_credito'];
    const {rows:tablas}=await pool.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name = ANY($1::text[])`,[tablasNuevas]);
    assert.equal(tablas.length, tablasNuevas.length);
    const {rows:extra}=await pool.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('pasillos','producto_codigos_barras')`);
    assert.equal(extra.length, 0);
    const {rows:[pedidoCols]}=await pool.query(`SELECT COUNT(*)::int AS n FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='pedidos' AND column_name IN ('franja_entrega_id','picker_id','repartidor_id','preferencia_sustitucion')`);
    assert.equal(pedidoCols.n, 4);
    const {rows:[productoCols]}=await pool.query(`SELECT COUNT(*)::int AS n FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='productos' AND column_name IN ('codigo_barras','pasillo')`);
    assert.equal(productoCols.n, 2);
    const usuarios=[];
    for(const nombre of ['Ana','Pedro']) {
      const {rows:[u]}=await pool.query("INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol) VALUES($1,$1,$1,'solo-prueba','CLIENTE') RETURNING id",[nombre]);usuarios.push(u.id);
    }
    const {rows:[picker]}=await pool.query("INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol) VALUES('Pika','picker1','picker1','solo-prueba','PICKER') RETURNING id, rol");
    assert.equal(picker.rol, 'PICKER');
    const vm=require('node:vm');
    const filename=path.join(__dirname,'../src/routes/carritos.routes.js');
    const realRequire=require('node:module').createRequire(filename);
    let reservar;
    const context={module:{exports:{}},console,require(name){
      if(name==='../database')return pool;
      if(name==='express')return {Router:()=>({use(){},put(_p,handler){reservar=handler;}})};
      if(name.startsWith('../middleware'))return ()=>()=>{};
      return realRequire(name);
    }};
    vm.runInNewContext(fs.readFileSync(filename,'utf8'),context,{filename});
    async function intento(usuario) {
      const res={code:200,status(code){this.code=code;return this;},json(body){this.body=body;return this;}};
      await reservar({usuario:{id:usuario},params:{id:crypto.randomUUID()},body:{revision:1,items:[{productoId:producto.id,cantidad:1}]}},res);
      return res.code;
    }
    const codigos=await Promise.all(usuarios.map(intento));
    assert.deepEqual(codigos.sort(),[200,409]);
    const {rows:[r]}=await pool.query('SELECT SUM(cantidad)::int cantidad FROM reservas_carrito');
    assert.equal(r.cantidad,1);
    // Los triggers siguen incrementando la versión.
    const {rows:[actualizado]}=await pool.query('UPDATE productos SET stock=stock+1 WHERE id=$1 RETURNING version',[producto.id]);
    assert.equal(Number(actualizado.version),2);
  } finally {
    if(pool)await pool.end();
    if(creado)await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
});
