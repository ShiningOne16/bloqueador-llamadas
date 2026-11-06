# Servidor de la lista de spam (Cloudflare)

Un Worker de Cloudflare con una base D1 (reportes) y un KV (la lista publicada).
El coste de Cloudflare depende del volumen y los límites del plan. El acceso a Tellows se contrata por separado.
La app transmite únicamente el número elegido, la categoría y un identificador aleatorio de instalación
al confirmar un reporte; Cloudflare también recibe la IP de la conexión. La base guarda huellas del
número y huellas con sal secreta de la IP y la instalación. Esto reduce la exposición, pero no garantiza
anonimato: el espacio de números telefónicos puede enumerarse. No se reciben agendas ni historiales.

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

## Publicar desde Chrome en Android, sin compartir credenciales

Para actualizar el Worker existente desde el panel de Cloudflare utiliza
[`dashboard/worker.js`](dashboard/worker.js), que reúne los módulos del servidor en un solo archivo.
No pegues únicamente `src/index.js`: sus importaciones requieren los otros módulos.

1. En Chrome abre el panel de Cloudflare, inicia sesión y activa **Sitio para computadoras** si hace falta.
2. En **Workers & Pages** selecciona **bloqueador-spam**, el que ya sirve la URL de la app. No crees otro Worker.
3. Conserva los bindings de producción **DB** (la D1 existente), **LISTA** (el KV existente) y el secreto
   **SAL**. No cambies su valor ni copies los identificadores de ejemplo de `wrangler.toml`.
4. Abre **Edit code / Editar código**. Guarda una copia del código anterior y sustituye el contenido
   del archivo JavaScript principal por todo el contenido de `dashboard/worker.js`.
5. Pulsa **Deploy / Implementar** para publicar la nueva versión en producción. Solo guardar un borrador no basta.
6. En **Settings / Configuración → Triggers / Activadores → Cron Triggers**, conserva o añade el cron
   `0 */6 * * *` (cada seis horas, UTC). No actives Tellows; sus dos variables de activación deben seguir desactivadas.
7. Comprueba `/lista?fuentes=3`: tras la siguiente ejecución del cron, debe aparecer `"min":1`.
   Justo después de publicar puede seguir apareciendo `"min":3`, porque KV conserva la lista anterior
   hasta regenerarla. La caché HTTP puede conservar una respuesta otra hora.

Este método usa tu sesión del navegador: no tienes que crear un API Token ni enviarlo al chat.
No ejecuta migraciones ni sustituye los recursos o secretos del servidor.

Para regenerar el archivo después de modificar las fuentes: `npm install` y `npm run build:dashboard`.
Las pruebas verifican que el archivo publicado corresponde a las fuentes actuales y prueban sus
handlers de reportar, publicar por cron y retirar con SQLite real.

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

- `POST /reportar` `{numero, categoria, instalacion}`: solo números mexicanos; una red y una instalación
  cuentan una vez por número. Máximo 30 intentos al día por red e instalación. Se rechazan lotes y campos
  adicionales (nombres, agendas, historiales, etc.). No prueba que cada instalación sea una persona distinta;
  las reinstalaciones y redes coordinadas siguen siendo una limitación de estos controles.
- `POST /retirar` `{numero, instalacion}`: retira únicamente el voto de esa instalación y vuelve a publicar
  la lista. Es idempotente y admite reintentos tras fallos de publicación. Tiene un límite separado de
  30 solicitudes al día por red e instalación, sin reiniciar los contadores de reportes.
- `GET /lista`: JSON `{v, generado, min, n:[[huella, categoria, reportes]]}` con los números con al menos un reporte explícito (`min: 1`). No implica confirmación de otros usuarios.
- `GET /lista?fuentes=2`: añade Tellows cuando esté autorizado y configurado. Sus entradas son
  `[huella, 0, 0, "tellows", score]`; `tellows_hasta` indica su caducidad en milisegundos. El cero
  en reportes no se interpreta como un reporte comunitario. `fuentes.tellows` informa del estado.
- `GET /lista?fuentes=3`: mismo formato combinado, con `comunidad_hasta`. La comunidad caduca en la app
  como máximo 7 días después de `generado`, incluso sin conexión. La app también calcula ese límite para
  listas de servidores antiguos que no traigan el campo. Las listas comunitarias sin fecha no se usan.
- Los reportes se borran a los 180 días. La lista pública se regenera cada 6 horas y al retirar un reporte;
  la caché HTTP puede durar una hora. Las copias en otros teléfonos se corrigen en su próxima descarga o
  al caducar. No existe revocación inmediata sin conexión y las versiones antiguas no aplican el nuevo
  límite de 7 días.

## Reportes voluntarios en Android (2.1.5)

La agenda y el historial se consultan únicamente en el teléfono. Una llamada perdida o un nombre/nota
que contenga «spam», «estafa» o «fraude» solo muestra una sugerencia de revisión al abrir su panel.
Nunca se reporta automáticamente ni se crea una lista blanca comunitaria con los contactos.

**Reportar y bloquear** muestra primero el número, la categoría y los datos que se transmitirán. Al
confirmar, el bloqueo personal queda activo aunque falle internet; el envío puede reintentarse. Antes
de abrir la conexión se excluyen contactos ocultos y los de la sesión con NIP alternativo. Si la lista
privada no puede descifrarse, no se autoriza el envío.

**No es spam** quita el bloqueo manual y añade una excepción local a la lista de spam. Si había un voto
enviado, intenta retirarlo; un fallo de red no deshace la corrección local y se ofrece un botón para
reintentar. Los horarios, concentración y otras reglas personales siguen aplicándose.

Desde Android 2.1.6, la app guarda también los intentos sin confirmación antes de abrir la conexión.
Si el servidor guardó el voto pero se perdió la respuesta, **No es spam** permite retirarlo igualmente.
El estado pendiente se conserva después de la corrección local y de los fallos de retirada, hasta que
el servidor confirma el borrado. También se recuperan los intentos locales guardados por versiones anteriores.

Este cambio requiere desplegar el Worker actualizado para habilitar `/retirar`; un servidor anterior
seguirá aceptando los reportes normales y la corrección local funcionará, pero retirar el voto dará un
error recuperable hasta el despliegue. No hay cambios de esquema. Usa D1, KV y SAL existentes, sin
reemplazar identificadores reales por los marcadores de `wrangler.toml` y sin activar Tellows.

## Umbral de un reporte y llamadas archivadas (Android 2.1.9)

La lista comunitaria admite un número desde el primer reporte. Se mantienen la deduplicación por red
e instalación, los 30 intentos diarios y las caducidades. Tellows sigue desactivado; su mínimo de tres
comentarios no cambia. Esta política requiere desplegar el Worker actualizado con D1/KV/SAL existentes.
Tras el despliegue, el próximo cron vuelve a publicar con `min: 1` (hasta seis horas). Las apps anteriores
que exigían tres reportes deben actualizarse para bloquear entradas con uno o dos reportes.

El botón de Archivadas revisa y confirma números únicos y una categoría. Emite solicitudes individuales
a `/reportar`, nunca un lote JSON, una agenda o un historial. Respeta los números ocultos y «No es spam»;
si no se puede descifrar la lista privada, impide el proceso. Al fallar una solicitud o recibir 429,
detiene los siguientes envíos, conserva el bloqueo local y permite reintentar sin repetir los confirmados.

## Pruebas

`npm test` (requiere Node 22; usa SQLite real para probar el SQL).

