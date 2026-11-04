---

description: "Lista de tareas de F16 — Apariencia: tema e icono de Ajustes"
---

# Tasks: Apariencia: tema e icono de Ajustes (F16)

**Input**: Documentos de diseño en `/specs/016-appearance-theme/`: [plan.md](plan.md), [spec.md](spec.md), [wireframe.md](wireframe.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-010). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en orden de prioridad. Las pruebas en el teléfono van en la **última fase** (petición del usuario).

**Historias de la spec:**
- US1 — Elegir el tema de la app (P1);
- US2 — El tema se mantiene y llega a todas partes (P1);
- US3 — El engranaje de Ajustes (P1);
- US4 — Un fallo al guardar o al leer no rompe nada (P2);
- US5 — Cambiar el tema sin perder nada (P2);
- US6 — Pantalla aprobada, accesible y traducida (P2).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37), sin ventana. Se arrancan cuando hacen falta y se cierran al terminar la fase. En ellos vale `connectedDebugAndroidTest`;
  - teléfono de referencia `adb -s P31281001066`, **solo en la última fase**. Nunca `connectedDebugAndroidTest`: se instalan los dos APK y se usa `am instrument`, con la salida a un fichero y la pantalla despierta (`KEYCODE_WAKEUP` cada 60 s).

  `adb` está en `$HOME/Library/Android/sdk/platform-tools/adb`.
- **En el teléfono** (research R11):
  - nunca se borran ni se vuelven a descargar los modelos;
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - Inicio, Biblioteca e Historial no se capturan;
  - los volcados van a `/data/local/tmp` y se borran al momento;
  - los ajustes se anotan al empezar y se restauran al terminar, con el tema de la app en «Sistema».
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US6).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

- [X] T001 [P] Textos de `theme-ui.md` §4 en `app/src/main/res/values/strings_settings.xml` y `app/src/main/res/values-en/strings_settings.xml`, bajo un comentario «F16 Apariencia (D-067)». `StringsParityTest` pasa.
- [X] T002 [P] Prueba nueva en `…/test/…/core/designsystem/DesignSystemConformanceTest.kt` (SC-007, research R7):
  - `settingsIcon_isTheLucideGear`: `res/drawable/ic_settings.xml` declara `icon 'settings'`;
  - ningún `ic_*.xml` declara `icon 'sun'`;
  - existe `ic_sun_moon.xml` con `icon 'sun-moon'`.

  Falla hasta T003.
- [X] T003 Iconos (research R7, quickstart §1):
  - en `tools/icons/icons.txt`, `ic_settings = settings` con el comentario «Gear for Settings (D-067; the mockups use a sun, G19)» y `ic_sun_moon = sun-moon`;
  - regenerar `app/src/main/res/drawable/` con el comando de quickstart §1. `git status` debe mostrar solo `ic_settings.xml` (modificado) y `ic_sun_moon.xml` (nuevo); si cambia cualquier otro, **se para y se revisa**;
  - `val SunMoon = R.drawable.ic_sun_moon` en `…/microrag/core/designsystem/icon/LisdataIcons.kt`, también en `all`.

  T002 pasa.
- [X] T004 [P] Regla de capas en `…/test/…/architecture/LayeringTest.kt` (`theSettingsScreensUseNoBackendApi`, U9): añadir `"import android.app.UiModeManager"` a la lista prohibida y al mensaje.

---

## Phase 2: Foundational (dominio, almacén, controlador y aviso)

**Propósito:** todo lo que usan US1–US5. **Bloquea las historias.**

### Pruebas (antes, y deben fallar)

- [X] T005 Tipos y fakes (analyze, L3):
  - primero los tipos, sin lógica, en `…/microrag/domain/settings/`: `ThemeModels.kt` (data-model §1), `AppThemeStore.kt` y `AppThemeController.kt` (theme-api §1; `isShowing` solo si T015 elige la variante B);
  - después, en `…/test/…/testing/SettingsFakes.kt`:
  - `FakeAppThemeStore`, con el valor guardado, `failWrite`, `failRead` y el registro de escrituras;
  - `FakeAppThemeController`, con `failApply`, `showing` configurable y el registro de lo aplicado.
- [X] T006 [P] `…/test/…/domain/settings/ThemeUseCasesTest.kt` (theme-api P1, P3 y P5):
  - **Observe:** emite lo que lee el almacén, también `readFailed`.
  - **Save:**
    - el mismo valor da `Unchanged`, sin escribir ni aplicar;
    - con la lectura fallida, se guarda aunque el valor sea `SYSTEM`;
    - un fallo de escritura da `Failed` sin aplicar;
    - un fallo al aplicar da `Failed` y restaura la copia anterior, o `SYSTEM` si la lectura había fallado;
    - un guardado bueno escribe, aplica, da `Saved` y deja `announceSaved(settle = true)`.
  - **Reconcile** (P5; los casos de la variante que elija T015):
    - aplica la copia leída;
    - con la lectura fallida, aplica `SYSTEM` (FR-016, H2);
    - en la variante B, no aplica cuando `isShowing` es `true`.
- [X] T007 [P] `…/test/…/data/settings/DataStoreAppThemeStoreTest.kt`, con el patrón de `DataStoreOnboardingStoreTest`:
  - sin fichero, `SYSTEM` sin `readFailed`;
  - escribir `DARK` y reabrir el almacén da `DARK`;
  - una clave desconocida da `SYSTEM`;
  - con un `DataStore` que lanza `IOException` al leer, `SYSTEM` con `readFailed`; al escribir, `false`. Nunca lanza;
  - con el fichero corrupto (bytes basura), la lectura da `SYSTEM` con `readFailed`, y la escritura siguiente funciona y se lee en otra sesión (M2);
  - el `RecordingLogger` solo recibe el tipo de fallo, nunca el valor (FR-020).
- [X] T008 [P] Ampliar `…/test/…/core/navigation/SettingsSavedAnnouncementTest.kt` (theme-api P6, research R6), con el calificador `es` porque el anuncio también exige el idioma resuelto (L4):
  - con `announceSaved(settle = true)`, no anuncia antes de 1 s del reloj de la prueba, anuncia una vez después y consume el aviso;
  - si la composición se descarta antes del margen y se vuelve a crear, el anuncio sale una sola vez, en la nueva;
  - `announceSaved()` sin margen sigue igual que en F12.
- [X] T009 [P] `…/androidTest/…/core/theme/PlatformAppThemeControllerTest.kt` (research R1, R3 y R12; theme-api P4), con el controlador construido sobre el mismo contexto que inyecta Koin (M1):
  - se fija el modo del emulador con `cmd uimode night no` (vía `UiAutomation.executeShellCommand`). `apply(DARK)`: una actividad recién creada (`ActivityScenario`) tiene el modo nocturno `YES`. Con `night yes` y `apply(LIGHT)`, tiene `NO`. Nunca se compara con `Resources.getSystem()` (H1);
  - `apply(SYSTEM)`: la actividad sigue al modo fijado del emulador en los dos sentidos;
  - **reaplicar no recrea:** con `DARK` aplicado y una actividad abierta, otro `apply(DARK)` no la recrea ni le llega un cambio de configuración. Lo dice un contador de `onCreate`/`onConfigurationChanged` tras 2 s. Esto decide entre las variantes A y B (R3);
  - cada cambio se espera con un plazo máximo de 5 s;
  - `@After` aplica `SYSTEM` y restaura el `cmd uimode night` que había, incluidos `auto` y `custom` (L6).

### Implementación

- [X] T010 Dominio en `…/microrag/domain/settings/` (los tipos ya existen desde T005):
  - `ThemeUseCases.kt`: `ObserveThemeSettingsUseCase`, `SaveThemeSettingsUseCase` y `ReconcileThemeUseCase`;
  - `SettingsNotices.kt`: `announceSaved(settle: Boolean = false)`, `settlePending` y `consumeSaved()`, que también limpia el margen.

  T006 pasa.
- [X] T011 [P] `…/microrag/data/settings/DataStoreAppThemeStore.kt`, con `create(noBackupDir, scope)` hacia `datastore/appearance.preferences_pb`, `ReplaceFileCorruptionHandler` que marca la lectura fallida (M2), la clave `theme` y registro solo del tipo de fallo con `AppLogger`. T007 pasa.
- [X] T012 [P] `…/microrag/core/theme/PlatformAppThemeController.kt` (theme-api P4): `UiModeManager.setApplicationNightMode` con `AUTO`, `NO` o `YES`, y captura de `RuntimeException` → `false`. `isShowing` solo con la variante B, con `UiModeManager.getNightMode()` para `SYSTEM` y nunca con `Resources.getSystem()`.
- [X] T013 Inyección (theme-api §4): `…/microrag/core/di/CoreModule.kt`, `…/microrag/data/di/DataModule.kt` y `…/microrag/domain/di/DomainModule.kt`. `AppModulesTest` pasa.
- [X] T014 `SettingsSavedAnnouncement` en `…/microrag/core/navigation/MicroRagAppShell.kt`: con `settlePending`, espera también `SETTLE_MS = 1000` en la composición (P6). T008 pasa.
- [X] T015 **Puerta de R1 y R3:**
  - crear el AVD `MicroRAG_API32` si no existe: `sdkmanager "system-images;android-32;google_apis;arm64-v8a"` y `avdmanager create avd`, con el mismo perfil que `MicroRAG_API33`. Aprobado por el usuario en el analyze (D-069);
  - pasar T009 en `MicroRAG_API32`, `MicroRAG_API33` y `Pixel_10` (`connectedDebugAndroidTest` filtrado a la clase).

  **Resultado esperado:**
  - si `MODE_NIGHT_AUTO` no devuelve la app al modo del emulador, **se para y se consulta**: `CUSTOM` no es alternativa (L5);
  - si reaplicar no recrea en los tres, se usa la variante A, y si no, la B;
  - la variante elegida se anota en `research.md` R3 y en el informe, y T010 y T012 se completan con ella.

  **Resultado:** 3 de 3 en API 32, 33 y 37. Se elige la variante A, sin `isShowing`.

**Checkpoint:** dominio, almacén, controlador y aviso listos y probados. El modo por app está verificado en los dos emuladores.

---

## Phase 3: User Story 1 - Elegir el tema de la app (Priority: P1) 🎯 MVP

**Goal:** la fila «Tema» en Ajustes y la pantalla Tema, que guarda con «Guardar preferencias» (A1–A3, D-067).

**Independent Test:** en el emulador, con el modo del sistema en claro y en oscuro, elegir cada tema y guardarlo. La app cambia, la fila muestra el valor y la pantalla lo marca al volver.

### Pruebas

- [X] T016 [P] [US1] `…/test/…/feature/settings/ThemeViewModelTest.kt` (theme-ui U1–U4), con el patrón de `LanguagesViewModelTest`:
  - abre con el guardado marcado;
  - marcar cambia `selected` sin llamar a guardar, y sobrevive a un `SavedStateHandle` restaurado;
  - una relectura en `onStart` sigue al guardado solo si no se tocó nada;
  - guardar el mismo valor y guardar otro envían `ThemeEvent.Close`;
  - un segundo toque mientras guarda no hace nada.
- [X] T017 [P] [US1] `…/test/…/feature/settings/ThemeScreenTest.kt` (Robolectric, calificadores `es-w390dp-h844dp`; wireframe §3):
  - barra «Tema», «APARIENCIA» y «Elige cómo se ve la app» como encabezado;
  - «TEMA DE LA APLICACIÓN» con tres opciones de radio («Sistema» con su subtítulo, «Claro» y «Oscuro») en un grupo seleccionable;
  - el botón «Guardar preferencias», y «Guardando…» deshabilitado;
  - tocar una opción llama a `onSelect`, y el botón a `onSave`;
  - sin tarjeta hero.
- [X] T018 [P] [US1] Ampliar `…/test/…/feature/settings/SettingsScreenTest.kt` y `…/test/…/feature/settings/SettingsViewModelTest.kt`:
  - `structureOfPng09` incluye «Tema» y «Sistema» entre «Idiomas» e «Índice y rendimiento»;
  - la fila pide su destino (`theOtherRowsAskForTheirDestination`);
  - el subtítulo es «Claro» u «Oscuro» según `theme`;
  - el ViewModel lee el tema al crearse y en `onResume`.
- [X] T019 [P] [US1] Ampliar `…/test/…/core/navigation/NavigationTest.kt`:
  - Ajustes → Tema, sin barra inferior en `Theme`, y Atrás vuelve a Ajustes;
  - `settingsViewModels()` construye `SettingsViewModel` con el caso de uso nuevo y registra `ThemeViewModel` con fakes (L3).

### Implementación

- [X] T020 [US1] En `…/microrag/feature/settings/`, `ThemeUiState.kt` (con `ThemeEvent`), `ThemeViewModel.kt`, `ThemeScreen.kt` y `ThemeRoute.kt`, copiando la forma de `LanguagesScreen`/`LanguagesViewModel`/`LanguagesRoute` (theme-ui §3). Incluye:
  - las vistas previas `@LisdataPreviews` en `SettingsPreviewData.kt`;
  - `viewModelOf(::ThemeViewModel)` en `…/microrag/feature/di/FeatureModule.kt`.

  T016 y T017 pasan, salvo los casos de fallo (US4).
- [X] T021 [US1] Fila «Tema»:
  - `theme` en `SettingsUiState.kt`;
  - `SettingsViewModel.kt` recibe `ObserveThemeSettingsUseCase` y lo lee al crearse y en `onResume`, con su alta en `FeatureModule`;
  - la fila en `SettingsScreen.kt` (theme-ui §2), `onTheme` en `SettingsActions` y `SettingsRoute.kt`, y su vista previa.

  T018 pasa.
- [X] T022 [US1] Navegación: `@Serializable data object Theme` en `…/microrag/core/navigation/Destinations.kt`, y en `MicroRagNavHost.kt` `composable<Theme> { ThemeRoute(onBack = back) }` y `onTheme` en `SettingsRoute`. T019 pasa.

**Checkpoint:** US1 funciona en el emulador y en la JVM.

---

## Phase 4: User Story 2 - El tema se mantiene y llega a todas partes (Priority: P1)

**Goal:** persistencia, pantalla de arranque, barras del sistema, «Sistema» en vivo y reconciliación (FR-008 a FR-012, FR-017).

**Independent Test:** quickstart §3, pasos 4–6 y 9.

- [X] T023 [US2] Reconciliación al arrancar en `…/microrag/MicroRagApplication.kt` (theme-api P5): después de `startKoin`, lanzar en `get<ApplicationScope>().scope` una corrutina que resuelva y ejecute `ReconcileThemeUseCase` dentro de ella, de modo que nada toca disco en el hilo principal (L9). StrictMode en depuración no registra violaciones nuevas en el arranque (logcat filtrado a la app). La recreación rara tras el primer fotograma, si hay discrepancia, va al informe.
- [X] T024 [US2] Verificación en `Pixel_10` (quickstart §3, pasos 4–6 y 9):
  - 10 arranques en frío grabados por combinación (Claro y Oscuro con el emulador en el modo contrario), revisados fotograma a fotograma (SC-002);
  - el tiempo hasta ver el tema nuevo tras «Guardar preferencias» (SC-001) y tras `cmd uimode` (SC-004), medido con las marcas de tiempo de `screenrecord` (M6);
  - reinicio del emulador (SC-003);
  - «Sistema» con `cmd uimode night yes|no` (SC-004);
  - barras del sistema legibles (FR-011).

  Se repite lo esencial en `MicroRAG_API33` y `MicroRAG_API32`. Las cifras van al informe. Si hay destello, **se para y se consulta**.

---

## Phase 5: User Story 3 - El engranaje de Ajustes (Priority: P1)

**Goal:** el engranaje en todos sus usos y la documentación al día (FR-001 a FR-003). Los recursos se hicieron en T003.

**Independent Test:** T002 en verde, y capturas de la barra inferior y de Inicio en claro y en oscuro.

- [X] T025 [P] [US3] Documentación del icono:
  - `docs/roadmap/INVENTARIO.md` §2.6: la fila de `ic_settings` pasa a `settings` con «Engranaje por decisión del usuario (D-067); el PNG usa un sol (G19)», y se añade la fila de `ic_sun_moon` («Ajustes: fila Tema»);
  - la ficha P03: «`ic_settings` (engranaje)»;
  - G19: «✅ Implementado en F16 (`ic_settings` = Lucide `settings`, D-067)», y G45: «✅ Implementado en F16» (L1);
  - `specs/001-foundations-design-system/quickstart.md`: el comando de regenerar iconos pasa `"$TMP/package/icons"`.
- [X] T026 [US3] Comprobación visual en `Pixel_10`: la barra inferior (pestaña Ajustes seleccionada y sin seleccionar) y la cabecera de Inicio, con datos de prueba, en claro y en oscuro. El catálogo de depuración muestra el engranaje y `SunMoon`. `CatalogAccessibilityTest` sigue pasando.

---

## Phase 6: User Story 4 - Un fallo al guardar o al leer no rompe nada (Priority: P2)

**Goal:** A4 y A5 (FR-015, FR-016).

**Independent Test:** las pruebas JVM con el almacén y el controlador que fallan.

### Pruebas

- [X] T027 [P] [US4] Ampliar `ThemeViewModelTest`:
  - un guardado `Failed` deja la pantalla abierta, con `saveFailed` y la opción marcada;
  - `onSaveFailureShown` lo limpia;
  - un segundo intento que funciona cierra;
  - con `readFailed`, la opción marcada es «Sistema».
- [X] T028 [P] [US4] Ampliar `ThemeScreenTest`:
  - con `saveFailed`, el snackbar «No se pudieron guardar las preferencias. Se mantienen las anteriores.» aparece una vez y se anuncia;
  - con `saved.readFailed`, la tarjeta «No se pudieron leer tus preferencias. Se usan las de por defecto.» va encima del botón.

### Implementación

- [X] T029 [US4] Estados de fallo en `ThemeViewModel.kt` y `ThemeScreen.kt` (`LisdataInfoCard` y el snackbar). `SaveFailure`, hoy `private` en `LanguagesScreen.kt`, pasa a un composable `internal` de `feature/settings` que usan las dos pantallas (L2). T027 y T028 pasan, y `LanguagesScreenTest` sigue pasando sin tocarlo.

---

## Phase 7: User Story 5 - Cambiar el tema sin perder nada (Priority: P2)

**Goal:** FR-013 y FR-014. Sin código nuevo (research R5); se verifica.

**Independent Test:** quickstart §3, paso 7.

- [X] T030 [P] [US5] Prueba instrumentada `…/androidTest/…/core/theme/ThemeChangeKeepsStateTest.kt`, con la app sobre fakes de chat:
  - un borrador escrito en el compositor y la pestaña Chat abierta;
  - `apply(DARK)` desde la prueba, esperando la recreación con un plazo máximo de 5 s;
  - tras la recreación, el borrador y la pestaña siguen;
  - `@After` aplica `SYSTEM` y espera a que se aplique (L6).

  Si el entorno de pruebas no permite levantar el shell con fakes, se sustituye por el recorrido de T031 y se anota.

  **Resultado:** la prueba no abre el chat. En el teléfono abriría la conversación del usuario, y la regla del teléfono lo prohíbe. Se hace en Ajustes: con la pestaña y la ficha de modelo abiertas, `apply()` del tema contrario recrea la actividad y las dos siguen. El borrador del chat está en el `SavedStateHandle` (`ChatViewModel`, clave `QUESTION`), cubierto en la JVM. Pasa en `Pixel_10`.
- [X] T031 [US5] Recorrido en `Pixel_10` con datos de prueba: borrador, conversación de prueba abierta y desplazada, y un diálogo abierto con «Sistema» al cambiar el modo del emulador. Todo se conserva. La respuesta en curso y la indexación se comprueban en el teléfono (T040).

  **Resultado:** el emulador no tiene el onboarding completo ni modelos, así que no hay chat utilizable. Con «Sistema» y la ficha de modelo abierta, `cmd uimode night yes` y después `no` la dejan abierta. El borrador y la conversación van en el teléfono (T040).

---

## Phase 8: User Story 6 - Pantalla aprobada, accesible y traducida (Priority: P2)

**Goal:** FR-018, FR-019 y FR-021; SC-008 a SC-010.

- [X] T032 [P] [US6] `…/androidTest/…/feature/settings/ThemeScreenAccessibilityTest.kt` (ATF, `enableAccessibilityChecks`), parametrizada como `LanguagesScreenAccessibilityTest`:
  - estados: cargando, «Sistema», «Oscuro» marcado sin guardar, guardando, fallo al leer;
  - en claro y en oscuro, al 200 % y en horizontal;
  - comprueba los objetivos de 48 dp y las frases.

  `SettingsScreenAccessibilityTest` sigue pasando con la fila nueva.
- [X] T033 [US6] Ajustar la pantalla hasta que T032 pase. Si ATF marca algo que contradice el wireframe o una propuesta aprobada, **se para y se consulta**. **Resultado:** T032 pasó a la primera en `Pixel_10` (los 6 estados, en claro y en oscuro, con ATF, al 200 % y en horizontal), sin cambios en la pantalla.
- [X] T034 [P] [US6] `StringsParityTest` y la revisión del inglés de los textos nuevos. Las correcciones van al informe. **Resultado:** paridad completa; los 9 textos en inglés revisados, sin correcciones.

---

## Phase 9: Polish sin teléfono

- [X] T035 Pasar toda la batería JVM:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`, con solo los avisos previos de LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0;
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests`, que no cambia.

  **Resultado:** JVM 1.024 de 1.024; lint sin errores, con los 2 avisos previos; Python 181 OK.
- [X] T036 Batería instrumentada completa en `Pixel_10` y en `MicroRAG_API33`. En API 33 siguen los 4 fallos previos de Atrás desde la raíz. Cada prueba que toca el modo por app lo deja en «Sistema». **Resultado:** `Pixel_10` OK con 815 pruebas en 19,7 min; API 33 con 815 pruebas y solo los 4 fallos previos, en 20,6 min. Los dos emuladores acaban en modo claro.
- [X] T037 Recorrido en el emulador (quickstart §3, pasos 1–9), con capturas frente al wireframe en español e inglés, en claro y en oscuro, al 200 % y en horizontal. Con cada tema se abren al menos un diálogo de confirmación, una hoja inferior (la ficha de modelo), un menú «⋯» y un snackbar (FR-009).
- [X] T038 Borrador de `specs/016-appearance-theme/report.md`:
  - resumen;
  - resultados de build, lint y pruebas;
  - cifras de T024;
  - diferencias con el PNG 09 (la fila nueva y el engranaje) y con el wireframe;
  - el riesgo residual de Android 12;
  - correcciones del inglés.

  Avance de tareas en este fichero.

---

## Phase 10: Última fase — teléfono de referencia y cierre

- [ ] T039 Batería instrumentada completa en el A063, con los APK finales y la pantalla despierta. El modo por app no se puede leer (R2), así que al terminar se fuerza siempre «Sistema» desde la app. El `cmd uimode night` del teléfono se anota antes y se restaura después, incluidos `auto` y `custom` (L6).

  **Avance (2026-11-04), sin terminar:**
  - **Primera tanda:** el cliente `adb` se cortó en `ShellInsetsTest` (salida 255), pero la instrumentación siguió en el teléfono. El `TestRunner` cerró con «815 tests, 3 failed», y el log ya no guardaba cuáles fueron.
  - **Segunda tanda,** con la salida en `/data/local/tmp`: 236 pruebas sin fallos cuando el usuario pidió parar.
  - Hay que repetirla entera, con la salida en el teléfono, e identificar esos 3 fallos.
- [ ] T040 Recorrido **acordado con el usuario antes de empezar** (quickstart §4):
  - el engranaje;
  - la fila y la pantalla Tema con los tres temas y los dos modos del teléfono;
  - el reinicio;
  - arranques en frío, con el muestreo de un píxel sin guardar imágenes;
  - una respuesta y una indexación durante el cambio, sobre documentos de prueba en una carpeta propia. Se cuentan documentos y fragmentos indexados antes y después (SC-005);
  - red a 0 bytes para el uid de la app;
  - limpieza y teléfono restaurado.
- [ ] T041 Cerrar `report.md` y poner F16 en `docs/roadmap/FEATURES.md` como «implementada; pendiente de la revisión de cierre». **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias. T002 antes que T003.
- **Foundational (fase 2):** T005 (tipos y fakes) antes que T006–T009; T010 antes que T013 y T014; T015 después de T009 y T012, y antes de cerrar T010 y T012 con la variante elegida. **Bloquea US1–US5.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2 (fase 4):** depende de US1, para guardar desde la pantalla, y de T015.
- **US3 (fase 5):** depende solo de T003. Puede ir en paralelo con US1 y US2.
- **US4 (fase 6):** depende de T020.
- **US5 (fase 7):** depende de US1 y US2.
- **US6 (fase 8):** depende de US1 y US4.
- **Polish (fase 9):** depende de todo lo anterior.
- **Última fase (10):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Dominio antes que datos y plataforma; los casos de uso antes que el ViewModel; el ViewModel antes que la pantalla.
- **Paradas de consulta:**
  - la del analyze (hecha: A3 confirmado y emulador de API 32 aprobado, D-069);
  - T003, si la regeneración cambia otros iconos;
  - T015, si `MODE_NIGHT_AUTO` no se comporta como en R1;
  - T024, si hay destello en el arranque;
  - T033, si ATF contradice el wireframe;
  - T040, porque el recorrido del teléfono se acuerda antes con el usuario;
  - T041, la revisión de cierre.

### Parallel Opportunities

- **Fase 1:** T001, T002 y T004.
- **Fase 2:** T006–T009 (pruebas, después de T005), y T011 y T012.
- **US1:** T016–T019.
- **US3:** T025 en paralelo con US1 y US2.
- **US4:** T027 y T028.
- **US6:** T032 y T034.

---

## Parallel Example: User Story 1

```text
T016 ThemeViewModelTest      │ T017 ThemeScreenTest
T018 SettingsScreen/VM tests │ T019 NavigationTest
          ↓ (todas fallan)
T020 pantalla Tema → T021 fila «Tema» → T022 navegación
```

---

## Implementation Strategy

### MVP (US1)

Fases 1 y 2, la puerta de R1 (T015) y US1: se puede elegir el tema y la app cambia. Con eso se valida la forma antes de lo demás.

### Incremental

1. **US2:** el arranque, la persistencia y la reconciliación, verificados en el emulador.
2. **US3:** el engranaje y su documentación, en paralelo.
3. **US4:** los fallos.
4. **US5:** el estado conservado.
5. **US6:** accesibilidad y textos.
6. **Polish y teléfono.**

### Notes

- Cada prueba instrumentada que llama a `setApplicationNightMode` lo deja en `SYSTEM` en su `@After`, para no contaminar las demás ni el teléfono.
- Las tareas marcadas «se para y se consulta» detienen el ciclo automático (D-068).
