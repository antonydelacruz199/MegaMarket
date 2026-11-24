/**
 * Paginación compartida para listados (page 1-based).
 * @param {import('express').Request} req
 * @param {{ defaultLimit?: number, maxLimit?: number }} [opts]
 */
function paginar(req, opts = {}) {
  const defaultLimit = opts.defaultLimit ?? 20;
  const maxLimit = opts.maxLimit ?? 50;
  const pageRaw = Number(req.query.page ?? req.query.pagina ?? 1);
  const limitRaw = Number(req.query.limit ?? req.query.limite ?? defaultLimit);
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? Math.floor(pageRaw) : 1;
  const limit = Math.min(
    Math.max(Number.isFinite(limitRaw) ? Math.floor(limitRaw) : defaultLimit, 1),
    maxLimit
  );
  const offset = (page - 1) * limit;
  return { page, limit, offset };
}

/**
 * @param {number} total
 * @param {{ page: number, limit: number }} pagina
 */
function metaPaginacion(total, { page, limit }) {
  const totalSeguro = Math.max(0, Number(total) || 0);
  return {
    pagina: page,
    limite: limit,
    total: totalSeguro,
    hayMas: page * limit < totalSeguro
  };
}

module.exports = { paginar, metaPaginacion };
