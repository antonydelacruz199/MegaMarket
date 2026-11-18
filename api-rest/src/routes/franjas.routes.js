const express = require('express');
const franjasController = require('../controllers/franjas.controller');
const verificarToken = require('../middleware/verificarToken');

const router = express.Router();

router.get('/', verificarToken, franjasController.listar);
router.get('/:id', verificarToken, franjasController.obtenerPorId);

module.exports = router;
