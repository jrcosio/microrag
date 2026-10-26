# Quickstart — validación de F03

Guía para comprobar F03 de extremo a extremo. Los detalles de comportamiento están en `contracts/` y en `data-model.md`; aquí solo se indica cómo ejecutar y qué esperar.

## 0. Preparación

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG=com.lisdatasolutions.microrag
PHONE=P31281001066      # Nothing Phone (1), API 35: ruta UIDT
EMU=emulator-5554       # Pixel_10, API 37: ruta UIDT
```

> **Teléfono:** nunca `connected*AndroidTest`, porque desinstala la app y borra los modelos (F02, quickstart §1). Se usa `installDebug` + `installDebugAndroidTest` + `am instrument`.

## 1. Build, lint y tests JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

Esperado:
- todo en verde;
- `NetworkConfinementTest`, `ModelDownloaderTest`, `ModelDownloadRunnerTest`, `ModelSetupStatusResolverTest` y `ModelCompatibilityCheckerTest` incluidos.

## 2. Tests instrumentados

```bash
# Emulador (sin modelos)
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.lisdatasolutions.microrag.debug.feasibility.RequiresModels

# Teléfono
./gradlew :app:installDebug :app:installDebugAndroidTest
$ADB -s $PHONE shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
```

Esperado:
- `ManifestInvariantsTest` pasa con la lista cerrada de permisos (contracts/platform-invariants.md);
- pasan la accesibilidad de la Bienvenida y la Preparación (todos los estados, claro y oscuro), la navegación y `NoTrafficInstrumentedTest`.

## 3. Primer arranque y descarga real (emulador)

1. `adb -s $EMU uninstall $PKG` e instalar la debug.
2. Abrir la app: aparece la **Bienvenida**, igual que el PNG 01. Atrás cierra la app.
3. «Comenzar» → **Preparación**, «PASO 1 DE 3», con los dos modelos «Por configurar» y «Continuar» deshabilitado con su motivo.
4. Tocar «Gemma 4»: se abre la confirmación con los dos modelos, «Total: 2,75 GB», el espacio libre y «Red: Wi-Fi».
5. «Descargar» → explicación de notificaciones → permiso del sistema → «PASO 2 DE 3»:
   - EmbeddingGemma 2 muestra «Descargando N %» y Gemma 4, «En cola»;
   - la notificación muestra el mismo porcentaje.
6. Al terminar EmbeddingGemma 2: «Verificando» → «Preparando» → «Listo».
7. Relanzar la app a mitad de la descarga: se abre la Preparación (`[Welcome, Setup]`) con el progreso real.

## 4. Pruebas en el teléfono de referencia

**Partida.** El teléfono conserva los modelos de F02 en `no_backup/models`.

- **Prueba de verificación sin descarga:** con los ficheros presentes y sin registro, la Preparación debe mostrar «Verificando» → «Preparando» → «Listo» sin descargar nada.
- **Prueba de descarga:** se borran antes los dos ficheros requeridos (los de diagnóstico se quedan). Copia de respaldo en `$HOME/MicroRAG-models`.

```bash
$ADB -s $PHONE shell run-as $PKG rm -f no_backup/models/gemma-4-E2B-it.litertlm \
  no_backup/models/embeddinggemma-2-text-270m.litertlm no_backup/models/verification.json
$ADB -s $PHONE shell run-as $PKG rm -f no_backup/datastore/onboarding.preferences_pb
```

| Escenario | Pasos | Esperado |
|---|---|---|
| **US2:** descarga completa | Flujo del §3 con Wi-Fi | Termina en «PASO 3 DE 3». Anotar la duración total y la de la prueba de Gemma (SC-010: < 30 s) |
| **US4-1 / SC-002:** muerte del proceso | Con Gemma ≈ 50 %: Inicio y luego `$ADB shell am kill $PKG` (o deslizar desde Recientes). Reabrir | Continúa desde ≈ 50 %. Bytes recibidos (`dumpsys netstats` / logcat `MicroRAG`) ≤ tamaño × 1,05 |
| **Stop del Administrador de tareas** | Detener desde la notificación del sistema o el Administrador de tareas. Reabrir | La reconciliación reprograma; la descarga continúa |
| **US4-3 / SC-003:** sin red | `$ADB shell cmd connectivity airplane-mode enable`, esperar 60 s y luego `disable` | «Esperando conexión» y reanudación sola en ≤ 30 s |
| **US4-2:** reinicio | `$ADB reboot` a mitad de la descarga, sin abrir la app | Se reanuda sola (riesgo R13-1). Si no, se reanuda al abrir la app, y se documenta |
| **US2-7 / FR-018:** pausa | «Detener» en la notificación y luego tocar la fila | «En pausa» conservando los bytes, y se reanuda |
| **US3-3:** huella incorrecta | Con la descarga en pausa, alterar 1 byte del parcial (`run-as $PKG sh -c 'printf X \| dd of=no_backup/models/downloads/embeddinggemma-2-text-270m.litertlm.download bs=1 seek=4096 conv=notrunc'`) y reanudar | Al terminar: «Error · La descarga no superó la verificación», sin fichero en `models/`. «Reintentar» descarga desde cero |
| **US6:** importar | `$ADB push $HOME/MicroRAG-models/embeddinggemma-2-text-270m.litertlm /sdcard/Download/`. En la confirmación: «Seleccionar archivo del dispositivo» | «Importando» → «Verificando» → «Preparando» → «Listo». Con otro fichero (p. ej. el de 440M): «Incompatible · El archivo no corresponde a EmbeddingGemma 2» |
| **US5:** entrada a Inicio | «Continuar» con los dos «Listo». Relanzar | Se abre Inicio. Atrás cierra la app |
| **SC-007:** arranque | `am force-stop` y `am start -W` (5 medidas) con el onboarding completado, en la build release firmada con la clave de debug | Mediana < 1 s |
| **SC-008:** sin red | Modo avión y recorrer la app con los modelos «Listo» | Todo funciona; `TrafficStats` del UID sin cambios |
| **SC-010:** caché del motor | Tras «Listo», abrir el diagnóstico de F02 y medir el init de Gemma en GPU sin borrar la caché | ≈ 5,4 s, no ≈ 16,9 s |

**Espacio insuficiente** (en el emulador, que tiene poco disco): llenar `/data` con `adb shell fallocate -l <N>G /data/local/tmp/fill` hasta dejar menos de lo que falta + 500 MB. Esperado:
- «Sin espacio · Faltan X»;
- la acción abre los ajustes de almacenamiento;
- tras borrar el relleno y volver a la app, se puede reintentar.

## 5. Ruta WorkManager (API 31–33)

Esta ruta la cubren tests JVM con Robolectric (`sdk = 32`). Para validarla en un dispositivo hace falta una AVD API 33 arm64; solo se instala la imagen con aprobación del usuario.

```bash
sdkmanager "system-images;android-33;google_apis;arm64-v8a"
```

Esperado: el mismo flujo del §3, con la notificación del servicio en primer plano.

## 6. Accesibilidad manual

Recorrido con TalkBack de la Bienvenida y la Preparación en el teléfono. Esperado:
- logo anunciado e ilustración no;
- cada fila se anuncia como una unidad, con estado y progreso verbal;
- el motivo de «Continuar» deshabilitado se anuncia;
- con fuente al 200 % no hay recortes.

## 7. Release

```bash
apkanalyzer manifest permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

Esperado:
- exactamente la lista de contracts/platform-invariants.md;
- `ModelDownloadJobService` con `BIND_JOB_SERVICE`;
- nada de diagnóstico en el dex de release.
