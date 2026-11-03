# Quickstart: validar el tema y el icono de Ajustes (F16)

Guía de validación de extremo a extremo. Los detalles están en los contratos (`contracts/`) y en el modelo de datos.

## Requisitos

- **JBR de Android Studio:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **`adb`:** `$HOME/Library/Android/sdk/platform-tools/adb`.
- **Emuladores:** `MicroRAG_API32` (API 32, se crea en T015 con `system-images;android-32;google_apis;arm64-v8a`), `MicroRAG_API33` (API 33) y `Pixel_10` (API 37).
- **Iconos, solo si se regeneran:** `npm` y `python3`.
- **Teléfono de referencia (A063, API 35):** **solo en la última fase**, con las reglas de privacidad de F11–F13 (research R11).

## 1. Iconos

```bash
TMP=$(mktemp -d) && (cd "$TMP" && npm pack lucide-static@1.61.0 --ignore-scripts && tar -xzf lucide-static-1.61.0.tgz)
python3 -I tools/icons/lucide_to_vector.py "$TMP/package/icons" tools/icons/icons.txt app/src/main/res/drawable
git status --short app/src/main/res/drawable
```

**Esperado:** solo cambia `ic_settings.xml` (cabecera `icon 'settings'`) y aparece `ic_sun_moon.xml`. Los demás iconos no cambian (research R7).

## 2. JVM

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

**Esperado:**
- **dominio:**
  - `ThemeUseCasesTest`:
    - guardar: sin cambios, con la lectura fallida, fallo de escritura, fallo al aplicar con la copia restaurada, y guardado con el aviso con margen;
    - reconciliar: la copia, o `SYSTEM` con la lectura fallida, según la variante elegida en T015 (theme-api P3, P5);
- **datos:** `DataStoreAppThemeStoreTest`: por defecto `SYSTEM`, sobrevive a reabrir, valor desconocido, fichero corrupto que se sustituye, y fallos de lectura y escritura (P1);
- **pantallas:**
  - `ThemeViewModelTest`: U1–U4;
  - `ThemeScreenTest`: estructura del wireframe §3, estados A4–A5 y semántica U7;
  - `SettingsScreenTest`: la fila «Tema» entre «Idiomas» e «Índice y rendimiento», con su subtítulo y su clic;
  - `SettingsViewModelTest`: relectura en `onResume`;
- **shell:** `SettingsSavedAnnouncementTest`, con el anuncio tras el margen de 1 s (P6, calificador `es`);
- **resto:** `NavigationTest` (sin barra inferior en `Theme`), `StringsParityTest`, `LayeringTest` (regla de `UiModeManager`), `AppModulesTest` y `DesignSystemConformanceTest` (el engranaje, SC-007);
- **lint:** sin errores nuevos.

## 3. Emulador

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner > build/instrumented.txt
```

Escribe la salida en un fichero; no la pases por `tail`.

**Esperado:**
- **instrumentadas:**
  - `PlatformAppThemeControllerTest`: `NO` y `YES` imponen el modo contrario al del emulador, `AUTO` lo devuelve al del emulador, y reaplicar el mismo valor no recrea (research R1, R3). Deja `AUTO` y el modo del emulador como estaban;
  - `ThemeChangeKeepsStateTest` (R5);
  - `ThemeScreenAccessibilityTest` y `SettingsScreenAccessibilityTest` (ATF), en claro y en oscuro, al 200 % y en horizontal;
  - la batería de F01–F13.
- **recorrido, en API 32, API 33 y API 37:**
  1. **Engranaje** en la barra inferior y en la cabecera de Inicio, en claro y en oscuro.
  2. **Ajustes:** la fila «Tema» dice «Sistema».
  3. **Tema:** marcar «Oscuro» con el emulador en claro y volver con Atrás no cambia nada. Marcar «Oscuro» y guardar: la app pasa a oscuro, vuelve a Ajustes y la fila dice «Oscuro». Con TalkBack, se anuncia «Preferencias guardadas».
  4. **Arranque en frío:**

     ```bash
     adb shell am force-stop com.lisdatasolutions.microrag
     adb shell screenrecord --time-limit 4 /data/local/tmp/start.mp4 & adb shell am start -n com.lisdatasolutions.microrag/.MainActivity
     ```

     Ningún fotograma sale en el otro tema. Se hacen 10 por combinación, con «Claro» y «Oscuro» y el emulador en el modo contrario (SC-002). Con las marcas de tiempo de la grabación se mide también el tiempo hasta ver el tema nuevo tras «Guardar preferencias» (SC-001) y tras `cmd uimode` (SC-004).
  5. **Reinicio del emulador:** sigue en el tema guardado (SC-003).
  6. **«Sistema»** y `adb shell cmd uimode night yes|no`: la app cambia sin cerrarse. Con «Claro» u «Oscuro», no cambia (SC-004).
  7. **Estado conservado:** cambiar el tema no pierde el borrador del compositor, la conversación de prueba ni un diálogo abierto. La respuesta en curso y la indexación (SC-005) se comprueban en el teléfono (§4), que tiene los modelos.
  8. **Fallos:** con el almacén de pruebas que falla (prueba JVM), A4 y A5.
  9. **Barras del sistema** legibles en claro y en oscuro (FR-011).

Al terminar, el emulador queda con el tema de la app en «Sistema» y el modo del sistema como estaba.

## 4. Teléfono de referencia (última fase)

**Al empezar,** se anotan los ajustes del teléfono:
- modo oscuro (`adb shell cmd uimode night`);
- `font_scale`;
- giro (`accelerometer_rotation` y `user_rotation`);
- idioma de la app.

**Durante la fase:**
- La batería instrumentada completa con los APK finales, con la pantalla despierta (`KEYCODE_WAKEUP` cada 60 s).
- Recorrido acordado con el usuario:
  - el engranaje;
  - la fila y la pantalla Tema (en Ajustes no hay datos del usuario);
  - los tres temas con los dos modos del teléfono;
  - el reinicio.
- **Arranque en frío:** abre Inicio, que muestra el documento del usuario, así que no se graba. Se muestrea el color de un píxel del fondo, en una esquina sin texto, en los primeros fotogramas, y no se guarda ninguna imagen (research R11).
- No se abre, captura ni vuelca la conversación ni el documento del usuario.
- Sin tráfico de red de la app (`dumpsys netstats`, uid 10628).

**Al terminar,** el teléfono queda con sus ajustes y con el tema de la app en «Sistema». Se comprueba el giro con `dumpsys window` (`ROTATION_0`).
