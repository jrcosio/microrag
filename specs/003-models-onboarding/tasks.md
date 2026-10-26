---

description: "Lista de tareas de F03 — Modelos y primer arranque"
---

# Tasks: Modelos y primer arranque (F03)

**Input**: Documentos de diseño en `/specs/003-models-onboarding/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **Obligatorios** por la constitución (VI) y por los criterios de la spec (SC-001, SC-002, SC-004, SC-008, SC-009). En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec:
- US1 — Bienvenida y entrada (P1);
- US2 — Descarga con confirmación (P1);
- US3 — Nunca «Listo» sin verificación real (P1);
- US4 — Reanudación robusta (P1);
- US5 — Almacenamiento y entrada a Inicio (P2);
- US6 — Importar desde el dispositivo (P3).

**Entorno**:
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Teléfono de referencia: `adb -s P31281001066`. **Nunca `connected*AndroidTest` en el teléfono** (quickstart §0).
- Emulador: `Pixel_10` (`emulator-5554`).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US6).
- Las rutas son relativas a la raíz del repositorio. Abreviaturas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencias verificadas y recursos de texto.

- [X] T001 Añadir a `gradle/libs.versions.toml` (research R1):
  - versiones `work = "2.12.0"`, `datastore = "1.2.1"` y `mockwebserver = "5.5.1"`;
  - librerías `androidx-work-runtime-ktx`, `androidx-work-testing`, `androidx-datastore-preferences` y `mockwebserver3` (`com.squareup.okhttp3:mockwebserver3`).
- [X] T002 En `app/build.gradle.kts`:
  - `implementation` de work-runtime-ktx y datastore-preferences;
  - `testImplementation` de work-testing y mockwebserver3.

  Ejecutar `./gradlew :app:dependencies --configuration debugRuntimeClasspath` y anotar en research R1 las versiones resueltas de `room-runtime`, `kotlinx-coroutines-android` y `core`. Comprobar `assembleDebug` y `lintDebug` en verde.
- [X] T003 [P] Crear `app/src/main/res/values/strings_onboarding.xml` (es) y `app/src/main/res/values-en/strings_onboarding.xml` (en) con **todos** los textos de `contracts/onboarding-ui.md`:
  - Bienvenida;
  - Preparación;
  - estados de fila, detalles y motivos;
  - bloqueo de «Continuar»;
  - confirmación;
  - línea de aviso del permiso de notificaciones en la confirmación;
  - aviso de notificaciones denegadas;
  - canal y textos de la notificación;
  - descripciones accesibles.

  Los textos de los PNG van literales. Los que llevan número usan `plurals` o `%1$d`, según el contrato.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos de dominio, resolución de estados, almacenamiento del onboarding, registro de verificación y ampliación del repositorio de ficheros. Lo usan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase.

### Tests (escribir primero)

- [X] T004 [P] Ampliar `…/test/domain/model/ModelCatalogTest.kt`:
  - `ModelCatalog.required` es `[EG2_270M, GEMMA4_E2B]`, en ese orden;
  - `downloadUrl` de cada fichero es `https://huggingface.co/<repo>/resolve/<revision>/<fileName>`;
  - tamaños y huellas de D-023 y D-025.
- [X] T005 [P] `…/test/domain/onboarding/ModelSetupStatusResolverTest.kt`. Tabla de prioridades de data-model §2. Incluye la prueba **exhaustiva de SC-001**: recorre todas las combinaciones de `ModelFileState` × progreso × preparación × preferencias × red y comprueba que `Ready` aparece **solo** con `Verified` y backend preparado. Comprueba también:
  - el porcentaje (`floor`, como mucho 100);
  - `SetupRowAction` de cada estado.
- [X] T006 [P] `…/test/domain/model/SetupStepResolverTest.kt`:
  - paso 1, 2 y 3 según data-model §8;
  - `ContinueBlocker` de cada caso;
  - `continueEnabled` solo con los dos `Ready` y el almacenamiento `Offline`.
- [X] T007 [P] `…/test/data/models/VerificationRecordStoreTest.kt`:
  - lectura y escritura atómica;
  - fichero ausente o corrupto, que se trata como vacío;
  - entradas con un SHA-256 distinto del catálogo, que se ignoran.
- [X] T008 [P] Ampliar `…/test/data/models/DefaultModelFileRepositoryTest.kt`:
  - `adopt`: con coincidencia, `rename` y `Verified`, sin copia (el fichero de origen desaparece y el inode es el mismo, o la ruta cambia sin duplicar espacio); con 1 byte alterado, `Mismatch` y fichero borrado;
  - `installFromStream`: con coincidencia, `.part` y `rename`; con discrepancia, nada en `models/`;
  - `markPrepared` y `preparedBackend`;
  - `refresh` con registro válido: `Verified` sin calcular hash (contador de lecturas a 0);
  - `refresh` con otro `lastModified` o tamaño: `Unverified`;
  - `refresh` no borra `models/downloads/*.download`.
- [X] T009 [P] `…/test/data/onboarding/DataStoreOnboardingStoreTest.kt`, con DataStore en `TemporaryFolder`:
  - valores por defecto;
  - transiciones de `stage`;
  - `requestDownload` (añade, `confirmed = true`, `paused = false`);
  - `setPaused` y `clearRequest`;
  - persistencia al recrear el store.
- [X] T010 [P] `…/test/architecture/NetworkConfinementTest.kt` (research R9). Recorre `src/main/java` y `src/debug/java` y falla si un fichero fuera de `data/download/` importa:
  - `java.net.`, excepto `java.net.URI`;
  - `javax.net.`;
  - `okhttp3.`;
  - `android.net.http.`;
  - `java.nio.channels.SocketChannel`.

  Incluye un caso que demuestra que el test detecta un fichero infractor sintético en un directorio temporal.

### Implementación

- [X] T011 [P] En `…/microrag/domain/model/`:
  - `ModelCatalog.required` y la propiedad `ModelFile.downloadUrl` (data-model §1);
  - `OnboardingStage.kt`, `NetworkKind.kt` y `StartDestination.kt`;
  - `DownloadProgress.kt` con `DownloadOutcome` y `DownloadError`;
  - `ModelSetupStatus.kt`, con `SetupRowAction`;
  - `SetupStep.kt`, con `SetupStepResolver`, `ContinueBlocker` y `StorageStatus`.
- [X] T012 [P] Interfaces de dominio en `…/microrag/domain/onboarding/`: `OnboardingStore.kt`, `DeviceResources.kt` y `ModelDownloadController.kt` (contracts/download-api.md § Dominio). También `PreparationState` en `ModelSetupStatus.kt` o en un fichero propio.
- [X] T013 `…/microrag/domain/onboarding/ModelSetupStatusResolver.kt`: función pura según data-model §2. Hace pasar T005 y T006, junto con `SetupStepResolver` de T011.
- [X] T014 [P] `…/microrag/data/models/AppDirs.kt` (`models/`, `models/downloads/`, `documents/` e `index/` bajo `noBackupFilesDir`, data-model §10) y `VerificationRecordStore.kt` (kotlinx-serialization, `tmp` + `rename`). Hace pasar T007.
- [X] T015 Ampliar `ModelFileRepository` y `DefaultModelFileRepository` con `adopt`, `installFromStream`, `markPrepared`, `preparedBackend` y el chequeo con registro en `refresh` (data-model §4 y §7).
  - `installVerified` y `verify` también escriben el registro.
  - `refresh` limpia solo los `.part` de `models/`.

  Hace pasar T008 y mantiene en verde los tests de F02 (`DevModelImporterTest`, suite de diagnóstico).
- [X] T016 [P] `…/microrag/data/onboarding/DataStoreOnboardingStore.kt`, con `PreferenceDataStoreFactory` en `noBackupFilesDir/datastore/onboarding.preferences_pb` (research R6). Hace pasar T009.
- [X] T017 [P] `…/microrag/data/device/AndroidDeviceResources.kt`:
  - `totalRamBytes`, de `ActivityManager.MemoryInfo`;
  - `freeBytes`, de `StatFs(noBackupFilesDir)`;
  - `currentNetwork`;
  - `hasInternet`, un `callbackFlow` con `ConnectivityManager.registerDefaultNetworkCallback`.

  No importa APIs de red (T010).
- [X] T018 DI: en `…/microrag/data/di/DataModule.kt`, registrar `AppDirs`, `VerificationRecordStore`, `OnboardingStore` y `DeviceResources`, y pasar el registro a `DefaultModelFileRepository`. Actualizar `…/test/core/di/AppModulesTest.kt` (`verify()` con los `extraTypes` necesarios) y comprobarlo en verde.

**Checkpoint**: `./gradlew :app:testDebugUnitTest :app:lintDebug` en verde. F02 sigue funcionando: diagnóstico en el emulador sin modelos.

---

## Phase 3: User Story 1 — Bienvenida y entrada al primer uso (Priority: P1) 🎯 MVP

**Goal**: la app arranca en la Bienvenida, en la Preparación o en Inicio según la etapa guardada. La Bienvenida es fiel al PNG 01.

**Independent Test**:
1. Instalación limpia: aparece la Bienvenida.
2. «Comenzar»: aparece la Preparación (todavía marcador si US2 no está hecha).
3. Relanzar: aparece la Preparación sobre la Bienvenida.
4. Con `stage = COMPLETED` forzado por test: aparece Inicio.

### Tests for User Story 1 ⚠️

- [X] T019 [P] [US1] `…/test/domain/onboarding/ResolveStartDestinationUseCaseTest.kt`: `NOT_STARTED` → `Welcome`, `STARTED` → `SetupOverWelcome`, `COMPLETED` → `Home`. Más `StartOnboardingUseCase` → `STARTED`.
- [X] T020 [P] [US1] `…/androidTest/core/navigation/OnboardingNavigationTest.kt`, con un `OnboardingStore` falso:
  - el destino inicial de cada etapa;
  - «Comenzar» → `Setup`;
  - Atrás en `Setup` → `Welcome`;
  - Atrás en `Welcome` cierra la actividad;
  - pila `[Welcome, Setup]` al arrancar en `STARTED`.
- [X] T021 [P] [US1] `…/androidTest/feature/onboarding/WelcomeScreenAccessibilityTest.kt`:
  - ATF en claro y oscuro;
  - la CTA mide ≥ 48 dp;
  - el logo tiene descripción y la ilustración no expone semántica;
  - con escala de fuente 2.0, la CTA es visible y se puede pulsar en una altura compacta (360×640 dp).

### Implementation for User Story 1

- [X] T022 [P] [US1] `ResolveStartDestinationUseCase.kt` y `StartOnboardingUseCase.kt` en `…/microrag/domain/onboarding/`. Hace pasar T019.
- [X] T023 [P] [US1] `…/microrag/feature/onboarding/welcome/WelcomeIllustration.kt`: órbitas, documento y tiles con `ic_file_text`, `ic_sparkle` e `ic_shield_check`, usando solo tokens y `clearAndSetSemantics {}`. Se escala o se oculta según la altura disponible (contracts/onboarding-ui.md).
- [X] T024 [US1] `…/microrag/feature/onboarding/welcome/WelcomeScreen.kt` y `WelcomePreviews.kt`. Composición del PNG 01 con `gradients.welcome`, `LisdataLogo`, el chip sobre oscuro, el H1 en dos colores, los textos, `LisdataGradientButton` y el pie. Contenido desplazable. Previews en claro, oscuro, compacta y fuente 2.0.
- [X] T025 [US1] Arranque:
  - `MainActivity.kt`: splash de la plataforma retenida con `OnPreDrawListener` hasta resolver `StartDestination` (research R10);
  - `MicroRagAppShell` y `MicroRagNavHost`: reciben el destino inicial, `Welcome` usa `WelcomeScreen` y `Setup` sigue como marcador hasta T047;
  - la pila `[Welcome, Setup]` para `STARTED`;
  - `DebugRouting.initialRoute` mantiene la prioridad.

  Hace pasar T020 y T021.

**Checkpoint**: US1 verificable en el emulador. Comparar la Bienvenida con el PNG 01 y anotar las diferencias.

---

## Phase 4: User Story 2 — Descargar los modelos con confirmación previa (Priority: P1)

**Goal**: desde la Preparación, el usuario confirma y los modelos se descargan en segundo plano con progreso real y notificación (UIDT en API ≥ 34, WorkManager en 31–33). Se puede pausar y reanudar.

**Independent Test**: quickstart §3, pasos 3–6. Progreso real en la fila y en la notificación, y pausa y reanudación.

### Tests for User Story 2 ⚠️

- [X] T026 [P] [US2] `…/test/data/download/HostAllowlistTest.kt`:
  - `huggingface.co`, `us.aws.cdn.hf.co` y `cas-bridge.xethub.hf.co` se permiten;
  - `hf.co.evil.com`, `evilhf.co` y `huggingface.co.evil` se rechazan;
  - la comparación ignora mayúsculas.
- [X] T027 [P] [US2] `…/test/data/download/ModelDownloaderTest.kt`, parte básica con MockWebServer, sin TLS y con la lista de hosts inyectada:
  - 200 completo escribe el fichero entero y llama a `onProgress` con bytes crecientes;
  - redirección 302 a un host permitido se sigue; a un host no permitido, `ForbiddenHost`;
  - con `requireHttps = true` y URL `http`, `InsecureScheme`;
  - más de 5 redirecciones, error;
  - las cabeceras enviadas no incluyen identificadores (`User-Agent` fijo y `Accept-Encoding: identity`);
  - 404 y 500 dan `Http(code)`;
  - la cancelación conserva lo escrito y lanza `CancellationException`.
- [X] T028 [P] [US2] `…/test/data/download/ModelDownloadRunnerTest.kt`, parte básica con un descargador falso:
  - el orden es EG2 y después Gemma;
  - los modelos `Verified` se saltan;
  - antes de cada modelo se comprueba el espacio (bytes pendientes + 500 MB), y si falta, `NoSpace(needed)` sin descargar;
  - `adopt` tras completar y `Completed`;
  - `adopt` con `Mismatch` da `Failed(HashMismatch)` y corta la cola;
  - `progress` publica `active`, `bytesDone` y `totalBytes`, y `bytesDone` nunca retrocede dentro de un mismo intento (SC-004);
  - un `IOException` de escritura con espacio insuficiente da `NoSpace(needed)` sin reintentos y conserva el parcial;
  - los logs no contienen URL ni rutas.
- [X] T029 [P] [US2] Tests de casos de uso en `…/test/domain/onboarding/`:
  - `BuildDownloadConfirmationUseCaseTest`: modelos pendientes, total, espacio libre y el que quedará, red y `lowRam` con 7_499_999_999 frente a 7_500_000_000;
  - `StartModelDownloadUseCaseTest`: `NoSpace` sin programar; `Started` con `requestDownload` y `schedule(pendientes)`;
  - `PauseModelDownloadUseCase` y `ResumeModelDownloadUseCase`;
  - `ObserveModelSetupUseCaseTest`: combina los flujos y emite estados coherentes.
- [X] T030 [P] [US2] `…/test/data/download/DefaultModelDownloadControllerTest.kt` (Robolectric):
  - con `@Config(sdk = [35])`: `JobInfo` con `isUserInitiated`, la red con `NET_CAPABILITY_INTERNET` sin exigir red no medida, los bytes estimados, `isPersisted` y el id 3001;
  - con `@Config(sdk = [32])`: trabajo único `model-download` con `KEEP` y `NetworkType.CONNECTED`;
  - `cancel` en ambas rutas;
  - `isScheduledOrRunning`.
- [X] T031 [P] [US2] `…/test/data/download/ModelDownloadWorkerTest.kt` (work-testing y Robolectric con `sdk = 32`):
  - llama a `setForeground` con el tipo `dataSync`;
  - tolera `ForegroundServiceStartNotAllowedException`;
  - con cualquier resultado del runner (`Completed`, `NoSpace`, `Failed` o `Cancelled`) devuelve `success`, sin bucles de reintento (contracts/download-api.md).
- [X] T032 [P] [US2] `…/test/feature/onboarding/setup/SetupViewModelTest.kt`, parte de descarga con casos de uso falsos:
  - tocar una fila `Unconfigured` abre la confirmación;
  - con API ≥ 33 y sin permiso, la confirmación incluye la línea de aviso; «Descargar» emite directamente la petición del permiso del sistema (sin diálogo propio) y la descarga se programa con cualquier resultado;
  - salir de la Preparación (`onCleared`) no cancela ni pausa la descarga (FR-003);
  - notificaciones denegadas muestran el aviso;
  - `Downloading` → «Detener» → `Paused` → tocar → reanudar;
  - `NoSpace` en la confirmación deshabilita «Descargar».
- [X] T033 [P] [US2] `…/androidTest/feature/onboarding/SetupScreenAccessibilityTest.kt`. Por cada estado de fila de data-model §2 y por cada paso, en claro y oscuro:
  - ATF;
  - objetivos de 48 dp;
  - la fila fusionada con nombre, papel y estado verbal;
  - la CTA deshabilitada expone el texto de bloqueo;
  - el chip de estado tiene `liveRegion = Polite` (FR-031);
  - fuente 2.0 sin recortes (recorrer con scroll y filtrar los nodos recortados, como en F02).
- [X] T034 [P] [US2] Actualizar `…/androidTest/ManifestInvariantsTest.kt` a P4' (contracts/platform-invariants.md):
  - la lista cerrada **exacta** de permisos;
  - `ModelDownloadJobService` con `BIND_JOB_SERVICE` y `exported=false`;
  - `DownloadActionReceiver` con `exported=false`;
  - `SystemForegroundService` con `dataSync`;
  - ningún permiso de almacenamiento.

### Implementation for User Story 2

- [X] T035 [P] [US2] `…/microrag/data/download/HostAllowlist.kt`: `huggingface.co` y sufijo `.hf.co`, con comparación por etiquetas. Hace pasar T026.
- [X] T036 [US2] `…/microrag/data/download/ModelDownloader.kt`, parte básica de contracts/download-api.md:
  - `HttpsURLConnection` con redirecciones manuales;
  - lista de hosts y `https`;
  - cabeceras;
  - escritura en *append* con búfer de 256 KiB y `sync` cada 64 MiB;
  - cancelación cooperativa;
  - E/S en `Dispatchers.IO`.

  Hace pasar T027.
- [X] T037 [US2] `…/microrag/data/download/ModelDownloadRunner.kt`, parte básica: cola, espacio, `adopt`, `progress` (≥ 0,5 % o 500 ms), `NoSpace` ante disco lleno y logs sin URL. Hace pasar T028.
- [X] T038 [P] [US2] `…/microrag/data/download/DownloadNotifications.kt`:
  - canal `model_downloads` con importancia baja;
  - notificación en curso con progreso, «Detener» (`PendingIntent` inmutable a `DownloadActionReceiver`) y toque que abre `MainActivity`;
  - limitación a 1 actualización por segundo.
- [X] T039 [US2] `…/microrag/data/download/ModelDownloadJobService.kt` (API ≥ 34):
  - `setNotification` con `JOB_END_NOTIFICATION_POLICY_REMOVE`;
  - scope propio;
  - `runQueue` y `jobFinished`;
  - `onStopJob` cancela y devuelve `true`, salvo con `STOP_REASON_CANCELLED_BY_APP`.

  Y `ModelDownloadWorker.kt` (API 31–33): `setForeground(dataSync)` tolerante y `runQueue`. Hace pasar T031.
- [X] T040 [US2] `…/microrag/data/download/DefaultModelDownloadController.kt` (UIDT o WorkManager según `SDK_INT`) y `DownloadActionReceiver.kt` (pausa con `goAsync`). Hace pasar T030.
- [X] T041 [US2] `app/src/main/AndroidManifest.xml`:
  - permisos de P4';
  - `ModelDownloadJobService`;
  - `DownloadActionReceiver`;
  - `SystemForegroundService` con `tools:node="merge"` y `foregroundServiceType="dataSync"`;
  - comentarios con la referencia a la constitución I y a D-011.

  Hace pasar T034. Comprobar el manifiesto fusionado (`processDebugMainManifest`) y que el lint sigue en verde.
- [X] T042 [P] [US2] Casos de uso en `…/microrag/domain/onboarding/`: `ObserveModelSetupUseCase`, `BuildDownloadConfirmationUseCase`, `StartModelDownloadUseCase`, `PauseModelDownloadUseCase` y `ResumeModelDownloadUseCase`. Hace pasar T029.
- [X] T043 [US2] DI: en `DataModule`, registrar `ModelDownloader`, `ModelDownloadRunner` (single de proceso, compartido entre el job y la UI) y `ModelDownloadController`. Casos de uso con `factoryOf`. Actualizar `AppModulesTest`.
- [X] T044 [US2] **Verificación temprana (plan, riesgo 1).** Crear la anotación `…/androidTest/data/download/RequiresNetwork.kt` y `…/androidTest/data/download/UidtDownloadSmokeTest.kt` (`@LargeTest @RequiresNetwork`).
  - El test usa el grafo real de Koin: `ModelDownloadController.schedule` y espera, con un tiempo límite de 10 min, a que EmbeddingGemma 2 (165 MB) quede `Verified` en `ModelFileRepository`.
  - Se ejecuta **solo en el emulador API 37**, bajo demanda (quickstart §2). Queda excluido de las ejecuciones habituales y del teléfono.
  - Anotar en research R3: tiempo, notificación y comportamiento de la cancelación.
  - El teléfono se valida después con la UI (T053 y T060).

  Si UIDT no funciona como está documentado, **parar** y replantear con el usuario.
- [X] T045 [P] [US2] `…/microrag/feature/onboarding/setup/SetupUiState.kt` y `SetupTexts.kt` (estado → textos, detalle, `StatusKind`, descripción accesible y bloqueo, según contracts/onboarding-ui.md). Ampliar `LisdataConfirmationDialog` en `…/microrag/core/designsystem/component/Dialogs.kt` con un slot de contenido, con su preview, sin romper los usos actuales.
- [X] T046 [US2] `…/microrag/feature/onboarding/setup/SetupViewModel.kt`, parte de descarga: estado combinado, confirmación, permiso de notificaciones, pausa y reanudación, y `onResumeVisible` (lo completan US3 y US4). Hace pasar T032.
- [X] T047 [US2] `SetupScreen.kt`, `DownloadConfirmationDialog.kt` y `SetupPreviews.kt` en `…/microrag/feature/onboarding/setup/`:
  - composición del PNG 02 (filas en el orden Gemma 4 y EmbeddingGemma 2, tarjeta de almacenamiento, tarjetas de información, aviso de notificaciones, texto de bloqueo y CTA);
  - `rememberLauncherForActivityResult(RequestPermission)`;
  - previews de cada estado de fila y de cada paso, en claro y oscuro.

  Conectar `Setup` en `MicroRagNavHost` y `SetupViewModel` en `FeatureModule`. Hace pasar T033.

**Checkpoint**: quickstart §3, pasos 1–5, en el emulador. EmbeddingGemma 2 llega a «Verificando» (US3 añade «Preparando» y «Listo»).

---

## Phase 5: User Story 3 — Nunca «Listo» sin verificación real (Priority: P1)

**Goal**: tras la huella correcta, la prueba de compatibilidad decide entre «Listo» (GPU o CPU) e «Incompatible». Un fichero con la huella incorrecta acaba en «Error» con «Reintentar».

**Independent Test**: quickstart §4, escenarios US2 (hasta «Listo») y US3-3 (huella incorrecta).

### Tests for User Story 3 ⚠️

- [X] T048 [P] [US3] `…/test/rag/runtime/ModelCompatibilityCheckerTest.kt`, con `EngineFactory` y motores falsos:
  - metadatos sin el backend requerido dan `Incompatible`;
  - EmbeddingGemma: `rawDimensions` 768 → 256 con norma 1 da `Compatible(CPU)`; otra dimensión o una excepción dan `Incompatible`;
  - Gemma: GPU correcta da `Compatible(GPU)`; GPU con excepción o 0 tokens y CPU correcta dan `Compatible(CPU)`; las dos fallando dan `Incompatible`;
  - siempre se libera el motor;
  - nunca hay dos motores en GPU (con el `EngineCoordinator` real).
- [X] T049 [P] [US3] `…/test/domain/onboarding/PrepareModelsUseCaseTest.kt`:
  - solo prepara los `Verified` sin backend, de uno en uno y primero EmbeddingGemma;
  - con `Compatible`, `markPrepared`;
  - con `Incompatible`, `PreparationState.Incompatible` y nunca `Ready`;
  - una prueba en curso no se duplica si se llama dos veces;
  - la cancelación no deja ningún backend registrado.
- [X] T050 [P] [US3] `…/test/domain/onboarding/RetryModelUseCaseTest.kt`: `Failed` vuelve a programar desde cero (borra el parcial si el fallo fue de huella); `Incompatible` abre la confirmación. Ampliar `SetupViewModelTest` con la secuencia «Verificando» → «Preparando» → «Listo» y con «Error» → «Reintentar».

### Implementation for User Story 3

- [X] T051 [US3] `…/microrag/rag/runtime/ModelCompatibilityChecker.kt` (research R8). Usa `withEngine` de `EngineCoordinator`, `GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 8, greedy = true)` y un prompt fijo y corto, y libera al terminar. Registrarlo en `RuntimeModule`. Hace pasar T048. `LayeringTest` sigue en verde.
- [X] T052 [US3] `PrepareModelsUseCase.kt` (scope de aplicación inyectado, exclusión mutua y `PreparationState` como `StateFlow`) y `RetryModelUseCase.kt`. Integrarlos en `SetupViewModel.onResumeVisible` y en la acción de fila. Hace pasar T049 y T050.
- [ ] T053 [US3] Validar en el teléfono (quickstart §4):
  - verificación de los ficheros de F02 existentes sin descargar: «Verificando» → «Preparando» → «Listo (GPU)»;
  - tiempo de la prueba de Gemma (SC-010: < 30 s);
  - init posterior con caché, desde el diagnóstico de F02 (≈ 5,4 s);
  - escenario US3-3: 1 byte alterado → «Error» → «Reintentar».

  Anotar las cifras para el informe.

**Checkpoint**: «Listo» solo aparece tras huella y prueba (T005 y T048 en verde, y validación en el teléfono).

---

## Phase 6: User Story 4 — Descarga robusta: reanudar sin volver a empezar (Priority: P1)

**Goal**: reanudación con `Range` tras la muerte del proceso, la pérdida de red o un reinicio. Reintentos con espera creciente y reconciliación al abrir la app.

**Independent Test**: quickstart §4, escenarios US4-1, Stop del Administrador de tareas, US4-3 y US4-2.

### Tests for User Story 4 ⚠️

- [X] T054 [P] [US4] Ampliar `ModelDownloaderTest` (contracts/download-api.md, tabla de protocolo):
  - con parcial de n bytes, envía `Range: bytes=n-`;
  - 206 correcto: añade solo lo que falta y el total es exacto;
  - 206 con otro inicio o total: trunca y repite desde 0 una vez (`RangeMismatch`);
  - 200 con parcial: trunca y escribe desde 0;
  - 416 con el parcial completo: `Complete`; con el parcial incompleto: truncado;
  - lectura corta: `Network`;
  - total transferido ≤ tamaño × 1,05 en una reanudación al 50 % (SC-002).
- [X] T055 [P] [US4] Ampliar `ModelDownloadRunnerTest`, en tiempo virtual:
  - 5 fallos seguidos con esperas de 2, 4, 8, 16 y 32 s → `Failed`;
  - un intento que avanza ≥ 1 MiB pone el contador a cero;
  - la cancelación da `Cancelled` y conserva el parcial.
- [X] T056 [P] [US4] `…/test/domain/onboarding/ReconcileDownloadsUseCaseTest.kt`:
  - pedidos, sin pausa y sin job → `schedule`;
  - en pausa → no programa;
  - con job en curso → no duplica;
  - ficheros completos sin registro → `verify`;
  - parciales de modelos fuera del catálogo → se borran;
  - último resultado `Failed` → no reprograma hasta «Reintentar»;
  - `NoSpace` → reprograma solo si ya hay espacio suficiente.

  Ampliar `ObserveModelSetupUseCaseTest`: sin red y con un pedido → `WaitingForNetwork`.

### Implementation for User Story 4

- [X] T057 [US4] Completar `ModelDownloader` con `Range`, 206, 200, 416, truncado, `RangeMismatch` y la comprobación de lectura completa. Hace pasar T054.
- [X] T058 [US4] Completar `ModelDownloadRunner` con reintentos y espera exponencial (inyectando `delay`) y el reinicio del contador. Comprobar que el `onStopJob` de T039 y la reprogramación de WorkManager tras `isStopped` reanudan con lo descargado. Hace pasar T055.
- [X] T059 [US4] `ReconcileDownloadsUseCase.kt`, integrado en `SetupViewModel.onResumeVisible` antes de `PrepareModelsUseCase`. Hace pasar T056.
- [ ] T060 [US4] Validar en el teléfono (quickstart §4):
  - muerte del proceso al ≈ 50 % de Gemma (SC-002, con los bytes transferidos);
  - Stop del Administrador de tareas;
  - modo avión 60 s (SC-003, con el tiempo de reanudación);
  - reinicio (US4-2; si UIDT no reanuda solo, documentarlo en research R13 y en el informe);
  - pausa y reanudación desde la notificación.

  Anotar las cifras.

**Checkpoint**: descarga completa de los ≈ 2,75 GB en el teléfono con al menos una interrupción de cada tipo.

---

## Phase 7: User Story 5 — Almacenamiento listo y entrada a Inicio (Priority: P2)

**Goal**: el paso 3 habilita «Continuar». Pulsarlo completa el onboarding y abre Inicio sin pila. «Sin espacio» ofrece liberar espacio.

**Independent Test**: quickstart §4, escenario US5, y el escenario de espacio en el emulador.

### Tests for User Story 5 ⚠️

- [X] T061 [P] [US5] `…/test/domain/onboarding/CompleteOnboardingUseCaseTest.kt`: solo con los dos `Ready` → `COMPLETED`; en otro caso no cambia nada. Ampliar `SetupViewModelTest`:
  - `StorageStatus` (`Offline`, `NoSpace` con la cantidad necesaria);
  - `OpenStorageSettings` para `NoSpace`;
  - «Continuar» emite el evento de navegación.
- [X] T062 [P] [US5] Ampliar `OnboardingNavigationTest`: «Continuar» → `Home` con la pila `[Home]`, Atrás cierra, y relanzar con `COMPLETED` abre `Home`.

### Implementation for User Story 5

- [X] T063 [US5] `CompleteOnboardingUseCase.kt` y el estado de almacenamiento en `SetupViewModel`:
  - crea las carpetas privadas `noBackupFilesDir/models/`, `noBackupFilesDir/documents/` y `noBackupFilesDir/index/` (`AppDirs`, data-model §10; F04 y F06 usarán esas rutas);
  - calcula el espacio;
  - abre `Settings.ACTION_INTERNAL_STORAGE_SETTINGS`.

  En `SetupScreen`: navegación `navigate(Home) { popUpTo<Welcome> { inclusive = true } }`. Hace pasar T061 y T062.

**Checkpoint**: el flujo completo Bienvenida → Preparación → Inicio funciona en el emulador y en el teléfono.

---

## Phase 8: User Story 6 — Importar un modelo desde el dispositivo (Priority: P3)

**Goal**: «Seleccionar archivo del dispositivo» importa con el selector del sistema, con la misma verificación.

**Independent Test**: quickstart §4, escenario US6.

### Tests for User Story 6 ⚠️

- [X] T064 [P] [US6] `…/test/domain/onboarding/ImportModelFileUseCaseTest.kt`, con un `ContentResolver` falso o un `InputStream` inyectado:
  - el fichero correcto da `Verified` y se prepara después;
  - otro fichero da `Incompatible` («El archivo no corresponde a …») y nada queda en `models/`;
  - el progreso `Importing` sale de los bytes;
  - la cancelación no deja `.part`.

  - un documento con tamaño distinto del catálogo da `Incompatible` sin copiar.

  Ampliar `SetupViewModelTest`: `onFilePicked` importa el modelo de la fila que abrió la confirmación.

### Implementation for User Story 6

- [X] T065 [US6] `ImportModelFileUseCase.kt` (scope de aplicación y `Importing` en el `PreparationState` o en un `StateFlow` propio, combinado por `ObserveModelSetupUseCase`). En el diálogo: «Seleccionar archivo del dispositivo» lanza `rememberLauncherForActivityResult(OpenDocument)` con `arrayOf("*/*")` para el modelo de la fila tocada, sin selector adicional. Hace pasar T064.
- [ ] T066 [US6] Validar en el teléfono el escenario US6: importación correcta y fichero ajeno (quickstart §4).

---

## Phase 9: Polish, validación completa y cierre

- [X] T067 [P] `…/androidTest/NoTrafficInstrumentedTest.kt` (research R9, SC-008): recorrer Bienvenida, Preparación con todos los modelos «Listo» (fakes) e Inicio. Los bytes de `TrafficStats` del UID no cambian.
- [X] T068 [P] Revisar que todas las cadenas tienen traducción en `values-en` (lint `MissingTranslation` en verde) y que no queda ningún texto fijo en el código de las pantallas.
- [ ] T069 Puertas completas (quickstart §1 y §2):
  - `assembleDebug`, `assembleRelease`, `lintDebug`, `lintRelease` y `testDebugUnitTest`;
  - `connectedDebugAndroidTest` en el emulador;
  - `am instrument` en el teléfono sin `@RequiresModels`.

  Anotar los recuentos.
- [ ] T070 Validación de extremo a extremo en el teléfono (quickstart §4) partiendo de cero: descarga real de ≈ 2,75 GB con Wi-Fi, prueba de compatibilidad y entrada a Inicio. Medir:
  - duración de la descarga;
  - duración de la verificación de cada modelo;
  - prueba de Gemma (SC-010);
  - arranque en frío con el onboarding completado (SC-007, mediana de 5);
  - recorrido en modo avión (SC-008);
  - toques de la app desde la instalación hasta Inicio, sin contar los diálogos del sistema (SC-005: ≤ 4);
  - relanzar en cada paso y comprobar que se recupera el mismo paso y los mismos estados (SC-006).
- [ ] T071 Recorrido manual con TalkBack y fuente al 200 % en el teléfono (quickstart §6). Anotar los hallazgos y corregir lo que bloquee.
- [X] T072 [P] Comprobaciones de release (quickstart §7) en `specs/003-models-onboarding/checklists/release-verification.md`:
  - permisos exactos;
  - componentes;
  - sin diagnóstico en release;
  - tamaño del APK frente a F02 (54,3 MB) y desglose de lo que añaden WorkManager y DataStore.
- [ ] T073 Diferencias frente a los PNG 01 y 02 (capturas del emulador y del teléfono, claro y oscuro). Informe de cierre en `specs/003-models-onboarding/report.md`: resumen, puertas, diferencias, cifras medidas, desviaciones y riesgos verificados (R13-1 reinicio).
- [ ] T074 Actualizar `docs/roadmap/FEATURES.md` (F03 ✅ implementada, pendiente de revisión), `docs/roadmap/INVENTARIO.md` (P01, P02, G02, G09, G10, G30 y G31) y `docs/roadmap/DECISIONS.md` (cualquier decisión nueva de la implementación). Dejar el teléfono como estaba: los modelos de F02 siguen presentes y los ajustes de pantalla, restaurados.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias.
- **Foundational (fase 2)**: depende de la fase 1. **Bloquea todas las historias.**
- **US1 (fase 3)**: depende de la fase 2.
- **US2 (fase 4)**: depende de la fase 2. Usa la navegación de US1 para llegar a `Setup` (T025 antes de T047).
- **US3 (fase 5)**: depende de US2 (el estado de la Preparación y `onResumeVisible`) y de `adopt` (fase 2).
- **US4 (fase 6)**: depende de US2 (descargador, runner y planificadores).
- **US5 (fase 7)**: depende de US3, porque necesita modelos «Listo».
- **US6 (fase 8)**: depende de US3 (preparación tras importar).
- **Polish (fase 9)**: depende de todas.

### User Story Dependencies

- **US1** es independiente: con la Preparación como marcador, ya se puede probar.
- **US2 → US3 → US5** forman el camino principal hacia Inicio.
- **US4** completa US2 y se puede hacer en paralelo con US3: el descargador y el runner son ficheros distintos de los de compatibilidad.
- **US6** solo necesita US3.

### Parallel Opportunities

- **Fase 2:** T004–T010 (tests) en paralelo. Después T011, T012, T014, T016 y T017 en paralelo. T013, T015 y T018 van en secuencia.
- **US1:** T019, T020 y T021 en paralelo. Después T022 y T023 en paralelo.
- **US2:** T026–T034 (tests) en paralelo. T035, T038, T042 y T045 en paralelo.
- **US3 ∥ US4:** tras US2, T048–T050 y T054–T056 en paralelo.

---

## Parallel Example: Phase 2

```text
T004 ModelCatalogTest (+)            T005 ModelSetupStatusResolverTest
T006 SetupStepResolverTest           T007 VerificationRecordStoreTest
T008 DefaultModelFileRepositoryTest  T009 DataStoreOnboardingStoreTest
T010 NetworkConfinementTest
→ T011 domain/model   T012 interfaces   T014 AppDirs + registro   T016 DataStore   T017 DeviceResources
→ T013 resolver → T015 repositorio → T018 DI
```

---

## Implementation Strategy

### MVP primero (US1)

1. Fases 1 y 2.
2. US1: la app ya arranca en la Bienvenida y recuerda la etapa.
3. Validar contra el PNG 01.

### Entrega incremental

1. **US2:** descarga con progreso. Antes, la verificación temprana de UIDT (T044).
2. **US3:** «Listo» real, con la prueba de compatibilidad en el teléfono.
3. **US4:** robustez, con las pruebas de interrupción en el teléfono.
4. **US5:** entrada a Inicio. El onboarding ya es completo.
5. **US6:** importación.
6. **Polish:** puertas, validación completa, informe y estado del roadmap.

## Notes

- **Modelos del teléfono:** los 5 ficheros de F02 están en `no_backup/models`, y hay copia en `$HOME/MicroRAG-models`. Las pruebas que borran ficheros los restauran al terminar (T074).
- **Logs:** nada de URL firmadas, rutas de usuario ni contenido. Solo `ModelId`, códigos y bytes, con la etiqueta `MicroRAG`.
- **AVD API 33:** la validación de la ruta WorkManager en un dispositivo requiere instalar `system-images;android-33;google_apis;arm64-v8a`. Solo con aprobación del usuario; si no, queda cubierta por los tests JVM (T030 y T031).
