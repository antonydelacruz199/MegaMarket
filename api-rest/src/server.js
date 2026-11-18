require('dotenv').config();

const express = require('express');
const pool = require('./database');

const sesionRoutes = require('./routes/sesion.routes');
const categoriasRoutes = require('./routes/categorias.routes');
const productosRoutes = require('./routes/productos.routes');
const pedidosRoutes = require('./routes/pedidos.routes');
const inventarioRoutes = require('./routes/inventario.routes');

const app = express();
const port = Number(process.env.PORT) || 8000;

app.use(express.json());

app.get('/api/health', async (_req, res) => {
  if (!process.env.DATABASE_URL || !String(process.env.DATABASE_URL).trim()) {
    return res.status(500).json({
      ok: false,
      database: 'missing_DATABASE_URL'
    });
  }

  try {
    await pool.query('SELECT 1');
    return res.status(200).json({
      ok: true,
      database: 'connected'
    });
  } catch (error) {
    console.error('Health check falló:', error.message);
    return res.status(500).json({
      ok: false,
      database: 'disconnected'
    });
  }
});

app.use('/api/auth', sesionRoutes);
app.use('/api/categorias', categoriasRoutes);
app.use('/api/productos', productosRoutes);
app.use('/api/franjas', require('./routes/franjas.routes'));
app.use('/api/pedidos', pedidosRoutes);
app.use('/api/picking', require('./routes/picking.routes'));
app.use('/api/sustituciones', require('./routes/sustituciones.routes'));
app.use('/api/entregas', require('./routes/entregas.routes'));
app.use('/api/medios', require('./routes/medios.routes'));
app.use('/api/carritos', require('./routes/carritos.routes'));
app.use('/api/inventario', inventarioRoutes);

app.use((_req, res) => {
  res.status(404).json({ error: 'Ruta no encontrada' });
});

app.use((error, _req, res, _next) => {
  console.error('Error no controlado:', error.message);
  res.status(500).json({ error: 'Error interno del servidor' });
});

function iniciarAlmacen() {
  try {
    const { getStorage } = require('./storage');
    const storage = getStorage();
    const preparado = typeof storage.preparar === 'function'
      ? storage.preparar()
      : Promise.resolve();
    Promise.resolve(preparado).then(() => {
      console.log(`Almacén de medios: ${storage.name}`);
    }).catch((error) => {
      console.error('No se pudo preparar el almacén de medios:', error.message);
    });
  } catch (error) {
    console.error('Configuración de medios inválida:', error.message);
  }
}

app.listen(port, '0.0.0.0', () => {
  console.log(`MegaMarket API escuchando en 0.0.0.0:${port}`);
  iniciarAlmacen();
  const { iniciarSchedulerExpiradas } = require('./jobs/sustitucionesExpiradas');
  const job = iniciarSchedulerExpiradas();
  if (job) {
    console.log(
      `Job sustituciones expiradas activo cada ${job.intervaloMs} ms`
    );
  }
});
