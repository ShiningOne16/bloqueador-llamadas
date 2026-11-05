# Solicitud de acceso a Tellows (borrador para enviar por el propietario)

Contacto y formulario: https://www.tellows.com/s/about-en/tellows-api-partnership-program

**Subject: Scorelist API partnership for an Android call-blocking app in Mexico**

Hello Tellows team,

I am developing Bloqueador, an Android call-blocking and SMS app focused initially on Mexico.
The project is currently under development. We plan to offer a monthly subscription, but billing
is not active yet.

We would like to integrate your Scorelist API for Mexican phone numbers (country=mx), initially
filtering for scores 7–9 and at least three comments.

Our proposed architecture is:

- Our Cloudflare Worker downloads the Scorelist daily using a server-side credential.
- It converts phone numbers to fingerprints and distributes that derived list to our app's users.
- The app checks incoming calls locally, without sending their numbers or address books to Tellows.
- The cached data expires within 72 hours of a successful download, including on offline devices.
- Users can override false positives locally.

Could you confirm the appropriate partnership license, pricing and permitted distribution model
for this use, including whether serving the derived fingerprint list from a public endpoint is allowed?
Please also specify attribution requirements, update and retention limits, and any required privacy
notices or changes to our proposed architecture.

We would appreciate evaluation credentials and the current Scorelist schema for Mexico before launch.

Project: https://github.com/ShiningOne16/bloqueador-llamadas

Thank you.
