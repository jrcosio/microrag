# Implementation Plan: Modelos y primer arranque (F03)

**Branch**: `003-models-onboarding` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-models-onboarding/spec.md`

## Summary

F03 sustituye los marcadores de `welcome` y `setup` de F01 por el onboarding real:
- **Bienvenida** (PNG 01) → **Preparación inicial** (PNG 02, una sola pantalla con 3 pasos que reflejan el estado real, D-027) → **Inicio**.
- **Modelos:** la Preparación obtiene los dos modelos elegidos en F02 (Gemma 4 E2B genérico y EmbeddingGemma 2 270M, ≈ 2,75 GB) de dos formas:
  - **descarga reanudable** desde las URL de Hugging Face fijadas a commit;
  - **importación** con el selector del sistema.
- **«Listo»:** un modelo solo lo está cuando:
  1. su SHA-256 coincide con el del catálogo;
  2. ha superado una prueba real de arranque en el dispositivo: Gemma en GPU, con CPU como alternativa, y EmbeddingGemma en CPU.

**Enfoque técnico** (research):
- **Red:** un único paquete con acceso a red, `data.download`. Usa `HttpsURLConnection`, redirecciones manuales limitadas a `huggingface.co` y `*.hf.co`, y reanudación con `Range` sobre un `.download` en `noBackupFilesDir/models/downloads/`.
- **Trabajo largo:** *user-initiated data transfer jobs* en Android 14+ y WorkManager con servicio en primer plano `dataSync` en Android 12–13. Ambas rutas ejecutan el mismo `ModelDownloadRunner`.
- **Verificación:** hash en el sitio y `rename` atómico, sin copiar (`ModelFileRepository.adopt`). Un registro `verification.json` permite un chequeo barato en cada arranque.
- **Prueba de compatibilidad:** `ModelCompatibilityChecker`, en `rag.runtime`, sobre `EngineFactory` y `EngineCoordinator` de F02. Se ejecuta en el proceso de la app con la Preparación visible.
- **Persistencia:** DataStore en `noBackupFilesDir` para la etapa del onboarding y la intención de descarga. La splash de la plataforma se mantiene hasta resolver el destino inicial.

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios).

**Primary Dependencies** (verificadas el 2026-10-26, research R1):
- **Nuevas:**
  - `androidx.work:work-runtime-ktx:2.12.0`;
  - `androidx.datastore:datastore-preferences:1.2.1`.
- **Solo tests:**
  - `androidx.work:work-testing:2.12.0`;
  - `com.squareup.okhttp3:mockwebserver3:5.5.1`.
- **Existentes:** LiteRT-LM 0.18.1 (F02), Compose BOM 2026.09.00, navigation-compose 2.10.2, Koin 4.2.2, kotlinx-serialization 1.12.0 y coroutines 1.11.0.
- **Sin cliente HTTP de terceros:** `HttpsURLConnection` de la plataforma.

**Storage**: contracts/platform-invariants.md § Almacenamiento.
- Modelos y parciales en `noBackupFilesDir/models/`.
- Registro `verification.json`.
- DataStore en `noBackupFilesDir/datastore/`.

**Testing**:
- **JVM (JUnit 4 + Truth):**
  - descargador contra MockWebServer;
  - runner con tiempo virtual;
  - resolución de estados y paso;
  - repositorio (adopt, stream y registro);
  - prueba de compatibilidad con una fábrica falsa;
  - ViewModel con fakes;
  - DataStore en directorio temporal;
  - confinamiento de red (estático).
- **Robolectric:** controlador UIDT/WorkManager, con `sdk = 32` y `sdk = 35`, y worker con `work-testing`.
- **Instrumentados:**
  - invariantes del manifiesto (lista cerrada);
  - accesibilidad (ATF y 48 dp) de la Bienvenida y la Preparación en todos los estados, claro y oscuro;
  - navegación y destino inicial;
  - ausencia de tráfico.
- **Teléfono:** flujo real, reanudación y reinicio (quickstart §4).

**Target Platform**:
- Android, minSdk 31 y targetSdk 37.
- Teléfono de referencia: Nothing Phone (1), API 35 (ruta UIDT).
- Emulador: `Pixel_10`, API 37.
- Ruta WorkManager (API 31–33): solo JVM, salvo aprobación para instalar una AVD API 33.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- Arranque en frío hasta Inicio con el onboarding completado: mediana < 1 s (SC-007). El chequeo de modelos no calcula hashes.
- Prueba de compatibilidad de Gemma: < 30 s, y deja la caché del motor lista: primer init ≈ 5,4 s en lugar de ≈ 16,9 s (SC-010).
- Progreso de la descarga publicado cada ≥ 0,5 % o 500 ms; notificación como mucho 1 vez por segundo.
- Reanudación tras recuperar la red: ≤ 30 s (SC-003).

**Constraints**:
- Red solo en `data.download` (FR-026) y solo hacia hosts de Hugging Face por `https` (FR-011).
- Lista cerrada de permisos (contracts/platform-invariants.md).
- Nunca «Listo» sin huella y prueba (FR-020, FR-021).
- Un único motor en GPU (`EngineCoordinator`).
- Sin hilo principal para E/S ni inferencia.
- Ningún log con URL firmadas, rutas de usuario ni contenido.
- La UI no importa LiteRT-LM, `java.net` ni JobScheduler.

**Scale/Scope**:
- 2 pantallas de producto (P01 y P02), 1 diálogo y 1 notificación.
- 2 modelos (≈ 2,75 GB).
- ≈ 13 estados de fila.
- 2 rutas de planificación.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F03 | Estado |
|---|---|---|
| I. On-device y privacidad | La única red es la descarga de los modelos del catálogo, por `https`, con lista de hosts y SHA-256 antes de cargar. El acceso a red queda confinado en `data.download`, con dos tests (R9). Datos en `noBackupFilesDir`; `allowBackup=false`. Sin telemetría. Peticiones sin identificadores. La importación usa SAF, sin permisos de almacenamiento | ✅ |
| II. Stack y arquitectura | Screen → ViewModel → casos de uso → interfaces de dominio → `data`. Koin y DataStore. Versiones verificadas (R1). La UI no importa APIs de red, JobScheduler ni LiteRT-LM. **Desviación justificada:** UIDT con JobScheduler en API ≥ 34 en lugar de WorkManager, que se mantiene en API 31–33 (Complexity Tracking) | ✅ (justificado) |
| III. RAG según el informe | Sin cambios en prefijos ni dimensiones. La prueba de EmbeddingGemma comprueba 768 → 256 con norma 1. Los ficheros son los de D-023 y D-025 | ✅ |
| IV. Rendimiento y recursos | E/S en `io`; prueba de compatibilidad en las colas `llm`/`embeddings` a través de `EngineCoordinator`, con un solo motor en GPU, liberado al terminar. Aviso con RAM < 7,5 GB. El arranque no calcula hashes | ✅ |
| V. Veracidad del estado | «Listo» exige `Verified` y backend preparado, con test exhaustivo (SC-001). Progreso con bytes reales. «Verificando» con porcentaje real e indeterminado solo en «Preparando». Un fichero con huella incorrecta se borra. Sin datos falsos fuera de previews y tests | ✅ |
| VI. Calidad y pruebas | Tests unitarios de dominio, datos y ViewModel. Instrumentados de manifiesto, accesibilidad, navegación y tráfico. Validación real en el teléfono. Cifras de descarga y de la prueba de Gemma en el informe de cierre | ✅ |
| VII. UI fiel y accesible | Textos de los PNG literales. Estados no maquetados según la propuesta aprobada (D-027, contracts/onboarding-ui.md). Componentes `Lisdata*` y tokens. Accesibilidad: ATF, 48 dp, `liveRegion`, fuente al 200 % y ilustración sin anunciar | ✅ |
| VIII. Idioma y proceso | Documentación en español y código en inglés. Parada tras analyze. D-027 registrada. Push solo tras aprobación | ✅ |

**Re-check post-diseño (Phase 1):** sin violaciones nuevas.
- `ModelCompatibilityChecker` vive en `rag.runtime`, sin LiteRT-LM.
- Los planificadores Android (`JobService`, `Worker`, receptor y notificaciones) viven en `data.download`, detrás de `ModelDownloadController`.
- El ViewModel solo conoce casos de uso.

## Project Structure

### Documentation (this feature)

```text
specs/003-models-onboarding/
├── spec.md
├── plan.md                      # este fichero
├── research.md                  # R1–R13
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── download-api.md          # dominio, descargador, runner, planificadores, repositorio, compatibilidad
│   ├── onboarding-ui.md         # P01, P02, diálogo, notificación, accesibilidad, navegación
│   └── platform-invariants.md   # permisos (lista cerrada), componentes, confinamiento, almacenamiento
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                       # + work 2.12.0, datastore 1.2.1, mockwebserver3 5.5.1
app/build.gradle.kts                            # + dependencias
app/src/main/AndroidManifest.xml                # + permisos (P4'), ModelDownloadJobService, DownloadActionReceiver, SystemForegroundService dataSync

app/src/main/java/com/lisdatasolutions/microrag/
├── MainActivity.kt                             # splash hasta resolver el destino; pasa startDestination al shell
├── core/
│   ├── navigation/{MicroRagAppShell, MicroRagNavHost}.kt   # startDestination; Welcome/Setup reales; Continuar → Home sin pila
│   └── designsystem/component/Dialogs.kt       # + slot de contenido en LisdataConfirmationDialog (+ preview)
├── domain/
│   ├── model/
│   │   ├── ModelCatalog.kt                     # + required, ModelFile.downloadUrl
│   │   ├── ModelSetupStatus.kt                 # data-model §2
│   │   ├── DownloadProgress.kt                 # + DownloadOutcome, DownloadError
│   │   ├── OnboardingStage.kt, NetworkKind.kt, StartDestination.kt
│   │   └── SetupStep.kt                        # SetupStepResolver, ContinueBlocker
│   └── onboarding/
│       ├── OnboardingStore.kt, DeviceResources.kt, ModelDownloadController.kt   # interfaces
│       ├── ModelSetupStatusResolver.kt         # función pura (SC-001)
│       └── *UseCase.kt                         # contracts/download-api.md § Casos de uso
├── data/
│   ├── di/DataModule.kt                        # + store, device, download, registro
│   ├── models/
│   │   ├── DefaultModelFileRepository.kt       # + adopt, installFromStream, markPrepared, registro en refresh
│   │   ├── ModelFileRepository.kt
│   │   ├── AppDirs.kt                          # models/, models/downloads/, documents/, index/
│   │   └── VerificationRecordStore.kt
│   ├── onboarding/DataStoreOnboardingStore.kt
│   ├── device/AndroidDeviceResources.kt        # RAM, StatFs, ConnectivityManager
│   └── download/                               # ÚNICO paquete con APIs de red
│       ├── HostAllowlist.kt
│       ├── ModelDownloader.kt
│       ├── ModelDownloadRunner.kt
│       ├── DefaultModelDownloadController.kt   # UIDT (≥ 34) / WorkManager (31–33)
│       ├── ModelDownloadJobService.kt
│       ├── ModelDownloadWorker.kt
│       ├── DownloadNotifications.kt
│       └── DownloadActionReceiver.kt
├── rag/
│   ├── di/RuntimeModule.kt                     # + ModelCompatibilityChecker
│   └── runtime/ModelCompatibilityChecker.kt
└── feature/
    ├── di/FeatureModule.kt                     # + SetupViewModel
    └── onboarding/
        ├── welcome/{WelcomeScreen, WelcomeIllustration, WelcomePreviews}.kt
        └── setup/{SetupScreen, SetupViewModel, SetupUiState, SetupTexts, DownloadConfirmationDialog, SetupPreviews}.kt

app/src/main/res/values/strings_onboarding.xml      # es
app/src/main/res/values-en/strings_onboarding.xml   # en

app/src/test/java/com/lisdatasolutions/microrag/
├── architecture/NetworkConfinementTest.kt
├── data/download/{HostAllowlistTest, ModelDownloaderTest, ModelDownloadRunnerTest, DefaultModelDownloadControllerTest, ModelDownloadWorkerTest}.kt
├── data/models/{DefaultModelFileRepositoryTest (+), VerificationRecordStoreTest}.kt
├── data/onboarding/DataStoreOnboardingStoreTest.kt
├── domain/model/{ModelCatalogTest (+), SetupStepResolverTest}.kt
├── domain/onboarding/{ModelSetupStatusResolverTest, StartModelDownloadUseCaseTest, ReconcileDownloadsUseCaseTest, PrepareModelsUseCaseTest, ImportModelFileUseCaseTest, ResolveStartDestinationUseCaseTest}.kt
├── rag/runtime/ModelCompatibilityCheckerTest.kt
├── feature/onboarding/setup/SetupViewModelTest.kt
└── core/di/AppModulesTest.kt (+)
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── ManifestInvariantsTest.kt                   # P4' (lista cerrada) + componentes
├── NoTrafficInstrumentedTest.kt
├── core/navigation/OnboardingNavigationTest.kt
└── feature/onboarding/{WelcomeScreenAccessibilityTest, SetupScreenAccessibilityTest}.kt
```

**Structure Decision**: módulo único con paquetes por capa (D-009), igual que F01 y F02.
- Las interfaces que la UI necesita (`OnboardingStore`, `DeviceResources`, `ModelDownloadController`) están en `domain`, y las implementaciones Android en `data`.
- `data.download` agrupa todo lo que toca la red y los planificadores, para que el confinamiento se compruebe por paquete.
- La pantalla de diagnóstico de F02 no cambia: usa el mismo `ModelFileRepository`.

## Diseño de ejecución

1. **Arranque.**
   - `MainActivity` mantiene la splash hasta que `ResolveStartDestinationUseCase` responde y pasa `startDestination` al shell (R10).
   - En `Application` no se programa nada. `EngineMemoryCallbacks` sigue igual.
2. **Preparación visible** (`SetupViewModel.onResumeVisible`). `ReconcileDownloadsUseCase`:
   1. `repository.refresh()`: chequeo barato con el registro;
   2. si hay pedidos sin pausa y sin job, `schedule`;
   3. verificación de los `Unverified` completos.

   Después, `PrepareModelsUseCase`: prueba de compatibilidad de los `Verified` sin backend, de uno en uno, primero EmbeddingGemma.
3. **Confirmar la descarga.**
   1. `BuildDownloadConfirmationUseCase` → diálogo.
   2. «Descargar» → permiso de notificaciones, si toca (FR-010).
   3. `StartModelDownloadUseCase`: espacio, `requestDownload` y `schedule(bytes pendientes)`.
4. **Job** (UIDT o worker).
   1. Notificación.
   2. `runner.runQueue()`: EmbeddingGemma y después Gemma. Por cada modelo, espacio → descarga con reintentos → `adopt` (hash y `rename`).
   3. Con `NoSpace` o `Failed` termina sin reprogramar. Con `Cancelled`, por pausa, no se reprograma. Si el sistema lo detiene (`onStopJob`), se reprograma.
5. **UI.** `ObserveModelSetupUseCase` combina:
   - `repository.states`;
   - `runner.progress`;
   - `PreparationState`;
   - `OnboardingStore`;
   - `DeviceResources.hasInternet`.

   `ModelSetupStatusResolver` lo convierte en `ModelSetupStatus`. `SetupStepResolver` calcula el paso y el bloqueo.
6. **«Continuar».** `CompleteOnboardingUseCase` → `stage = COMPLETED` → `Home`, borrando la pila.

## Riesgos y verificaciones tempranas

Ver research R13. Además:

1. **Primera verificación tras añadir las dependencias:** programar un UIDT desde la app en el emulador API 37 y en el teléfono (API 35) y descargar EmbeddingGemma 2 (165 MB) de extremo a extremo, antes de construir la UI completa. Si UIDT no se comporta como dice la documentación, se para y se replantea.
2. **Prueba de compatibilidad de Gemma con la Preparación visible:** si el usuario sale de la app durante «Preparando», `EngineMemoryCallbacks` libera el motor al terminar la prueba (no a mitad, porque el coordinador no cierra motores en uso). Al volver, si no hay backend registrado, se repite. Se valida en el teléfono.
3. **`refresh()` de F02 borra los `.part` de `models/`:** las descargas van en `models/downloads/` y usan la extensión `.download`. Hay un test que lo comprueba.
4. **Tamaño del APK:** WorkManager trae Room. Se mide y se reporta al cerrar.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `JobScheduler` con `setUserInitiated(true)` (UIDT) en API ≥ 34, en lugar de WorkManager (constitución II: «WorkManager para trabajo largo») | Es la API recomendada por Android para descargas largas que inicia el usuario. Esos jobs no tienen cuotas de App Standby ni el límite de 6 h de `dataSync`, y Android 16 desaconseja los workers largos para este caso. No hay librería Jetpack que soporte UIDT (documentación oficial, research R3) | WorkManager con servicio en primer plano en todas las versiones: choca con las cuotas de Android 16, el límite de `dataSync` de Android 15 y las restricciones de arranque en segundo plano, y en la práctica no reanuda de forma fiable una descarga de 2,6 GB. Se mantiene WorkManager en API 31–33, donde esas restricciones no aplican |
| Dos rutas de planificación (UIDT y WorkManager) | minSdk 31 (D-017), y UIDT solo existe desde API 34 | Subir minSdk a 34 contradice D-017. Usar solo UIDT deja sin descarga a Android 12–13 |
