// Generado con npm run build:dashboard. Pegar en el Worker existente.
// source-sha256: 05ed417e2c4b3bb54adb861f2850e42aa02f2e102cfcd383c780c3bdba9a08aa

// src/lib.js
var PEPPER = "bloqueador-spam-v1:";
var CATEGORIAS = ["otro", "cobranza", "ventas", "estafa", "encuesta"];
var UMBRAL_REPORTES = 1;
var MAX_REPORTES_POR_DIA = 30;
var RETENCION_DIAS = 180;
var DIA_MS = 24 * 60 * 60 * 1e3;
var VIGENCIA_LISTA_MS = 7 * DIA_MS;
function numeroMexicano(numero) {
  if (typeof numero !== "string" || !claveNumero(numero))
    return false;
  const digits = numero.replace(/[^0-9]/g, "");
  return digits.length === 12 && digits.startsWith("52") || digits.length === 14 && digits.startsWith("0052") || !numero.trim().startsWith("+") && digits.length === 10;
}
function claveNumero(numero) {
  if (typeof numero !== "string" || numero.length > 40)
    return "";
  if (/[^0-9+\-() .]/.test(numero))
    return "";
  const digitos = numero.replace(/[^0-9]/g, "");
  if (digitos.length < 7 || digitos.length > 15)
    return "";
  return digitos.slice(-10);
}
async function sha256hex(texto) {
  const datos = new TextEncoder().encode(texto);
  const resumen = await crypto.subtle.digest("SHA-256", datos);
  return [...new Uint8Array(resumen)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
async function huella(clave) {
  return (await sha256hex(PEPPER + clave)).slice(0, 12);
}
function instalacionValida(id) {
  return typeof id === "string" && /^[A-Za-z0-9-]{16,64}$/.test(id);
}
function categoriaDominante(conteos) {
  let mejor = 0;
  let max = -1;
  CATEGORIAS.forEach((nombre, i) => {
    const n = conteos && conteos[nombre] || 0;
    if (n > max) {
      max = n;
      mejor = i;
    }
  });
  return mejor;
}
function diaIso(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

// src/tellows.js
var TELLOWS_MAX_AGE = 3 * DIA_MS;
var MAX_BYTES = 8 * 1024 * 1024;
var MAX_ROWS = 5e4;
var CACHE_KEY = "tellows-mx";
var enabled = (env) => env.TELLOWS_ENABLED === "true" && env.TELLOWS_LICENSE_APPROVED === "true";
function parseCsv(text) {
  const rows = [];
  let row = [], field = "", quoted = false, closed = false;
  text = text.replace(/^\uFEFF/, "");
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
        closed = true;
      } else
        field += c;
    } else if (c === ";") {
      row.push(field);
      field = "";
      closed = false;
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n")
        i++;
      row.push(field);
      if (row.some((value) => value.trim()))
        rows.push(row);
      row = [];
      field = "";
      closed = false;
      if (rows.length > MAX_ROWS + 1)
        throw new Error("Demasiadas filas.");
    } else if (c === '"' && field === "" && !closed)
      quoted = true;
    else {
      if (closed)
        throw new Error("CSV no v\xE1lido.");
      field += c;
    }
  }
  if (quoted)
    throw new Error("CSV incompleto.");
  row.push(field);
  if (row.some((value) => value.trim()))
    rows.push(row);
  if (rows.length > MAX_ROWS + 1)
    throw new Error("Demasiadas filas.");
  return rows;
}
function mexicoNumber(value) {
  const raw = value.trim();
  if (!/^[+\d ()-]+$/.test(raw))
    return "";
  const digits = raw.replace(/\D/g, "");
  if (digits.length === 12 && digits.startsWith("52"))
    return digits.slice(2);
  if (digits.length === 14 && digits.startsWith("0052"))
    return digits.slice(4);
  if (!raw.startsWith("+") && digits.length === 10)
    return digits;
  return "";
}
async function convertScorelist(text) {
  const [header, ...rows] = parseCsv(text);
  if (!header)
    throw new Error("Respuesta vac\xEDa.");
  const columns = header.map((s) => s.trim().toLowerCase());
  const number = columns.findIndex((s) => ["nummer", "number"].includes(s));
  const score = columns.indexOf("score");
  const comments = columns.findIndex((s) => ["kommentare", "comments"].includes(s));
  if ([number, score, comments].some((i) => i < 0))
    throw new Error("Columnas no v\xE1lidas.");
  const accepted = /* @__PURE__ */ new Map();
  for (const row of rows) {
    const n = row[number], s = row[score], c = row[comments];
    if (n == null || s == null || c == null || !/^[1-9]$/.test(s.trim()) || !/^\d+$/.test(c.trim())) {
      throw new Error("Fila no v\xE1lida.");
    }
    if (Number(s) < 7 || Number(c) < 3)
      continue;
    const key = mexicoNumber(n);
    if (key)
      accepted.set(key, Math.max(Number(s), accepted.get(key) || 0));
  }
  const entries = [];
  const numbers = [...accepted];
  for (let i = 0; i < numbers.length; i += 128) {
    entries.push(...await Promise.all(numbers.slice(i, i + 128).map(
      async ([key, score2]) => (
        // No inventar una categoría a partir del score. El 0 significa "Otro".
        [await huella(key), 0, 0, "tellows", score2]
      )
    )));
  }
  return entries;
}
async function readLimited(response) {
  if (!response.body || Number(response.headers.get("content-length")) > MAX_BYTES)
    throw new Error("Tama\xF1o no v\xE1lido.");
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    for (; ; ) {
      const { done, value } = await reader.read();
      if (done)
        break;
      size += value.byteLength;
      if (size > MAX_BYTES)
        throw new Error("Respuesta demasiado grande.");
      chunks.push(value);
    }
  } finally {
    await reader.cancel().catch(() => {
    });
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}
async function loadTellows(env, now = Date.now(), fetcher = fetch) {
  const inactive = { estado: "pendiente", actualizado: 0, total: 0, pais: "mx" };
  if (!enabled(env) || !/^[a-fA-F0-9]{32}$/.test(env.TELLOWS_APIKEY_MD5 || "")) {
    return { n: [], hasta: 0, fuente: inactive };
  }
  let saved = null;
  try {
    saved = JSON.parse(await env.LISTA.get(CACHE_KEY));
  } catch {
  }
  if (!saved || saved.v !== 1 || !Number.isFinite(saved.generado) || !Array.isArray(saved.n))
    saved = null;
  let failed = false;
  if (!saved || now - saved.generado >= DIA_MS || saved.generado > now) {
    try {
      const url = new URL("https://www.tellows.de/stats/partnerscoredata");
      for (const [key, value] of Object.entries({
        apikeyMd5: env.TELLOWS_APIKEY_MD5,
        country: "mx",
        lang: "es",
        numberformatinternational: "1",
        minscore: "7",
        mincomments: "3",
        json: "0"
      }))
        url.searchParams.set(key, value);
      const response = await fetcher(url.toString(), {
        redirect: "error",
        signal: AbortSignal.timeout(2e4),
        headers: { accept: "text/csv" }
      });
      if (!response.ok)
        throw new Error("Proveedor no disponible.");
      const n2 = await convertScorelist(await readLimited(response));
      saved = { v: 1, generado: now, n: n2 };
      await env.LISTA.put(CACHE_KEY, JSON.stringify(saved));
    } catch {
      failed = true;
    }
  }
  const valid = saved && saved.generado <= now && now < saved.generado + TELLOWS_MAX_AGE;
  const n = valid ? saved.n : [];
  return {
    n,
    hasta: valid ? saved.generado + TELLOWS_MAX_AGE : 0,
    fuente: {
      estado: valid ? failed ? "sin_actualizar" : "activa" : "no_disponible",
      actualizado: saved?.generado || 0,
      total: n.length,
      pais: "mx"
    }
  };
}

// src/index.js
var JSON_UTF8 = "application/json; charset=utf-8";
var LISTA_VACIA = JSON.stringify({ v: 1, generado: 0, min: UMBRAL_REPORTES, n: [] });
var responder = (objeto, status = 200) => new Response(JSON.stringify(objeto), { status, headers: { "content-type": JSON_UTF8 } });
async function contar(env, clave, dia) {
  const fila = await env.DB.prepare(
    "INSERT INTO limites (clave, dia, n) VALUES (?, ?, 1) ON CONFLICT (clave, dia) DO UPDATE SET n = n + 1 RETURNING n"
  ).bind(clave, dia).first();
  return fila ? fila.n : 1;
}
async function reportar(request, env, retirar = false) {
  if (!env.SAL)
    return responder({ error: "Servidor sin configurar (falta SAL)." }, 500);
  const texto = await request.text();
  if (texto.length > 2048)
    return responder({ error: "Solicitud demasiado grande." }, 413);
  let cuerpo;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    return responder({ error: "JSON no v\xE1lido." }, 400);
  }
  if (!cuerpo || typeof cuerpo !== "object" || Array.isArray(cuerpo) || Object.keys(cuerpo).some((key) => !["numero", "categoria", "instalacion"].includes(key))) {
    return responder({ error: "Solo se acepta un n\xFAmero seleccionado, categor\xEDa e instalaci\xF3n." }, 400);
  }
  const clave = claveNumero(cuerpo && cuerpo.numero);
  if (!clave || !numeroMexicano(cuerpo.numero))
    return responder({ error: "Solo se aceptan n\xFAmeros de M\xE9xico." }, 400);
  if (!retirar && !CATEGORIAS.includes(cuerpo.categoria))
    return responder({ error: "Categor\xEDa no v\xE1lida." }, 400);
  if (!instalacionValida(cuerpo.instalacion))
    return responder({ error: "Instalaci\xF3n no v\xE1lida." }, 400);
  const ip = request.headers.get("CF-Connecting-IP") || "desconocida";
  const ipHuella = (await sha256hex(`${env.SAL}:ip:${ip}`)).slice(0, 24);
  const instHuella = (await sha256hex(`${env.SAL}:inst:${cuerpo.instalacion}`)).slice(0, 24);
  const ahora = Date.now();
  const dia = diaIso(ahora);
  const prefijo = retirar ? "retirar:" : "";
  const porIp = await contar(env, `${prefijo}ip:${ipHuella}`, dia);
  const porInstalacion = await contar(env, `${prefijo}inst:${instHuella}`, dia);
  if (porIp > MAX_REPORTES_POR_DIA || porInstalacion > MAX_REPORTES_POR_DIA) {
    return responder({ error: "Demasiados reportes hoy." }, 429);
  }
  if (retirar) {
    await env.DB.prepare("DELETE FROM reportes WHERE huella = ? AND instalacion = ?").bind(await huella(clave), instHuella).run();
    await publicar(env);
    return responder({ ok: true });
  }
  const resultado = await env.DB.prepare(
    "INSERT OR IGNORE INTO reportes (huella, ip, instalacion, categoria, creado) VALUES (?, ?, ?, ?, ?)"
  ).bind(await huella(clave), ipHuella, instHuella, cuerpo.categoria, ahora).run();
  return responder({ ok: true, ya: !(resultado.meta && resultado.meta.changes > 0) });
}
async function publicar(env, ahora = Date.now(), fetcher = fetch) {
  await env.DB.prepare("DELETE FROM reportes WHERE creado < ?").bind(ahora - RETENCION_DIAS * DIA_MS).run();
  const { results } = await env.DB.prepare(
    "SELECT huella, categoria, COUNT(*) AS c FROM reportes GROUP BY huella, categoria"
  ).all();
  const porNumero = /* @__PURE__ */ new Map();
  for (const fila of results) {
    const e = porNumero.get(fila.huella) || { total: 0, cats: {} };
    e.total += fila.c;
    e.cats[fila.categoria] = fila.c;
    porNumero.set(fila.huella, e);
  }
  const entradas = [];
  for (const [h, e] of porNumero) {
    if (e.total >= UMBRAL_REPORTES)
      entradas.push([h, categoriaDominante(e.cats), e.total]);
  }
  const tellows = await loadTellows(env, ahora, fetcher);
  const combined = new Map(tellows.n.map((entry) => [entry[0], entry]));
  for (const entry of entradas)
    combined.set(entry[0], entry);
  const n = [...combined.values()];
  await env.LISTA.put("lista", JSON.stringify({
    v: 1,
    generado: ahora,
    min: UMBRAL_REPORTES,
    n,
    comunidad_hasta: ahora + VIGENCIA_LISTA_MS,
    tellows_hasta: tellows.hasta,
    fuentes: { tellows: tellows.fuente }
  }));
  await env.DB.prepare("DELETE FROM reportes WHERE creado < ?").bind(ahora - RETENCION_DIAS * DIA_MS).run();
  await env.DB.prepare("DELETE FROM limites WHERE dia < ?").bind(diaIso(ahora - 3 * DIA_MS)).run();
  return n.length;
}
async function manejar(request, env) {
  const url = new URL(request.url);
  const { pathname } = url;
  if (pathname === "/lista" && request.method === "GET") {
    const valor = await env.LISTA.get("lista");
    let body = valor || LISTA_VACIA;
    if (valor && !["2", "3"].includes(url.searchParams.get("fuentes"))) {
      const lista = JSON.parse(valor);
      lista.n = lista.n.filter((entry) => entry[3] !== "tellows");
      body = JSON.stringify(lista);
    }
    return new Response(body, {
      headers: { "content-type": JSON_UTF8, "cache-control": "public, max-age=3600" }
    });
  }
  if (pathname === "/reportar" && request.method === "POST")
    return reportar(request, env);
  if (pathname === "/retirar" && request.method === "POST")
    return reportar(request, env, true);
  if (pathname === "/")
    return new Response("Bloqueador: servidor de la lista de spam.");
  return responder({ error: "No encontrado." }, 404);
}
var src_default = {
  fetch: (request, env) => manejar(request, env),
  scheduled: (_evento, env, ctx) => ctx.waitUntil(publicar(env))
};

// dashboard-entry.js
var dashboard_entry_default = src_default;
export {
  dashboard_entry_default as default
};
