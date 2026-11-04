'use strict';
const test = require('node:test');
const assert = require('node:assert');
const lib = require('./lib');

test('la huella coincide con la de la app', () => {
  // Mismo valor que SpamRulesTest.kt
  assert.strictEqual(lib.huella('5512345678'), '3a1ec8160591');
});

test('claveNumero usa los últimos 10 dígitos y rechaza lo raro', () => {
  assert.strictEqual(lib.claveNumero('+52 55 1234 5678'), '5512345678');
  assert.strictEqual(lib.claveNumero('(55) 1234-5678'), '5512345678');
  assert.strictEqual(lib.claveNumero('12345'), '');
  assert.strictEqual(lib.claveNumero('abc5512345678'), '');
  assert.strictEqual(lib.claveNumero(5512345678), '');
  assert.strictEqual(lib.claveNumero('1'.repeat(16)), '');
});

test('categoriaDominante elige la más reportada y usa "otro" si no hay datos', () => {
  assert.strictEqual(lib.categoriaDominante({ cobranza: 2, ventas: 5 }), 2);
  assert.strictEqual(lib.categoriaDominante({}), 0);
  assert.strictEqual(lib.categoriaDominante(undefined), 0);
});
