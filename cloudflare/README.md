# Servidor de la lista de spam (Cloudflare, plan gratis)

Un Worker de Cloudflare con una base D1 (reportes) y un KV (la lista publicada). Todo cabe en el plan gratuito.
No guarda números legibles (solo una huella de 12 caracteres) ni IP ni identificadores (solo huellas con una sal secreta).

## Montarlo (una sola vez)

Necesitas una cuenta gratuita de Cloudflare y Node.js 20 o más nuevo. Desde esta carpeta (`cloudflare/`):

1. `npm install`
2. `npx wrangler login` (abre el navegador para autorizar)
3. `npx wrangler d1 create bloqueador-spam` y copia el `database_id` que muestra en `wrangler.toml`.
4. `npx wrangler kv namespace create LISTA` y copia el `id` en `wrangler.toml`.
5. `npx wrangler d1 execute bloqueador-spam --remote --file=schema.sql`
6. `npx wrangler secret put SAL` y escribe un texto largo y aleatorio (guárdalo; no lo cambies después).
7. `npx wrangler deploy` — al final muestra la dirección, algo como `https://bloqueador-spam.TU-USUARIO.workers.dev`.
8. Pega esa dirección en `app/src/main/res/values/servidor.xml` (`spam_server_url`) y publica la app.

La lista se regenera sola cada 6 horas (hasta entonces `/lista` devuelve una lista vacía).

## Tellows para México

La integración está preparada y **desactivada por defecto**. No se ha descargado una lista real ni
se incluyen credenciales de demostración. Hace falta un acuerdo del
[programa de socios de Tellows](https://www.tellows.com/s/about-en/tellows-api-partnership-program)
que permita usar sus datos en Bloqueador y distribuirlos a sus usuarios, incluyendo la lista de
huellas que se sirve desde `/lista`. Las licencias de uso interno para centrales telefónicas no
autorizan esa distribución. Confirma también con Tellows la atribución, retención y frecuencia
de actualización acordadas antes de activarlo.

Una vez autorizado:

1. Solicita acceso a **Scorelist API**, país **México (mx)**, para una app Android con copia local
   y distribución mediante nuestro Worker. La app tiene una futura suscripción mensual.
2. Guarda el valor de autenticación `apikeyMd5` entregado por Tellows como secreto:
   `npx wrangler secret put TELLOWS_APIKEY_MD5`. Ese valor también es una credencial:
   no lo pongas en GitHub, en el APK, en capturas ni en la conversación.
3. En `wrangler.toml`, cambia `TELLOWS_ENABLED` y `TELLOWS_LICENSE_APPROVED` a `"true"`
   únicamente cuando estén cubiertos esos usos. No configures una clave dentro de ese archivo.
4. Despliega con los identificadores reales de D1/KV del servidor existente. Los identificadores
   del archivo de ejemplo siguen siendo marcadores; no crees una base nueva sobre producción.
5. El siguiente cron descargará la lista. Comprueba `/lista`: `fuentes.tellows.estado` debe ser
   `activa`, con un `actualizado` reciente. Después pulsa **Actualizar ahora** en la app.

Se usa el endpoint documentado `https://www.tellows.de/stats/partnerscoredata`, con
`country=mx`, formato internacional, `minscore=7` y `mincomments=3`.
La descarga se hace como máximo una vez al día cuando funciona el proveedor. Se valida de nuevo
el umbral en el servidor y se rechazan teléfonos de otros países. El CSV se procesa en memoria:
KV y la lista publicada solo guardan huellas, no teléfonos, nombres ni comentarios. Las entradas
de Tellows conservan el score; su categoría es **Otro**, sin inferir fraude a partir del score.

Si falla Tellows, se conserva la copia anterior hasta un máximo de **72 horas desde la descarga
correcta**, sin renovar su caducidad por los reintentos. Pasado ese plazo se retiran esas entradas
tanto en el servidor como en la app, incluso sin conexión. Los reportes comunitarios suficientes
siguen funcionando por separado. Ajusta ese plazo si el acuerdo exige uno menor.
La app 2.1.2 solicita `/lista?fuentes=2` e interpreta la caducidad. `/lista` sigue entregando
solo las entradas comunitarias a las versiones anteriores, que no conocen ese plazo.

Para desactivar, vuelve ambas variables a `"false"`, despliega y deja que se regenere la lista.
Las copias ya descargadas caducan en 72 horas como máximo. No hay revocación inmediata de
dispositivos sin conexión.

Referencia del formato y parámetros: [documentación oficial](https://si.tellows.org/openapi/index.html).

## Cómo funciona

- `POST /reportar` `{numero, categoria, instalacion}`: una red y una instalación solo cuentan una vez por número; máximo 30 reportes al día por red e instalación.
- `GET /lista`: JSON `{v, generado, min, n:[[huella, categoria, reportes]]}` con los números reportados por al menos 3 personas distintas.
- `GET /lista?fuentes=2`: añade Tellows cuando esté autorizado y configurado. Sus entradas son
  `[huella, 0, 0, "tellows", score]`; `tellows_hasta` indica su caducidad en milisegundos. El cero
  en reportes no se interpreta como tres reportes propios. `fuentes.tellows` informa del estado.
- Los reportes se borran a los 180 días.

## Pruebas

`npm test` (requiere Node 22; usa SQLite real para probar el SQL).

