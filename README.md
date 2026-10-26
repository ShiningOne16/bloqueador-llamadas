# Bloqueador de llamadas (Android)

App que rechaza automáticamente las llamadas de números que **no están en tus contactos**.
Usa la API oficial de Android para filtrar llamadas (`CallScreeningService`), así que no necesita
root ni quedarse abierta: Android le pregunta a la app antes de que suene cada llamada.

## Qué hace

Funciona como app de teléfono, con cinco pestañas:

- **Recientes:** registro de llamadas con filtros (todas, perdidas, bloqueadas). Toca una para ver su historial, llamar, mandar SMS o guardarla como contacto.
- **Contactos:** tu agenda con búsqueda (sin importar acentos), favoritos y letras. Llama, manda SMS o edita.
- **Teclado:** marcador con sugerencias de contactos mientras escribes. Mantén el 0 para "+", mantén ⌫ para borrar todo.
- **Mensajes:** tus SMS por conversación, con filtros de no leídos y desconocidos. "Responder" abre tu app de SMS.
- **Bloqueo:** los ajustes de bloqueo de llamadas y SMS (abajo).

La pantalla de llamada en curso (contestar/colgar) sigue siendo la del sistema.

### Bloqueo

- Rechaza llamadas de números que no están en tus contactos, sin que suene el teléfono.
- Opcional: bloquea números ocultos o privados.
- Opcional: si el mismo número vuelve a llamar en menos de 3 minutos, lo deja pasar (por si es una urgencia).
- Muestra una lista de las llamadas bloqueadas.
- Las llamadas bloqueadas siguen apareciendo en el historial del teléfono, por si quieres devolverlas.
- **Mensajes:** los SMS de números desconocidos llegan a tu app de mensajes, pero sin sonido ni notificación,
  y quedan en la pestaña "Mensajes". Opcional: incluir empresas (BANCO, TELCEL…) y códigos cortos.

Requiere **Android 10 o superior**.

## Cómo obtener el APK

### Opción A: Android Studio (computadora)
1. Abre la carpeta `BloqueadorLlamadas` con Android Studio.
2. Espera a que sincronice Gradle.
3. Conecta tu teléfono con la depuración USB activada y pulsa ▶ Run.
   O bien: *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.

### Opción B: GitHub (sin instalar nada)
1. Crea un repositorio en GitHub y sube el contenido de esta carpeta.
2. En la pestaña **Actions** se ejecuta "Compilar APK" automáticamente.
3. Al terminar, descarga el artefacto **Bloqueador-APK** e instálalo en tu teléfono
   (tendrás que permitir "instalar apps de origen desconocido").

## Primer uso
1. Abre la app.
2. Pulsa **Activar filtrado** y elige *Bloqueador* como app de filtrado de llamadas / spam.
3. Acepta los permisos que pide al abrir (contactos, registro de llamadas, teléfono y SMS).
4. En la pestaña **Mensajes**, toca **Dar acceso a notificaciones** y activa *Bloqueador*.
   Si Android dice "Configuración restringida": abre la info de la app → menú ⋮ → *Permitir configuración restringida*.
5. Listo. Puedes cerrar la app.

Para desactivarla temporalmente, usa el interruptor "Bloquear desconocidos".

## Estructura
- `BlockerService.kt` – decide si cada llamada entrante pasa o se rechaza.
- `SmsNotificationListener.kt` – quita las notificaciones de SMS de desconocidos.
- `Contacts.kt` – consultas a la agenda.
- `Prefs.kt` – ajustes y registro de llamadas bloqueadas.
- `MainActivity.kt` – navegación entre pestañas (Jetpack Compose).
- `PhoneData.kt` – lectura de registro de llamadas, contactos y SMS; acciones (llamar, SMS, contactos).
- `ScreenRecents.kt`, `ScreenContacts.kt`, `ScreenKeypad.kt`, `ScreenMessages.kt`, `ScreenBlocking.kt` – cada pestaña.
- `Ui.kt` – componentes comunes.

## Notas
- Si la app no tiene permiso de contactos, **no bloquea nada** (para no colgarle a gente conocida).
- Los números se comparan por sus últimos 10 dígitos, así `+52 55 1234 5678` y `55 1234 5678` cuentan como el mismo.
- Android no deja que una app que no es la de mensajes predeterminada bloquee SMS; por eso aquí se silencian
  (el mensaje queda en tu app de SMS, sin avisar).
- Algunas marcas (Xiaomi, Huawei, etc.) pueden pedir que desactives el ahorro de batería para la app.
