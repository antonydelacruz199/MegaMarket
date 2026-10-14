const { Pool } = require('pg');

const databaseUrl = process.env.DATABASE_URL;

if (!databaseUrl || !String(databaseUrl).trim()) {
  console.error('Falta DATABASE_URL en api-rest/.env');
}

const pool = new Pool({
  connectionString: databaseUrl,
  ssl: databaseUrl ? { rejectUnauthorized: false } : undefined
});

pool.on('error', (error) => {
  console.error('Error inesperado del pool PostgreSQL:', error.message);
});

module.exports = pool;
