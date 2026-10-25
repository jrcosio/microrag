---

description: "Lista de tareas de F01 — Fundaciones y design system"
---

# Tasks: Fundaciones y design system (F01)

**Input**: Documentos de diseño en `/specs/001-foundations-design-system/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **Obligatorios** por la constitución (VI) y por los criterios de la spec (SC-002, SC-003, SC-006, SC-007, SC-008, SC-010). En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec:
- US1 — Identidad visual (P1);
- US2 — Navegación (P1);
- US3 — Privacidad (P2);
- US4 — Accesibilidad (P2).

**Entorno**: todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (research R1). Los tests instrumentados se ejecutan en el emulador `Pixel_10` (API 37, arm64).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US4).
- Las rutas son relativas a la raíz del repositorio. `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`. `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dejar el toolchain y el scaffold en el estado de research R1.

- [ ] T001 Actualizar `gradle/libs.versions.toml` con las versiones de research R1:
  - agp 9.4.1; kotlin 2.4.21;
  - composeBom 2026.09.00; navigation 2.10.2; kotlinxSerialization 1.12.0; coroutines 1.11.0;
  - koinBom 4.2.2; robolectric 4.17; truth 1.4.5;
  - androidx.test runner/core 1.7.0, ext-junit 1.3.0, espresso 3.7.0;
  - entradas de librería para `navigation-compose`, `navigation-testing`, `kotlinx-serialization-json`, `kotlinx-coroutines-android`/`-test`, `koin-bom`, `koin-android`, `koin-androidx-compose`, `koin-test-junit4`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `compose-ui-test-junit4`, `compose-ui-test-junit4-accessibility`, `robolectric`, `truth`, `androidx-test-runner`, `androidx-test-core`;
  - alias de plugin `kotlin-serialization`.
- [ ] T002 Actualizar `build.gradle.kts` (raíz): plugins `android.application`, `kotlin.compose` y `kotlin.serialization` con `apply false`. Actualizar `settings.gradle.kts` con `rootProject.name = "MicroRAG"`.
- [ ] T003 Actualizar `app/build.gradle.kts`:
  - aplicar el plugin `kotlin.serialization`;
  - `minSdk = 31`;
  - `compileOptions` en Java 17;
  - `buildFeatures { compose = true; buildConfig = true }`;
  - `defaultConfig.ndk.abiFilters += "arm64-v8a"` y `x86_64` solo en `debug` (research R12);
  - `testOptions.unitTests.isIncludeAndroidResources = true`;
  - `lint { abortOnError = true; checkDependencies = false }`;
  - dependencias de T001, con BOM de Compose y de Koin.
- [ ] T004 Comprobar el toolchain con `./gradlew assembleDebug`. Si falla por la combinación AGP 9.4.1 + Kotlin 2.4.21, aplicar el fallback de research R1: AGP 9.3.3 / Kotlin 2.2.10 con versiones de librerías compatibles. Registrar el resultado en `docs/roadmap/DECISIONS.md` (D-020) solo si se usa el fallback.
- [ ] T005 Eliminar restos del scaffold:
  - `app/src/main/java/com/lisdatasolutions/microrag/ui/theme/Color.kt`, `Theme.kt` y `Type.kt`;
  - `app/src/test/java/com/lisdatasolutions/microrag/ExampleUnitTest.kt`;
  - `app/src/androidTest/java/com/lisdatasolutions/microrag/ExampleInstrumentedTest.kt`;
  - `app/src/main/res/xml/backup_rules.xml`;
  - los colores morados de `app/src/main/res/values/colors.xml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tokens, recursos, servicios base y piezas compartidas que necesitan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza hasta cerrar esta fase.

### Tests (escribir primero)

- [ ] T006 [P] Test de `AppDispatchers` en `app/src/test/java/com/lisdatasolutions/microrag/core/dispatchers/AppDispatchersTest.kt`. Comprueba que `database`, `llm` y `embeddings` ejecutan en hilos con nombre `microrag-db`, `microrag-llm` y `microrag-embed`, y que dos tareas del mismo dispatcher no se solapan (`contracts/base-services.md`).
- [ ] T007 [P] Test del grafo Koin en `app/src/test/java/com/lisdatasolutions/microrag/core/di/AppModulesTest.kt`, con `verify()` de koin-test sobre `appModules` (FR-030).
- [ ] T008 [P] Test de `AppLogger` en `app/src/test/java/com/lisdatasolutions/microrag/core/logging/AppLoggerTest.kt`. Con un `isEnabled = false` inyectable, la lambda del mensaje no se evalúa (FR-027).

### Recursos

- [ ] T009 [P] Copiar `docs/disegn_app/Tipografias/GoogleSans-VariableFont_GRAD,opsz,wght.ttf` a `app/src/main/res/font/google_sans_variable.ttf` (research R4).
- [ ] T010 [P] Copiar `docs/disegn_app/logoLISDATA.png` sin modificar a `app/src/main/res/drawable-nodpi/logo_lisdata.png` (research R6).
- [ ] T011 [P] Crear el convertidor `tools/icons/lucide_to_vector.py`. Recibe la ruta de `icon-nodes.json`, la ruta de `icons.txt` y el directorio de salida. Convierte `path`, `circle`, `ellipse`, `rect` (`rx`/`ry`), `line`, `polyline` y `polygon` a `pathData` y escribe VectorDrawable de 24×24 con trazo 2, extremos y uniones redondeados, sin relleno y `strokeColor` `#FF000000`. No ejecuta nada del paquete descargado (research R5).
- [ ] T012 [P] Crear `tools/icons/icons.txt` con el mapeo `ic_* ← lucide` de los 29 iconos de `INVENTARIO.md` §2.6, y `tools/icons/LICENSE-lucide.txt` con el texto ISC de lucide-static 1.61.0.
- [ ] T013 Generar los 29 drawables `app/src/main/res/drawable/ic_*.xml` con T011 y T012 desde `lucide-static@1.61.0`, descargado con `npm pack --ignore-scripts` en un directorio temporal fuera del repo (`quickstart.md`, «Regenerar iconos»).
- [ ] T014 [P] Crear las cadenas base en español en `app/src/main/res/values/strings.xml` y en inglés en `app/src/main/res/values-en/strings.xml` con las claves de `contracts/design-system-api.md` («Recursos de cadena compartidos»). `app_name` = «MicroRAG» (D-014).
- [ ] T015 [P] Actualizar el tema XML:
  - `app/src/main/res/values/themes.xml`: `Theme.MicroRAG`, ventana y `windowSplashScreenBackground` = `#F7FBFD`;
  - crear `app/src/main/res/values-night/themes.xml` con `#03161F`;
  - dejar en `app/src/main/res/values/colors.xml` solo lo que referencie el tema XML (research R16).

### Tokens y tema

- [ ] T016 [P] Crear `…/microrag/core/designsystem/theme/LisdataColors.kt`: `@Immutable data class LisdataColors` con todos los tokens de `data-model.md` §1 e instancias `LightLisdataColors` y `DarkLisdataColors`. Es el único fichero, junto con T017, con hex de color.
- [ ] T017 [P] Crear `…/microrag/core/designsystem/theme/LisdataGradients.kt` con los degradados de `data-model.md` §2 como `Brush`: `cta` con `#0099D4 → #0072DD` (D-019), `brandAccent`, `hero`, `welcome` y `stepProgress`.
- [ ] T018 [P] Crear `…/microrag/core/designsystem/theme/LisdataTypography.kt`:
  - `GoogleSans` como `FontFamily` variable con pesos 400, 500, 600 y 700 vía `FontVariation`;
  - la `Typography` de M3 con la escala de `data-model.md` §3;
  - `LisdataTypography` con los extras `navLabel`, `navLabelSelected`, `progressValue` y `eyebrow`.
- [ ] T019 [P] Crear `…/microrag/core/designsystem/theme/LisdataSpacing.kt` y `LisdataShapes.kt` con los tokens de `data-model.md` §4, incluido el mapeo a `Shapes` de M3.
- [ ] T020 Crear `…/microrag/core/designsystem/theme/LisdataTheme.kt`. Depende de T016–T019.
  - `@Composable fun LisdataTheme(darkTheme, content)` construye el `ColorScheme` de M3 desde los tokens (research R14), sin color dinámico.
  - Las `CompositionLocal` de colores, gradientes, tipografía, espaciado y formas se exponen con `object LisdataTheme { val colors … }`.
- [ ] T021 [P] Crear `…/microrag/core/designsystem/theme/Contrast.kt` con las funciones puras `relativeLuminance(Color)` y `contrastRatio(Color, Color)` (WCAG 2.x), que reutiliza `ContrastTest`.
- [ ] T022 [P] Crear `…/microrag/core/designsystem/model/`:
  - `StatusKind.kt`, `ProgressValue.kt`, `StageStatus.kt`, `IconTileStyle.kt`, `CitationUi.kt` y `AnswerState.kt`, según `data-model.md` §7;
  - `ProgressValue.Determinate` valida que `fraction` esté en [0,1].
- [ ] T023 [P] Crear `…/microrag/core/designsystem/icon/`:
  - `LisdataIcons.kt`, un objeto con los 29 `@DrawableRes`;
  - `LisdataIcon.kt`;
  - `LisdataLogo.kt`, que usa `logo_lisdata` con la descripción `R.string.cd_logo` y escala por altura.
- [ ] T024 [P] Crear `…/microrag/core/designsystem/preview/LisdataPreviews.kt` con la anotación multipreview `@LisdataPreviews` (claro, oscuro, `fontScale = 2f`) y un `LisdataPreviewSurface` que envuelve en `LisdataTheme`.

### Componentes compartidos por el shell

- [ ] T025 Crear `…/microrag/core/designsystem/component/Bars.kt` con `LisdataTopBar` (título, Atrás opcional, acciones y subtítulo) y `LisdataBottomBar` (M3 `NavigationBar` tematizada, 4 destinos, icono y etiqueta siempre visibles), según `contracts/design-system-api.md`. Crear también `BarsPreviews.kt`, con textos largos incluidos.
- [ ] T026 Crear `…/microrag/core/designsystem/component/States.kt` con `LisdataEmptyState`, `LisdataErrorState` y `LisdataSkeletonRow` (anuncia «Cargando»), y `StatesPreviews.kt`.

### Servicios base

- [ ] T027 [P] Crear `…/microrag/core/dispatchers/AppDispatchers.kt` (research R13) y `…/microrag/core/logging/AppLogger.kt`: la interfaz y `DefaultAppLogger`, sujeto a `BuildConfig.DEBUG`. Los tests T006 y T008 tienen que pasar.
- [ ] T028 Crear los módulos Koin y la aplicación. Los tests T007 tienen que pasar.
  - `…/microrag/core/di/CoreModule.kt`, `…/microrag/domain/di/DomainModule.kt`, `…/microrag/data/di/DataModule.kt` y `…/microrag/feature/di/FeatureModule.kt` (los tres últimos vacíos y documentados);
  - `…/microrag/core/di/AppModules.kt`;
  - `…/microrag/MicroRagApplication.kt` con `startKoin`;
  - registrar `android:name=".MicroRagApplication"` en `app/src/main/AndroidManifest.xml`.
- [ ] T029 Crear la base de depuración:
  - `app/src/debug/AndroidManifest.xml`, que declara `DebugLauncherActivity` con `MAIN`/`LAUNCHER` y la etiqueta «MicroRAG Debug»;
  - `app/src/debug/java/com/lisdatasolutions/microrag/debug/DebugLauncherActivity.kt`, con una lista de entradas registrables («Catálogo de componentes» y «Menú de rutas», que se rellenan en US1 y US2).

**Checkpoint**: `./gradlew assembleDebug testDebugUnitTest` en verde; el tema, los iconos y los servicios base disponibles.

---

## Phase 3: User Story 1 — Identidad visual LISDATA coherente (Priority: P1) 🎯 MVP

**Goal**: los ~35 componentes `Lisdata*` con sus variantes, sus previews y el catálogo de debug, con tokens AA verificados.

**Independent Test**:
1. Abrir «MicroRAG Debug → Catálogo de componentes».
2. Recorrer todos los componentes en claro y en oscuro.
3. Compararlos con los PNG y con `INVENTARIO.md` §2/§4.
4. Comprobar que `ContrastTest` y `DesignSystemConformanceTest` están en verde.

### Tests for User Story 1 ⚠️

- [ ] T030 [P] [US1] Crear `app/src/test/java/com/lisdatasolutions/microrag/core/designsystem/ContrastTest.kt`:
  - recalcula todos los pares de `data-model.md` §1.1 en claro y oscuro con `Contrast.kt`;
  - umbral de 4,5 en texto normal y 3,0 en texto grande o gráfico;
  - verifica el blanco sobre `gradients.cta` muestreado en 0 %…100 % con ≥ 3,0 (SC-002, D-019).
- [ ] T031 [P] [US1] Crear `app/src/test/java/com/lisdatasolutions/microrag/core/designsystem/DesignSystemConformanceTest.kt`:
  - escanea `app/src/main/java`;
  - ningún `Color(0x` fuera de `core/designsystem/theme/`;
  - ningún `.sp` fuera de `core/designsystem/theme/`;
  - ningún `Text("…")` con literal fuera de los ficheros `*Previews.kt` (SC-008, FR-001, FR-032);
  - ningún uso de `dynamicLightColorScheme`/`dynamicDarkColorScheme` en `src/main` (FR-002, P9).

### Implementation for User Story 1

**Regla de previews (FR-010)**: cada `*Previews.kt` de T032–T039 incluye:
- la anotación `@LisdataPreviews` (claro, oscuro y fuente al 200 %);
- una preview con textos largos;
- una preview del estado de error en los componentes que lo tienen (botones deshabilitados u ocupados, chips de error, filas en error, `LisdataSourceCitation` no disponible, `LisdataAnswerCard` fallida).

- [ ] T032 [P] [US1] Crear `…/microrag/core/designsystem/component/Buttons.kt` con `LisdataGradientButton` (`gradients.cta`, 52 dp, deshabilitado y ocupado), `LisdataDarkButton` y `LisdataOutlinedButton` (normal y destructivo), y `ButtonsPreviews.kt`.
- [ ] T033 [P] [US1] Crear `…/microrag/core/designsystem/component/Cards.kt` y `CardsPreviews.kt`:
  - `LisdataCard`, con borde de 1 dp y radio 16;
  - `LisdataHeroCard`, con `gradients.hero` y órbitas decorativas sin semántica;
  - `LisdataInfoCard`, con texto `textSecondaryOnContainer`;
  - `LisdataIconTile`, con los cinco estilos;
  - `LisdataStatCard`;
  - `LisdataQuickActionTile`.
- [ ] T034 [P] [US1] Crear `…/microrag/core/designsystem/component/Chips.kt` y `ChipsPreviews.kt`:
  - `LisdataStatusChip`, con los ocho `StatusKind`, punto o icono más texto;
  - `LisdataFilterChip`, M3 tematizado (D-018);
  - `LisdataCountChip`;
  - `LisdataEyebrow` y `LisdataSectionHeader`;
  - `LisdataStepIndicator`, que anuncia «Paso N de M».
- [ ] T035 [P] [US1] Crear `…/microrag/core/designsystem/component/Rows.kt` y `RowsPreviews.kt`:
  - `LisdataModelStatusRow`;
  - `LisdataDocumentRow`, con semántica agrupada y switch deshabilitado si no está indexado;
  - `LisdataQueueRow`;
  - `LisdataSourceCitation`, con la variante «Fuente ya no disponible»;
  - `LisdataSettingsRow`, incluida la variante destructiva;
  - `LisdataSwitchRow` (`toggleable`);
  - `LisdataRadioRow` (`selectable`, `Role.RadioButton`);
  - `LisdataStageRow`.
- [ ] T036 [P] [US1] Crear `…/microrag/core/designsystem/component/Progress.kt` y `ProgressPreviews.kt` con `LisdataProgressRing` (determinado o indeterminado, `progressValue` en el centro y `stateDescription` en palabras) y `LisdataProgressCard`. Ninguno inventa un porcentaje (constitución V).
- [ ] T037 [P] [US1] Crear `…/microrag/core/designsystem/component/Chat.kt` y `ChatPreviews.kt`:
  - `LisdataChatBubble`;
  - `LisdataAnswerCard`, con los estados `AnswerState`, «Copiar respuesta», chip de fuentes y Reintentar; el nombre del asistente sale de `R.string.assistant_name`;
  - `LisdataChatComposer`, en el que enviar pasa a detener mientras se genera (G12).
- [ ] T038 [P] [US1] Crear `…/microrag/core/designsystem/component/Inputs.kt` y `InputsPreviews.kt` con `LisdataSearchField` (48 dp, radio 24, limpiar) y `LisdataFilePickerCard` (borde discontinuo, sin texto de arrastrar y soltar, G14).
- [ ] T039 [P] [US1] Crear `…/microrag/core/designsystem/component/Dialogs.kt` y `DialogsPreviews.kt` con `LisdataConfirmationDialog` (M3 `AlertDialog`, foco inicial en Cancelar, variante destructiva) y `LisdataModelMissingBlock`.
- [ ] T040 [US1] Añadir a `app/src/main/res/values/strings.xml` y `values-en/strings.xml` las cadenas que usan los componentes de T032–T039 (descripciones de contenido, estados y acciones), sin literales en los componentes.
- [ ] T041 [US1] Crear en `app/src/debug/java/com/lisdatasolutions/microrag/debug/catalog/` los ficheros `SampleData.kt`, con datos de ejemplo etiquetados como «Ejemplo», y `CatalogEntries.kt`, con una `CatalogEntry` por componente y variante, agrupadas por sección. Depende de T025, T026 y T032–T039.
- [ ] T042 [US1] Crear `app/src/debug/java/com/lisdatasolutions/microrag/debug/catalog/ComponentCatalogScreen.kt`:
  - lista seccionada;
  - conmutador de tema claro/oscuro y de `fontScale` (1,0 / 1,5 / 2,0) mediante `CompositionLocalProvider(LocalDensity)`;
  - registrar la entrada «Catálogo de componentes» en `DebugLauncherActivity`.

**Checkpoint**: el catálogo muestra todos los componentes en los dos temas; T030 y T031 en verde.

---

## Phase 4: User Story 2 — Moverse por la estructura de la app (Priority: P1)

**Goal**: los 10 destinos tipados, el shell con barra inferior y las reglas de pila N1–N8, los marcadores «Pendiente · Fxx» y el menú de rutas de debug.

**Independent Test**: seguir `quickstart.md` §4 y comprobar que `NavigationTest` está en verde.

### Tests for User Story 2 ⚠️

- [ ] T043 [P] [US2] Crear `app/src/test/java/com/lisdatasolutions/microrag/core/navigation/NavigationTest.kt` con Robolectric, Compose ui-test, `TestNavHostController` y `StateRestorationTester`. Cubre las reglas de `contracts/navigation.md`:
  - N1, una sola instancia por pestaña;
  - N2/N5, Atrás vuelve a Inicio;
  - N3, Atrás en Inicio no deja pila;
  - N4, reselección vuelve a la raíz;
  - N6, visibilidad de la barra inferior por destino y su ocultación con el teclado, mediante la función pura `shouldShowBottomBar(destination, imeVisible)` probada en todas sus combinaciones (FR-020);
  - N7, restauración del destino y de la pestaña al recrear la Activity por rotación **y por cambio de tema claro/oscuro** (`uiMode`), y tras restaurar el estado guardado (SC-007);
  - N8, `Document(-1)` muestra el marcador sin cierre.

### Implementation for User Story 2

- [ ] T044 [P] [US2] Crear `…/microrag/core/navigation/Destinations.kt`, con las 10 rutas `@Serializable` de `contracts/navigation.md`, y `TopLevelDestination.kt`, el enum con ruta, icono y etiqueta.
- [ ] T045 [P] [US2] Crear `…/microrag/core/navigation/PendingDestination.kt`: un marcador con `LisdataTopBar` y `LisdataEmptyState`, el texto «Pendiente · Fxx», la ruta y los argumentos, a partir de recursos. Añadir sus previews en `PendingDestinationPreviews.kt`.
- [ ] T046 [US2] Crear `…/microrag/core/navigation/NavigationActions.kt` con `navigateToTopLevel` (N1), la reselección (N4) y Atrás (N2, N3, N5).
- [ ] T047 [US2] Crear `…/microrag/core/navigation/MicroRagNavHost.kt`: `NavHost` con inicio en `Home` (FR-022), los 10 `composable<…>` que muestran `PendingDestination` con su feature propietaria (research R17) y sin `deepLinks` (FR-023).
- [ ] T048 [US2] Crear `…/microrag/core/navigation/MicroRagAppShell.kt`:
  - función pura `shouldShowBottomBar(destination, imeVisible)`;
  - `Scaffold` con `LisdataBottomBar` visible según esa función, que recibe `WindowInsets.isImeVisible` (FR-020);
  - consumo de insets edge-to-edge;
  - el Atrás del sistema delegado según N2 y N3.
- [ ] T049 [US2] Crear el enrutado de depuración por variante:
  - `app/src/release/java/com/lisdatasolutions/microrag/debug/DebugRouting.kt`, un *stub* cuyo `initialRoute(intent)` devuelve `null`;
  - `app/src/debug/java/com/lisdatasolutions/microrag/debug/DebugRouting.kt`, que interpreta el extra `EXTRA_ROUTE` y devuelve la ruta tipada.
- [ ] T050 [US2] Reescribir `…/microrag/MainActivity.kt`: `enableEdgeToEdge()`, `LisdataTheme { MicroRagAppShell(initialRoute = DebugRouting.initialRoute(intent)) }` y sin restos del tema morado.
- [ ] T051 [US2] Crear `app/src/debug/java/com/lisdatasolutions/microrag/debug/routes/RouteMenuScreen.kt`:
  - un botón por destino, con argumentos de prueba válidos e inválidos;
  - cada botón lanza `MainActivity` con `EXTRA_ROUTE`;
  - registrar la entrada «Menú de rutas» en `DebugLauncherActivity`.

**Checkpoint**: navegación completa verificada por T043 y manualmente según `quickstart.md` §4.

---

## Phase 5: User Story 3 — Privacidad garantizada desde la instalación (Priority: P2)

**Goal**: el paquete sin permisos de red, normales ni peligrosos; sin backup ni transferencia; sin enlaces profundos; release sin herramientas de debug ni logs.

**Independent Test**: seguir `quickstart.md` §2 (apkanalyzer y `bmgr`) y comprobar que `ManifestInvariantsTest` está en verde.

### Tests for User Story 3 ⚠️

- [ ] T052 [P] [US3] Crear `app/src/androidTest/java/com/lisdatasolutions/microrag/ManifestInvariantsTest.kt` sobre el `PackageInfo` instalado:
  - `requestedPermissions` ⊆ {`${packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`} (P4);
  - `FLAG_ALLOW_BACKUP` desactivado (P2);
  - ninguna actividad con `ACTION_VIEW` + `CATEGORY_BROWSABLE` (P5);
  - la etiqueta de la aplicación es «MicroRAG» (P10).

### Implementation for User Story 3

- [ ] T053 [US3] Editar `app/src/main/AndroidManifest.xml`: `android:allowBackup="false"`, eliminar `android:fullBackupContent` y mantener `android:dataExtractionRules="@xml/data_extraction_rules"`. Comprobar que no se declara ningún permiso (P2, P4).
- [ ] T054 [US3] Reescribir `app/src/main/res/xml/data_extraction_rules.xml`: `<cloud-backup>` y `<device-transfer>` excluyen `root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database` y `device_sharedpref` (research R9, P3).
- [ ] T055 [US3] Verificar el APK de release según `quickstart.md` §2:
  - permisos, `min-sdk` 31;
  - `allowBackup`;
  - ausencia de `BROWSABLE`;
  - `apkanalyzer dex packages` sin `debug.catalog` ni `DebugLauncherActivity` (P6).

  Anotar la salida en `specs/001-foundations-design-system/checklists/release-verification.md`.

**Checkpoint**: T052 en verde en el emulador; P1–P6 verificados.

---

## Phase 6: User Story 4 — Accesibilidad básica en todas las piezas (Priority: P2)

**Goal**: todos los componentes anuncian nombre y estado, tienen áreas táctiles ≥ 48 dp, cumplen contraste según ATF y no recortan texto con la fuente al 200 %.

**Independent Test**: comprobar que `CatalogAccessibilityTest` está en verde y hacer el recorrido con TalkBack y fuente al 200 % del `quickstart.md` §3 y §5.

### Tests for User Story 4 ⚠️

- [ ] T056 [P] [US4] Crear `app/src/androidTest/java/com/lisdatasolutions/microrag/CatalogAccessibilityTest.kt`:
  - `createComposeRule()` con `enableAccessibilityChecks()` (`ui-test-junit4-accessibility`) sobre cada `CatalogEntry` en claro y oscuro;
  - asserts explícitos de `assertTouchHeightIsAtLeast(48.dp)` y `assertTouchWidthIsAtLeast(48.dp)` en chips, filtros y botones de icono (SC-006);
  - comprobación de `stateDescription` en switches, radios y progreso.

### Implementation for User Story 4

- [ ] T057 [US4] Corregir en `…/microrag/core/designsystem/component/*.kt` las violaciones que detecte T056:
  - `minimumInteractiveComponentSize()` en chips y botones de icono;
  - `semantics(mergeDescendants = true)` en las filas;
  - `contentDescription = null` en iconos decorativos;
  - `stateDescription` en progreso y estados.
- [ ] T058 [US4] Revisar con fuente al 200 % en el catálogo (conmutador de T042) y en el emulador (`adb shell settings put system font_scale 2.0`). Corregir recortes en los componentes: contenedores que crecen, `maxLines` y elipsis solo donde el diseño lo prevé. Registrar el resultado en `specs/001-foundations-design-system/checklists/accessibility-review.md`.
- [ ] T059 [US4] Hacer un recorrido manual con TalkBack del catálogo y de los marcadores de navegación (`quickstart.md` §5) y anotar las incidencias y su corrección en `specs/001-foundations-design-system/checklists/accessibility-review.md`.

**Checkpoint**: T056 en verde; revisión manual documentada.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T060 Ejecutar `./gradlew clean assembleDebug assembleRelease lintDebug testDebugUnitTest connectedDebugAndroidTest` y dejarlo todo en verde, corrigiendo los avisos de lint sin `baseline` (SC-010).
- [ ] T061 Medir el arranque en frío en el emulador `Pixel_10` según `quickstart.md` §6 (`am start -W`, 5 ejecuciones, mediana < 1000 ms) y anotarlo con el dispositivo y la versión en `specs/001-foundations-design-system/checklists/release-verification.md` (SC-009). La medición en el teléfono de referencia se repite en F02.
- [ ] T062 Revisar visualmente el catálogo frente a los PNG (`quickstart.md` §3). Documentar las diferencias frente al mockup, incluidos los tamaños de los controles M3 (D-018), en `specs/001-foundations-design-system/checklists/mockup-differences.md`.
- [ ] T063 [P] Actualizar `docs/roadmap/INVENTARIO.md` §6: G19, G20 y G43 con su estado tras F01, y G08/G39 como resueltos por la implementación.
- [ ] T064 [P] Actualizar `docs/roadmap/FEATURES.md`: F01 ✅ con un enlace al informe de cierre, y `docs/roadmap/DECISIONS.md` si durante la implementación hubo decisiones nuevas (p. ej. D-020 del fallback de T004).
- [ ] T065 Preparar el informe de cierre de F01, que se entrega al usuario:
  - resumen de lo implementado;
  - resultado de build, lint y tests;
  - diferencias frente al mockup (T062);
  - cifra de arranque (T061);
  - estado en `FEATURES.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: T001 → T002 → T003 → T004. T005 puede ir junto a T004.
- **Foundational (fase 2)**: depende de la fase 1.
  - Los tests T006–T008 van primero.
  - Recursos T009–T015 en paralelo, salvo T013, que depende de T011 y T012.
  - Tokens T016–T019 en paralelo, y después T020 → T023 y T024.
  - T025 y T026 dependen de T020, T022 y T023.
  - T027 → T028; T029 depende de T020.
- **US1 (fase 3)** y **US2 (fase 4)**: ambas dependen solo de la fase 2 y pueden ir en paralelo.
  - Dentro de US1, T041 y T042 dependen de T032–T040.
  - Dentro de US2, T047 depende de T044 y T045; T048 de T046 y T047; T050 de T048 y T049.
- **US3 (fase 5)**: depende de la fase 2. T055 requiere US1 y US2 integradas para verificar que release no incluye el catálogo.
- **US4 (fase 6)**: depende de US1 (catálogo) y de US2 (marcadores).
- **Polish (fase 7)**: depende de todas las historias.

### User Story Dependencies

- **US1 (P1)**: independiente tras la fase 2.
- **US2 (P1)**: independiente tras la fase 2. Usa `LisdataTopBar`, `LisdataBottomBar` y `LisdataEmptyState` de la fase 2.
- **US3 (P2)**: independiente tras la fase 2 (T052–T054). La verificación T055 se hace al final.
- **US4 (P2)**: requiere el catálogo de US1.

### Parallel Opportunities

- Fase 2: T006, T007 y T008; T009, T010, T011, T012, T014 y T015; T016, T017, T018, T019, T021 y T022.
- US1: T032–T039. Son ficheros distintos y comparten solo tokens.
- US2: T044 y T045. T043 se escribe en paralelo con ellas.
- US1 y US2 pueden desarrollarse a la vez.

---

## Parallel Example: User Story 1

```bash
# Tests primero (deben fallar):
Task: "ContrastTest en app/src/test/.../core/designsystem/ContrastTest.kt"
Task: "DesignSystemConformanceTest en app/src/test/.../core/designsystem/DesignSystemConformanceTest.kt"

# Grupos de componentes en paralelo:
Task: "Buttons.kt + ButtonsPreviews.kt"
Task: "Cards.kt + CardsPreviews.kt"
Task: "Chips.kt + ChipsPreviews.kt"
Task: "Rows.kt + RowsPreviews.kt"
Task: "Progress.kt + ProgressPreviews.kt"
Task: "Chat.kt + ChatPreviews.kt"
Task: "Inputs.kt + InputsPreviews.kt"
Task: "Dialogs.kt + DialogsPreviews.kt"
```

---

## Implementation Strategy

### MVP primero (US1)

1. Fases 1 y 2: toolchain, tokens, tema, iconos y servicios base.
2. Fase 3 (US1): componentes y catálogo.
3. **Parar y validar**: revisión del catálogo frente a los PNG y `ContrastTest` en verde.

### Entrega incremental

1. Setup + Foundational → base compilable.
2. US1 → catálogo revisable (MVP del design system).
3. US2 → app navegable con marcadores.
4. US3 → invariantes de privacidad verificadas.
5. US4 → auditoría de accesibilidad.
6. Polish → informe de cierre de F01.

---

## Notes

- `[P]` = ficheros distintos y sin dependencias pendientes.
- Cada tarea termina con un commit atómico en inglés: `feat(designsystem): …`, `build: …`, `test(navigation): …`.
- Antes de marcar una tarea, compilar y ejecutar los tests que la cubren.
- Ninguna tarea introduce red, base de datos ni modelos (fuera del alcance de F01).
