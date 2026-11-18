const CODIGO_MAX = 32;
const PASILLO_MAX = 40;
const CODIGO_RE = /^[0-9A-Za-z]+$/;
const PASILLO_RE = /^[0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+(?:[ -][0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$/;

function textoAlmacenado(valor) {
  if (valor === null || valor === undefined) return null;
  const texto = String(valor).trim();
  return texto === '' ? null : texto;
}

function campo(body, camel, snake) {
  if (!body || typeof body !== 'object') return undefined;
  if (Object.prototype.hasOwnProperty.call(body, camel)) return body[camel];
  if (snake && Object.prototype.hasOwnProperty.call(body, snake)) return body[snake];
  return undefined;
}

/**
 * Vacío o ausente se guarda como NULL para no chocar con el índice único
 * parcial de `codigo_barras`. Si el valor no cambia, se conserva aunque sea
 * un código antiguo.
 */
function normalizarCodigoBarras(valor, actual) {
  if (valor === undefined) return { valor: textoAlmacenado(actual) };
  if (valor === null) return { valor: null };
  const codigo = String(valor).trim();
  if (codigo === '') return { valor: null };
  const previo = textoAlmacenado(actual);
  if (previo !== null && codigo === previo) return { valor: previo };
  if (codigo.length > CODIGO_MAX) {
    return { error: `codigo_barras admite como máximo ${CODIGO_MAX} caracteres` };
  }
  if (!CODIGO_RE.test(codigo)) {
    return { error: 'codigo_barras solo admite letras y dígitos, sin espacios' };
  }
  return { valor: codigo };
}

function normalizarPasillo(valor, actual) {
  if (valor === undefined) return { valor: textoAlmacenado(actual) };
  if (valor === null) return { valor: null };
  const pasillo = String(valor).trim().replace(/\s+/g, ' ');
  if (pasillo === '') return { valor: null };
  const previo = textoAlmacenado(actual);
  if (previo !== null && pasillo === previo) return { valor: previo };
  if (pasillo.length > PASILLO_MAX) {
    return { error: `pasillo admite como máximo ${PASILLO_MAX} caracteres` };
  }
  if (!PASILLO_RE.test(pasillo)) {
    return { error: 'pasillo solo admite letras, números, espacios y guiones' };
  }
  return { valor: pasillo };
}

function leerUbicacion(body, actual = {}) {
  const codigo = normalizarCodigoBarras(
    campo(body, 'codigoBarras', 'codigo_barras'),
    actual.codigoBarras
  );
  const pasillo = normalizarPasillo(campo(body, 'pasillo'), actual.pasillo);
  const errores = [codigo.error, pasillo.error].filter(Boolean);
  return {
    errores,
    codigoBarras: errores.length ? null : codigo.valor,
    pasillo: errores.length ? null : pasillo.valor
  };
}

function esCodigoBarrasDuplicado(error) {
  const restriccion = `${error && error.constraint ? error.constraint : ''} ${
    error && error.detail ? error.detail : ''
  }`;
  return restriccion.includes('codigo_barras');
}

module.exports = {
  CODIGO_MAX,
  PASILLO_MAX,
  textoAlmacenado,
  normalizarCodigoBarras,
  normalizarPasillo,
  leerUbicacion,
  esCodigoBarrasDuplicado
};
