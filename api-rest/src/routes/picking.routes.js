const express = require('express');
const pickingController = require('../controllers/picking.controller');
const sustitucionesController = require('../controllers/sustituciones.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken, verificarRol('PICKER'));

router.get('/pedidos', pickingController.listar);
router.get('/pedidos/:id', pickingController.obtenerPorId);
router.post(
  '/pedidos/:id/detalles/:detalleId/escaneos',
  pickingController.escanear
);
router.post(
  '/pedidos/:id/detalles/:detalleId/faltante',
  pickingController.marcarFaltante
);
router.post(
  '/pedidos/:id/detalles/:detalleId/eliminar',
  pickingController.eliminarLinea
);
router.get(
  '/pedidos/:id/detalles/:detalleId/sustitutos',
  sustitucionesController.listarCandidatos
);
router.post(
  '/pedidos/:id/detalles/:detalleId/sustituciones',
  sustitucionesController.proponer
);
router.post(
  '/pedidos/:id/detalles/:detalleId/aviso-sin-stock',
  sustitucionesController.avisarSinStock
);

module.exports = router;
