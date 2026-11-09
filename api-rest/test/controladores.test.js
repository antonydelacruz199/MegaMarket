// Pruebas de los controladores con transacciones SQLite en memoria.
// El adaptador elimina FOR UPDATE: la concurrencia PostgreSQL se prueba aparte.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const A='123e4567-e89b-42d3-a456-426614174001';
const B='123e4567-e89b-42d3-a456-426614174002';
const P='123e4567-e89b-42d3-a456-426614174003';
const C='123e4567-e89b-42d3-a456-426614174004';
function escenario() {
  const db=new DatabaseSync(':memory:');
  db.exec(`
    CREATE TABLE usuarios(id TEXT PRIMARY KEY);
    INSERT INTO usuarios VALUES('${A}'),('${B}');
    CREATE TABLE productos(id TEXT PRIMARY KEY,nombre TEXT,stock INTEGER,precio_centimos INTEGER,precio_oferta_centimos INTEGER,es_oferta INTEGER DEFAULT 0,activo INTEGER DEFAULT 1,deleted_at TEXT);
    INSERT INTO productos(id,nombre,stock,precio_centimos) VALUES('${P}','Arroz',5,100);
    CREATE TABLE carritos(id TEXT PRIMARY KEY,usuario_id TEXT,revision INTEGER DEFAULT 0,cerrado INTEGER DEFAULT 0);
    CREATE TABLE reservas_carrito(carrito_id TEXT,producto_id TEXT,cantidad INTEGER,PRIMARY KEY(carrito_id,producto_id));
    CREATE TABLE pedidos(id TEXT PRIMARY KEY DEFAULT 'pedido-1',cliente_id TEXT,client_uuid TEXT UNIQUE,total_centimos INTEGER,estado TEXT,solicitud_hash TEXT,fecha TEXT,version INTEGER,created_at TEXT,updated_at TEXT);
    CREATE TABLE pedido_detalle(id INTEGER PRIMARY KEY,pedido_id TEXT,producto_id TEXT,nombre_producto TEXT,cantidad INTEGER,precio_unitario_centimos INTEGER,subtotal_centimos INTEGER,version INTEGER,created_at TEXT,updated_at TEXT,deleted_at TEXT);
    CREATE TABLE direcciones(id INTEGER PRIMARY KEY,pedido_id TEXT,departamento TEXT,provincia TEXT,distrito TEXT,direccion TEXT,telefono TEXT,version INTEGER,created_at TEXT,updated_at TEXT,deleted_at TEXT);
    CREATE TABLE movimientos_inventario(uuid_operacion TEXT,producto_id TEXT,pedido_id TEXT,usuario_id TEXT,tipo TEXT,cantidad INTEGER);
  `);
  const cliente={release(){},async query(sql,params=[]) {
    const orden=[];
    sql=sql.replace(/FOR UPDATE/g,'').replace(/\$(\d+)/g,(_,n)=>{orden.push(params[Number(n)-1]);return '?';});
    return {rows:db.prepare(sql).all(...orden)};
  }};
  let reservar;
  function cargar(file) {
    const filename=path.resolve(__dirname,'../src',file);
    const realRequire=createRequire(filename);
    const scope={module:{exports:{}},console,require(name){
      if(name==='../database')return {connect:async()=>cliente};
      if(name==='express')return {Router:()=>({use(){},put(_path,handler){reservar=handler;}})};
      if(name.startsWith('../middleware'))return ()=>()=>{};
      return realRequire(name);
    }};
    vm.runInNewContext(fs.readFileSync(filename,'utf8'),scope,{filename});
    return scope.module.exports;
  }
  const pedidos=cargar('controllers/pedidos.controller.js');
  cargar('routes/carritos.routes.js');
  const direccion={departamento:'Junín',provincia:'Huancayo',distrito:'Centro',direccion:'Calle 1',telefono:'999999999'};
  async function ejecutar(handler,usuario,body,params={}) {
    const res={code:200,status(code){this.code=code;return this;},json(body){this.body=body;return this;}};
    await handler({usuario:{id:usuario},body,params},res);return res;
  }
  return {db,
    reservar:(cantidad,revision=1,usuario=A)=>ejecutar(reservar,usuario,{revision,items:cantidad?[{productoId:P,cantidad}]:[]},{id:C}),
    comprar:(items,usuario=A)=>ejecutar(pedidos.crear,usuario,{contratoVersion:2,clientUuid:C,direccion,items}),
    item:(cantidad,precio=100)=>({productoId:P,cantidad,precioEsperadoCentimos:precio}),
    disponible:()=>db.prepare('SELECT stock-(SELECT COALESCE(SUM(cantidad),0) FROM reservas_carrito) n FROM productos').get().n
  };
}
test('reservar reduce disponible, quitar devuelve y una revisión antigua no reaplica',async()=>{
  const e=escenario();try {
    assert.equal((await e.reservar(3)).code,200);assert.equal(e.disponible(),2);
    assert.equal((await e.reservar(1,2)).code,200);assert.equal(e.disponible(),4);
    await e.reservar(3,1);assert.equal(e.disponible(),4);
    await e.reservar(0,3);assert.equal(e.disponible(),5);
  }finally{e.db.close();}
});
test('comprar consume reserva sin segundo descuento y reintento no duplica venta',async()=>{
  const e=escenario();try {
    await e.reservar(3);assert.equal(e.disponible(),2);
    assert.equal((await e.comprar([e.item(3)])).code,201);assert.equal(e.disponible(),2);
    assert.equal((await e.comprar([e.item(3)])).code,200);assert.equal(e.disponible(),2);
    assert.equal(e.db.prepare('SELECT COUNT(*) n FROM pedidos').get().n,1);
    await e.reservar(3,2);assert.equal(e.disponible(),2);
  }finally{e.db.close();}
});
test('productos repetidos no permiten vender 8 unidades cuando quedan 5',async()=>{
  const e=escenario();try {
    assert.equal((await e.comprar([e.item(4),e.item(4)])).code,409);
    assert.equal(e.disponible(),5);
    assert.equal(e.db.prepare('SELECT COUNT(*) n FROM pedidos').get().n,0);
  }finally{e.db.close();}
});
test('otra cuenta no modifica reservas ni recupera pedidos ajenos',async()=>{
  const e=escenario();try {
    await e.reservar(2);
    assert.equal((await e.reservar(0,2,B)).code,409);assert.equal(e.disponible(),3);
    await e.comprar([e.item(2)]);
    const respuesta=await e.comprar([e.item(2)],B);
    assert.equal(respuesta.code,409);assert.equal(respuesta.body.pedido,undefined);
  }finally{e.db.close();}
});
test('precio nuevo requiere aceptación y el rechazo no descuenta stock',async()=>{
  const e=escenario();try {
    await e.reservar(2);e.db.exec('UPDATE productos SET precio_centimos=120');
    const rechazo=await e.comprar([e.item(2)]);
    assert.equal(rechazo.code,409);assert.equal(rechazo.body.codigo,'PRECIO_CAMBIADO');assert.equal(e.disponible(),3);
    const confirmado=await e.comprar([e.item(2,120)]);
    assert.equal(confirmado.code,201);assert.equal(confirmado.body.pedido.total_centimos,240);assert.equal(e.disponible(),3);
  }finally{e.db.close();}
});
