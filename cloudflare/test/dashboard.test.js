import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import * as bundle from '../dashboard/worker.js';

test('el archivo para el panel coincide con las fuentes y solo exporta el Worker', () => {
  const source = readdirSync(new URL('../src/', import.meta.url)).filter((name) => name.endsWith('.js')).sort()
    .map((name) => `${name}\0${readFileSync(new URL(`../src/${name}`, import.meta.url), 'utf8')}`).join('\0');
  const digest = createHash('sha256').update(source).digest('hex');
  const code = readFileSync(new URL('../dashboard/worker.js', import.meta.url), 'utf8');
  assert.ok(code.includes(`// source-sha256: ${digest}`), 'Regenera el archivo con npm run build:dashboard');
  assert.deepEqual(Object.keys(bundle), ['default']);
  assert.equal(typeof bundle.default.fetch, 'function');
  assert.equal(typeof bundle.default.scheduled, 'function');
});

test('el archivo para Chrome recibe un voto, lo publica por cron y permite retirarlo', async () => {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const kv = new Map();
  const env = {
    SAL: 'sal-exclusiva-de-prueba',
    DB: { prepare(sql) {
      const stmt = db.prepare(sql);
      const execute = (args) => ({
        run: async () => ({ meta: { changes: Number(stmt.run(...args).changes) } }),
        first: async () => stmt.get(...args) ?? null,
        all: async () => ({ results: stmt.all(...args) }),
      });
      return { ...execute([]), bind: (...args) => execute(args) };
    } },
    LISTA: { get: async (key) => kv.get(key) ?? null, put: async (key, value) => { kv.set(key, value); } },
  };
  try {
    const data = { numero: '5512345678', categoria: 'estafa', instalacion: 'instalacion-prueba-panel' };
    const post = (path, body) => bundle.default.fetch(new Request(`https://example.invalid/${path}`, {
      method: 'POST', headers: { 'CF-Connecting-IP': '192.0.2.1' }, body: JSON.stringify(body),
    }), env);
    assert.equal((await post('reportar', data)).status, 200);
    let scheduled;
    bundle.default.scheduled({}, env, { waitUntil(promise) { scheduled = promise; } });
    await scheduled;
    const list = await (await bundle.default.fetch(new Request('https://example.invalid/lista?fuentes=3'), env)).json();
    assert.equal(list.min, 1);
    assert.deepEqual(list.n, [['3a1ec8160591', 3, 1]]);
    assert.equal(list.fuentes.tellows.estado, 'pendiente');
    assert.equal((await post('retirar', { numero: data.numero, instalacion: data.instalacion })).status, 200);
    assert.deepEqual(JSON.parse(kv.get('lista')).n, []);
  } finally { db.close(); }
});
