import test from 'node:test';
import assert from 'node:assert';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { manejar, publicar } from '../src/index.js';
import { huella, claveNumero, categoriaDominante, UMBRAL_REPORTES, MAX_REPORTES_POR_DIA, DIA_MS } from '../src/lib.js';

// Base de datos real (SQLite) con la forma de la API de D1, para probar el SQL de verdad.
function crearEntorno() {
  const sqlite = new DatabaseSync(':memory:');
  sqlite.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const DB = {
    prepare(sql) {
      const st = sqlite.prepare(sql);
      const ejecutar = (args) => ({
        run: async () => ({ meta: { changes: Number(st.run(...args).changes) } }),
        all: async () => ({ results: st.all(...args) }),
        first: async () => st.get(...args) ?? null,
      });
      return { ...ejecutar([]), bind: (...args) => ejecutar(args) };
    },
  };
  const kv = new Map();
  const LISTA = { get: async (k) => kv.get(k) ?? null, put: async (k, v) => { kv.set(k, v); } };
  return { env: { DB, LISTA, SAL: 'sal-de-prueba' }, sqlite };
}

let contador = 0;
function reportar(env, { numero = '55 1234 5678', categoria = 'cobranza', ip, instalacion } = {}) {
  contador++;
  const cuerpo = { numero, categoria, instalacion: instalacion ?? `instalacion-${String(contador).padStart(16, '0')}` };
  return manejar(new Request('https://x.test/reportar', {
    method: 'POST', body: JSON.stringify(cuerpo), headers: { 'CF-Connecting-IP': ip ?? `10.0.0.${contador}` },
  }), env);
}

test('la huella coincide con la de la app', async () => {
  assert.strictEqual(await huella('5512345678'), '3a1ec8160591');
});

test('claveNumero usa los últimos 10 dígitos y rechaza lo raro', () => {
  assert.strictEqual(claveNumero('+52 55 1234 5678'), '5512345678');
  assert.strictEqual(claveNumero('12345'), '');
  assert.strictEqual(claveNumero('abc5512345678'), '');
  assert.strictEqual(claveNumero(5512345678), '');
});

test('categoriaDominante', () => {
  assert.strictEqual(categoriaDominante({ cobranza: 2, ventas: 5 }), 2);
  assert.strictEqual(categoriaDominante({}), 0);
});

test('un reporte válido se guarda y el segundo de la misma red no cuenta', async () => {
  const { env, sqlite } = crearEntorno();
  const r1 = await (await reportar(env, { ip: '1.1.1.1' })).json();
  assert.deepStrictEqual(r1, { ok: true, ya: false });
  const r2 = await (await reportar(env, { ip: '1.1.1.1' })).json();
  assert.deepStrictEqual(r2, { ok: true, ya: true });
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 1);
});

test('la misma instalación desde otra red tampoco cuenta doble', async () => {
  const { env, sqlite } = crearEntorno();
  const instalacion = 'instalacion-fija-1234567';
  await reportar(env, { ip: '1.1.1.1', instalacion });
  const r = await (await reportar(env, { ip: '2.2.2.2', instalacion })).json();
  assert.strictEqual(r.ya, true);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 1);
});

test('no se guardan IP ni identificadores legibles', async () => {
  const { env, sqlite } = crearEntorno();
  await reportar(env, { ip: '203.0.113.9', instalacion: 'instalacion-secreta-0001' });
  const fila = sqlite.prepare('SELECT * FROM reportes').get();
  assert.ok(!JSON.stringify(fila).includes('203.0.113.9'));
  assert.ok(!JSON.stringify(fila).includes('instalacion-secreta'));
  assert.ok(!JSON.stringify(fila).includes('5512345678'));
});

test('entradas no válidas dan 400', async () => {
  const { env } = crearEntorno();
  assert.strictEqual((await reportar(env, { numero: '123' })).status, 400);
  assert.strictEqual((await reportar(env, { categoria: 'inventada' })).status, 400);
  assert.strictEqual((await reportar(env, { instalacion: 'corta' })).status, 400);
  const mal = await manejar(new Request('https://x.test/reportar', { method: 'POST', body: 'no es json' }), env);
  assert.strictEqual(mal.status, 400);
  const grande = await manejar(new Request('https://x.test/reportar', { method: 'POST', body: 'x'.repeat(3000) }), env);
  assert.strictEqual(grande.status, 413);
});

test('sin SAL el servidor no acepta reportes', async () => {
  const { env } = crearEntorno();
  delete env.SAL;
  assert.strictEqual((await reportar(env)).status, 500);
});

test('el límite diario responde 429', async () => {
  const { env } = crearEntorno();
  const ip = '9.9.9.9';
  let ultimo;
  for (let i = 0; i < MAX_REPORTES_POR_DIA + 1; i++) {
    ultimo = await reportar(env, { ip, numero: `55${String(10000000 + i)}` });
  }
  assert.strictEqual(ultimo.status, 429);
});

test('la lista solo incluye números con suficientes personas distintas y la categoría dominante', async () => {
  const { env } = crearEntorno();
  for (let i = 0; i < UMBRAL_REPORTES; i++) await reportar(env, { numero: '5512345678', categoria: i === 0 ? 'ventas' : 'cobranza' });
  for (let i = 0; i < UMBRAL_REPORTES - 1; i++) await reportar(env, { numero: '5598765432' });
  assert.strictEqual(await publicar(env), 1);
  const lista = await (await manejar(new Request('https://x.test/lista'), env)).json();
  assert.strictEqual(lista.v, 1);
  assert.strictEqual(lista.n.length, 1);
  assert.deepStrictEqual(lista.n[0], ['3a1ec8160591', 1, UMBRAL_REPORTES]); // categoría 1 = cobranza
});

test('antes de publicar la lista está vacía y se puede cachear', async () => {
  const { env } = crearEntorno();
  const res = await manejar(new Request('https://x.test/lista'), env);
  assert.strictEqual(res.headers.get('cache-control'), 'public, max-age=3600');
  assert.deepStrictEqual((await res.json()).n, []);
});

test('publicar borra reportes viejos y contadores de días anteriores', async () => {
  const { env, sqlite } = crearEntorno();
  await reportar(env);
  const futuro = Date.now() + 200 * DIA_MS;
  await publicar(env, futuro);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 0);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM limites').get().c, 0);
});

test('rutas desconocidas dan 404 y los métodos incorrectos no reportan', async () => {
  const { env } = crearEntorno();
  assert.strictEqual((await manejar(new Request('https://x.test/otra'), env)).status, 404);
  assert.strictEqual((await manejar(new Request('https://x.test/reportar'), env)).status, 404);
});
