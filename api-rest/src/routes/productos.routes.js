const express = require('express');
const productosController = require('../controllers/productos.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.get('/', verificarToken, productosController.listar);
router.get('/:id', verificarToken, productosController.obtenerPorId);

router.post(
  '/',
  verificarToken,
  verificarRol('ADMINISTRADOR'),
  productosController.crear
);
router.patch(
  '/:id',
  verificarToken,
  verificarRol('ADMINISTRADOR'),
  productosController.actualizar
);
router.delete(
  '/:id',
  verificarToken,
  verificarRol('ADMINISTRADOR'),
  productosController.eliminar
);

module.exports = router;
