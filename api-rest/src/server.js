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
app.use('/api/pedidos', pedidosRoutes);
app.use('/api/inventario', inventarioRoutes);

app.use((_req, res) => {
  res.status(404).json({ error: 'Ruta no encontrada' });
});

app.use((error, _req, res, _next) => {
  console.error('Error no controlado:', error.message);
  res.status(500).json({ error: 'Error interno del servidor' });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`MegaMarket API escuchando en 0.0.0.0:${port}`);
});
