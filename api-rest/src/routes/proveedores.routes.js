const express = require('express');
const proveedoresController = require('../controllers/proveedores.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken, verificarRol('ADMINISTRADOR'));

router.get('/', proveedoresController.listar);
router.get('/:id/ordenes', proveedoresController.ordenesDelProveedor);
router.get('/:id', proveedoresController.obtener);
router.post('/', proveedoresController.crear);
router.patch('/:id', proveedoresController.actualizar);

module.exports = router;
