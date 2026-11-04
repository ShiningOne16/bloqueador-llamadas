'use strict';
const crypto = require('crypto');

// Debe coincidir con SpamRules.kt en la app Android.
const PEPPER = 'bloqueador-spam-v1:';
const CATEGORIAS = ['otro', 'cobranza', 'ventas', 'estafa', 'encuesta'];
const UMBRAL_REPORTES = 3;      // reportes de personas distintas para entrar en la lista
const MAX_REPORTES_POR_DIA = 30;

/** Últimos 10 dígitos, como en la app. Devuelve '' si no parece un teléfono (7 a 15 dígitos). */
function claveNumero(numero) {
  if (typeof numero !== 'string' || numero.length > 40) return '';
  if (/[^0-9+\-() .]/.test(numero)) return '';
  const digitos = numero.replace(/[^0-9]/g, '');
  if (digitos.length < 7 || digitos.length > 15) return '';
  return digitos.slice(-10);
}

function huella(clave) {
  return crypto.createHash('sha256').update(PEPPER + clave).digest('hex').slice(0, 12);
}

function categoriaDominante(conteos) {
  let mejor = 0;
  let max = -1;
  CATEGORIAS.forEach((nombre, i) => {
    const n = (conteos && conteos[nombre]) || 0;
    if (n > max) { max = n; mejor = i; }
  });
  return mejor;
}

module.exports = { PEPPER, CATEGORIAS, UMBRAL_REPORTES, MAX_REPORTES_POR_DIA, claveNumero, huella, categoriaDominante };
