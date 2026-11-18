const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const { Pool } = require('pg');
const { validarArchivoImagen, MAX_BYTES } = require('../src/storage/validateImage');
const { esClaveRemotaProducto, esClaveRemotaEntrega } = require('../src/storage/keys');
const memoryStorage = require('../src/storage/memoryStorage');
const { crearDiskStorage, rutaContenida } = require('../src/storage/diskStorage');
const { setStorageForTests, resetStorageForTests } = require('../src/storage');

const skip = !process.env.TEST_DATABASE_URL;
const JPEG_MINI = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0xff, 0xd9]);
const PNG_MINI = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d
]);
const WEBP_MINI = Buffer.concat([
  Buffer.from('RIFF'),
  Buffer.alloc(4),
  Buffer.from('WEBP')
]);

function claveDe(tipo, id, ext = 'jpg') {
  return `${tipo}/${id}/${crypto.randomUUID()}.${ext}`;
}

async function carpetaTemporal() {
  const padre = await fsp.mkdtemp(path.join(os.tmpdir(), 'mm-disk-'));
  return { padre, root: path.join(padre, 'MEDIA_ROOT_SECRETO') };
}

async function nombresEn(dir) {
  const salida = [];
  async function recorrer(actual) {
    let entradas;
    try {
      entradas = await fsp.readdir(actual, { withFileTypes: true });
    } catch (error) {
      if (error.code === 'ENOENT') return;
      throw error;
    }
    for (const entrada of entradas) {
      const ruta = path.join(actual, entrada.name);
      if (entrada.isDirectory()) await recorrer(ruta);
      else salida.push(ruta);
    }
  }
  await recorrer(dir);
  return salida;
}

function ejecutar(handler, usuario, params = {}, body = {}, file = null, query = {}) {
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
    setHeader(nombre, valor) {
      this.headers[nombre] = valor;
    },
    send(buf) {
      this.body = buf;
      return this;
    }
  };
  return handler({ usuario, params, body, file, query }, res).then(() => res);
}

test('diskStorage guarda, lee y sobrevive a otro proceso', async () => {
  const { padre, root } = await carpetaTemporal();
  try {
    const storage = crearDiskStorage({ root });
    assert.equal(storage.name, 'disk');
    for (const metodo of ['subir', 'existe', 'obtener', 'urlFirmada']) {
      assert.equal(typeof memoryStorage[metodo], 'function');
      assert.equal(typeof storage[metodo], 'function');
    }
    assert.equal(memoryStorage.name, 'memory');

    const productoId = crypto.randomUUID();
    const pedidoId = crypto.randomUUID();
    const producto = claveDe('productos', productoId, 'jpg');
    const entrega = claveDe('entregas', pedidoId, 'png');
    const webp = claveDe('productos', productoId, 'webp');

    await storage.subir({ clave: producto, buffer: JPEG_MINI, contentType: 'image/jpeg' });
    await storage.subir({ clave: entrega, buffer: PNG_MINI, contentType: 'image/png' });
    await storage.subir({ clave: webp, buffer: WEBP_MINI, contentType: 'image/webp' });

    assert.equal(esClaveRemotaProducto(producto), true);
    assert.equal(esClaveRemotaEntrega(entrega), true);
    assert.deepEqual(await storage.obtener(producto), {
      buffer: JPEG_MINI,
      contentType: 'image/jpeg'
    });
    assert.equal((await storage.obtener(entrega)).contentType, 'image/png');
    assert.equal((await storage.obtener(webp)).contentType, 'image/webp');

    const enDisco = await fsp.readFile(path.join(root, ...producto.split('/')));
    assert.deepEqual(enDisco, JPEG_MINI);
    const parciales = (await nombresEn(root)).filter((ruta) => ruta.endsWith('.partial'));
    assert.equal(parciales.length, 0);

    const firmada = await storage.urlFirmada(entrega, { ttlSeconds: 60 });
    assert.equal(firmada.proxy, true);
    assert.match(firmada.url, /^disk:\/\/megamarket\//);
    assert.equal(firmada.url.includes('MEDIA_ROOT_SECRETO'), false);
    assert.equal(firmada.url.includes(root), false);

    const reinicio = crearDiskStorage({ root });
    assert.deepEqual((await reinicio.obtener(producto)).buffer, JPEG_MINI);

    const script = path.join(padre, 'leer-otro-proceso.js');
    await fsp.writeFile(script, `
      const { crearDiskStorage } = require(${JSON.stringify(path.join(__dirname, '../src/storage/diskStorage.js'))});
      (async () => {
        const storage = crearDiskStorage({ root: ${JSON.stringify(root)} });
        const obj = await storage.obtener(${JSON.stringify(producto)});
        if (!obj || obj.contentType !== 'image/jpeg') process.exit(2);
        if (!obj.buffer.equals(Buffer.from(${JSON.stringify(JPEG_MINI.toString('base64'))}, 'base64'))) process.exit(3);
      })().catch((error) => {
        console.error(error && error.message);
        process.exit(1);
      });
    `);
    const proceso = spawnSync(process.execPath, [script], { encoding: 'utf8' });
    assert.equal(proceso.status, 0, proceso.stderr || proceso.stdout);

    const ausente = claveDe('productos', productoId);
    assert.equal(await storage.existe(ausente), false);
    assert.equal(await storage.obtener(ausente), null);
    await assert.rejects(
      () => storage.urlFirmada(ausente),
      (error) => error.codigo === 'OBJETO_NO_ENCONTRADO'
    );
  } finally {
    await fsp.rm(padre, { recursive: true, force: true });
  }
});

test('diskStorage rechaza traversal, MIME, tamaño y reemplazo inconsistente', async () => {
  const { padre, root } = await carpetaTemporal();
  const fuera = await fsp.mkdtemp(path.join(os.tmpdir(), 'mm-fuera-'));
  const secreto = path.join(fuera, 'secreto.txt');
  await fsp.writeFile(secreto, 'no-tocar');
  try {
    const base = path.resolve(os.tmpdir(), 'mm-raiz');
    assert.equal(rutaContenida(base, path.join(base, 'productos', 'a.jpg')), true);
    assert.equal(rutaContenida(base, path.resolve(base, '..', 'secreto.jpg')), false);
    assert.equal(rutaContenida(base, base), false);
    assert.equal(rutaContenida(base, path.join(base + '-evil', 'a.jpg')), false);

    assert.throws(
      () => crearDiskStorage({ root: '   ' }),
      (error) => error.codigo === 'STORAGE_CONFIG'
    );
    assert.throws(
      () => crearDiskStorage({ root: path.parse(os.tmpdir()).root }),
      (error) => error.codigo === 'STORAGE_CONFIG'
    );

    const storage = crearDiskStorage({ root });
    const productoId = crypto.randomUUID();
    const clave = claveDe('productos', productoId);
    await storage.subir({ clave, buffer: JPEG_MINI, contentType: 'image/jpeg' });
    await assert.rejects(
      () => storage.subir({ clave, buffer: JPEG_MINI, contentType: 'image/jpeg' }),
      (error) => error.codigo === 'OBJETO_EXISTENTE'
    );
    assert.deepEqual((await storage.obtener(clave)).buffer, JPEG_MINI);

    const invalidaPng = claveDe('productos', productoId, 'png');
    await assert.rejects(
      () => storage.subir({ clave: invalidaPng, buffer: JPEG_MINI, contentType: 'image/jpeg' }),
      (error) => error.codigo === 'MIME_INVALIDO'
    );
    assert.equal(await storage.existe(invalidaPng), false);

    const pequeno = crearDiskStorage({ root, maxBytes: 4 });
    await assert.rejects(
      () => pequeno.subir({
        clave: claveDe('productos', productoId),
        buffer: JPEG_MINI,
        contentType: 'image/jpeg'
      }),
      (error) => error.codigo === 'ARCHIVO_DEMASIADO_GRANDE'
    );
    await assert.rejects(
      () => storage.subir({ clave, buffer: Buffer.alloc(0), contentType: 'image/jpeg' }),
      (error) => error.codigo === 'ARCHIVO_INVALIDO' || error.codigo === 'OBJETO_EXISTENTE'
    );

    const malignas = [
      '../secreto.txt',
      `productos/${productoId}/../../secreto.txt`,
      `entregas\\..\\..\\secreto.txt`,
      `${clave}\0.jpg`,
      secreto,
      'productos/no-es-uuid/archivo.jpg',
      ''
    ];
    for (const mala of malignas) {
      await assert.rejects(
        () => storage.subir({ clave: mala, buffer: JPEG_MINI, contentType: 'image/jpeg' }),
        (error) => error.codigo === 'CLAVE_INVALIDA' || error.codigo === 'PATH_TRAVERSAL'
      );
      await assert.rejects(
        () => storage.eliminar(mala),
        (error) => error.codigo === 'CLAVE_INVALIDA' || error.codigo === 'PATH_TRAVERSAL'
      );
    }
    assert.equal(await fsp.readFile(secreto, 'utf8'), 'no-tocar');
    assert.equal((await fsp.readdir(fuera)).length, 1);

    const hermana = claveDe('productos', productoId, 'png');
    await storage.subir({ clave: hermana, buffer: PNG_MINI, contentType: 'image/png' });
    assert.equal(await storage.existe(clave), true);
    await storage.eliminar(clave);
    assert.equal(await storage.existe(clave), false);
    assert.deepEqual((await storage.obtener(hermana)).buffer, PNG_MINI);
    await assert.rejects(
      () => storage.eliminar(clave),
      (error) => error.codigo === 'OBJETO_NO_ENCONTRADO'
    );

    const dir = path.join(root, 'productos', productoId);
    const basura = path.join(dir, `.basura.${crypto.randomUUID()}.partial`);
    await fsp.writeFile(basura, 'incompleto');
    await storage.preparar();
    await assert.rejects(() => fsp.stat(basura), (error) => error.code === 'ENOENT');
    assert.equal(await storage.existe(hermana), true);

    try {
      const enlace = path.join(root, 'entregas');
      await fsp.symlink(fuera, enlace, 'junction');
      await assert.rejects(
        () => storage.subir({
          clave: claveDe('entregas', crypto.randomUUID()),
          buffer: JPEG_MINI,
          contentType: 'image/jpeg'
        }),
        (error) => error.codigo === 'PATH_TRAVERSAL' || error.codigo === 'CLAVE_INVALIDA'
      );
      assert.equal(await fsp.readFile(secreto, 'utf8'), 'no-tocar');
      assert.deepEqual(await fsp.readdir(fuera), ['secreto.txt']);
    } catch (error) {
      if (!['EPERM', 'EINVAL', 'ENOTSUP', 'EACCES'].includes(error.code)) throw error;
    }
  } finally {
    await fsp.rm(padre, { recursive: true, force: true });
    await fsp.rm(fuera, { recursive: true, force: true });
  }
});

test('diskStorage informa errores de disco y no deja archivos a medias', async () => {
  const { padre, root } = await carpetaTemporal();
  try {
    const bueno = crearDiskStorage({ root });
    const productoId = crypto.randomUUID();
    const clave = claveDe('productos', productoId);
    await bueno.subir({ clave, buffer: JPEG_MINI, contentType: 'image/jpeg' });

    const sinEspacio = Object.create(fsp);
    sinEspacio.open = async () => {
      const error = new Error('sin espacio');
      error.code = 'ENOSPC';
      throw error;
    };
    const lleno = crearDiskStorage({ root, io: sinEspacio });
    const nueva = claveDe('productos', productoId);
    await assert.rejects(
      () => lleno.subir({ clave: nueva, buffer: JPEG_MINI, contentType: 'image/jpeg' }),
      (error) => error.codigo === 'STORAGE_ERROR'
        && error.causaCodigo === 'ENOSPC'
        && !error.message.includes('MEDIA_ROOT_SECRETO')
    );
    assert.equal(await bueno.existe(nueva), false);
    assert.deepEqual((await bueno.obtener(clave)).buffer, JPEG_MINI);

    const corte = Object.create(fsp);
    corte.open = async (ruta, flags, mode) => {
      const handle = await fsp.open(ruta, flags, mode);
      const escribir = handle.writeFile.bind(handle);
      handle.writeFile = async () => {
        await escribir(Buffer.from('parcial'));
        const error = new Error('corte de escritura');
        error.code = 'EIO';
        throw error;
      };
      return handle;
    };
    const inestable = crearDiskStorage({ root, io: corte });
    const aMedias = claveDe('entregas', crypto.randomUUID());
    await assert.rejects(
      () => inestable.subir({ clave: aMedias, buffer: JPEG_MINI, contentType: 'image/jpeg' }),
      (error) => error.codigo === 'STORAGE_ERROR' && error.causaCodigo === 'EIO'
    );
    const restos = (await nombresEn(root)).filter((ruta) => ruta.endsWith('.partial'));
    assert.equal(restos.length, 0);
    assert.equal(await bueno.existe(aMedias), false);

    const lectura = Object.create(fsp);
    lectura.readFile = async () => {
      const error = new Error('fallo de lectura');
      error.code = 'EIO';
      throw error;
    };
    const ciego = crearDiskStorage({ root, io: lectura });
    assert.equal(await ciego.existe(clave), true);
    await assert.rejects(
      () => ciego.obtener(clave),
      (error) => error.codigo === 'STORAGE_ERROR' && error.causaCodigo === 'EIO'
    );
    assert.deepEqual((await bueno.obtener(clave)).buffer, JPEG_MINI);
  } finally {
    await fsp.rm(padre, { recursive: true, force: true });
  }
});

test('el código fuente no publica MEDIA_ROOT y el selector respeta memory', () => {
  const src = path.join(__dirname, '../src');
  const pendientes = [src];
  while (pendientes.length) {
    const actual = pendientes.pop();
    for (const entrada of fs.readdirSync(actual, { withFileTypes: true })) {
      const ruta = path.join(actual, entrada.name);
      if (entrada.isDirectory()) pendientes.push(ruta);
      else if (entrada.name.endsWith('.js')) {
        assert.equal(fs.readFileSync(ruta, 'utf8').includes('express.static('), false);
      }
    }
  }

  const modulo = path.join(__dirname, '../src/storage/index.js');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mm-sel-'));
  try {
    const disco = spawnSync(process.execPath, ['-e', `
      process.env.MEDIA_STORAGE = 'disk';
      process.env.MEDIA_ROOT = ${JSON.stringify(root)};
      const { getStorage, resetStorageForTests } = require(${JSON.stringify(modulo)});
      resetStorageForTests();
      if (getStorage().name !== 'disk') process.exit(2);
    `], { encoding: 'utf8' });
    assert.equal(disco.status, 0, disco.stderr);

    const memoria = spawnSync(process.execPath, ['-e', `
      delete process.env.MEDIA_STORAGE;
      delete process.env.MEDIA_ROOT;
      const { getStorage, resetStorageForTests } = require(${JSON.stringify(modulo)});
      resetStorageForTests();
      if (getStorage().name !== 'memory') process.exit(2);
    `], { encoding: 'utf8' });
    assert.equal(memoria.status, 0, memoria.stderr);

    const raro = spawnSync(process.execPath, ['-e', `
      process.env.MEDIA_STORAGE = 'firebase';
      const { getStorage, resetStorageForTests } = require(${JSON.stringify(modulo)});
      resetStorageForTests();
      try { getStorage(); process.exit(2); }
      catch (error) { if (error.codigo !== 'STORAGE_CONFIG') process.exit(3); }
    `], { encoding: 'utf8' });
    assert.equal(raro.status, 0, raro.stderr);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

async function conEsquema(fn) {
  const schema = 'megamarket_dk_' + crypto.randomBytes(6).toString('hex');
  const { padre, root } = await carpetaTemporal();
  const storage = crearDiskStorage({ root });
  const admin = new Pool({ connectionString: process.env.TEST_DATABASE_URL });
  const databasePath = require.resolve('../src/database');
  const original = require.cache[databasePath];
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
    require.cache[databasePath] = {
      id: databasePath,
      filename: databasePath,
      loaded: true,
      exports: pool
    };
    delete require.cache[require.resolve('../src/controllers/medios.controller')];
    setStorageForTests(storage);
    const medios = require('../src/controllers/medios.controller');
    await fn(pool, medios, storage, root);
  } finally {
    resetStorageForTests();
    delete require.cache[require.resolve('../src/controllers/medios.controller')];
    if (original) require.cache[databasePath] = original;
    else delete require.cache[databasePath];
    if (pool) await pool.end();
    if (creado) await admin.query(`DROP SCHEMA "${schema}" CASCADE`);
    await admin.end();
    await fsp.rm(padre, { recursive: true, force: true });
  }
}

function archivo(buffer, nombre, mime) {
  return { buffer, mimetype: mime, originalname: nombre, size: buffer.length };
}

test('PostgreSQL disk: producto, reemplazo sin borrar y referencia en Neon', { skip }, async () => {
  await conEsquema(async (pool, medios, storage, root) => {
    const tag = crypto.randomBytes(3).toString('hex');
    const { rows: [admin] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES($1,$1,$1,'x','ADMINISTRADOR') RETURNING id`,
      [`adm_${tag}`]
    );
    const { rows: [cliente] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
      [`cl_${tag}`]
    );
    const { rows: [cat] } = await pool.query(
      `INSERT INTO categorias(nombre) VALUES($1) RETURNING id`,
      [`C_${tag}`]
    );
    const localKey = `${Date.now()}.jpg`;
    const { rows: [producto] } = await pool.query(
      `INSERT INTO productos(nombre,marca,descripcion,categoria_id,precio_centimos,stock,imagen_key)
       VALUES($1,'M','d',$2,100,5,$3) RETURNING id, imagen_key`,
      [`P_${tag}`, cat.id, localKey]
    );

    const local = await ejecutar(
      medios.obtenerProducto,
      { id: cliente.id, rol: 'CLIENTE' },
      { productoId: producto.id }
    );
    assert.equal(local.code, 404);
    assert.equal(local.body.codigo, 'IMAGEN_LOCAL');

    const mime = await ejecutar(
      medios.subirProducto,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { productoId: producto.id },
      {},
      archivo(Buffer.from('no-image'), 'x.jpg', 'image/jpeg')
    );
    assert.equal(mime.code, 400);
    assert.equal(mime.body.codigo, 'MIME_INVALIDO');
    assert.equal((await nombresEn(root)).length, 0);

    const validacion = validarArchivoImagen({
      buffer: Buffer.alloc(MAX_BYTES + 1, 0xff),
      originalname: 'grande.jpg',
      mimetype: 'image/jpeg'
    });
    assert.equal(validacion.codigo, 'ARCHIVO_DEMASIADO_GRANDE');

    const subida = await ejecutar(
      medios.subirProducto,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { productoId: producto.id },
      {},
      archivo(JPEG_MINI, '..\\..\\Windows\\evil.jpg', 'image/jpeg')
    );
    assert.equal(subida.code, 201);
    assert.equal(subida.body.eliminadaAnterior, false);
    assert.equal(subida.body.imagenAnterior, localKey);
    assert.equal(esClaveRemotaProducto(subida.body.imagenKey), true);
    assert.equal(subida.body.imagenKey.includes('evil'), false);
    assert.equal(subida.body.imagenKey.includes('Windows'), false);
    assert.equal(JSON.stringify(subida.body).includes('MEDIA_ROOT_SECRETO'), false);
    await assert.rejects(
      () => storage.existe(localKey),
      (error) => error.codigo === 'CLAVE_INVALIDA'
    );

    const bytes = await fsp.readFile(path.join(root, ...subida.body.imagenKey.split('/')));
    assert.deepEqual(bytes, JPEG_MINI);
    const { rows: [fila] } = await pool.query(
      `SELECT imagen_key FROM productos WHERE id = $1`,
      [producto.id]
    );
    assert.equal(fila.imagen_key, subida.body.imagenKey);

    const descarga = await ejecutar(
      medios.obtenerProducto,
      { id: cliente.id, rol: 'CLIENTE' },
      { productoId: producto.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(descarga.code, 200);
    assert.deepEqual(descarga.body, JPEG_MINI);

    const meta = await ejecutar(
      medios.obtenerProducto,
      { id: cliente.id, rol: 'CLIENTE' },
      { productoId: producto.id }
    );
    assert.equal(meta.code, 200);
    assert.equal(meta.body.proxy, true);
    assert.match(meta.body.url, /^disk:\/\/megamarket\//);
    assert.equal(meta.body.url.includes(root), false);

    const reemplazo = await ejecutar(
      medios.subirProducto,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { productoId: producto.id },
      {},
      archivo(PNG_MINI, 'otra.png', 'image/png')
    );
    assert.equal(reemplazo.code, 201);
    assert.notEqual(reemplazo.body.imagenKey, subida.body.imagenKey);
    assert.equal(await storage.existe(subida.body.imagenKey), true);
    assert.equal(await storage.existe(reemplazo.body.imagenKey), true);
    assert.deepEqual(
      await fsp.readFile(path.join(root, ...subida.body.imagenKey.split('/'))),
      JPEG_MINI
    );
    const { rows: [nuevaFila] } = await pool.query(
      `SELECT imagen_key FROM productos WHERE id = $1`,
      [producto.id]
    );
    assert.equal(nuevaFila.imagen_key, reemplazo.body.imagenKey);

    const trasReinicio = crearDiskStorage({ root });
    assert.deepEqual((await trasReinicio.obtener(reemplazo.body.imagenKey)).buffer, PNG_MINI);

    const io = Object.create(fsp);
    io.open = async () => {
      const error = new Error('sin espacio');
      error.code = 'ENOSPC';
      throw error;
    };
    setStorageForTests(crearDiskStorage({ root, io }));
    const lleno = await ejecutar(
      medios.subirProducto,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { productoId: producto.id },
      {},
      archivo(JPEG_MINI, 'otra.jpg', 'image/jpeg')
    );
    assert.equal(lleno.code, 500);
    assert.equal(lleno.body.codigo, 'STORAGE_ERROR');
    const { rows: [sigue] } = await pool.query(
      `SELECT imagen_key FROM productos WHERE id = $1`,
      [producto.id]
    );
    assert.equal(sigue.imagen_key, reemplazo.body.imagenKey);
    assert.equal((await nombresEn(root)).some((ruta) => ruta.endsWith('.partial')), false);

    setStorageForTests(storage);
    await pool.query(`UPDATE productos SET imagen_key = $2 WHERE id = $1`, [
      producto.id,
      claveDe('productos', producto.id)
    ]);
    const ausente = await ejecutar(
      medios.obtenerProducto,
      { id: cliente.id, rol: 'CLIENTE' },
      { productoId: producto.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(ausente.code, 404);
    assert.equal(ausente.body.codigo, 'OBJETO_NO_ENCONTRADO');

    await pool.query(`UPDATE productos SET imagen_key = $2 WHERE id = $1`, [
      producto.id,
      '../secreto.txt'
    ]);
    const traversal = await ejecutar(
      medios.obtenerProducto,
      { id: cliente.id, rol: 'CLIENTE' },
      { productoId: producto.id }
    );
    assert.equal(traversal.code, 409);
    assert.equal(traversal.body.codigo, 'IMAGEN_KEY_INVALIDA');
  });
});

test('PostgreSQL disk: entrega privada, roles y errores de lectura', { skip }, async () => {
  await conEsquema(async (pool, medios, storage, root) => {
    const tag = crypto.randomBytes(3).toString('hex');
    const { rows: [cliente] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
      [`cl_${tag}`]
    );
    const { rows: [ajenoCliente] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES($1,$1,$1,'x','CLIENTE') RETURNING id`,
      [`c2_${tag}`]
    );
    const { rows: [admin] } = await pool.query(
      `INSERT INTO usuarios(nombre,usuario,correo,clave_hash,rol)
       VALUES($1,$1,$1,'x','ADMINISTRADOR') RETURNING id`,
      [`adm_${tag}`]
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
       VALUES($1,$2,100,'EN_CAMINO',$3,'h') RETURNING id`,
      [cliente.id, crypto.randomUUID(), repartidor.id]
    );

    const denegado = await ejecutar(
      medios.subirEntrega,
      { id: ajeno.id, rol: 'REPARTIDOR' },
      { pedidoId: pedido.id },
      {},
      archivo(JPEG_MINI, 'foto.jpg', 'image/jpeg')
    );
    assert.equal(denegado.code, 403);
    assert.equal((await nombresEn(root)).length, 0);

    const ok = await ejecutar(
      medios.subirEntrega,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      { pedidoId: pedido.id },
      {},
      archivo(JPEG_MINI, '..\\..\\privada.jpg', 'image/jpeg')
    );
    assert.equal(ok.code, 201);
    assert.equal(esClaveRemotaEntrega(ok.body.fotoReferencia), true);
    assert.equal(ok.body.fotoReferencia.includes('privada'), false);
    assert.deepEqual(
      await fsp.readFile(path.join(root, ...ok.body.fotoReferencia.split('/'))),
      JPEG_MINI
    );

    const sinRegistro = await ejecutar(
      medios.obtenerEntrega,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(sinRegistro.code, 404);

    await pool.query(
      `INSERT INTO pagos(uuid_operacion, pedido_id, monto_centimos, moneda, medio_pago, modalidad, estado,
         numero_operacion_banco, fecha_confirmacion)
       VALUES($1,$2,100,'PEN','QR','CONTRAENTREGA','CONFIRMADO','OP1',now())`,
      [crypto.randomUUID(), pedido.id]
    );
    await pool.query(
      `INSERT INTO entregas(uuid_operacion, pedido_id, repartidor_id, foto_referencia, latitud, longitud)
       VALUES($1,$2,$3,$4,0,0)`,
      [crypto.randomUUID(), pedido.id, repartidor.id, ok.body.fotoReferencia]
    );
    await pool.query(`UPDATE pedidos SET estado = 'ENTREGADO' WHERE id = $1`, [pedido.id]);

    const lecturaAjena = await ejecutar(
      medios.obtenerEntrega,
      { id: ajeno.id, rol: 'REPARTIDOR' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(lecturaAjena.code, 403);
    const otroCliente = await ejecutar(
      medios.obtenerEntrega,
      { id: ajenoCliente.id, rol: 'CLIENTE' },
      { pedidoId: pedido.id }
    );
    assert.equal(otroCliente.code, 403);

    const dueno = await ejecutar(
      medios.obtenerEntrega,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(dueno.code, 200);
    assert.deepEqual(dueno.body, JPEG_MINI);
    assert.equal(dueno.headers['Cache-Control'], 'private, no-store');

    const meta = await ejecutar(
      medios.obtenerEntrega,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { pedidoId: pedido.id }
    );
    assert.equal(meta.code, 200);
    assert.equal(meta.body.privado, true);
    assert.match(meta.body.url, /^disk:\/\/megamarket\//);
    assert.equal(JSON.stringify(meta.body).includes('MEDIA_ROOT_SECRETO'), false);
    assert.equal(JSON.stringify(meta.body).includes(root), false);

    const adminBytes = await ejecutar(
      medios.obtenerEntrega,
      { id: admin.id, rol: 'ADMINISTRADOR' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(adminBytes.code, 200);
    assert.deepEqual(adminBytes.body, JPEG_MINI);

    const cerrado = await ejecutar(
      medios.subirEntrega,
      { id: repartidor.id, rol: 'REPARTIDOR' },
      { pedidoId: pedido.id },
      {},
      archivo(JPEG_MINI, 'otra.jpg', 'image/jpeg')
    );
    assert.equal(cerrado.code, 409);
    assert.equal(cerrado.body.codigo, 'ENTREGA_CERRADA');
    assert.equal(await storage.existe(ok.body.fotoReferencia), true);

    const reinicio = crearDiskStorage({ root });
    assert.deepEqual((await reinicio.obtener(ok.body.fotoReferencia)).buffer, JPEG_MINI);

    await pool.query(`UPDATE entregas SET foto_referencia = $2 WHERE pedido_id = $1`, [
      pedido.id,
      '../secreto.txt'
    ]);
    const noRemota = await ejecutar(
      medios.obtenerEntrega,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(noRemota.code, 404);
    assert.equal(noRemota.body.codigo, 'FOTO_NO_REMOTA');

    await pool.query(`UPDATE entregas SET foto_referencia = $2 WHERE pedido_id = $1`, [
      pedido.id,
      claveDe('entregas', pedido.id)
    ]);
    const sinArchivo = await ejecutar(
      medios.obtenerEntrega,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(sinArchivo.code, 404);
    assert.equal(sinArchivo.body.codigo, 'OBJETO_NO_ENCONTRADO');

    await pool.query(`UPDATE entregas SET foto_referencia = $2 WHERE pedido_id = $1`, [
      pedido.id,
      ok.body.fotoReferencia
    ]);
    const io = Object.create(fsp);
    io.readFile = async () => {
      const error = new Error('fallo de lectura');
      error.code = 'EIO';
      throw error;
    };
    setStorageForTests(crearDiskStorage({ root, io }));
    const lecturaRota = await ejecutar(
      medios.obtenerEntrega,
      { id: cliente.id, rol: 'CLIENTE' },
      { pedidoId: pedido.id },
      {},
      null,
      { download: '1' }
    );
    assert.equal(lecturaRota.code, 500);
    assert.equal(await storage.existe(ok.body.fotoReferencia), true);

    const { validarEvidencia } = require('../src/entregas');
    const ajena = validarEvidencia({
      fotoReferencia: `entregas/${crypto.randomUUID()}/${crypto.randomUUID()}.jpg`,
      latitud: 0,
      longitud: 0,
      pedidoId: pedido.id
    });
    assert.equal(ajena.codigo, 'FOTO_AJENA');
  });
});
