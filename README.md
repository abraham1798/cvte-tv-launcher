# cvte-tv-launcher

Launcher ligero estilo Apple TV y guía de limpieza (debloat) para televisores Android con placa **CVTE**
(probado en una MT5886 con Android 7.0), **sin root**, todo por ADB en red.

*A lightweight Apple-TV-style home screen and debloat scripts for Android TVs built on CVTE boards
(tested on an MT5886, Android 7.0). No root, everything over network ADB. Docs are in Spanish.*

## Qué incluye

- **Launcher nativo** (`launcher/`, paquete `com.cleanyourtv.launcher`, Java sin AndroidX, ~0,7 MB):
  - Reloj grande, saludo y fecha con la fuente [Outfit](https://github.com/Outfitio/Outfit-Fonts) (OFL).
  - Botones Entradas, Wi-Fi (con el nombre de la red) y Ajustes.
  - Dock con apps o **accesos directos a entradas** (por ejemplo, un mosaico que abre HDMI 1).
  - Cuadrícula de apps con tarjetas de vidrio translúcido, iconos de línea blancos y un único resaltado azul
    que salta de mosaico en mosaico.
  - Navegación con el control determinista: Arriba/Abajo mueven exactamente una fila y se recuerda la última
    posición.
  - Menú **Entradas** que usa la API propia de CVTE: en estas placas, abrir HDMI con el TV Input Framework
    estándar deja la pantalla en negro.
  - Fondo de pantalla opcional y animación opcional de estrellas.
- **Scripts de limpieza** con copia de seguridad previa y reversión completa.
- **Herramientas** para generar los logos del dock y mejorar o preparar el fondo de pantalla.

## Requisitos

- Tele con Android 7.0 o superior (minSdk 24) y ADB por red (puerto 5555) activado en Opciones de
  desarrollador.
- En el PC: `adb`, un JDK (probado con JDK 26), bash (en Windows sirve Git Bash) y, del Android SDK,
  **build-tools 34** y **platforms/android-34**. No hace falta Gradle ni Android Studio.
  - El script busca el SDK en `ANDROID_SDK`, en `../android-sdk`, en `%LOCALAPPDATA%\cleanyourtv\android-sdk` o
    en `~/Android/Sdk`.

## Uso rápido

```bash
echo 192.168.x.x > .tv-host        # la IP de tu tele 
adb connect 192.168.1.50:5555       # acepta el aviso "¿Permitir depuración?" en la tele
bash build-and-install.sh           # compila, instala y lo pone como inicio predeterminado
```

`build-and-install.sh` instala con `pm install` (en algunas teles `adb install` se queda esperando) y usa
`cmd package set-home-activity`. **El launcher original de CVTE se deja activado**: en estas placas
desactivarlo puede impedir el arranque.

## Configuración

Copia `launcher/app/src/main/res/values/config.xml` a `launcher/app/src/main/res-local/values/config.xml`
(ignorado por git) y edita ahí. Los valores de `res-local` tienen prioridad.

| Clave | Para qué sirve |
|---|---|
| `dock_apps` | Mosaicos del dock: paquetes de apps o accesos `input:FUENTE\|Nombre\|banner` (`FUENTE` es el nombre de la entrada en la tele: `HDMI 1`, `AV`, `TV`…) |
| `dock_columns` | Tamaño de los mosaicos del dock (número más alto = más pequeños) |
| `columns` | Columnas de la cuadrícula |
| `hidden_apps` | Apps que no se muestran |
| `star_animation`, `show_libra` | Destellos de estrellas y constelación de Libra (las posiciones de `StarField.java` dependen del fondo) |

Para probar un tamaño de dock sin recompilar:
`adb shell am start -S -n com.cleanyourtv.launcher/.MainActivity --ei dock_columns 5`.

## Imágenes (no incluidas)

El repositorio no trae el fondo ni logos de marcas de terceros. Se añaden localmente y quedan ignorados por git:

- **Fondo:** `launcher/app/src/main/assets/wallpaper.jpg` (también `.png` o `.webp`), en 1920×1080.
  - El launcher no dibuja un oscurecido encima, para ahorrar trabajo en cada cuadro. Para que el texto se lea,
    oscurece la imagen con
    `java tools/Bake.java original.png launcher/app/src/main/assets/wallpaper.jpg`.
  - Para una imagen pequeña o muy comprimida, `tools/Enhance.java` la amplía (Lanczos), limpia manchas de
    compresión, enfoca y añade grano fino. Para leer WebP necesita TwelveMonkeys `imageio-webp` en el classpath.
- **Logos del dock:** PNG con fondo transparente en `launcher/app/src/main/assets/banners/`.
  - Se usan como `banners/<nombre>.png` en un acceso `input:`, o como `banners/<paquete>.png` para reemplazar
    el banner de una app.
  - `tools/Banners.java` muestra cómo generarlos a partir de un icono o logo que tengas tú.

## Limpieza (debloat)

1. **Copia de seguridad** antes de tocar nada: lista de paquetes, ajustes (`settings list`) y `getprop`, en
   una carpeta `backup/`.
2. **Desactivar** de a poco y comprobar entre tandas:
   `bash tools/disable.sh paquete1 paquete2`.
   - Usa `pm disable-user --user 0`: nunca desinstala apps del sistema.
   - Rechaza los paquetes de los que depende la tele (núcleo de Android, servicios de TV de CVTE, launcher
     original, menús de fábrica, Play Store, Play Services, teclado, Bluetooth).
   - Anota lo que desactiva en `backup/disabled-by-cleanup.txt`.
3. **Deshacer:** `bash restore.sh` reactiva todo lo anotado y devuelve el inicio al launcher de CVTE.

Paquetes que en la placa de prueba eran publicidad, telemetría o extras (revisa siempre con
`dumpsys package <paquete>` antes de desactivar):

| Paquete | Qué hacía |
|---|---|
| `com.zeasn.services.general` | Contenidos y anuncios de Zeasn; instalaba otros paquetes |
| `com.cvte.tv.pictorial` | Salvapantallas con contenido de internet |
| `com.cvte.tv.steward` | "Asistente" limpiador |
| `com.cvte.tv.appstore` | Tienda de apps de CVTE |
| `com.cvte.tv.remotedebug` | Depuración remota del fabricante |
| `com.cvte.tv.ucservice` | "User Center" del fabricante |

**No abras** apps cuyo nombre diga `fac`, `factory` o `factorymenu` (por ejemplo `com.cvte.fac.menu`): en
algunas placas CVTE abrirlas, aunque sea para mirar, activa un modo de fábrica difícil de revertir.

## Notas sobre placas CVTE

- **Entradas HDMI:** `TvView.tune()` con la URI de paso del TV Input Framework crea la sesión, pero la imagen
  queda en negro. Funciona `cn.cvte.tv.api` (en el boot classpath):
  `TvApiSDKManager → getTVApiSystemInputSource().eventSystemInputSourceSetInputSource(id)`, y después abrir
  `com.cvte.tv.androidsetting/.TvSettingActivity`. El launcher lo llama por reflexión (`CvteSources.java`).
- **Estado de las entradas:** la API de CVTE da todas por "conectadas"; el estado real se toma del TV Input
  Framework.
- **Home y Atrás:** el firmware cambia a la imagen de TV si ya estás en el inicio.
- **Logs:** estas placas descartan los mensajes de log de las apps. Para medir, usa `am start -W`,
  `dumpsys gfxinfo` o `dumpsys SurfaceFlinger --latency`.
- **Rendimiento:** pantalla a 50 Hz y GPU modesta. Redibujar a pantalla completa algo continuo (por ejemplo un
  shader de olas) baja la interfaz a unos 20 fps. Por eso el fondo es estático y las estrellas se redibujan
  solo en una zona pequeña, a unos 15 fps.
- **Compilación:** con JDK 21 o superior, el `d8` de build-tools 34 falla con clases anónimas (por los
  `MethodParameters` "mandated"). El código usa clases anidadas con nombre por eso.
- **Windows:** en rutas largas `aapt2`/`aapt` no encuentran rutas relativas. `build.sh` usa rutas absolutas y
  añade los assets por separado (si no, quedan guardados con `\`).

## Aviso de seguridad (observado en la tele de prueba)

En la placa de prueba, **todas** las apps reciben al arrancar un archivo `systemlibarm_10145.jar` en su carpeta
privada, que se compila y se carga. Además existe una carpeta oculta `/sdcard/.MLAndroid` y el proceso de
Google Play Services mantiene una conexión con `47.236.54.218:11080` (Alibaba Cloud, Singapur).

Una app normal no puede hacer eso en otras apps, así que apunta al firmware. Coincide con lo descrito en
televisores y cajas Android baratas con malware de fábrica, aunque no se identificó la familia exacta.

Si ves lo mismo en tu tele:
- No uses cuentas sensibles en ella.
- Aíslala en la red de invitados y bloquea esa IP en el router.
- Valora usar un dispositivo certificado por HDMI.

Para comprobarlo:

```bash
adb logcat -d | grep systemlibarm
adb shell cat /proc/net/tcp6
```

## Licencias

- Código del repositorio: licencia MIT (`LICENSE`).
- Fuente Outfit: SIL Open Font License 1.1 (`launcher/app/src/main/assets/fonts/OFL.txt`).
- Este proyecto no está afiliado a CVTE, MediaTek ni ningún fabricante de televisores. Úsalo bajo tu
  responsabilidad.
