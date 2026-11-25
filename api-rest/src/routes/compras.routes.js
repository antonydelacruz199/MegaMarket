const express = require('express');
const comprasController = require('../controllers/compras.controller');
const verificarToken = require('../middleware/verificarToken');
const verificarRol = require('../middleware/verificarRol');

const router = express.Router();

router.use(verificarToken, verificarRol('ADMINISTRADOR'));

router.get('/ordenes', comprasController.listarOrdenes);
router.get('/ordenes/:id', comprasController.obtenerOrden);
router.post('/ordenes', comprasController.crearOrden);
router.post('/ordenes/:id/aprobar', comprasController.aprobarOrden);
router.post('/ordenes/:id/cancelar', comprasController.cancelarOrden);
router.post('/ordenes/:id/pago', comprasController.pagarOrden);
router.post('/ordenes/:id/recepciones', comprasController.registrarRecepcion);
router.get('/ordenes/:id/recepciones', comprasController.historialRecepciones);
router.get(
  '/transferencias/:uuidOperacion',
  comprasController.consultarTransferencia
);

module.exports = router;
