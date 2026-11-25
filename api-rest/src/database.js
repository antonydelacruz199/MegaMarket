const dns = require('dns');
const net = require('net');
const { Pool } = require('pg');
const { textoError } = require('./erroresDb');

const INTENTO_TCP_MS = 2500;
const CACHE_IP_MS = 30000;
const cacheIp = new Map();
let avisoIp = false;

dns.setDefaultResultOrder('ipv4first');

function probarTcp(ip, port) {
  return new Promise((resolve) => {
    const socket = net.connect({ host: ip, port, family: 4 });
    const terminar = (ok) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(INTENTO_TCP_MS);
    socket.once('connect', () => terminar(true));
    socket.once('timeout', () => terminar(false));
    socket.once('error', () => terminar(false));
  });
}

async function elegirIpv4(host, port) {
  const guardada = cacheIp.get(host);
  if (guardada && guardada.expira > Date.now()) return guardada.ip;

  let ips = [];
  try {
    ips = await dns.promises.resolve4(host);
  } catch (_error) {
    return host;
  }
  if (!ips.length) return host;

  for (let i = 0; i < ips.length; i += 1) {
    const ip = ips[i];
    if (await probarTcp(ip, port)) {
      if (i > 0 && !avisoIp) {
        avisoIp = true;
        console.warn(
          `PostgreSQL: ${host} no respondió en la primera dirección. Se usa ${ip}.`
        );
      }
      cacheIp.set(host, { ip, expira: Date.now() + CACHE_IP_MS });
      return ip;
    }
  }
  return ips[0];
}

function crearSocket() {
  const socket = new net.Socket();
  const conectar = socket.connect.bind(socket);
  socket.connect = function connectConFailover(port, host) {
    if (typeof host !== 'string' || net.isIP(host)) {
      return conectar(port, host);
    }
    elegirIpv4(host, Number(port))
      .then((ip) => {
        if (socket.destroyed) return;
        conectar({ port: Number(port), host: ip, family: 4 });
      })
      .catch((error) => {
        if (!socket.destroyed) socket.destroy(error);
      });
    return socket;
  };
  return socket;
}

function connectionStringConSslCompatible(url) {
  const valor = String(url || '').trim();
  if (!valor) return '';
  if (/[?&]uselibpqcompat=/i.test(valor)) return valor;
  if (!/[?&]sslmode=(prefer|require|verify-ca)\b/i.test(valor)) return valor;
  return `${valor}${valor.includes('?') ? '&' : '?'}uselibpqcompat=true`;
}

function anotarError(error) {
  if (error && !String(error.message || '').trim()) {
    error.message = textoError(error);
  }
  return error;
}

function envolverQuery(objetivo) {
  const original = objetivo.query.bind(objetivo);
  objetivo.query = function queryConDetalle(...args) {
    const resultado = original(...args);
    if (!resultado || typeof resultado.catch !== 'function') return resultado;
    return resultado.catch((error) => {
      throw anotarError(error);
    });
  };
}

const databaseUrl = connectionStringConSslCompatible(process.env.DATABASE_URL);

if (!databaseUrl) {
  console.error('Falta DATABASE_URL en api-rest/.env');
}

const pool = new Pool({
  connectionString: databaseUrl || undefined,
  ssl: databaseUrl ? { rejectUnauthorized: false } : undefined,
  connectionTimeoutMillis: 20000,
  keepAlive: true,
  max: 10,
  stream: crearSocket
});

pool.on('error', (error) => {
  console.error('Error inesperado del pool PostgreSQL:', textoError(error));
});

envolverQuery(pool);

const conectar = pool.connect.bind(pool);
pool.connect = function connectConDetalle(...args) {
  const resultado = conectar(...args);
  if (!resultado || typeof resultado.then !== 'function') return resultado;
  return resultado
    .then((cliente) => {
      envolverQuery(cliente);
      return cliente;
    })
    .catch((error) => {
      throw anotarError(error);
    });
};

module.exports = pool;
