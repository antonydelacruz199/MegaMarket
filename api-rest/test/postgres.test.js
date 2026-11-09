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
    for(const file of ['001_esquema_inicial.sql','002_reservas.sql']) await pool.query(fs.readFileSync(path.join(__dirname,'../sql',file),'utf8'));
    const {rows:[categoria]}=await pool.query("INSERT INTO categorias(nombre) VALUES('Prueba') RETURNING id");
    const {rows:[producto]}=await pool.query("INSERT INTO productos(nombre,marca,categoria_id,precio_centimos,stock) VALUES('Arroz','Marca',$1,100,1) RETURNING id",[categoria.id]);
    await assert.rejects(pool.query("INSERT INTO productos(nombre,marca,categoria_id,precio_centimos,stock) VALUES('  ARROZ  ','Otra',$1,100,1)",[categoria.id]),{code:'23505'});
    const usuarios=[];
    for(const nombre of ['Ana','Pedro']) {
      const {rows:[u]}=await pool.query("INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol) VALUES($1,$1,$1,'solo-prueba','CLIENTE') RETURNING id",[nombre]);usuarios.push(u.id);
    }
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
