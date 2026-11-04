# Bloqueador de llamadas (Android)

App que rechaza automáticamente las llamadas de números que **no están en tus contactos**.
Usa la API oficial de Android para filtrar llamadas (`CallScreeningService`), así que no necesita
root ni quedarse abierta: Android le pregunta a la app antes de que suene cada llamada.

## Qué hace

Funciona como app de teléfono, con cuatro pestañas principales (Recientes, Mensajes, Protección y Premium).
Dentro de **Recientes**, una barra inferior agrupa **Recientes, Contactos y Favoritos**, con un botón
independiente de **Marcar** a su lado. El botón Atrás vuelve al registro de llamadas; tocar Marcar
otra vez también cierra el teclado. Cada vista conserva su búsqueda, filtros y número marcado al cambiar de sección.

- **Recientes:** registro de llamadas con búsqueda (por nombre o número) y filtros (todas, perdidas, bloqueadas). Toca una para ver su historial, llamar, mandar SMS o guardarla como contacto. Las llamadas se agrupan por día (Hoy, Ayer, día de la semana, fecha). Desliza una fila a la derecha para llamar o a la izquierda para mandar SMS: el fondo muestra la acción, crece al acercarse y vibra suave al engancharse (la fila regresa sola a su lugar).
- **Contactos:** tu agenda con búsqueda en píldora (sin importar acentos), favoritos, filtro por origen, botón **+** para crear y los mismos gestos de deslizar para llamar o mandar SMS. Crea, edita y elimina contactos dentro de la app, eligiendo dónde guardarlos: **Google** (respaldo en la nube), **SIM** (solo nombre y un número) o **solo el teléfono**.
- **Favoritos:** acceso separado a los contactos marcados como favoritos, con búsqueda y filtro por origen.
- **Marcar:** marcador con sugerencias de contactos mientras escribes. Mantén el 0 para "+", mantén ⌫ para borrar todo.
- **Mensajes:** SMS por conversación, redacción y respuestas dentro de Bloqueador, borradores, elección de SIM,
  estado de envío y filtros. Si eliges Bloqueador como app predeterminada, recibe, guarda y elimina SMS.
  **Multimedia recibida** permite descargar y abrir el texto y los adjuntos de MMS.
- **Protección:** te dice en una frase si estás protegido, los pasos que faltan, los ajustes y la actividad.

Al tocar una llamada se abre un panel con **Llamada, WhatsApp, Editar y Bloquear**, además del acceso
a SMS junto al número y su historial. No incluye pagos. Editar abre el contacto existente o el formulario
para guardarlo. WhatsApp requiere el código de país del número (o que Android pueda deducirlo).
El bloqueo manual se aplica a llamadas en todos los perfiles y tiene prioridad sobre sus excepciones;
se revierte desde el mismo panel. El interruptor principal de llamadas permite pausarlo. No bloquea SMS.
También se puede gestionar desde **Protección → Bloqueados manualmente**, incluso después de borrar el historial.

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

### Archivar y eliminar llamadas y mensajes

Mantén pulsada una fila de llamadas o conversaciones para entrar en **selección múltiple**.
Toca otras filas para añadirlas o quitarlas; la barra superior permite seleccionar los elementos visibles,
cancelar y borrar con confirmación. En una conversación, mantener pulsada una burbuja permite seleccionar
varios SMS individuales. Atrás o la X cancelan la selección. Los gestos de llamar/enviar se pausan mientras
seleccionas. Los registros nuevos no se añaden solos a la selección y los SMS posteriores al corte se conservan.
El borrado múltiple de SMS requiere que Bloqueador sea la app predeterminada.

- En **Recientes**, toca una llamada y usa el panel inferior para **Archivar**, **Desarchivar** o **Eliminar del registro**; también puedes mantenerla pulsada para seleccionar varias.
  Si la fila agrupa varias llamadas, la acción afecta únicamente a los registros de ese grupo.
  El filtro **Archivadas** permite recuperarlas. Archivar solo las oculta en Bloqueador; las llamadas
  nuevas del mismo número siguen apareciendo normalmente.
- **Eliminar llamadas** pide confirmación y el permiso opcional para escribir en el registro. Borra esos
  registros del historial del teléfono y de otras apps que lo consulten. Si Android no concede el permiso,
  se puede abrir la app Teléfono para completar el borrado allí. No modifica la actividad de Protección.
- En **Mensajes**, el menú **⋮** de una conversación y el de su cabecera permiten **Archivar**, **Desarchivar**
  o **Eliminar**. El archivo es local a Bloqueador y se consulta desde **Archivadas**. Un SMS nuevo devuelve
  la conversación a la bandeja al actualizar; los mensajes no se borran y las notificaciones no cambian.
- **Eliminar SMS** borra directamente desde Bloqueador si es tu app predeterminada, con confirmación.
  Mantén pulsada una burbuja para seleccionar uno o varios mensajes. Si no es la app predeterminada,
  se abre la app elegida en Android para completar allí el borrado.

El archivo guarda referencias locales; no crea una copia de seguridad. Si borras los originales desde
otra app, dejan de estar disponibles también en Archivadas. Archivar o desarchivar no exige permisos de escritura.

### Usar Bloqueador como app de SMS

1. Abre **Mensajes → Usar como app de SMS** y confirma la elección en el diálogo de Android.
2. Concede los permisos de SMS y, si quieres recibir avisos, el de notificaciones. **Mostrar mis SIM**
   permite elegir la línea de envío cuando hay varias tarjetas. Sin cambiar la app predeterminada,
   Bloqueador sigue funcionando como lector y bloqueador de llamadas.
3. Pulsa **Nuevo** o **Responder**, escribe el texto, comprueba la SIM y pulsa **Enviar SMS**.
   Nunca se envía un mensaje solo por abrir un enlace o una conversación.

Los SMS existentes permanecen en la base de datos de Android. La recepción guarda primero una cola
local duradera, incluso antes del desbloqueo. Los SMS se incorporan durante la entrega del sistema y
WorkManager recupera los pendientes. Las confirmaciones
de cada parte de un SMS largo se guardan en SQLite. **Enviado** significa que el teléfono aceptó todas las
partes, no que el destinatario las recibió. Los fallos y los envíos sin confirmar no se reintentan solos;
revisa el texto antes de reenviar, ya que algunas partes pueden haber llegado. Máximo: 10 partes por envío.

Los borradores se conservan localmente. Las notificaciones de SMS desconocidos se deciden antes de
publicarlas cuando Bloqueador es la app predeterminada, respetando los perfiles y horarios. En este modo
no hace falta acceso a las notificaciones de otras apps para silenciar sus propios SMS.

La recepción de MMS utiliza el transporte del operador: guarda los avisos, descarga con la API de Android
y conserva las descargas fallidas para reintentar desde **Multimedia recibida**. Los adjuntos se abren con
un visor instalado mediante permisos temporales de archivo. Esta versión envía **SMS de texto a un solo
destinatario**; no envía fotos/MMS, no ofrece grupos MMS ni RCS. Los MMS recibidos tienen su propia vista.
Los costos de SMS y datos dependen del operador. Puedes cambiar de app predeterminada desde Android.

Antes de usar esta versión como app principal, hay que comprobar en un teléfono real: recepción con la
app cerrada y tras reiniciar, SIM doble, SMS largos, modo avión/fallos, notificaciones denegadas y recepción
de MMS según el operador. Las pruebas automatizadas no sustituyen esa validación de módem y red.

### Premium

La pestaña **Premium** está en la barra inferior, junto a Recientes, Mensajes y Protección. Abre la **vista previa gratuita** de:

- **Horarios de protección:** elige días de la semana y horas de inicio y fin para llamadas y SMS.
  Se usa la hora local del teléfono. Un turno nocturno continúa al día siguiente: lunes 22:00–07:00
  incluye el martes hasta las 07:00. Horas iguales significan las 24 horas del día seleccionado.
- **Contactos por horario:** desde el editor de un perfil, pulsa **Elegir de mi agenda** para buscar
  y seleccionar los números que se rechazarán solo durante sus días y horas. Puedes elegir cada teléfono
  de un contacto por separado. Fuera de ese horario vuelven a entrar; las excepciones de "siempre"
  conservan prioridad y el editor avisa si alguna afecta al número seleccionado. Sin horario, el bloqueo
  de esos contactos se aplica todo el día. No cambia sus SMS ni modifica tu agenda.
- **Excepciones de llamadas:** permite números aunque no estén en contactos o bloquea números aunque
  estén guardados. Las reglas son por número completo (comparado por los últimos 10 dígitos), sin prefijos.
  Se aplican incluso fuera del horario del perfil; no se aplican a SMS ni a números ocultos.
- **Contactos ocultos:** oculta a un contacto desde Premium con un PIN de 4 dígitos. Sus llamadas se rechazan
  (incluso con el filtro pausado, sin anotarse en el historial del teléfono ni en Protección) y sus SMS se
  descartan si Bloqueador es tu app de SMS predeterminada; si no, solo se quita el aviso. No aparece en
  contactos, recientes, mensajes ni marcador. El PIN se guarda como hash PBKDF2 y la lista va cifrada con el
  almacén seguro de Android; tras 5 intentos fallidos hay un bloqueo temporal que va creciendo, y la pantalla
  no permite capturas. **Olvidé mi PIN** borra para siempre todos los contactos ocultos: su contacto de la
  agenda (también en Google), su registro de llamadas y sus SMS (estos solo si Bloqueador es la app
  predeterminada). Otras apps, como la agenda o el teléfono del sistema, pueden seguir mostrando al contacto
  y sus llamadas anteriores.
- **Notas de números:** ponle una etiqueta (hasta 30 letras) y una nota (hasta 200) a cualquier número, desde el panel
  de una llamada o desde Premium → Notas de números. Si el número no está en tu agenda, la etiqueta aparece como su
  nombre en Recientes y la búsqueda también la encuentra. Se guardan solo en tu teléfono; los números de contactos
  ocultos no se listan y sus notas se borran si restableces el PIN.
- **Perfiles:** Trabajo, Noche y Vacaciones vienen preparados. Puedes editarlos, crear otros, eliminarlos
  y seleccionar uno a la vez. Cada perfil guarda su horario, excepciones y reglas de llamadas y SMS.

Al instalar o actualizar, sigue seleccionada **Protección básica**: no cambia el bloqueo hasta que elijas
un perfil. Puedes volver a Protección básica en cualquier momento. Fuera del horario del perfil se
permiten llamadas y avisos de SMS, salvo las excepciones de llamadas. Los interruptores principales de
**Protección** pausan todas las reglas del canal correspondiente, incluidas las excepciones de llamadas.
Un bloqueo explícito no se anula al volver a llamar. Los perfiles y borradores del editor se guardan localmente.

El modelo comercial será una **suscripción mensual**. El cobro, el precio y la verificación de compras
todavía no están configurados: esta versión permite probar las funciones y no realiza cargos.

### Verificación

GitHub Actions ejecuta `testDebugUnitTest` y `assembleRelease`, verifica la firma y adjunta el APK.
Las pruebas cubren límites de horario, turnos nocturnos, cambio de semana, excepciones, selección
de perfiles y conservación del borrador al recrear la actividad. Antes de distribuir, prueba llamadas
reales y notificaciones SMS en un dispositivo con el rol de filtrado y los permisos concedidos.

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
4. En **Mensajes**, elige **Usar como app de SMS** para enviar, recibir y borrar dentro de Bloqueador.
   Si prefieres mantener otra app de SMS, activa Bloqueador en **Acceso a notificaciones** para silenciar sus avisos.
   Si Android dice "Configuración restringida": abre la info de la app → menú ⋮ → *Permitir configuración restringida*.
5. Listo. Puedes cerrar la app.

Para desactivarla temporalmente, usa el interruptor "Bloquear desconocidos".

## Diseño

Rediseñado con la skill UI/UX Pro Max: estilo minimalista con paleta de fondo blanco (`#FFFFFF`),
azul `#4C98E9`, negro azulado `#0F1319` y gris `#555E69`, y verde de "protegido".
Modo claro y oscuro con contraste mínimo 4.5:1: sobre el azul `#4C98E9` el texto es oscuro (el blanco
solo daría 3:1) y, en modo claro, los textos e íconos azules usan un tono más profundo del mismo matiz.
Íconos vectoriales con texto, botones de al menos 48 dp, bienvenida que explica los permisos antes
de pedirlos y confirmación antes de borrar. Colores, letra y espaciado están en `Theme.kt`.

Acabado premium: avatares tonales (fondo suave y letra oscura, contraste ≥ 7:1), filtros en píldora,
panel de llamada con acción principal en azul de marca, tarjeta de protección discreta, tarjeta
Premium con degradado azul noche, filas que se reordenan con animación y vibración suave en gestos
y selección.

## Estructura
- `BlockerService.kt` – decide si cada llamada entrante pasa o se rechaza.
- `SmsNotificationListener.kt` – quita las notificaciones de SMS de desconocidos.
- `Contacts.kt` – consultas a la agenda.
- `Prefs.kt` – ajustes y registro de llamadas bloqueadas.
- `MainActivity.kt` – navegación entre pestañas (Jetpack Compose).
- `ScreenPhone.kt` – submenú de Recientes, Contactos, Favoritos y Marcar.
- `ScreenPremium.kt` – selección y edición de perfiles, horarios y excepciones.
- `ScreenScheduledContacts.kt` – selección y búsqueda de números de la agenda para bloquear por horario.
- `ArchiveState.kt`, `ArchiveStore.kt` – estado y persistencia del archivo local de llamadas y SMS.
- `HistoryMenu.kt` – acciones de archivar, desarchivar y eliminar.
- `PremiumRules.kt` – reglas de horario y prioridad de excepciones, sin dependencias de Android.
- `PremiumStore.kt` – persistencia local de perfiles y selección activa.
- `PhoneData.kt` – lectura de registro de llamadas, contactos y SMS; acciones (llamar, SMS, contactos).
- `ScreenRecents.kt`, `ScreenContacts.kt`, `ScreenKeypad.kt`, `ScreenMessages.kt`, `ScreenProtection.kt` – cada pestaña.
- `Theme.kt` – sistema de diseño (colores, tipografía, formas, espaciado).
- `ContactStore.kt` – cuentas disponibles y crear/editar/eliminar contactos.
- `ScreenContactForm.kt` – formulario de contacto.
- `Ui.kt` – componentes comunes.

## Notas
- Si la app no tiene permiso de contactos, **no bloquea nada** (para no colgarle a gente conocida).
- Los números se comparan por sus últimos 10 dígitos, así `+52 55 1234 5678` y `55 1234 5678` cuentan como el mismo.
- Android no deja que una app que no es la de mensajes predeterminada bloquee SMS; por eso aquí se silencian
  (el mensaje queda en tu app de SMS, sin avisar).
- Algunas marcas (Xiaomi, Huawei, etc.) pueden pedir que desactives el ahorro de batería para la app.

