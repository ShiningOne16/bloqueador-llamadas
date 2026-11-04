'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const admin = require('firebase-admin');
const lib = require('./lib');

admin.initializeApp();
const db = admin.firestore();

/**
 * La app llama a esta función para reportar un número. Requiere un usuario anónimo de Firebase Auth.
 * Guarda un reporte por persona y número, limita los reportes por día y suma el conteo del número.
 */
exports.reportar = onCall({ region: 'us-central1', maxInstances: 20 }, async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Falta iniciar sesión.');

  const clave = lib.claveNumero(request.data && request.data.numero);
  if (!clave) throw new HttpsError('invalid-argument', 'Número no válido.');
  const categoria = request.data && request.data.categoria;
  if (!lib.CATEGORIAS.includes(categoria)) throw new HttpsError('invalid-argument', 'Categoría no válida.');

  const huellaNumero = lib.huella(clave);
  const reporteRef = db.collection('reports').doc(`${uid}_${huellaNumero}`);
  const limiteRef = db.collection('limits').doc(uid);
  const spamRef = db.collection('spam').doc(huellaNumero);
  const hoy = new Date().toISOString().slice(0, 10);

  return db.runTransaction(async (t) => {
    const [reporte, limite] = await Promise.all([t.get(reporteRef), t.get(limiteRef)]);
    if (reporte.exists) return { ya: true };

    const usados = limite.exists && limite.data().dia === hoy ? limite.data().n : 0;
    if (usados >= lib.MAX_REPORTES_POR_DIA) throw new HttpsError('resource-exhausted', 'Demasiados reportes hoy.');

    t.set(reporteRef, { categoria, creado: admin.firestore.FieldValue.serverTimestamp() });
    t.set(limiteRef, { dia: hoy, n: usados + 1 });
    t.set(spamRef, {
      reportes: admin.firestore.FieldValue.increment(1),
      categorias: { [categoria]: admin.firestore.FieldValue.increment(1) },
      ultimo: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    return { ya: false };
  });
});

/** Cada 6 horas publica un solo archivo con los números que ya tienen suficientes reportes. */
exports.publicarLista = onSchedule({ region: 'us-central1', schedule: 'every 6 hours', timeoutSeconds: 300 }, async () => {
  const snap = await db.collection('spam').where('reportes', '>=', lib.UMBRAL_REPORTES).get();
  const entradas = snap.docs.map((d) => {
    const datos = d.data();
    return [d.id, lib.categoriaDominante(datos.categorias), datos.reportes];
  });
  const contenido = JSON.stringify({ v: 1, generado: Date.now(), min: lib.UMBRAL_REPORTES, n: entradas });
  await admin.storage().bucket().file('spam_list.json').save(contenido, {
    contentType: 'application/json',
    metadata: { cacheControl: 'public, max-age=3600' },
  });
});
