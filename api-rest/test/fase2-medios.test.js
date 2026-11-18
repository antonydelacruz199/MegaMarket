const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Pool } = require('pg');
const { validarArchivoImagen } = require('../src/storage/validateImage');
const {
  esClaveLocal,
  perteneceAProducto,
  imagenKeyProductoAceptable
} = require('../src/storage/keys');
const memoryStorage = require('../src/storage/memoryStorage');
const { setStorageForTests, resetStorageForTests } = require('../src/storage');

const skip = !process.env.TEST_DATABASE_URL;

/** JPEG / PNG con firmas mágicas válidas. */
const JPEG_MINI = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0xff, 0xd9]);
const PNG_MINI = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d
]);

async function conEsquema(fn) {
  const schema = 'megamarket_md_' + crypto.randomBytes(6).toString('hex');
  const admin = new Pool({ connectionString: process.env.TEST_DATABASE_URL });
  let pool;
  let creado = false;
  try {
    await admin.query(`CREATE SCHEMA "${schema}"`);
    creado = true;
    pool = new Pool({
      connectionString: process.env.TEST_DATABASE_URL,
      options: `-c search_path=${schema}`
    });
    for (const file of [
      '001_esquema_inicial.sql',
      '002_reservas.sql',
      '003_operaciones_extendidas.sql'
    ]) {
      await pool.query(fs.readFileSync(path.join(__dirname, '../sql', file), 'utf8'));
    }

    const databasePath = require.resolve('../src/database');
    const original = require.cache[databasePath];
    require.cache[databasePath] = {
      id: databasePath,
      filename: databasePath,
      loaded: true,
      exports: pool
    };
    delete require.cache[require.resolve('../src/controllers/medios.controller')];
    memoryStorage._reset();
    setStorageForTests(memoryStorage);
    const medios = require('../src/controllers/medios.controller');
    await fn(pool, medios);
    resetStorageForTests();
    memoryStorage._reset();

    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
  } finally {
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
  }
}

async function ejecutar(handler, usuario, params = {}, body = {}, file = null, query = {}) {
  const res = {
    code: 200,
    body: null,
    headers: {},
    status(code) {
      this.code = code;
      return this;
    },
    json(payload) {
      this.body = payload;
      return this;
    },
    setHeader(k, v) {
      this.headers[k] = v;
    },
    send(buf) {
      this.body = buf;
      return this;
    }
  };
  await handler(
    { usuario, params, body, file, query },
    res
  );
  return res;
}

function archivoJpeg(nombre = 'foto.jpg') {
  return {
    buffer: JPEG_MINI,
    mimetype: 'image/jpeg',
    originalname: nombre,
    size: JPEG_MINI.length
  };
}

async function semillaProducto(pool) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [admin] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','ADMINISTRADOR') RETURNING id`,
    [`adm_${tag}`]
  );
  const { rows: [cat] } = await pool.query(
    `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
    [`C_${tag}`]
  );
  const { rows: [producto] } = await pool.query(
    `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,imagen_key)
     VALUES($1,'M','d',$2,100,5,$3) RETURNING *`,
    [`P_${tag}`, cat.id, `${Date.now()}.jpg`]
  );
  return { admin, producto };
}

async function semillaEntrega(pool) {
  const tag = crypto.randomBytes(3).toString('hex');
  const { rows: [cliente] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
    [`cl_${tag}`]
  );
  const { rows: [repartidor] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','REPARTIDOR') RETURNING id`,
    [`rp_${tag}`]
  );
  const { rows: [ajeno] } = await pool.query(
    `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
     VALUES($1,$1,$1,'x','REPARTIDOR') RETURNING id`,
    [`ra_${tag}`]
  );
  const { rows: [pedido] } = await pool.query(
    `INSERT INTO pedidos(cliente_id, client_uuid, total_centimos, estado, repartidor_id, solicitud_hash)
     VALUES($1,$2,100,'EN_CAMINO',$3,'h') RETURNING *`,
    [cliente.id, crypto.randomUUID(), repartidor.id]
  );
  return { cliente, repartidor, ajeno, pedido };
}

test('validación imagen: MIME real, extensión y tamaño', () => {
  assert.equal(validarArchivoImagen(null).codigo, 'ARCHIVO_REQUERIDO');
  assert.equal(
    validarArchivoImagen({ buffer: Buffer.from('hola'), originalname: 'a.jpg', mimetype: 'image/jpeg' })
      .codigo,
    'MIME_INVALIDO'
  );
  assert.equal(
    validarArchivoImagen({ buffer: JPEG_MINI, originalname: 'a.png', mimetype: 'image/jpeg' }).codigo,
    'EXTENSION_INCOHERENTE'
  );
  assert.equal(
    validarArchivoImagen({ buffer: JPEG_MINI, originalname: 'a.jpg', mimetype: 'image/jpeg' }).ok,
    true
  );
  assert.equal(
    validarArchivoImagen({ buffer: PNG_MINI, originalname: 'a.png', mimetype: 'image/png' }).ok,
    true
  );
  assert.equal(esClaveLocal('123.jpg'), true);
  assert.equal(esClaveLocal('productos/x/y.jpg'), false);
  assert.equal(
    imagenKeyProductoAceptable('productos/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee/x.jpg', 'other').ok,
    false
  );
});

test('PostgreSQL medios: subida producto, reemplazo sin borrar, clave local', { skip }, async () => {
  await conEsquema(async (pool, medios) => {
    const s = await semillaProducto(pool);
    const localKey = s.producto.imagen_key;

    const getLocal = await ejecutar(
      medios.obtenerProducto,
      { id: s.admin.id, rol: 'ADMINISTRADOR' },
      { productoId: s.producto.id }
    );
    assert.equal(getLocal.code, 404);
    assert.equal(getLocal.body.codigo, 'IMAGEN_LOCAL');

    const subida = await ejecutar(
      medios.subirProducto,
      { id: s.admin.id, rol: 'ADMINISTRADOR' },
      { productoId: s.producto.id },
      {},
      archivoJpeg()
    );
    assert.equal(subida.code, 201);
    assert.ok(perteneceAProducto(subida.body.imagenKey, s.producto.id));
    assert.equal(subida.body.imagenAnterior, localKey);
    assert.equal(subida.body.eliminadaAnterior, false);
    assert.ok(memoryStorage._keys().includes(subida.body.imagenKey));

    const primera = subida.body.imagenKey;
    const reemplazo = await ejecutar(
      medios.subirProducto,
      { id: s.admin.id, rol: 'ADMINISTRADOR' },
      { productoId: s.producto.id },
      {},
      archivoJpeg('otra.jpg')
    );
    assert.equal(reemplazo.code, 201);
    assert.notEqual(reemplazo.body.imagenKey, primera);
    assert.ok(memoryStorage._keys().includes(primera)); // no eliminada
    assert.ok(memoryStorage._keys().includes(reemplazo.body.imagenKey));

    const { rows: [prod] } = await pool.query(
      `SELECT imagen_key FROM productos WHERE id=$1`,
      [s.producto.id]
    );
    assert.equal(prod.imagen_key, reemplazo.body.imagenKey);

    const getOk = await ejecutar(
      medios.obtenerProducto,
      { id: s.admin.id, rol: 'CLIENTE' },
      { productoId: s.producto.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(getOk.code, 200);
    assert.ok(Buffer.isBuffer(getOk.body));

    const invalido = await ejecutar(
      medios.subirProducto,
      { id: s.admin.id, rol: 'ADMINISTRADOR' },
      { productoId: s.producto.id },
      {},
      { buffer: Buffer.from('no-image'), mimetype: 'image/jpeg', originalname: 'x.jpg' }
    );
    assert.equal(invalido.code, 400);
    assert.equal(invalido.body.codigo, 'MIME_INVALIDO');
  });
});

test('PostgreSQL medios: entrega privada, permisos y pertenencia', { skip }, async () => {
  await conEsquema(async (pool, medios) => {
    const s = await semillaEntrega(pool);

    const ajeno = await ejecutar(
      medios.subirEntrega,
      { id: s.ajeno.id, rol: 'REPARTIDOR' },
      { pedidoId: s.pedido.id },
      {},
      archivoJpeg()
    );
    assert.equal(ajeno.code, 403);

    const ok = await ejecutar(
      medios.subirEntrega,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { pedidoId: s.pedido.id },
      {},
      archivoJpeg()
    );
    assert.equal(ok.code, 201);
    assert.ok(ok.body.fotoReferencia.startsWith(`entregas/${s.pedido.id}/`));

    // Sin fila en entregas aún → no lectura
    const sinReg = await ejecutar(
      medios.obtenerEntrega,
      { id: s.repartidor.id, rol: 'REPARTIDOR' },
      { pedidoId: s.pedido.id }
    );
    assert.equal(sinReg.code, 404);

    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
         numero_operacion_banco, fecha_confirmacion)
       VALUES($1,$2,100,'PEN','QR','CONTRAENTREGA','CONFIRMADO','OP1',now())`,
      [crypto.randomUUID(), s.pedido.id]
    );
    await pool.query(
      `INSERT INTO entregas(uuid_operacion, pedido_id, repartidor_id, foto_referencia, latitud, longitud)
       VALUES($1,$2,$3,$4,0,0)`,
      [crypto.randomUUID(), s.pedido.id, s.repartidor.id, ok.body.fotoReferencia]
    );
    await pool.query(`UPDATE pedidos SET estado='ENTREGADO' WHERE id=$1`, [s.pedido.id]);

    const lecturaAjena = await ejecutar(
      medios.obtenerEntrega,
      { id: s.ajeno.id, rol: 'REPARTIDOR' },
      { pedidoId: s.pedido.id }
    );
    assert.equal(lecturaAjena.code, 403);

    const lecturaOk = await ejecutar(
      medios.obtenerEntrega,
      { id: s.cliente.id, rol: 'CLIENTE' },
      { pedidoId: s.pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(lecturaOk.code, 200);
    assert.ok(Buffer.isBuffer(lecturaOk.body));
    assert.equal(lecturaOk.headers['Cache-Control'], 'private, no-store');

    // Referencia de otro pedido rechazada al registrar entrega
    const { validarEvidencia } = require('../src/entregas');
    const ajena = validarEvidencia({
      fotoReferencia: `entregas/${crypto.randomUUID()}/${crypto.randomUUID()}.jpg`,
      latitud: 0,
      longitud: 0,
      pedidoId: s.pedido.id
    });
    assert.equal(ajena.codigo, 'FOTO_AJENA');
  });
});
