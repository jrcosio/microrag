# Implementation Plan: Fundaciones y design system (F01)

**Branch**: `001-foundations-design-system` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-foundations-design-system/spec.md`

## Summary

F01 deja la base de MicroRAG compilable y verificable.

**Configuración de proyecto conforme a la constitución:**
- minSdk 31;
- sin backup ni transferencia entre dispositivos;
- sin permisos de red;
- ABI arm64;
- logs solo en debug.

**Estructura y servicios base:**
- un único módulo con paquetes por capa;
- Koin con un módulo por capa y verificación del grafo;
- dispatchers dedicados de un solo hilo.

**Navegación:** Navigation Compose con rutas tipadas para los 10 destinos lógicos, barra inferior de 4 pestañas con reglas de pila estrictas y marcadores «Pendiente · Fxx».

**Design system LISDATA completo**, con tokens claro y oscuro medidos en los PNG y ajustados por contraste (D-015, D-019):
- Google Sans variable;
- 29 iconos Lucide generados como vector drawables;
- logo oficial;
- ~35 componentes `Lisdata*` con previews.

**Herramientas solo de debug:** catálogo y menú de rutas, en el source set `debug`.

Todo se verifica con tests JVM (contraste, conformidad, DI, navegación) e instrumentados (accesibilidad ATF e invariantes del manifiesto).

## Technical Context

**Language/Version**: Kotlin 2.4.21 (K2), Java 17 como bytecode target y JDK 21 (JBR de Android Studio) para compilar (research R1).

**Primary Dependencies** (versiones verificadas el 2026-10-25, research R1):
- Build: AGP 9.4.1, Gradle 9.5.0.
- UI: Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0), activity-compose 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1.
- Navegación: navigation-compose 2.10.2 con kotlinx-serialization 1.12.0.
- Concurrencia y DI: kotlinx-coroutines 1.11.0, Koin 4.2.2.
- Recursos: Lucide 1.61.0 (iconos, ISC), Google Sans variable (OFL).

**Storage**: N/A. F01 no persiste nada; el estado de navegación usa `SavedState`.

**Testing**:
- JUnit 4.13.2 y Truth 1.4.5.
- Robolectric 4.17 + Compose ui-test + navigation-testing para la navegación en la JVM.
- koin-test-junit4 4.2.2 (`verify()`).
- Compose `ui-test-junit4-accessibility` (ATF) y androidx.test (runner 1.7.0, ext-junit 1.3.0) para los tests instrumentados en el emulador `Pixel_10` (API 37, arm64).

**Target Platform**: Android 12+ (API 31–36). Dispositivos ARM64; x86_64 solo para el emulador en debug.

**Project Type**: mobile-app (Android nativa, un solo módulo `:app`, D-009).

**Performance Goals**: primera vista en menos de 1 s desde arranque en frío (SC-009); listas y catálogo fluidos.

**Constraints**:
- Ninguna dependencia de red (constitución I).
- Cero hex, `.sp` o textos literales fuera de `core/designsystem` (SC-008).
- WCAG AA en todos los pares de tokens (SC-002).
- Catálogo y menú de rutas ausentes en release (FR-011, FR-024).

**Scale/Scope**:
- 10 destinos;
- ~35 componentes con previews;
- 29 iconos;
- 2 temas;
- 2 idiomas de recursos (es por defecto, en).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F01 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin permisos de red (P4) y `allowBackup=false` con D2D excluido (P2, P3). `AppLogger` solo en debug y con prohibición de registrar contenido. Sin deep links (P5). No incorpora SDKs cloud ni de telemetría | ✅ |
| II. Stack y arquitectura | Kotlin, Compose y M3 tematizado solo con tokens (R14). MVVM por capas en un módulo (D-009). Coroutines y Koin. Navigation Compose (R2). Versiones verificadas y registradas (R1) | ✅ |
| III. RAG según el informe | No aplica en F01 (sin RAG); solo prepara los dispatchers `database`, `llm` y `embeddings` | ✅ N/A |
| IV. Rendimiento y recursos | Dispatchers de un solo hilo con nombre (R13). Nada pesado en Main (no hay trabajo pesado en F01). SC-009 < 1 s | ✅ |
| V. Veracidad del estado | `ProgressValue` obliga a modo indeterminado sin total. Los datos de ejemplo solo están en previews y en el catálogo de debug. Los marcadores no fingen contenido | ✅ |
| VI. Calidad y pruebas | Tests JVM (contraste, conformidad, DI, dispatchers, navegación) e instrumentados (ATF, manifiesto). Build, lint y tests en verde como criterio de cierre | ✅ |
| VII. UI fiel y accesible | Tokens desde los PNG con ajustes AA aprobados (D-015, D-019). Controles M3 tematizados (D-018), con diferencias documentadas. 48 dp, TalkBack, fuente al 200 % y strings es/en | ✅ |
| VIII. Idioma y proceso | Documentación en español y código en inglés. Rama `001-…`, paradas tras clarify y analyze, DECISIONS actualizado (D-014 a D-019) | ✅ |

Resultado: **PASS**, sin violaciones. Se reevaluó tras la fase 1 (data-model y contratos) y sigue en **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/001-foundations-design-system/
├── spec.md
├── plan.md              # este fichero
├── research.md          # R1–R18
├── data-model.md        # tokens, destinos, modelos de UI, servicios base
├── quickstart.md        # validación de punta a punta
├── contracts/
│   ├── navigation.md
│   ├── design-system-api.md
│   ├── platform-invariants.md
│   └── base-services.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
settings.gradle.kts                      # rootProject.name = "MicroRAG"
gradle/libs.versions.toml                # versiones de research R1
build.gradle.kts                         # plugins: android.application, kotlin.compose, kotlin.serialization
app/build.gradle.kts                     # minSdk 31, Java 17, abiFilters, buildConfig, lint, deps
app/src/main/AndroidManifest.xml         # MicroRagApplication, allowBackup=false, dataExtractionRules
app/src/main/res/
├── drawable/ic_*.xml                    # 29 iconos Lucide (generados)
├── drawable-nodpi/logo_lisdata.png      # logo oficial
├── font/google_sans_variable.ttf
├── values/{strings,themes,colors}.xml   # es (por defecto)
├── values-en/strings.xml
├── values-night/themes.xml
└── xml/data_extraction_rules.xml
app/src/main/java/com/lisdatasolutions/microrag/
├── MicroRagApplication.kt               # startKoin
├── MainActivity.kt                      # edge-to-edge, LisdataTheme, MicroRagAppShell, DebugRouting
├── core/
│   ├── di/CoreModule.kt, AppModules.kt
│   ├── dispatchers/AppDispatchers.kt
│   ├── logging/AppLogger.kt
│   ├── navigation/                      # Destinations.kt, TopLevelDestination.kt, MicroRagNavHost.kt,
│   │                                    # MicroRagAppShell.kt, NavigationActions.kt, PendingDestination.kt
│   └── designsystem/
│       ├── theme/                       # LisdataColors.kt, LisdataGradients.kt, LisdataTypography.kt,
│       │                                # LisdataSpacing.kt, LisdataShapes.kt, LisdataTheme.kt, Contrast.kt
│       ├── icon/LisdataIcons.kt, LisdataIcon.kt, LisdataLogo.kt
│       ├── model/                       # StatusKind, ProgressValue, StageStatus, IconTileStyle, CitationUi, AnswerState
│       ├── component/                   # un fichero por grupo (Buttons.kt, Cards.kt, Chips.kt, Rows.kt,
│       │                                # Progress.kt, Chat.kt, Inputs.kt, States.kt, Bars.kt, Dialogs.kt)
│       └── preview/                     # LisdataPreviews.kt (multipreview) + *Previews.kt por grupo
├── domain/di/DomainModule.kt            # vacío (punto de extensión)
├── data/di/DataModule.kt                # vacío
└── feature/di/FeatureModule.kt          # vacío
app/src/debug/
├── AndroidManifest.xml                  # DebugLauncherActivity (MAIN/LAUNCHER «MicroRAG Debug»)
└── java/com/lisdatasolutions/microrag/debug/
    ├── DebugLauncherActivity.kt, DebugRouting.kt
    ├── catalog/ComponentCatalogScreen.kt, CatalogEntries.kt, SampleData.kt
    └── routes/RouteMenuScreen.kt
app/src/release/java/com/lisdatasolutions/microrag/debug/DebugRouting.kt   # stub → null
app/src/test/java/com/lisdatasolutions/microrag/
├── core/designsystem/ContrastTest.kt, DesignSystemConformanceTest.kt
├── core/di/AppModulesTest.kt
├── core/dispatchers/AppDispatchersTest.kt
├── core/logging/AppLoggerTest.kt
└── core/navigation/NavigationTest.kt
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── CatalogAccessibilityTest.kt
└── ManifestInvariantsTest.kt
tools/icons/
├── lucide_to_vector.py                  # convertidor SVG-nodes → VectorDrawable
├── icons.txt                            # mapeo ic_* ← lucide
└── LICENSE-lucide.txt                   # ISC
```

Se eliminan del scaffold:
- `ui/theme/{Color,Theme,Type}.kt` (tema morado);
- `res/values/colors.xml`, salvo lo que use el tema XML;
- `res/xml/backup_rules.xml`;
- `ExampleUnitTest.kt` y `ExampleInstrumentedTest.kt`.

**Structure Decision**: un solo módulo `:app` (D-009), organizado por paquetes `core.*`, `domain`, `data` y `feature`, con source sets `debug`/`release` para separar las herramientas de depuración (R7). La regla «la UI no importa nativo, SDKs de inferencia ni SQLite» se vigila por convención. A partir de F02 se puede ampliar `DesignSystemConformanceTest` con una comprobación de imports por paquete.

## Complexity Tracking

Sin violaciones de la constitución que justificar.

| Decisión con coste | Por qué | Alternativa más simple descartada |
|---|---|---|
| Source sets `debug`/`release` con un *stub* `DebugRouting` | Garantiza en compilación que el catálogo y el menú no van en release (FR-011, FR-024) | `BuildConfig.DEBUG` en ejecución dejaría el código dentro del APK |
| Script propio de conversión de iconos | Hace la generación reproducible y fija la versión de Lucide (R5) | Importar a mano con Vector Asset Studio no es reproducible |
| Subir AGP/Kotlin respecto al scaffold | Versiones estables actuales compatibles con las librerías elegidas (R1); fallback documentado | Quedarse con Kotlin 2.2.10 obligaría a usar versiones antiguas de las librerías |
