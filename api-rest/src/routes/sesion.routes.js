const express = require('express');
const sesionController = require('../controllers/sesion.controller');
const verificarToken = require('../middleware/verificarToken');

const router = express.Router();

router.post('/login', sesionController.login);
router.get('/me', verificarToken, sesionController.me);

module.exports = router;
