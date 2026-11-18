const express = require('express');
const entregasController = require('../controllers/entregas.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken, verificarRol('REPARTIDOR'));

router.get('/pedidos', entregasController.listar);
router.get('/pedidos/:id', entregasController.obtenerPorId);
router.post('/pedidos/:id', entregasController.registrar);

module.exports = router;
