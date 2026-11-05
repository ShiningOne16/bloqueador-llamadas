# Solicitud de cotización a Hiya (borrador para enviar por el propietario)

**Destinatario:** api@hiya.com
**Producto solicitado:** Hiya Protect / Cache API para identificar y bloquear llamadas de spam.
**Estado:** borrador; no enviado. No se han contratado servicios ni solicitado cargos.

Referencia de acceso: https://developer.hiya.com/docs/getting-started/introduction

---

**Subject: Hiya Protect Cache API — pricing and partnership inquiry for an Android app in Mexico**

Hello Hiya team,

I am developing Bloqueador, an Android call-blocking and SMS app initially focused on Mexico.
The project is under development. We plan to offer a monthly subscription, but billing is not
active yet.

We are interested in Hiya Protect and the Cache API for identifying and blocking incoming spam
and fraud calls. Could you advise whether this product is available for an independent Android
app and provide a quotation?

Our proposed architecture is:

- A Cloudflare Worker downloads and refreshes the licensed spam/fraud dataset using server-side credentials.
- The server converts phone numbers to fingerprints and distributes the derived list to our app's users.
- This derived list would be served from a public HTTPS endpoint, unless your license requires authenticated distribution.
- The app checks incoming calls locally, without transmitting incoming phone numbers, address books,
  call histories or SMS content to Hiya.
- Cached entries would expire within 72 hours of a successful refresh, or sooner if required by your terms.
- Users can override false positives locally.

Please confirm whether this architecture and distribution model are permitted. If not, please
suggest an approved API or SDK integration and explain any required data sharing.

Could your quotation and technical response address the following?

1. **Pricing:** setup and evaluation fees, recurring charges, minimum monthly commitment, contract
   term, billing currency, applicable taxes, overage fees and cancellation conditions.
2. **Billing metric:** whether charges depend on monthly active users, devices, downloads, lookups
   or another metric, and how that metric would be measured for an offline-first app.
3. **Illustrative volumes:** pricing for 100, 1,000 and 10,000 monthly active users. These are
   comparison scenarios, not forecasts or commitments.
4. **Mexico coverage:** availability and scope of the Mexican spam/fraud dataset, available categories,
   update frequency and any coverage or accuracy information you can share.
5. **License:** permission to distribute a derived fingerprint list, store data on our server and user
   devices, use it in a paid subscription app, and combine it with our own community reports.
6. **Privacy and attribution:** required notices, branding, data-use policies, analytics, telemetry
   or call-reporting obligations, including whether incoming calls can be checked entirely locally.
7. **Data lifecycle:** permitted retention, cache expiry, refresh limits, removal/correction updates,
   false-positive handling and deletion requirements when the agreement ends.
8. **Evaluation and onboarding:** test credentials, sandbox or trial availability, production approval
   requirements, required business/entity information and expected onboarding time.

Project: https://github.com/ShiningOne16/bloqueador-llamadas

We would like to compare available plans before selecting a provider. Please do not activate a
paid service or incur charges without a separate acceptance from us.

Thank you.
