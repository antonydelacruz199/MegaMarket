function textoError(error) {
  if (!error) return 'error desconocido';
  const vistos = new Set();
  const partes = [];

  const visitar = (actual, profundidad) => {
    if (!actual || profundidad > 5 || vistos.has(actual)) return;
    vistos.add(actual);
    const mensaje = typeof actual.message === 'string' ? actual.message.trim() : '';
    const codigo = actual.code ? String(actual.code) : '';
    const linea = [codigo, mensaje].filter(Boolean).join(' ');
    if (linea && !partes.includes(linea)) partes.push(linea);
    if (Array.isArray(actual.errors)) {
      actual.errors.forEach((item) => visitar(item, profundidad + 1));
    }
    if (actual.cause) visitar(actual.cause, profundidad + 1);
  };

  visitar(error, 0);
  return partes.join(' | ') || String(error);
}

module.exports = { textoError };
