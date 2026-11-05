// Reglas puras (sin base de datos ni red). Deben coincidir con SpamRules.kt en la app Android.
export const PEPPER = 'bloqueador-spam-v1:';
export const CATEGORIAS = ['otro', 'cobranza', 'ventas', 'estafa', 'encuesta'];
export const UMBRAL_REPORTES = 3;        // personas distintas para entrar en la lista
export const MAX_REPORTES_POR_DIA = 30;  // por IP y por instalación
export const RETENCION_DIAS = 180;
export const DIA_MS = 24 * 60 * 60 * 1000;
export const VIGENCIA_LISTA_MS = 7 * DIA_MS;

/** La comunidad de esta versión cubre México; evita colisiones con países distintos. */
export function numeroMexicano(numero) {
  if (typeof numero !== 'string' || !claveNumero(numero)) return false;
  const digits = numero.replace(/[^0-9]/g, '');
  return (digits.length === 12 && digits.startsWith('52')) ||
    (digits.length === 14 && digits.startsWith('0052')) ||
    (!numero.trim().startsWith('+') && digits.length === 10);
}

/** Últimos 10 dígitos, como en la app. Devuelve '' si no parece un teléfono (7 a 15 dígitos). */
export function claveNumero(numero) {
  if (typeof numero !== 'string' || numero.length > 40) return '';
  if (/[^0-9+\-() .]/.test(numero)) return '';
  const digitos = numero.replace(/[^0-9]/g, '');
  if (digitos.length < 7 || digitos.length > 15) return '';
  return digitos.slice(-10);
}

export async function sha256hex(texto) {
  const datos = new TextEncoder().encode(texto);
  const resumen = await crypto.subtle.digest('SHA-256', datos);
  return [...new Uint8Array(resumen)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Huella de 12 caracteres del número (la que usa la lista que descarga la app). */
export async function huella(clave) {
  return (await sha256hex(PEPPER + clave)).slice(0, 12);
}

export function instalacionValida(id) {
  return typeof id === 'string' && /^[A-Za-z0-9-]{16,64}$/.test(id);
}

/** Índice de la categoría con más reportes (0 = "otro" si no hay datos). */
export function categoriaDominante(conteos) {
  let mejor = 0;
  let max = -1;
  CATEGORIAS.forEach((nombre, i) => {
    const n = (conteos && conteos[nombre]) || 0;
    if (n > max) { max = n; mejor = i; }
  });
  return mejor;
}

export function diaIso(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}
