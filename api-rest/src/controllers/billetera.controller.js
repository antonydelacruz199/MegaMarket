const pool = require('../database');
const {
  mapearCuenta,
  asegurarCuentaCliente,
  asegurarCuentaComercio,
  listarMovimientosCuenta,
  SALDO_INICIAL_CLIENTE_CENTIMOS,
  SALDO_INICIAL_COMERCIO_CENTIMOS
} = require('../billetera');

async function saldoPropio(req, res) {
  const clienteDb = await pool.connect();
  try {
    if (req.usuario.rol === 'CLIENTE') {
      await clienteDb.query('BEGIN');
      const cuenta = await asegurarCuentaCliente(clienteDb, req.usuario.id);
      await clienteDb.query('COMMIT');
      return res.status(200).json({
        cuenta: mapearCuenta(cuenta),
        saldoInicialCentimos: SALDO_INICIAL_CLIENTE_CENTIMOS,
        simulacion: true
      });
    }

    if (req.usuario.rol === 'ADMINISTRADOR') {
      await clienteDb.query('BEGIN');
      const cuenta = await asegurarCuentaComercio(clienteDb);
      await clienteDb.query('COMMIT');
      return res.status(200).json({
        cuenta: mapearCuenta(cuenta),
        saldoInicialCentimos: SALDO_INICIAL_COMERCIO_CENTIMOS,
        etiqueta: 'MegaMarket comercio',
        simulacion: true
      });
    }

    return res.status(403).json({
      error: 'Solo cliente o administrador consultan saldo',
      codigo: 'PERMISO_DENEGADO'
    });
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al consultar saldo:', error.message);
    return res.status(500).json({ error: 'No se pudo consultar el saldo' });
  } finally {
    clienteDb.release();
  }
}

async function movimientosPropios(req, res) {
  const clienteDb = await pool.connect();
  try {
    const limite = Number(req.query.limite || 50);
    let cuenta;
    if (req.usuario.rol === 'CLIENTE') {
      await clienteDb.query('BEGIN');
      cuenta = await asegurarCuentaCliente(clienteDb, req.usuario.id);
      await clienteDb.query('COMMIT');
    } else if (req.usuario.rol === 'ADMINISTRADOR') {
      await clienteDb.query('BEGIN');
      cuenta = await asegurarCuentaComercio(clienteDb);
      await clienteDb.query('COMMIT');
    } else {
      return res.status(403).json({
        error: 'Solo cliente o administrador consultan movimientos',
        codigo: 'PERMISO_DENEGADO'
      });
    }

    const movimientos = await listarMovimientosCuenta(pool, cuenta.id, limite);
    return res.status(200).json({
      cuenta: mapearCuenta(cuenta),
      movimientos,
      simulacion: true
    });
  } catch (error) {
    try {
      await clienteDb.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    console.error('Error al listar movimientos:', error.message);
    return res.status(500).json({ error: 'No se pudieron listar los movimientos' });
  } finally {
    clienteDb.release();
  }
}

/**
 * Admin: saldo comercial explícito (alias semántico).
 */
async function saldoComercio(req, res) {
  if (req.usuario.rol !== 'ADMINISTRADOR') {
    return res.status(403).json({ error: 'Solo administrador', codigo: 'PERMISO_DENEGADO' });
  }
  return saldoPropio(req, res);
}

module.exports = {
  saldoPropio,
  movimientosPropios,
  saldoComercio
};
