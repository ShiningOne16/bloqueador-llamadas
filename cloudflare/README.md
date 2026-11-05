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

## Cómo funciona

- `POST /reportar` `{numero, categoria, instalacion}`: una red y una instalación solo cuentan una vez por número; máximo 30 reportes al día por red e instalación.
- `GET /lista`: JSON `{v, generado, min, n:[[huella, categoria, reportes]]}` con los números reportados por al menos 3 personas distintas.
- Los reportes se borran a los 180 días.

## Pruebas

`npm test` (requiere Node 22; usa SQLite real para probar el SQL).
