# Bloqueador de llamadas (Android)

App que rechaza automáticamente las llamadas de números que **no están en tus contactos**.
Usa la API oficial de Android para filtrar llamadas (`CallScreeningService`), así que no necesita
root ni quedarse abierta: Android le pregunta a la app antes de que suene cada llamada.

## Qué hace

- Rechaza llamadas de números que no están en tus contactos, sin que suene el teléfono.
- Opcional: bloquea números ocultos o privados.
- Opcional: si el mismo número vuelve a llamar en menos de 3 minutos, lo deja pasar (por si es una urgencia).
- Muestra una lista de las llamadas bloqueadas.
- Las llamadas bloqueadas siguen apareciendo en el historial del teléfono, por si quieres devolverlas.

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
3. Pulsa **Permitir contactos**.
4. Listo. Puedes cerrar la app.

Para desactivarla temporalmente, usa el interruptor "Bloquear desconocidos".

## Estructura
- `BlockerService.kt` – decide si cada llamada entrante pasa o se rechaza.
- `Prefs.kt` – ajustes y registro de llamadas bloqueadas.
- `MainActivity.kt` – pantalla de configuración (Jetpack Compose).

## Notas
- Si la app no tiene permiso de contactos, **no bloquea nada** (para no colgarle a gente conocida).
- Los números se comparan por sus últimos 10 dígitos, así `+52 55 1234 5678` y `55 1234 5678` cuentan como el mismo.
- Algunas marcas (Xiaomi, Huawei, etc.) pueden pedir que desactives el ahorro de batería para la app.
