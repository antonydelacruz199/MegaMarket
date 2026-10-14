const express = require('express');
const categoriasController = require('../controllers/categorias.controller');
const verificarToken = require('../middleware/verificarToken');

const router = express.Router();

router.get('/', verificarToken, categoriasController.listar);

module.exports = router;
