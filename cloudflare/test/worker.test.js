import test from 'node:test';
import assert from 'node:assert';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { manejar, publicar } from '../src/index.js';
import { huella, claveNumero, categoriaDominante, UMBRAL_REPORTES, MAX_REPORTES_POR_DIA, DIA_MS, VIGENCIA_LISTA_MS } from '../src/lib.js';
import { convertScorelist, loadTellows, TELLOWS_MAX_AGE } from '../src/tellows.js';

const CSV_HEADER = 'Nummer;Score;Kommentare;Land;Vorwahl;Suchanfragen;Anruftyp';
const csvResponse = (rows = ['+525512345678;8;4;52;55;100;Unbekannt']) =>
  new Response(CSV_HEADER + '\n' + rows.join('\n'), { headers: { 'content-type': 'text/csv' } });
function configureTellows(env) {
  Object.assign(env, {
    TELLOWS_ENABLED: 'true', TELLOWS_LICENSE_APPROVED: 'true',
    TELLOWS_APIKEY_MD5: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  });
}

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

test('la lista conserva el conteo y la categoría dominante de reportes distintos', async () => {
  const { env } = crearEntorno();
  for (let i = 0; i < 3; i++) await reportar(env, { numero: '5512345678', categoria: i === 0 ? 'ventas' : 'cobranza' });
  for (let i = 0; i < UMBRAL_REPORTES - 1; i++) await reportar(env, { numero: '5598765432' });
  assert.strictEqual(await publicar(env), 1);
  const lista = await (await manejar(new Request('https://x.test/lista'), env)).json();
  assert.strictEqual(lista.v, 1);
  assert.strictEqual(lista.n.length, 1);
  assert.deepStrictEqual(lista.n[0], ['3a1ec8160591', 1, 3]); // categoría 1 = cobranza
});

test('un reporte basta para publicar un número y retirar el último voto lo elimina', async () => {
  const { env } = crearEntorno();
  const id = 'instalacion-unica-12345';
  await reportar(env, { instalacion: id, categoria: 'estafa' });
  await publicar(env);
  const lista = await (await manejar(new Request('https://x.test/lista?fuentes=3'), env)).json();
  assert.strictEqual(lista.min, 1);
  assert.deepStrictEqual(lista.n, [['3a1ec8160591', 3, 1]]);
  await retirar(env, '5512345678', id);
  assert.deepStrictEqual(JSON.parse(await env.LISTA.get('lista')).n, []);
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

test('Tellows no hace peticiones sin activación, licencia y credencial', async () => {
  const { env } = crearEntorno();
  const neverFetch = () => { throw new Error('No debe consultar al proveedor'); };
  assert.deepStrictEqual((await loadTellows(env, DIA_MS, neverFetch)).n, []);
  configureTellows(env);
  for (const [key, value] of [
    ['TELLOWS_ENABLED', 'false'], ['TELLOWS_LICENSE_APPROVED', 'false'], ['TELLOWS_APIKEY_MD5', 'incorrecta'],
  ]) {
    const copy = { ...env, [key]: value };
    assert.strictEqual((await loadTellows(copy, DIA_MS, neverFetch)).fuente.estado, 'pendiente');
  }
});

test('Scorelist acepta CSV con BOM, comillas, saltos de línea y columnas extra', async () => {
  const rows = await convertScorelist('\uFEFF' + CSV_HEADER + ';Anrufname\r\n' +
    '+525512345678;8;4;52;55;100;Otro;"Nombre; con ""comillas""\ny salto"\r\n' +
    '5512345678;9;8;52;55;100;Otro;duplicado\r\n' +
    '00525598765432;7;3;52;55;100;Otro;válido\r\n');
  assert.strictEqual(rows.length, 2);
  assert.deepStrictEqual(rows[0], [await huella('5512345678'), 0, 0, 'tellows', 9]);
  assert.strictEqual(rows[1][0], await huella('5598765432'));
});

test('Scorelist filtra riesgo, comentarios y números extranjeros', async () => {
  const rows = await convertScorelist(CSV_HEADER + '\n' + [
    '+525512345678;6;50;52;55;100;Otro',
    '+525598765432;9;2;52;55;100;Otro',
    '+15512345678;9;10;1;55;100;Otro',
    '+525511112222;7;3;52;55;100;Otro',
  ].join('\n'));
  assert.deepStrictEqual(rows, [[await huella('5511112222'), 0, 0, 'tellows', 7]]);
});

test('Scorelist rechaza HTML, columnas ausentes y CSV truncado', async () => {
  for (const text of ['<html>Error</html>', 'Nummer;Score\n123;8', CSV_HEADER + '\n"sin cierre', CSV_HEADER + '\n+525512345678;NaN;4']) {
    await assert.rejects(convertScorelist(text));
  }
});

test('la descarga usa México y no publica ni conserva credenciales o números legibles', async () => {
  const { env } = crearEntorno();
  configureTellows(env);
  let called = 0;
  await publicar(env, DIA_MS, async (input, options) => {
    called++;
    const url = new URL(input);
    assert.strictEqual(url.origin, 'https://www.tellows.de');
    assert.strictEqual(url.pathname, '/stats/partnerscoredata');
    assert.strictEqual(url.searchParams.get('country'), 'mx');
    assert.strictEqual(url.searchParams.get('minscore'), '7');
    assert.strictEqual(url.searchParams.get('mincomments'), '3');
    assert.strictEqual(url.searchParams.get('numberformatinternational'), '1');
    assert.strictEqual(options.redirect, 'error');
    return csvResponse();
  });
  assert.strictEqual(called, 1);
  const list = await (await manejar(new Request('https://x.test/lista?fuentes=2'), env)).json();
  assert.strictEqual(list.fuentes.tellows.estado, 'activa');
  assert.strictEqual(list.n.length, 1);
  const legacy = await (await manejar(new Request('https://x.test/lista'), env)).json();
  assert.deepStrictEqual(legacy.n, []); // Sin Tellows para clientes que no conocen su caducidad.
  for (const text of [JSON.stringify(list), await env.LISTA.get('tellows-mx')]) {
    assert.ok(!text.includes(env.TELLOWS_APIKEY_MD5));
    assert.ok(!text.includes('5512345678'));
  }
});

test('Tellows reutiliza la copia diaria y reemplaza entradas retiradas al actualizar', async () => {
  const { env } = crearEntorno();
  configureTellows(env);
  let calls = 0;
  const fetcher = async () => { calls++; return calls === 1 ? csvResponse() : csvResponse([]); };
  await loadTellows(env, DIA_MS, fetcher);
  assert.strictEqual((await loadTellows(env, DIA_MS + 6 * 3600000, fetcher)).n.length, 1);
  assert.strictEqual(calls, 1);
  assert.strictEqual((await loadTellows(env, 2 * DIA_MS, fetcher)).n.length, 0);
  assert.strictEqual(calls, 2);
});

test('fallos del proveedor conservan la última copia sin prolongar su caducidad', async () => {
  const { env } = crearEntorno();
  configureTellows(env);
  await loadTellows(env, DIA_MS, async () => csvResponse());
  for (const response of [new Response('Error', { status: 401 }), new Response('<html>Error</html>')]) {
    const result = await loadTellows(env, 2 * DIA_MS, async () => response);
    assert.strictEqual(result.n.length, 1);
    assert.strictEqual(result.fuente.estado, 'sin_actualizar');
    assert.strictEqual(result.hasta, DIA_MS + TELLOWS_MAX_AGE);
  }
  const expired = await loadTellows(env, DIA_MS + TELLOWS_MAX_AGE, async () => { throw new Error('Red caída'); });
  assert.deepStrictEqual(expired.n, []);
  assert.strictEqual(expired.hasta, 0);
  assert.strictEqual(expired.fuente.estado, 'no_disponible');
});

test('rechaza respuestas demasiado grandes sin reemplazar una copia válida', async () => {
  const { env } = crearEntorno();
  configureTellows(env);
  await loadTellows(env, DIA_MS, async () => csvResponse());
  const result = await loadTellows(env, 2 * DIA_MS, async () =>
    new Response('x'.repeat(8 * 1024 * 1024 + 1)));
  assert.strictEqual(result.fuente.estado, 'sin_actualizar');
  assert.strictEqual(result.n.length, 1);
});

test('combina fuentes sin duplicados y los reportes propios no caducan con Tellows', async () => {
  const { env } = crearEntorno();
  configureTellows(env);
  const now = Date.now();
  for (let i = 0; i < UMBRAL_REPORTES; i++) await reportar(env);
  await publicar(env, now, async () => csvResponse([
    '+525512345678;9;10;52;55;100;Otro',
    '+525598765432;8;3;52;55;100;Otro',
  ]));
  let list = JSON.parse(await env.LISTA.get('lista'));
  assert.strictEqual(list.n.length, 2);
  assert.deepStrictEqual(list.n.find((entry) => entry[0] === '3a1ec8160591'), ['3a1ec8160591', 1, UMBRAL_REPORTES]);
  await publicar(env, now + TELLOWS_MAX_AGE, async () => new Response('Error', { status: 503 }));
  list = JSON.parse(await env.LISTA.get('lista'));
  assert.deepStrictEqual(list.n, [['3a1ec8160591', 1, UMBRAL_REPORTES]]);
});

test('los reportes vencidos desaparecen de la publicación, no solo de la base', async () => {
  const { env } = crearEntorno();
  for (let i = 0; i < UMBRAL_REPORTES; i++) await reportar(env);
  await publicar(env, Date.now() + 200 * DIA_MS);
  assert.deepStrictEqual(JSON.parse(await env.LISTA.get('lista')).n, []);
});

function retirar(env, numero, instalacion, ip = '1.2.3.4') {
  return manejar(new Request('https://x.test/retirar', {
    method: 'POST', headers: { 'CF-Connecting-IP': ip },
    body: JSON.stringify({ numero, instalacion }),
  }), env);
}

test('rechaza agendas, historiales, lotes y campos adicionales', async () => {
  const { env, sqlite } = crearEntorno();
  for (const body of [
    [{ numero: '5512345678', categoria: 'estafa', instalacion: 'instalacion-1234567890' }],
    { numero: '5512345678', categoria: 'estafa', instalacion: 'instalacion-1234567890', contactos: [] },
    { numero: '5512345678', categoria: 'estafa', instalacion: 'instalacion-1234567890', historial: [] },
    { numero: '5512345678', categoria: 'estafa', instalacion: 'instalacion-1234567890', nombre: 'Ana' },
  ]) {
    const response = await manejar(new Request('https://x.test/reportar', { method: 'POST', body: JSON.stringify(body) }), env);
    assert.strictEqual(response.status, 400);
  }
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 0);
});

test('rechaza números extranjeros con el mismo sufijo que uno mexicano', async () => {
  const { env } = crearEntorno();
  assert.strictEqual((await reportar(env, { numero: '+345512345678' })).status, 400);
  assert.strictEqual((await reportar(env, { numero: '+15512345678' })).status, 400);
  assert.strictEqual((await reportar(env, { numero: '+525512345678' })).status, 200);
});

test('la publicación indica siete días de vigencia para clientes nuevos', async () => {
  const { env } = crearEntorno();
  const now = Date.now();
  await publicar(env, now);
  const lista = await (await manejar(new Request('https://x.test/lista?fuentes=3'), env)).json();
  assert.strictEqual(lista.comunidad_hasta, now + VIGENCIA_LISTA_MS);
});

test('retirar solo elimina el voto propio y recalcula el umbral comunitario', async () => {
  const { env, sqlite } = crearEntorno();
  const id = 'instalacion-propia-1234';
  await reportar(env, { instalacion: id });
  await reportar(env);
  await reportar(env);
  await publicar(env);
  assert.strictEqual(JSON.parse(await env.LISTA.get('lista')).n.length, 1);
  assert.strictEqual((await retirar(env, '5512345678', 'otra-instalacion-123456')).status, 200);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 3);
  assert.strictEqual((await retirar(env, '5512345678', id)).status, 200);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 2);
  assert.deepStrictEqual(JSON.parse(await env.LISTA.get('lista')).n, [['3a1ec8160591', 1, 2]]);
  assert.strictEqual((await retirar(env, '5512345678', id)).status, 200);
});

test('reintentar la retirada repara una publicación fallida y conserva otros votos', async () => {
  const { env, sqlite } = crearEntorno();
  const id = 'instalacion-propia-1234';
  await reportar(env, { instalacion: id });
  await reportar(env);
  await reportar(env);
  await publicar(env);
  const guardar = env.LISTA.put;
  env.LISTA.put = async () => { throw new Error('Fallo de KV'); };
  await assert.rejects(retirar(env, '5512345678', id));
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 2);
  env.LISTA.put = guardar;
  await retirar(env, '5512345678', id);
  assert.deepStrictEqual(JSON.parse(await env.LISTA.get('lista')).n, [['3a1ec8160591', 1, 2]]);
});

test('retirar no elimina reportes de otros números ni borra los límites diarios', async () => {
  const { env, sqlite } = crearEntorno();
  const id = 'instalacion-propia-1234';
  await reportar(env, { instalacion: id });
  await reportar(env, { instalacion: id, numero: '5598765432' });
  await retirar(env, '5512345678', id);
  assert.strictEqual(sqlite.prepare('SELECT COUNT(*) c FROM reportes').get().c, 1);
  assert.strictEqual(sqlite.prepare("SELECT SUM(n) n FROM limites WHERE clave LIKE 'inst:%'").get().n, 2);
});

