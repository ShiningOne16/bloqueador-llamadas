import { DIA_MS, huella } from './lib.js';

export const TELLOWS_MAX_AGE = 3 * DIA_MS;
const MAX_BYTES = 8 * 1024 * 1024;
const MAX_ROWS = 50_000;
const CACHE_KEY = 'tellows-mx';
const enabled = (env) => env.TELLOWS_ENABLED === 'true' && env.TELLOWS_LICENSE_APPROVED === 'true';

/** CSV de Scorelist: separador ;, comillas dobles escapadas y campos con saltos de línea. */
export function parseCsv(text) {
  const rows = [];
  let row = [], field = '', quoted = false, closed = false;
  text = text.replace(/^\uFEFF/, '');
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') { quoted = false; closed = true; }
      else field += c;
    } else if (c === ';') {
      row.push(field); field = ''; closed = false;
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field);
      if (row.some((value) => value.trim())) rows.push(row);
      row = []; field = ''; closed = false;
      if (rows.length > MAX_ROWS + 1) throw new Error('Demasiadas filas.');
    } else if (c === '"' && field === '' && !closed) quoted = true;
    else {
      if (closed) throw new Error('CSV no válido.');
      field += c;
    }
  }
  if (quoted) throw new Error('CSV incompleto.');
  row.push(field);
  if (row.some((value) => value.trim())) rows.push(row);
  if (rows.length > MAX_ROWS + 1) throw new Error('Demasiadas filas.');
  return rows;
}

/** Solo México: no confundir los últimos diez dígitos de otro país con un número mexicano. */
function mexicoNumber(value) {
  const raw = value.trim();
  if (!/^[+\d ()-]+$/.test(raw)) return '';
  const digits = raw.replace(/\D/g, '');
  if (digits.length === 12 && digits.startsWith('52')) return digits.slice(2);
  if (digits.length === 14 && digits.startsWith('0052')) return digits.slice(4);
  if (!raw.startsWith('+') && digits.length === 10) return digits;
  return '';
}

export async function convertScorelist(text) {
  const [header, ...rows] = parseCsv(text);
  if (!header) throw new Error('Respuesta vacía.');
  const columns = header.map((s) => s.trim().toLowerCase());
  const number = columns.findIndex((s) => ['nummer', 'number'].includes(s));
  const score = columns.indexOf('score');
  const comments = columns.findIndex((s) => ['kommentare', 'comments'].includes(s));
  if ([number, score, comments].some((i) => i < 0)) throw new Error('Columnas no válidas.');
  const accepted = new Map();
  for (const row of rows) {
    const n = row[number], s = row[score], c = row[comments];
    if (n == null || s == null || c == null || !/^[1-9]$/.test(s.trim()) || !/^\d+$/.test(c.trim())) {
      throw new Error('Fila no válida.');
    }
    if (Number(s) < 7 || Number(c) < 3) continue;
    const key = mexicoNumber(n);
    if (key) accepted.set(key, Math.max(Number(s), accepted.get(key) || 0));
  }
  const entries = [];
  const numbers = [...accepted];
  for (let i = 0; i < numbers.length; i += 128) {
    entries.push(...await Promise.all(numbers.slice(i, i + 128).map(async ([key, score]) =>
      // No inventar una categoría a partir del score. El 0 significa "Otro".
      [await huella(key), 0, 0, 'tellows', score]
    )));
  }
  return entries;
}

async function readLimited(response) {
  if (!response.body || Number(response.headers.get('content-length')) > MAX_BYTES) throw new Error('Tamaño no válido.');
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BYTES) throw new Error('Respuesta demasiado grande.');
      chunks.push(value);
    }
  } finally { await reader.cancel().catch(() => {}); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}

/** Solo el cron descarga Tellows. La clave nunca se incluye en /lista ni en la app. */
export async function loadTellows(env, now = Date.now(), fetcher = fetch) {
  const inactive = { estado: 'pendiente', actualizado: 0, total: 0, pais: 'mx' };
  if (!enabled(env) || !/^[a-fA-F0-9]{32}$/.test(env.TELLOWS_APIKEY_MD5 || '')) {
    return { n: [], hasta: 0, fuente: inactive };
  }
  let saved = null;
  try { saved = JSON.parse(await env.LISTA.get(CACHE_KEY)); } catch { /* Sin copia válida. */ }
  if (!saved || saved.v !== 1 || !Number.isFinite(saved.generado) || !Array.isArray(saved.n)) saved = null;
  let failed = false;
  if (!saved || now - saved.generado >= DIA_MS || saved.generado > now) {
    try {
      const url = new URL('https://www.tellows.de/stats/partnerscoredata');
      for (const [key, value] of Object.entries({
        apikeyMd5: env.TELLOWS_APIKEY_MD5, country: 'mx', lang: 'es',
        numberformatinternational: '1', minscore: '7', mincomments: '3', json: '0',
      })) url.searchParams.set(key, value);
      const response = await fetcher(url.toString(), {
        redirect: 'error', signal: AbortSignal.timeout(20_000), headers: { accept: 'text/csv' },
      });
      if (!response.ok) throw new Error('Proveedor no disponible.');
      const n = await convertScorelist(await readLimited(response));
      saved = { v: 1, generado: now, n };
      await env.LISTA.put(CACHE_KEY, JSON.stringify(saved));
    } catch {
      // No registrar la excepción ni la URL: podrían contener la credencial.
      failed = true;
    }
  }
  const valid = saved && saved.generado <= now && now < saved.generado + TELLOWS_MAX_AGE;
  const n = valid ? saved.n : [];
  return {
    n, hasta: valid ? saved.generado + TELLOWS_MAX_AGE : 0,
    fuente: {
      estado: valid ? (failed ? 'sin_actualizar' : 'activa') : 'no_disponible',
      actualizado: saved?.generado || 0, total: n.length, pais: 'mx',
    },
  };
}
