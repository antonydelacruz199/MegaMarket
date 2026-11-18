/**
 * Job de servidor: resuelve sustituciones vencidas sin apps ni admin.
 * Reutiliza resolverExpiradas (misma TX + FOR UPDATE + preferencia del pedido).
 * Concurrencia entre instancias: pg_try_advisory_lock.
 */

const pool = require('../database');

/** Clave estable de advisory lock (no depende de tablas nuevas). */
const ADVISORY_LOCK_KEY = 75245025;

function intervaloMs() {
  const raw = Number(process.env.SUSTITUCIONES_JOB_MS);
  if (Number.isFinite(raw) && raw >= 1000) return raw;
  return 15000;
}

function jobHabilitado() {
  const flag = String(process.env.SUSTITUCIONES_JOB || '1').trim().toLowerCase();
  return !['0', 'false', 'off', 'no'].includes(flag);
}

/**
 * Un ciclo: lock → BEGIN → resolverExpiradas → COMMIT → unlock.
 * Idempotente: filas ya decididas no se vuelven a procesar.
 */
async function ejecutarCicloExpiradas() {
  // Carga diferida para evitar ciclos al importar el controller en tests.
  const { resolverExpiradas } = require('../controllers/sustituciones.controller');
  const cliente = await pool.connect();
  let locked = false;
  try {
    const { rows } = await cliente.query(
      `SELECT pg_try_advisory_lock($1) AS ok`,
      [ADVISORY_LOCK_KEY]
    );
    if (!rows[0].ok) {
      return { skipped: true, reason: 'LOCK', resueltas: 0, sustituciones: [] };
    }
    locked = true;

    await cliente.query('BEGIN');
    const aplicados = await resolverExpiradas(cliente, {});
    await cliente.query('COMMIT');
    return {
      skipped: false,
      resueltas: aplicados.length,
      sustituciones: aplicados
    };
  } catch (error) {
    try {
      await cliente.query('ROLLBACK');
    } catch (_e) {
      // ignore
    }
    throw error;
  } finally {
    if (locked) {
      try {
        await cliente.query(`SELECT pg_advisory_unlock($1)`, [ADVISORY_LOCK_KEY]);
      } catch (_e) {
        // ignore
      }
    }
    cliente.release();
  }
}

/**
 * Arranca setInterval en el proceso Node del API.
 * @returns {{ stop: Function, intervaloMs: number } | null}
 */
function iniciarSchedulerExpiradas(opciones = {}) {
  if (!jobHabilitado() && !opciones.forzar) {
    console.log('Job sustituciones expiradas deshabilitado (SUSTITUCIONES_JOB=0)');
    return null;
  }
  const ms = opciones.intervalMs || intervaloMs();
  let corriendo = false;

  const tick = async () => {
    if (corriendo) return;
    corriendo = true;
    try {
      const resultado = await ejecutarCicloExpiradas();
      if (!resultado.skipped && resultado.resueltas > 0) {
        console.log(
          `Sustituciones expiradas resueltas: ${resultado.resueltas}`
        );
      }
    } catch (error) {
      console.error('Job sustituciones expiradas:', error.message);
    } finally {
      corriendo = false;
    }
  };

  // Primer pase al arrancar (no bloquea el listen).
  setImmediate(() => {
    tick().catch(() => {});
  });

  const timer = setInterval(() => {
    tick().catch(() => {});
  }, ms);

  return {
    intervaloMs: ms,
    stop() {
      clearInterval(timer);
    }
  };
}

module.exports = {
  ADVISORY_LOCK_KEY,
  intervaloMs,
  jobHabilitado,
  ejecutarCicloExpiradas,
  iniciarSchedulerExpiradas
};
