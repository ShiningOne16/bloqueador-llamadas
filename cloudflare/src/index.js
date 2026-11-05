import {
  CATEGORIAS, UMBRAL_REPORTES, MAX_REPORTES_POR_DIA, RETENCION_DIAS, DIA_MS,
  claveNumero, huella, sha256hex, instalacionValida, categoriaDominante, diaIso,
} from './lib.js';
import { loadTellows } from './tellows.js';

const JSON_UTF8 = 'application/json; charset=utf-8';
const LISTA_VACIA = JSON.stringify({ v: 1, generado: 0, min: UMBRAL_REPORTES, n: [] });

const responder = (objeto, status = 200) =>
  new Response(JSON.stringify(objeto), { status, headers: { 'content-type': JSON_UTF8 } });

/** Suma uno al contador del día y devuelve el total. */
async function contar(env, clave, dia) {
  const fila = await env.DB.prepare(
    'INSERT INTO limites (clave, dia, n) VALUES (?, ?, 1) ON CONFLICT (clave, dia) DO UPDATE SET n = n + 1 RETURNING n'
  ).bind(clave, dia).first();
  return fila ? fila.n : 1;
}

async function reportar(request, env) {
  if (!env.SAL) return responder({ error: 'Servidor sin configurar (falta SAL).' }, 500);
  const texto = await request.text();
  if (texto.length > 2048) return responder({ error: 'Solicitud demasiado grande.' }, 413);
  let cuerpo;
  try { cuerpo = JSON.parse(texto); } catch { return responder({ error: 'JSON no válido.' }, 400); }

  const clave = claveNumero(cuerpo && cuerpo.numero);
  if (!clave) return responder({ error: 'Número no válido.' }, 400);
  if (!CATEGORIAS.includes(cuerpo.categoria)) return responder({ error: 'Categoría no válida.' }, 400);
  if (!instalacionValida(cuerpo.instalacion)) return responder({ error: 'Instalación no válida.' }, 400);

  const ip = request.headers.get('CF-Connecting-IP') || 'desconocida';
  const ipHuella = (await sha256hex(`${env.SAL}:ip:${ip}`)).slice(0, 24);
  const instHuella = (await sha256hex(`${env.SAL}:inst:${cuerpo.instalacion}`)).slice(0, 24);
  const ahora = Date.now();
  const dia = diaIso(ahora);

  const porIp = await contar(env, `ip:${ipHuella}`, dia);
  const porInstalacion = await contar(env, `inst:${instHuella}`, dia);
  if (porIp > MAX_REPORTES_POR_DIA || porInstalacion > MAX_REPORTES_POR_DIA) {
    return responder({ error: 'Demasiados reportes hoy.' }, 429);
  }

  // OR IGNORE: si esa red o esa instalación ya reportó este número, no cuenta otra vez.
  const resultado = await env.DB.prepare(
    'INSERT OR IGNORE INTO reportes (huella, ip, instalacion, categoria, creado) VALUES (?, ?, ?, ?, ?)'
  ).bind(await huella(clave), ipHuella, instHuella, cuerpo.categoria, ahora).run();
  return responder({ ok: true, ya: !(resultado.meta && resultado.meta.changes > 0) });
}

/** Junta los números con suficientes reportes en un solo archivo y limpia datos viejos. */
export async function publicar(env, ahora = Date.now(), fetcher = fetch) {
  await env.DB.prepare('DELETE FROM reportes WHERE creado < ?').bind(ahora - RETENCION_DIAS * DIA_MS).run();
  const { results } = await env.DB.prepare(
    'SELECT huella, categoria, COUNT(*) AS c FROM reportes GROUP BY huella, categoria'
  ).all();
  const porNumero = new Map();
  for (const fila of results) {
    const e = porNumero.get(fila.huella) || { total: 0, cats: {} };
    e.total += fila.c;
    e.cats[fila.categoria] = fila.c;
    porNumero.set(fila.huella, e);
  }
  const entradas = [];
  for (const [h, e] of porNumero) {
    if (e.total >= UMBRAL_REPORTES) entradas.push([h, categoriaDominante(e.cats), e.total]);
  }
  const tellows = await loadTellows(env, ahora, fetcher);
  const combined = new Map(tellows.n.map((entry) => [entry[0], entry]));
  // Si también hay suficientes reportes propios, esa entrada no depende de Tellows.
  for (const entry of entradas) combined.set(entry[0], entry);
  const n = [...combined.values()];
  await env.LISTA.put('lista', JSON.stringify({
    v: 1, generado: ahora, min: UMBRAL_REPORTES, n,
    tellows_hasta: tellows.hasta, fuentes: { tellows: tellows.fuente },
  }));

  await env.DB.prepare('DELETE FROM reportes WHERE creado < ?').bind(ahora - RETENCION_DIAS * DIA_MS).run();
  await env.DB.prepare('DELETE FROM limites WHERE dia < ?').bind(diaIso(ahora - 3 * DIA_MS)).run();
  return n.length;
}

export async function manejar(request, env) {
  const url = new URL(request.url);
  const { pathname } = url;
  if (pathname === '/lista' && request.method === 'GET') {
    const valor = await env.LISTA.get('lista');
    let body = valor || LISTA_VACIA;
    // Los clientes antiguos no conocen la caducidad de Tellows: solo reciben comunidad.
    if (valor && url.searchParams.get('fuentes') !== '2') {
      const lista = JSON.parse(valor);
      lista.n = lista.n.filter((entry) => entry[3] !== 'tellows');
      body = JSON.stringify(lista);
    }
    return new Response(body, {
      headers: { 'content-type': JSON_UTF8, 'cache-control': 'public, max-age=3600' },
    });
  }
  if (pathname === '/reportar' && request.method === 'POST') return reportar(request, env);
  if (pathname === '/') return new Response('Bloqueador: servidor de la lista de spam.');
  return responder({ error: 'No encontrado.' }, 404);
}

export default {
  fetch: (request, env) => manejar(request, env),
  scheduled: (_evento, env, ctx) => ctx.waitUntil(publicar(env)),
};

