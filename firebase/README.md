# Servidor de la lista de spam (Firebase)

Este directorio es el "servidor" de los reportes de la comunidad. La app **no** guarda ni pide nada a un servidor
propio: usa tres piezas de Firebase.

| Pieza | Para qué |
|---|---|
| Authentication (anónimo) | Un identificador anónimo por instalación, sin correo ni teléfono. |
| Función `reportar` | Recibe el número y el tipo de spam, guarda **un** reporte por persona y número, limita a 30 al día y suma el conteo. |
| Función `publicarLista` | Cada 6 horas junta los números con **3 o más** reportes y publica un solo archivo `spam_list.json` (huellas, no números legibles). |
| Storage | Guarda ese archivo; cualquiera puede leerlo, nadie puede escribirlo. |

Firestore queda cerrado: ninguna app puede leerlo ni escribirlo directamente (`firestore.rules`).

## Pasos (una sola vez)

1. Entra a <https://console.firebase.google.com> y crea un proyecto (por ejemplo `bloqueador-spam`).
2. **Authentication → Método de acceso → Anónimo → Habilitar.**
3. **Firestore Database → Crear base de datos** (modo producción, región cercana).
4. **Storage → Comenzar** (modo producción).
5. **Plan Blaze.** Las funciones programadas solo se pueden publicar con el plan de pago por uso. Tiene cuota gratis
   amplia (con pocos miles de usuarios normalmente cuesta cero), pero pide tarjeta. En *Uso y facturación* pon un
   presupuesto con alerta para estar tranquilo.
6. **Agrega la app Android** al proyecto (⚙ Configuración → Tus apps → Android) con el nombre de paquete
   `com.ejemplo.bloqueador` y descarga `google-services.json`. Ponlo en `app/google-services.json`.
   No es un secreto: va dentro de la app. Lo que protege los datos son las reglas de este directorio.
7. En tu computadora, con Node 20 instalado:

   ```bash
   npm install -g firebase-tools
   firebase login
   cd firebase/functions && npm install && cd ..
   firebase use --add          # elige tu proyecto
   firebase deploy             # sube reglas y funciones
   ```
8. Al compilar de nuevo, la app detecta `google-services.json` y activa la función (Premium → Lista de spam).

## Pruebas

`cd functions && npm test` comprueba la huella de los números y las reglas de validación. La huella debe dar el
mismo resultado que `SpamRules.kt` en la app (hay una prueba con el mismo valor en los dos lados).

## Moderación

- Los números se pueden borrar a mano desde la consola: Firestore → colección `spam` → documento (la huella).
  Un número borrado vuelve a entrar si lo vuelven a reportar 3 personas distintas.
- Para cambiar el umbral o el límite diario edita `UMBRAL_REPORTES` y `MAX_REPORTES_POR_DIA` en `functions/lib.js`.

## Lo que falta endurecer antes de una versión pública grande

- **App Check (Play Integrity):** hoy cualquiera con conocimientos puede crear cuentas anónimas y reportar en masa.
  Los 3 reportes de personas distintas y el límite diario lo dificultan, pero no lo impiden. App Check exige que
  los reportes vengan de la app real instalada desde Google Play.
- **Aviso de privacidad** que mencione los reportes y el identificador anónimo, y la sección de seguridad de datos de Play.
