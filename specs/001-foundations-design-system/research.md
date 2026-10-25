# Research — F01 Fundaciones y design system

**Fecha:** 2026-10-25
**Rama:** `001-foundations-design-system`

Las versiones se verificaron el 2026-10-25 contra `maven-metadata.xml` de Google Maven y Maven Central, y contra el registro npm. No se supone ninguna versión (constitución II).

## R1 — Toolchain y versiones

| Pieza | Scaffold actual | Elegido | Fuente / nota |
|---|---|---|---|
| Android Gradle Plugin | 9.3.3 | **9.4.1** (última estable) | Google Maven; 9.6.0-alpha01 descartada por no ser estable |
| Gradle wrapper | 9.5.0 | **9.8.2** (última estable) | AGP 9.4.1 exige ≥ 9.6.0 (D-020) |
| Kotlin (KGP + plugin Compose) | 2.2.10 | **2.4.21** (última estable) | Maven Central. AGP 9.x declara KGP 2.2.10 como dependencia *runtime*; poner el plugin Compose a 2.4.21 en el classpath hace que Gradle resuelva KGP 2.4.21 (gana la versión más alta) |
| Plugin kotlinx-serialization | — | **2.4.21** | Lo exigen las rutas tipadas de Navigation |
| kotlinx-serialization-json | — | **1.12.0** | Maven Central |
| kotlinx-coroutines (core/android/test) | — | **1.11.0** | Maven Central |
| Compose BOM | 2026.02.01 | **2026.09.00** (última estable) | Google Maven (ui 1.12.1, material3 1.4.0 estables) |
| activity-compose | 1.13.0 | 1.13.0 | Última estable |
| core-ktx | 1.19.1 | 1.19.1 | Última estable |
| lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | 2.11.0 | Última estable |
| navigation-compose / navigation-testing | — | **2.10.2** | Última estable |
| Koin BOM (`koin-android`, `koin-androidx-compose`, `koin-test-junit4`) | — | **4.2.2** | Maven Central |
| JUnit 4 | 4.13.2 | 4.13.2 | — |
| Robolectric | — | **4.17** | Maven Central |
| Truth | — | 1.4.5 | Maven Central |
| androidx.test (ext-junit, runner, core, espresso) | 1.3.0 / — / — / 3.7.0 | 1.3.0 / 1.7.0 / 1.7.0 / 3.7.0 | Google Maven |
| Compose ui-test-junit4 y **ui-test-junit4-accessibility** | — | según la BOM (1.12.1) | Google Maven. Aporta `enableAccessibilityChecks()` (ATF) |
| JDK de build | — | JBR 21 de Android Studio (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) | El sistema no tiene un Java global; el daemon de Gradle pide toolchain 21 |
| `compileOptions` | Java 11 | **Java 17** | Coherente con AGP 9 y JDK 21. El `jvmTarget` de Kotlin sigue a `compileOptions` con el Kotlin integrado |
| compileSdk / targetSdk | 36.1 / 36 | **37.1** / 36 | Las librerías de AndroidX estables exigen compileSdk ≥ 37 (D-020). targetSdk se mantiene |
| minSdk | 24 | **31** | D-017 |

- **Decisión:** AGP 9.4.1 con Kotlin 2.4.21 y las librerías estables de la tabla.
- **Alternativa y fallback:** mantener la pareja generada por Android Studio (AGP 9.3.3 / Kotlin 2.2.10). Si `assembleDebug` falla por incompatibilidad AGP↔KGP al subir de versión, se vuelve a esa pareja. En ese caso se eligen versiones de librerías compiladas con Kotlin ≤ 2.3, porque el compilador lee metadatos de una versión posterior como máximo. El cambio se registra en DECISIONS.

## R2 — Navegación

- **Decisión:** **Navigation Compose 2.10.2 con rutas tipadas** (`@Serializable`).
  - `NavHost` único, con `Home` como destino inicial.
  - Para los destinos de pestaña se navega con `popUpTo<Home> { saveState = true }`, `launchSingleTop = true` y `restoreState = true`.
- **Motivo:**
  - La constitución (II) y `docs/disegn_app/CLAUDE.md` nombran Navigation Compose.
  - Es la versión estable y madura, y el patrón estándar de barra inferior con varias pilas cubre FR-017/FR-018: Atrás desde una pestaña vuelve a Inicio, Atrás desde Inicio sale y no hay pestañas duplicadas.
  - El estado de cada entrada se guarda en `SavedState` (FR-019).
- **Alternativas consideradas:** Navigation 3 (1.2.0, estable). Modela la pila como estado propio y es la opción más moderna, pero:
  - exigiría implementar a mano el guardado y restauración de las pilas por pestaña;
  - se aparta del texto de la constitución.

  Se reevaluará si una feature futura necesita escenas adaptativas.

## R3 — Inyección de dependencias

- **Decisión:**
  - Koin 4.2.2 (`koin-android` y `koin-androidx-compose` para los ViewModels de features futuras).
  - `startKoin` en `MicroRagApplication`.
  - Un módulo por capa: `coreModule` con dispatchers y logger, y `domainModule`, `dataModule` y `featureModule`, vacíos de momento, como punto de extensión documentado.
  - Test JVM con `verify()` de `koin-test` sobre la lista completa de módulos.
- **Motivo:** viene fijado por la constitución (II). Además, `verify()` comprueba el grafo sin necesidad de un dispositivo (FR-030).
- **Alternativa:** Hilt. Queda descartada porque la constitución fija Koin.

## R4 — Tipografía Google Sans

- **Decisión:** usar **la fuente variable** `GoogleSans-VariableFont_GRAD,opsz,wght.ttf` (4,8 MB), copiada como `res/font/google_sans_variable.ttf`.
  - Una sola `FontFamily` con `Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))` para 400, 500, 600 y 700.
  - Las itálicas no se incluyen porque los PNG no las usan.
- **Motivo:**
  - Con minSdk 31 las fuentes variables están soportadas.
  - Un fichero (4,8 MB) frente a cuatro estáticas (~8 MB).
  - Es un uso permitido por la licencia OFL incluida (`OFL.txt`, sin *Reserved Font Name*), que se acompaña en los avisos de licencias (F15).
- **Alternativas:**
  - Estáticas `GoogleSans-{Regular,Medium,SemiBold,Bold}.ttf`: 8 MB.
  - Subconjunto latino con fonttools: menor tamaño, pero añade tooling y una modificación de la fuente. Se pospone a F15 si el tamaño del APK preocupa.
  - Las variantes «17pt» son ópticas para 17 pt y se descartan en favor de las de óptica por defecto.

## R5 — Iconos Lucide como vector drawables

- **Decisión:** usar `lucide-static@1.61.0` (npm, licencia ISC; publicado el 2026-10-21).
  - Un script propio y versionado (`tools/icons/lucide_to_vector.py`) lee `icon-nodes.json` del paquete y genera `res/drawable/ic_*.xml`:
    - `viewportWidth/Height` = 24;
    - `strokeWidth` 2, con extremos y uniones redondeados;
    - `fillColor` transparente;
    - `strokeColor` negro, tintado en uso.
  - El script convierte `circle`, `ellipse`, `rect` (con `rx`/`ry`), `line`, `polyline`, `polygon` y `path` a `pathData`.
  - El paquete npm se descarga **fuera del repo** (directorio temporal, `npm pack` sin scripts) y solo se versionan los XML generados, un `tools/icons/icons.txt` con el mapeo `ic_* ← lucide` y `LICENSE-lucide.txt` (ISC).
- **Verificación:** los 29 iconos de `INVENTARIO.md` §2.6 existen en 1.61.0 (comprobado). `ic_settings` ← `sun` (G19), `ic_home` ← `house`, `ic_chat` ← `message-square-text` y `ic_more_horizontal` ← `ellipsis`.
- **Alternativas:**
  - Importar a mano con Vector Asset Studio: no reproducible.
  - Generar `ImageVector` en Kotlin: la spec pide recursos vectoriales y el XML es reutilizable fuera de Compose (atajos, notificaciones en F03/F07).

## R6 — Logo oficial

- **Decisión:** copiar `docs/disegn_app/logoLISDATA.png` (2286×770, con transparencia, 66 KB) sin modificar a `res/drawable-nodpi/logo_lisdata.png`.
  - El componente `LisdataLogo` lo escala por altura conservando la proporción y lleva la descripción accesible «Logotipo de LISDATA».
- **Motivo:** FR-007 prohíbe redibujarlo. El tamaño es aceptable.
- **Pendiente (G20):** sustituirlo por el SVG oficial cuando exista.

## R7 — Herramientas de depuración (catálogo y menú de rutas)

- **Decisión:**
  - Usar el **source set `debug`** para todo lo que solo existe en depuración:
    - `DebugLauncherActivity`, con un segundo icono de lanzador «MicroRAG Debug» declarado en `src/debug/AndroidManifest.xml`;
    - `ComponentCatalogScreen`;
    - `RouteMenuScreen`;
    - los datos de ejemplo del catálogo.
  - El menú de rutas abre `MainActivity` con un *extra* explícito.
  - `MainActivity` consulta `DebugRouting.initialRoute(intent)`:
    - en `src/debug` interpreta el extra;
    - en `src/release` es un *stub* que devuelve `null`.
- **Motivo:**
  - FR-011/FR-024 exigen que el catálogo no exista en la publicación, y separarlo por source set lo garantiza en compilación.
  - El intent es explícito y local, no un enlace profundo externo (FR-023).
- **Alternativas:**
  - Botones de depuración dentro de los marcadores: desaparecerían al sustituirse Inicio en F09.
  - Comprobar `BuildConfig.DEBUG` en tiempo de ejecución: el código seguiría dentro del APK de release.

## R8 — Pruebas

| Objetivo | Herramienta | Dónde |
|---|---|---|
| Contraste WCAG de todos los pares de tokens (claro y oscuro) | JUnit4 puro: cálculo de luminancia relativa y ratio | `app/src/test` (JVM) |
| Conformidad del design system: sin `Color(0x…)` ni `.sp` fuera de `core/designsystem/theme`, sin literales en `Text("…")` fuera de `*Previews.kt` y `src/debug` | JUnit4, escaneo de fuentes | `app/src/test` |
| Grafo Koin | `koin-test` `verify()` | `app/src/test` |
| Navegación: pestañas, Atrás, visibilidad de la barra inferior, reselección y restauración | Robolectric 4.17 + Compose ui-test + `navigation-testing` (`TestNavHostController`) + `StateRestorationTester` | `app/src/test` |
| Accesibilidad automática del catálogo (área táctil, etiquetas, contraste ATF) | `ui-test-junit4-accessibility` (`enableAccessibilityChecks()`) | `app/src/androidTest`, en el emulador `Pixel_10` (API 37, arm64) |
| Manifiesto fusionado: permisos, backup y ausencia de deep links | Comprobación de `apkanalyzer` o `aapt2` en el quickstart y test instrumentado con `PackageManager` | `androidTest` + `quickstart.md` |
| Fuente al 200 % y modo oscuro | Previews multi-configuración y revisión manual del catálogo | Revisión documentada |

**Motivo:** es la combinación más rápida y estable para la JVM, y deja la accesibilidad automática en el dispositivo, donde ATF obtiene medidas reales.

**Alternativa:** tests de captura (Roborazzi o Paparazzi). Se posponen: añaden mantenimiento de imágenes de referencia cuando la UI de producto aún no existe. Se pueden reconsiderar en F15.

## R9 — Copias de seguridad y transferencia entre dispositivos

- **Decisión:**
  - `android:allowBackup="false"`.
  - Se elimina `fullBackupContent` (no aplica con backup desactivado y minSdk 31).
  - `android:dataExtractionRules` con `<cloud-backup>` y `<device-transfer>` excluyendo todos los dominios (`root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`, `device_sharedpref`).
- **Motivo:** en Android 12+ `allowBackup=false` **no** desactiva la transferencia entre dispositivos (D2D); para eso hacen falta las `dataExtractionRules` (FR-026).
- **Verificación:** `adb shell bmgr backupnow <paquete>` no debe producir datos, más la inspección del manifiesto fusionado.

## R10 — Permisos del manifiesto fusionado

- **Hallazgo:** `androidx.core` (≥ 1.9) declara y usa el permiso `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Es de nivel **signature**, solo lo usa la propia app y sirve para registrar *receivers* no exportados en API < 33.
- **Decisión:**
  - Se mantiene. Eliminarlo con `tools:node="remove"` podría romper `ContextCompat.registerReceiver` en Android 12 (API 31–32).
  - FR-025 y SC-005 se precisan: ningún permiso de red y ningún permiso normal o peligroso que conceda el sistema o el usuario. Se admiten solo los permisos internos de nivel firma de AndroidX, documentados.
  - Un test instrumentado enumera los permisos solicitados y falla ante cualquier otro.
- **Alternativa:** eliminarlo. Se descarta por el riesgo en API 31–32.

## R11 — Registros (logs)

- **Decisión:** usar un envoltorio `AppLogger` con `buildFeatures.buildConfig = true`.
  - Solo emite cuando `BuildConfig.DEBUG` es verdadero.
  - Acepta mensajes en lambda, para no construir cadenas en release.
  - Prohíbe por convención registrar contenido privado (prompts, texto de documentos, nombres de archivo).
- **Motivo:** FR-027. `BuildConfig.DEBUG` es una constante de compilación y en release elimina el código.
- **Alternativa:** reglas de R8 `-assumenosideeffects`. Requieren R8 activo en release, que llega en F15. Se pueden añadir entonces como segunda barrera.

## R12 — ABIs

- **Decisión:** `defaultConfig.ndk.abiFilters = ["arm64-v8a"]`; en el tipo `debug` se añade `x86_64`.
- **Matiz:** F01 no empaqueta librerías nativas, así que el filtro no se puede observar en el APK hasta F02. La verificación de FR-028 en F01 es de configuración, y en F02 se comprueba el contenido del APK.
- **Nota:** el emulador disponible es arm64 (Apple Silicon); x86_64 solo hace falta para emuladores Intel.

## R13 — Dispatchers dedicados

- **Decisión:**
  - Clase `AppDispatchers(io, default, main, database, llm, embeddings)`.
  - `database`, `llm` y `embeddings` son `Executors.newSingleThreadExecutor(...)` con hilos con nombre (`microrag-db`, `microrag-llm`, `microrag-embed`) convertidos con `asCoroutineDispatcher()`.
  - Se proveen como `single` en Koin.
- **Motivo:**
  - La constitución (IV) pide dispatchers de un solo hilo.
  - Un hilo fijo y real, y no `limitedParallelism(1)`, que puede saltar de hilo, evita problemas de afinidad con conexiones nativas (SQLite y LiteRT-LM).

## R14 — Mapeo de tokens a Material 3

- **Decisión:**
  - `LisdataTheme` construye un `ColorScheme` de M3 a partir de los tokens:
    - `primary` = `brandBlue` en claro y `brandCyan` en oscuro;
    - `primaryContainer` = `surfaceInfo`, con `onPrimaryContainer` = `blue800`;
    - `secondaryContainer` = `surfaceInfoAlt`;
    - `background`, `surface` y `surfaceContainer*` desde los tokens de superficie;
    - `outline` y `outlineVariant` = `outline` y `divider`;
    - `error`/`errorContainer` desde el token de error de D-015.
  - Expone además `LisdataTheme.colors` (`LisdataColors`) con los roles que M3 no tiene: `success`, `warning`, `textSecondary`, `citationHighlight`, `neutralTile`, `disabledContainer` y `onDark*`.
  - Expone también `LisdataTheme.gradients`, `LisdataTheme.spacing`, `LisdataTheme.shapes` y `LisdataTheme.typography`, esta con estilos extra (`navLabel`, `progressValue`, `eyebrow`).
  - Sin color dinámico (FR-002).
- **Motivo:** los controles estándar de M3 (D-018) heredan los tokens sin configurar componente a componente.

## R15 — Barra inferior y teclado

- **Decisión:** el *shell* (`MicroRagAppShell`) muestra `LisdataBottomBar` solo si el destino actual es una de las cuatro pestañas **y** `WindowInsets.isImeVisible` es falso (FR-020).
  - Se usa edge-to-edge con `enableEdgeToEdge()` y `windowSoftInputMode="adjustResize"`, que ya está en el scaffold.
  - Los insets se consumen en el `Scaffold`.

## R16 — Splash e icono del lanzador

- **Decisión:**
  - Splash del sistema de Android 12+ con fondo `background` (claro y oscuro) mediante `Theme.MicroRAG` y `values-night`.
  - El **icono del lanzador** sigue siendo el del scaffold, porque no hay asset de marca para el icono.
  - Se registra el hueco **G43** en `INVENTARIO.md` (pedir el icono oficial). No se dibuja un icono de marca a mano (CLAUDE.md).

## R17 — Marcadores de destinos pendientes

- **Decisión:** usar un único composable `PendingDestination(routeName, args, ownerFeature)` en `core/navigation`.
  - Usa los componentes del design system: `LisdataTopBar` y `LisdataEmptyState`, con el texto «Pendiente · Fxx».
  - Está presente en todas las variantes hasta que su feature lo sustituya.
  - Un test de F15 comprobará que ya no se usa.
- **Correspondencia de destinos y features:**
  - `welcome`, `setup` → F03;
  - `home` → F09;
  - `chat` → F11;
  - `ingest` → F06;
  - `library`, `document` → F08;
  - `index` → F07;
  - `settings`, `languages` → F12.

## R18 — Argumentos de ruta

- **Decisión:**
  - `Chat(conversationId: Long? = null)`.
  - `Document(documentId: Long, chunkId: Long? = null)`: el `chunkId` opcional lo usará F11 para resaltar la cita (INVENTARIO P07).
  - Los identificadores son `Long`, coherentes con `INTEGER PRIMARY KEY` del esquema del informe.
  - Un identificador ausente o inválido nunca provoca un cierre: el marcador lo muestra.
- **Alternativa:** identificadores `String`. Se descarta para no convertir entre tipos frente a SQLite.
