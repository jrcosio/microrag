# Implementation Plan: Inicio (F09)

**Branch**: `009-home` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-home/spec.md`

## Summary

F09 sustituye el marcador de Inicio por la pantalla del PNG 03 y retira los accesos provisionales de F06 y F07.

- **Estado de la IA local** (R1): el chip «IA local disponible» solo aparece con los dos modelos verificados y preparados. Usa la regla del onboarding de F03, ahora compartida, y una comprobación que no calcula huellas. En cualquier otro caso, «Configuración pendiente» con «Completar configuración» → `setup`. Sin chip hasta la primera comprobación. Se vuelve a comprobar al volver a primer plano (R5).
- **Accesos con un toque** (R4):
  - «Nuevo chat» abre la pestaña Chat con una conversación nueva;
  - los cuatro accesos llevan a Ingesta, Biblioteca, Índice e Idiomas, y el icono de cabecera a Ajustes;
  - un doble toque abre un solo destino;
  - la Preparación abierta desde Inicio vuelve a Inicio sin duplicarla.
- **Resumen «TU BIBLIOTECA»** (R2): una consulta agregada sobre `documents` (todos sin sombras, y los fragmentos de los indexados; D-046), recalculada con cada cambio del índice. Estados vacío, cargando y error con reintento local.
- **Tarjeta de indexación** (R3): con una tanda activa de F07, entre el héroe y los accesos. Usa las mismas cifras y causas de pausa que el Índice, y abre `index`.
- **Accesibilidad:** frases completas para TalkBack. Accesos en una columna y cifras apiladas con la fuente grande. Claro, oscuro y horizontal.

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios).

**Primary Dependencies**: ninguna nueva (comprobado en `gradle/libs.versions.toml`). Se usan:
- Compose BOM 2026.09.00;
- Lifecycle 2.11.0 (`LifecycleEventEffect`, como en la Preparación);
- Navigation 2.10.2 (rutas tipadas);
- Koin 4.2.2;
- `androidx.sqlite:sqlite-bundled` 2.7.1 (una consulta nueva en `data/rag`).

**Storage**: sin cambios. `noBackupFilesDir/index/rag.db` en el esquema v3 (F08) y el registro de verificación de modelos de F03, solo en lectura.

**Testing**:
- **JVM:**
  - `ObserveLocalAiUseCaseTest` y la regla `allReady` compartida;
  - el resumen en `DefaultLibraryRepositoryTest` con `FakeRagIndex`;
  - `HomeViewModelTest` y `HomeScreenTest` (Robolectric, con gráficos nativos para las medidas);
  - `NavigationTest`;
  - `LayeringTest`.
- **Instrumentadas** (emulador API 37, AVD API 33 y teléfono):
  - `RagIndexSummaryTest` (SQLite real, regla H5);
  - `HomeScreenAccessibilityTest` (todos los estados);
  - `HomeScreenLayoutTest` y `HomeSummaryBenchmarkTest`, solo en el teléfono;
  - ajustes de `SystemBackTest` y `ShellInsetsTest`.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia A063 (API 35). Emulador `Pixel_10` (API 37) y AVD API 33.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- **SC-001:** Inicio visible en menos de 1 s en frío (mediana); el chip y el resumen, en menos de 1 s más.
- **SC-005:** el resumen, con p95 ≤ 50 ms con 1.000 documentos y 50.000 fragmentos.

**Constraints**:
- Sin red (I).
- Índice en `AppDispatchers.database` y ficheros en `io` (II, IV).
- Nunca «disponible» sin comprobación ni cifras de ejemplo (V).
- Sin recalcular huellas (F03 SC-007).
- Logs solo con cifras (I).
- La UI no importa nada de `data/`, SQLite, WorkManager, LiteRT ni `java.io.File` (II).

**Scale/Scope**:
- 1 pantalla nueva (Inicio), que sustituye un marcador.
- 3 casos de uso nuevos y 1 operación nueva de `RagIndex`.
- 2 parámetros nuevos en componentes de F01.
- Cambios puntuales en la navegación (Preparación, accesos provisionales).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F09 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red. Solo lee el índice y el registro de verificación. Logs con estados, cifras y milisegundos (data-model §4) | ✅ |
| II. Stack y arquitectura | Screen → ViewModel → casos de uso → repositorios de F03, F07 y F08 → `RagIndex`. Sin dependencias nuevas. `LayeringTest` cubre `feature/home` | ✅ |
| III. RAG según el informe | No toca la ingesta ni la recuperación. Las cifras respetan las sombras de reindexado (D-044) | ✅ |
| IV. Rendimiento | Inicio aparece sin esperar a nada (FR-025). El resumen es una consulta agregada sin `chunks` (R2). Comprobación de modelos sin huellas (R1). Mediciones en el teléfono (R8) | ✅ |
| V. Veracidad del estado | Sin chip hasta comprobar (H1). La misma regla que el onboarding (H3). Cifras reales, nunca de ejemplo (SC-006). Previews con datos etiquetados | ✅ |
| VI. Calidad y pruebas | Unitarias e instrumentadas de las reglas H1–H8, coherencia con SQLite real, accesibilidad de todos los estados y medidas en el teléfono | ✅ |
| VII. UI fiel y accesible | PNG 03 literal, con dos diferencias anotadas (contracts/home-ui.md). Los estados sin mockup son las propuestas P1–P5, que se aprueban en la parada tras analyze. `Lisdata*`, tokens, `strings.xml` en es y en, 48 dp, TalkBack, fuente al 200 % y horizontal | ✅ (P1–P5 pendientes de aprobación) |
| VIII. Proceso | Documentación en español y código en inglés. D-046 (clarify) y D-047 (plan). Parada tras analyze | ✅ |

**Re-check post-diseño:** sin violaciones.
- **Los dos parámetros nuevos de F01** (`decorationIcon` y `onClick` en `LisdataProgressCard`) son opcionales y no cambian a quien ya los usa.
- **Extraer la regla «listo»** no cambia el comportamiento del onboarding: la cubren sus pruebas de F03 y las nuevas.

## Project Structure

### Documentation (this feature)

```text
specs/009-home/
├── spec.md
├── plan.md                      # este fichero
├── research.md                  # R1–R8
├── data-model.md                # operación del índice, tipos, estado de pantalla, logs
├── quickstart.md
├── contracts/
│   ├── home-api.md              # casos de uso, repositorios y reglas H1–H8; cambios en F01, F03, F06 y F07
│   └── home-ui.md               # PNG 03, propuestas P1–P5, accesibilidad A1–A8, navegación
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/model/
│   ├── LocalAiStatus.kt                              # READY, SETUP_PENDING
│   └── ModelCatalog.kt                               # allReady(states, prepared)
├── domain/onboarding/
│   ├── LocalAiUseCases.kt                            # ObserveLocalAiUseCase, RefreshLocalAiUseCase
│   └── ModelSetupUseCases.kt                         # CompleteOnboardingUseCase → allReady
├── domain/rag/{RagIndex, RagModels}.kt               # librarySummary(), LibrarySummary
├── domain/library/
│   ├── LibraryModels.kt                              # SummaryResult
│   ├── LibraryRepository.kt                          # summary()
│   └── LibraryUseCases.kt                            # ObserveLibrarySummaryUseCase
├── data/rag/DefaultRagIndex.kt                       # librarySummary (R2)
├── data/library/DefaultLibraryRepository.kt          # summary() (R2)
├── domain/di/DomainModule.kt, feature/di/FeatureModule.kt
├── core/designsystem/component/{Cards, Progress}.kt  # decorationIcon, onClick
├── feature/home/
│   ├── HomeScreen.kt                                 # HomeRoute + HomeScreen (sin estado)
│   ├── HomeViewModel.kt, HomeUiState.kt, HomeTexts.kt
│   ├── HomeSummaryCard.kt
│   └── HomePreviews.kt                               # HomePreviewData (etiquetados) y previews
└── core/navigation/
    ├── MicroRagNavHost.kt                            # Inicio real; Setup sin duplicar Inicio; sin accesos provisionales
    ├── NavigationActions.kt                          # openNewChat()
    └── PendingDestination.kt                         # sin el parámetro actions
app/src/main/res/values{,-en}/strings_home.xml
app/src/main/res/values{,-en}/strings_ingest.xml, strings_indexing.xml   # sin los textos provisionales

app/src/test/java/com/lisdatasolutions/microrag/
├── domain/onboarding/ObserveLocalAiUseCaseTest.kt
├── data/library/DefaultLibraryRepositoryTest.kt      # ampliado: resumen
├── feature/home/{HomeViewModelTest, HomeScreenTest}.kt
├── core/navigation/NavigationTest.kt                 # ampliado: Inicio real, Preparación
├── architecture/LayeringTest.kt                      # feature/home
└── testing/FakeRagIndex.kt                           # librarySummary
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/rag/RagIndexSummaryTest.kt
├── data/library/HomeSummaryBenchmarkTest.kt
├── feature/home/{HomeScreenAccessibilityTest, HomeScreenLayoutTest}.kt
├── SystemBackTest.kt                                 # Inicio por su saludo
└── core/navigation/ShellInsetsTest.kt                # ídem
```

**Structure Decision:** módulo único (D-009).
- **`feature/home`:** la pantalla.
- **`domain/onboarding`:** el estado de la IA, porque es el dominio de los modelos de F03.
- **`domain/library` y `data/library`:** el resumen, junto a la Biblioteca, porque comparte su regla de recuento.

## Diseño de ejecución

1. **Abrir Inicio:** `HomeViewModel` se suscribe a tres flujos independientes:
   - `ObserveLocalAiUseCase` (refresh y luego READY o SETUP_PENDING);
   - `ObserveLibrarySummaryUseCase` (open, luego `documentChanges` → `librarySummary`);
   - `ObserveIndexingUseCase`.

   La pantalla pinta enseguida el saludo, el héroe y los accesos. El chip, el resumen y la tarjeta aparecen cuando llega su primer valor.
2. **Volver a primer plano:** `ON_RESUME` → `RefreshLocalAiUseCase`. Si cambian los ficheros, el primer flujo emite.
3. **Cambio en el índice** (importar, indexar, eliminar, quitar, publicar un reindexado): `documentChanges` → nuevo resumen. La tanda emite su estado → la tarjeta aparece, cambia o desaparece.
4. **Error del resumen:** `SummaryState.Error`. «Reintentar» cancela y vuelve a lanzar solo esa suscripción.
5. **Navegar:** cada acción comprueba que Inicio esté `RESUMED` y navega según R4.

## Riesgos y verificaciones tempranas

1. **Coherencia de `chunk_count` con `chunks`** (R2). `RagIndexSummaryTest` se escribe primero, sobre SQLite real, con una sombra a medias y un documento fuera del índice. **Si no coincide en algún estado, se para y se consulta** (la alternativa sería contar `chunks`).
2. **Pruebas que dependen del marcador:** `NavigationTest`, `SystemBackTest`, `ShellInsetsTest` y `OnboardingNavigationTest` se ajustan en el mismo commit que retira el marcador.
3. **Preparación desde Inicio:** el cambio de `onCompleted` se prueba en `NavigationTest` (pila final `[Home]`) y en el recorrido del teléfono.
4. **Texto grande en la rejilla:** `HomeScreenTest` mide con gráficos nativos que, al 200 %, ningún título ni subtítulo se corta (lección de F08).
5. **Propuestas P1–P5 sin aprobar:** las tareas de esos estados van después de la parada tras analyze.
6. **Teléfono:** el estado «Configuración pendiente» se provoca de forma reversible (quickstart §3.3), sin borrar ni volver a descargar los modelos.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
