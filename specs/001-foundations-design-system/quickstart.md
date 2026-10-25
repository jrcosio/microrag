# Quickstart — validar F01

Guía para comprobar de punta a punta que F01 cumple la spec. Las referencias apuntan a la spec (FR/SC) y a los contratos.

## Requisitos previos

- macOS con Android Studio instalado.
- JDK: el JBR 21 de Android Studio. No hay un Java global.

  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```

- SDK en `~/Library/Android/sdk` (ver `local.properties`), con las plataformas 36.1 instaladas.
- Emulador `Pixel_10` (API 37, arm64) arrancado (`adb devices` muestra `emulator-5554`), o un teléfono físico arm64 con Android 12 o superior.
- Solo para regenerar iconos: Node/npm, que ya están en `/opt/homebrew/bin`.

## 1. Build, lint y tests (SC-010)

```bash
./gradlew clean assembleDebug assembleRelease lintDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest        # requiere emulador o dispositivo
```

Resultado esperado: todo en verde. Los tests más relevantes son:
- `ContrastTest` (SC-002);
- `DesignSystemConformanceTest` (SC-008);
- `AppModulesTest` (FR-030);
- `AppDispatchersTest` (FR-031);
- `NavigationTest` (N1–N8, SC-003, SC-007);
- `CatalogAccessibilityTest` (SC-006);
- `ManifestInvariantsTest` (P4, P5, P10).

## 2. Invariantes del paquete (SC-005, `contracts/platform-invariants.md`)

```bash
APKA=~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer
$APKA manifest permissions app/build/outputs/apk/release/app-release-unsigned.apk   # solo …DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
$APKA manifest min-sdk      app/build/outputs/apk/release/app-release-unsigned.apk  # 31
$APKA manifest print        app/build/outputs/apk/release/app-release-unsigned.apk | grep -E "allowBackup|dataExtractionRules|BROWSABLE"
$APKA dex packages          app/build/outputs/apk/release/app-release-unsigned.apk | grep -i catalog   # vacío (P6)
```

Prueba de copia de seguridad (P3), con la app de debug instalada:

```bash
adb shell bmgr backupnow com.lisdatasolutions.microrag   # debe informar de que el paquete no es elegible o no hay datos
```

## 3. Revisión visual del design system (US1, SC-001, SC-004)

1. Instalar la variante de debug: `./gradlew installDebug`.
2. Abrir **«MicroRAG Debug»** → **Catálogo de componentes**.
3. Recorrer todas las secciones. Comparar con los PNG de `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/` y con `INVENTARIO.md` §2 y §4.
4. Alternar **tema oscuro** (conmutador del catálogo o ajuste del sistema).
5. Poner la **fuente al 200 %** (`adb shell settings put system font_scale 2.0`) y revisar que no hay recortes. Restaurar después con `font_scale 1.0`.
6. Anotar en el informe de cierre de F01 las diferencias encontradas frente al PNG. Las de tamaño de los controles M3 (D-018) son esperadas.

## 4. Navegación (US2, `contracts/navigation.md`)

1. Abrir **MicroRAG**: arranca en el marcador «Pendiente · F09» de Inicio con la barra inferior.
2. Tocar Chat, Biblioteca y Ajustes: un toque cada uno, con la pestaña seleccionada visible.
3. Pulsar Atrás en Chat: vuelve a Inicio. Pulsar Atrás en Inicio: la app se cierra.
4. Desde **MicroRAG Debug → Menú de rutas**, abrir cada destino secundario. Comprobar que no hay barra inferior y que el marcador muestra la feature que lo sustituirá y sus argumentos. Incluir `Document` con id inválido (N8).
5. Rotar el dispositivo y cambiar el tema en cada destino: se conservan el destino y la pestaña.
6. Simular la muerte del proceso: dejar la app en segundo plano y ejecutar `adb shell am kill com.lisdatasolutions.microrag`. Al volver, la app está en el mismo destino.

## 5. Accesibilidad (US4)

1. Activar TalkBack y recorrer el catálogo. Los switches, radios, chips y el progreso anuncian su nombre y estado. Los iconos decorativos y las órbitas no se anuncian.
2. `CatalogAccessibilityTest` (ATF) sin violaciones de área táctil, etiquetas ni contraste.

## 6. Arranque en frío (SC-009)

```bash
adb shell am force-stop com.lisdatasolutions.microrag
adb shell am start -W -n com.lisdatasolutions.microrag/.MainActivity | grep TotalTime   # < 1000 ms
```

Se toma la mediana de 5 ejecuciones y se anota en el cierre de F01 junto con el dispositivo: el emulador `Pixel_10`, a falta de teléfono de referencia hasta F02 (SC-009).

## Regenerar iconos (solo si cambia la lista)

```bash
TMP=$(mktemp -d) && (cd "$TMP" && npm pack lucide-static@1.61.0 --ignore-scripts && tar -xzf lucide-static-1.61.0.tgz)
python3 -I tools/icons/lucide_to_vector.py "$TMP/package/icon-nodes.json" tools/icons/icons.txt app/src/main/res/drawable
```
