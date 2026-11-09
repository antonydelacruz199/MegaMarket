const {test}=require('node:test');
const assert=require('node:assert/strict');
const {DatabaseSync}=require('node:sqlite');
const fs=require('node:fs');
const path=require('node:path');
test('migración Room 7 a 8 conserva pedidos y aísla datos sin propietario',()=>{
  const db=new DatabaseSync(':memory:');
  try {
    db.exec(`
      CREATE TABLE clientes(id INTEGER PRIMARY KEY);
      CREATE TABLE carrito(id INTEGER PRIMARY KEY,productoId INTEGER,cantidad INTEGER);
      CREATE UNIQUE INDEX index_carrito_productoId ON carrito(productoId);
      CREATE TABLE favoritos(productoId INTEGER PRIMARY KEY);
      CREATE TABLE pedidos(id INTEGER PRIMARY KEY,clienteId INTEGER);
      CREATE TABLE operaciones_pendientes(id INTEGER PRIMARY KEY,tipoEntidad TEXT,entidadIdLocal INTEGER,uuidOperacion TEXT);
      CREATE TABLE movimientos_inventario(uuidOperacion TEXT,pedidoId INTEGER);
      CREATE TABLE sync_metadata(ultimaSincronizacionExitosa INTEGER);
      INSERT INTO clientes VALUES(7);
      INSERT INTO carrito VALUES(1,3,2);
      INSERT INTO favoritos VALUES(3);
      INSERT INTO pedidos VALUES(11,7);
      INSERT INTO operaciones_pendientes VALUES(1,'PEDIDO',11,'pedido-11'),(2,'MOVIMIENTO_INVENTARIO',3,'movimiento-11'),(3,'PEDIDO',99,'desconocido');
      INSERT INTO movimientos_inventario VALUES('movimiento-11',11);
      INSERT INTO sync_metadata VALUES(1234);
    `);
    const archivo=path.resolve(__dirname,'../../cliente/src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt');
    const fuente=fs.readFileSync(archivo,'utf8');
    const bloque=fuente.slice(fuente.indexOf('val MIGRACION_7_8'),fuente.indexOf('val MIGRACION_2_3'));
    const instrucciones=[...bloque.matchAll(/db\.execSQL\("([^"\n]+)"\)/g)].map(m=>m[1]);
    assert.ok(instrucciones.length>=15);
    db.exec('BEGIN');for(const sql of instrucciones)db.exec(sql);db.exec('COMMIT');
    assert.equal(db.prepare('SELECT ownerId FROM carrito').get().ownerId,0);
    assert.equal(db.prepare('SELECT ownerId FROM favoritos').get().ownerId,0);
    assert.deepEqual(db.prepare('SELECT ownerId FROM operaciones_pendientes ORDER BY id').all().map(x=>x.ownerId),[7,7,0]);
    assert.equal(db.prepare('SELECT clienteId FROM pedidos WHERE id=11').get().clienteId,7);
    assert.equal(db.prepare('SELECT ultimaSincronizacionExitosa FROM sync_metadata').get().ultimaSincronizacionExitosa,null);
    db.exec('INSERT INTO carrito(id,productoId,cantidad,ownerId,cartUuid) VALUES(2,3,1,7,\'cart-7\'),(3,3,1,8,\'cart-8\')');
    assert.equal(db.prepare('SELECT COUNT(*) n FROM carrito').get().n,3);
    assert.ok(fuente.includes('MIGRACION_6_7,\n                        MIGRACION_7_8'));
  }finally{db.close();}
});
