---

description: "Lista de tareas de F09 — Inicio"
---

# Tasks: Inicio (F09)

**Input**: Documentos de diseño en `/specs/009-home/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-009). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en el orden de la spec:
- US1 — Empezar a preguntar o a gestionar con un toque (P1);
- US2 — Saber si la IA local está lista (P1);
- US3 — Ver de un vistazo cuánto hay en la biblioteca (P1);
- US4 — Seguir una indexación en curso desde Inicio (P2);
- US5 — Un Inicio fiel al PNG y accesible (P2).

**Entorno** (quickstart):
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Dispositivos:
  - teléfono de referencia: `adb -s P31281001066`;
  - emulador `Pixel_10`: `emulator-5554`;
  - AVD `MicroRAG_API33`: `emulator-5556`.

  **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument` (quickstart §2).
- En el teléfono:
  - nunca se borran ni se vuelven a descargar los modelos;
  - «Configuración pendiente» se provoca de forma reversible (quickstart §3.3);
  - los ficheros de prueba van a `Download/MicroRAG-F09` y se borran al final.
- **Cambios ajenos en el árbol** (el icono del usuario): nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**UI pendiente de aprobación**: las propuestas P1–P5 de contracts/home-ui.md se aprueban en la parada tras analyze. Las tareas que las implementan dan por hecha esa aprobación:
- T016: P1 y P2;
- T020: P3 y P4;
- T024: P5.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (cifras del resumen sobre SQLite real)

**Purpose**: comprobar primero que `chunk_count` coincide con los fragmentos guardados (riesgo 1 del plan).

- [X] T001 Crear `…/androidTest/…/data/rag/RagIndexSummaryTest.kt` sobre SQLite real, con estos documentos: pendiente, preparado, indexando a medias (con fragmentos ya guardados), indexado e incluido, indexado y excluido, con error, fuera del índice, y un indexado con una sombra de reindexado a medias. Comprueba la regla H5:
  - `librarySummary().documents == libraryCount("", todos los estados)`;
  - `indexedChunks` == `count(*)` de `chunks` de los documentos `done` sin sombra.

  Se comprueba también tras `publishReindex`, `removeFromIndex` y `deleteDocument`.
- [X] T002 Implementar R2 en el índice:
  - `LibrarySummary` en `…/microrag/domain/rag/RagModels.kt`;
  - `librarySummary()` en `…/microrag/domain/rag/RagIndex.kt` y en `…/microrag/data/rag/DefaultRagIndex.kt` (la consulta de data-model §1, con `withIndex`);
  - la misma regla en `…/test/…/testing/FakeRagIndex.kt`.
- [X] T003 Ejecutar T001 en el emulador API 37 y en el teléfono. **Si la regla H5 falla en algún estado, se para y se consulta** (plan, riesgo 1).

**Checkpoint**: las cifras salen de una consulta sobre `documents` y coinciden con el índice real.

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: los tres flujos que lee Inicio y los dos parámetros nuevos del sistema de diseño.

- [X] T004 [P] Crear `…/test/…/domain/onboarding/ObserveLocalAiUseCaseTest.kt`, con un `ModelFileRepository` falso:
  - no emite nada antes de que termine `refresh()` (H1);
  - `READY` solo con todos los modelos `Verified` y con backend; `Unverified`, `Missing` o sin backend dan `SETUP_PENDING` (H3);
  - tras `RefreshLocalAiUseCase`, un fichero que cambia hace emitir el estado nuevo;
  - nunca llama a `verify` ni a `installVerified` (H2);
  - `ModelCatalog.allReady` da lo mismo que la regla anterior de `CompleteOnboardingUseCase`.
- [X] T005 [P] Ampliar `…/test/…/data/library/DefaultLibraryRepositoryTest.kt` con `summary()`:
  - `Unavailable` con el índice incompatible y ante `IndexUnavailableException`;
  - se recalcula con cada `documentChanges` (importar, indexar, eliminar, quitar del índice, publicar);
  - no cuenta las sombras;
  - emite sin repetir valores iguales.
- [X] T006 Implementar R1:
  - `…/microrag/domain/model/LocalAiStatus.kt`;
  - `ModelCatalog.allReady` en `…/microrag/domain/model/ModelCatalog.kt`;
  - `ObserveLocalAiUseCase` y `RefreshLocalAiUseCase` en `…/microrag/domain/onboarding/LocalAiUseCases.kt`;
  - `CompleteOnboardingUseCase` con `allReady` en `ModelSetupUseCases.kt`;
  - registro en `…/microrag/domain/di/DomainModule.kt`.
- [X] T007 Implementar R2 en el repositorio:
  - `SummaryResult` en `…/microrag/domain/library/LibraryModels.kt`;
  - `summary()` en `LibraryRepository.kt` y en `…/microrag/data/library/DefaultLibraryRepository.kt` (el patrón de `page()`);
  - `ObserveLibrarySummaryUseCase` en `LibraryUseCases.kt` y en `DomainModule.kt`;
  - `summary()` en el falso de la Biblioteca de `…/test/…/testing/LibraryFakes.kt`.
- [X] T008 [P] Ampliar el sistema de diseño (contracts/home-api.md, cambios en F01):
  - `LisdataHeroCard(decorationIcon)`, centrado en las órbitas y oculto a TalkBack, en `…/microrag/core/designsystem/component/Cards.kt`;
  - `LisdataProgressCard(onClick, onClickLabel)` en `Progress.kt`;
  - previews en `CardsPreviews.kt` y `ProgressPreviews.kt`;
  - las entradas del héroe y de la tarjeta de progreso de `app/src/debug/java/com/lisdatasolutions/microrag/debug/catalog/CatalogEntries.kt`, con el icono y el clic nuevos, que `CatalogAccessibilityTest` recorre.

**Checkpoint**: las pruebas T004 y T005 pasan; los componentes nuevos aparecen en el catálogo de depuración.

---

## Phase 3: User Story 1 - Empezar a preguntar o a gestionar con un toque (Priority: P1) 🎯 MVP

**Goal**: Inicio real con cabecera, saludo, héroe y accesos, que llevan a su destino con un toque, sin los accesos provisionales.

**Independent Test**: cada control abre su destino al primer toque y Atrás vuelve a Inicio; Atrás desde Inicio cierra la app; los accesos provisionales ya no existen.

### Tests for User Story 1

- [X] T009 [P] [US1] Crear `…/test/…/feature/home/HomeScreenTest.kt` (Robolectric, `es-w390dp-h844dp`, `HomePreviewData`):
  - los textos del PNG 03: «¿Qué quieres explorar hoy?», «PREGUNTA A TU BIBLIOTECA», «Una respuesta.», «Todas tus fuentes.», «Nuevo chat» y los cuatro accesos con su descripción;
  - el icono de Ajustes con su etiqueta;
  - cada control llama a su acción;
  - no aparece ningún texto «(provisional)».
- [X] T010 [P] [US1] Ampliar `…/test/…/core/navigation/NavigationTest.kt`:
  - sustituir `provisionalHomeEntry…` por pruebas de cada control de Inicio → destino y Atrás → Inicio;
  - «Nuevo chat» → `Chat(conversationId = null)` con la pestaña Chat seleccionada y sin restaurar un Chat anterior;
  - Documentos y el icono de Ajustes → sus pestañas;
  - dos toques seguidos → un solo destino (FR-006);
  - Preparación abierta desde Inicio y completada → la pila queda `[Home]` (R4);
  - el módulo Koin de la prueba añade falsos de `ModelFileRepository`, del resumen de la Biblioteca y de la tanda para crear `HomeViewModel` (análisis, U1).

### Implementation for User Story 1

- [X] T011 [US1] Crear la pantalla en `…/microrag/feature/home/`:
  - `HomeUiState.kt` (data-model §3);
  - `HomeViewModel.kt`, de momento solo con el estado inicial;
  - `HomeScreen.kt` con `HomeRoute` y `HomeScreen` sin estado: cabecera (R6), hueco del chip, saludo, héroe con `decorationIcon` y la rejilla 2×2 con filas de la misma altura;
  - `HomePreviews.kt` con `HomePreviewData` etiquetado;
  - textos en `…/res/values{,-en}/strings_home.xml`;
  - registro en `…/microrag/feature/di/FeatureModule.kt`.
- [X] T012 [US1] Navegación (R4):
  - `openNewChat()` en `…/microrag/core/navigation/NavigationActions.kt`;
  - `Home` → `HomeRoute` en `MicroRagNavHost.kt`, con cada acción solo con la entrada `RESUMED`;
  - `SetupRoute.onCompleted` vuelve a Inicio sin duplicarlo;
  - se retiran los accesos provisionales, el parámetro `actions` de `PendingDestination.kt` y los textos `ingest_provisional_entry` e `index_provisional_entry` (es y en).
- [X] T013 [US1] Actualizar las pruebas instrumentadas que usaban el marcador:
  - `…/androidTest/…/SystemBackTest.kt`: Inicio se reconoce por su saludo;
  - `…/androidTest/…/core/navigation/ShellInsetsTest.kt`: entra en la Ingesta por el acceso Importar;
  - `OnboardingNavigationTest` se revisa.

  Se ejecutan en el emulador API 37.

**Checkpoint**: Inicio real y navegable; las pruebas de US1 pasan.

---

## Phase 4: User Story 2 - Saber si la IA local está lista (Priority: P1)

**Goal**: el chip dice el estado real de la IA y lleva a la Preparación cuando hace falta.

**Independent Test**: con los modelos listos se ve «IA local disponible»; con un fichero alterado, «Configuración pendiente», que abre la Preparación; al volver, «disponible» sin reiniciar.

### Tests for User Story 2

- [X] T014 [P] [US2] Crear `…/test/…/feature/home/HomeViewModelTest.kt` (estado de la IA):
  - `ai == null` hasta la primera emisión;
  - `READY` y `SETUP_PENDING` según el caso de uso;
  - `onResume()` llama a `RefreshLocalAiUseCase` y mantiene el valor anterior hasta que llega otro (R1).
- [X] T015 [P] [US2] Ampliar `HomeScreenTest.kt`:
  - «IA local disponible» con `READY`;
  - «Configuración pendiente» y «Completar configuración» con `SETUP_PENDING`, y el botón llama a su acción;
  - sin chip con `null`, y el saludo no se mueve al llegar el chip (P2);
  - con `SETUP_PENDING`, «Nuevo chat» y los cuatro accesos siguen activos y llaman a su acción (FR-011; análisis, G2).

### Implementation for User Story 2

- [X] T016 [US2] Implementar P1 y P2:
  - `HomeViewModel` sigue `ObserveLocalAiUseCase` y expone `onResume()`;
  - `HomeRoute` con `LifecycleEventEffect(ON_RESUME)` (R5);
  - el chip con su alto reservado y el botón «Completar configuración» → `Setup`;
  - la línea de log `home ai=… in N ms` (data-model §4).
- [X] T017 [US2] Verificar en el teléfono (quickstart §3.2–3.3):
  - el chip correcto tras un arranque en frío;
  - con la fecha del modelo de embeddings cambiada, «Configuración pendiente» → Preparación → «IA local disponible»;
  - la pila sin dos Inicio.

**Checkpoint**: estado de la IA real, sin huellas ni parpadeos.

---

## Phase 5: User Story 3 - Ver de un vistazo cuánto hay en la biblioteca (Priority: P1)

**Goal**: «TU BIBLIOTECA» con cifras reales y reactivas, estado vacío y error con reintento local.

**Independent Test**: las cifras coinciden con la Biblioteca y cambian solas; con 0 documentos se ve el estado vacío; con el índice no disponible, el error con «Reintentar», y los accesos siguen activos.

### Tests for User Story 3

- [X] T018 [P] [US3] Ampliar `HomeViewModelTest.kt` (resumen):
  - `Loading` → `Loaded`, `Empty` con 0 documentos, `Error(unavailable = true)` con `Unavailable`, y `Error(false)` ante una excepción;
  - «Reintentar» solo vuelve a suscribir el resumen;
  - un cambio del índice actualiza las cifras.
- [X] T019 [P] [US3] Ampliar `HomeScreenTest.kt`:
  - «24» «documentos» y «12.400» «fragmentos», con separador de miles;
  - «1 documento» y «1 fragmento»;
  - TalkBack lee «24 documentos y 12.400 fragmentos»;
  - estado vacío con «Importar primer documento» → acción de la Ingesta;
  - esqueleto al cargar;
  - error con «Reintentar» (los dos textos);
  - los accesos siguen activos en todos.

### Implementation for User Story 3

- [X] T020 [US3] Implementar P3 y P4:
  - `HomeSummaryCard.kt`: eyebrow, dos cifras y sus estados;
  - el mapeo de `SummaryResult` y el reintento en `HomeViewModel`;
  - los plurales y `home_summary_description` en `strings_home.xml` (es y en);
  - la línea de log `home summary …` (data-model §4).
- [X] T021 [US3] Crear `…/androidTest/…/data/library/HomeSummaryBenchmarkTest.kt` con los datos de `LibraryBenchmarkTest` (1.000 documentos y 50.000 fragmentos). Mide 50 llamadas tras 5 de calentamiento; p95 ≤ 50 ms (SC-005). Se ejecuta en el teléfono y se anotan las cifras.

**Checkpoint**: resumen real y reactivo; el benchmark cumple el objetivo.

---

## Phase 6: User Story 4 - Seguir una indexación en curso desde Inicio (Priority: P2)

**Goal**: la tarjeta de la tanda activa, con las cifras y la pausa de F07, que abre el Índice.

**Independent Test**: con una tanda activa aparece la tarjeta con las mismas cifras que el Índice; en pausa dice por qué; al tocarla abre el Índice; al terminar desaparece.

### Tests for User Story 4

- [X] T022 [P] [US4] Ampliar `HomeViewModelTest.kt`:
  - `Active` → `IndexingCard(done, total, pause)`;
  - `Idle` (también con documentos con error), `Loading` e `IndexUnavailable` → sin tarjeta (D-046).
- [X] T023 [P] [US4] Ampliar `HomeScreenTest.kt`:
  - la tarjeta va entre el héroe y los accesos;
  - «Indexando documentos» con «320 de 860 fragmentos»;
  - «Preparando los documentos» sin total;
  - el título de cada causa de pausa (R3);
  - tocarla llama a la acción del Índice;
  - TalkBack lee la frase y la acción (A4).

### Implementation for User Story 4

- [X] T024 [US4] Implementar P5:
  - el mapeo de `IndexingState` en `HomeViewModel`;
  - la tarjeta con `LisdataProgressCard(onClick)` y los textos de `feature/indexing` en `HomeTexts.kt`;
  - la navegación a `Index`.

**Checkpoint**: la tanda se sigue desde Inicio.

---

## Phase 7: User Story 5 - Un Inicio fiel al PNG y accesible (Priority: P2)

**Goal**: fidelidad al PNG 03 y accesibilidad en todos los estados, también con la fuente al 200 %, en oscuro y en horizontal.

**Independent Test**: capturas del teléfono frente al PNG 03; comprobaciones automáticas de accesibilidad en todos los estados.

### Tests for User Story 5

- [X] T025 [P] [US5] Ampliar `HomeScreenTest.kt` con medidas reales (`@GraphicsMode(NATIVE)`):
  - con la fuente al 150 % o más, los accesos van en una columna y las cifras se apilan;
  - al 200 %, ningún título ni subtítulo de los accesos queda cortado;
  - en horizontal (`es-w844dp-h390dp`), todo se alcanza desplazando;
  - la posición de desplazamiento se conserva al recrear la pantalla (`StateRestorationTester`; caso límite «Rotación»; análisis, G3).
- [X] T026 [P] [US5] Crear `…/androidTest/…/feature/home/HomeScreenAccessibilityTest.kt`, parametrizada sobre `HomePreviewData.all` en claro y en oscuro, y también en horizontal: comprobaciones automáticas, 48 dp, descripciones (A1–A6) y ancho con la fuente al 200 % (SC-008; análisis, G1).
- [X] T027 [P] [US5] Crear `…/androidTest/…/feature/home/HomeScreenLayoutTest.kt`, solo en el A063, como `IndexScreenLayoutTest`. Con una tanda activa, la tarjeta de indexación y la primera fila de accesos se ven sin desplazar, por encima de la barra inferior.

### Implementation for User Story 5

- [X] T028 [US5] Implementar A7 y A8 y completar `HomePreviewData.all` con todos los estados (P1–P5, vacío, error, fuente grande):
  - una columna de accesos y cifras apiladas desde el 150 %;
  - la altura del logotipo medida contra el PNG 03;
  - posición de desplazamiento guardada al girar.
- [X] T029 [US5] Capturas en el teléfono, en claro y en oscuro (quickstart §3.7), sin la barra de estado, y comparación con el PNG 03. Las diferencias van al informe.

**Checkpoint**: Inicio fiel y accesible.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T030 [P] Ampliar `…/test/…/architecture/LayeringTest.kt`: `feature/home` no importa nada de `data/`, WorkManager, SQLite, LiteRT ni `java.io.File`.
- [X] T031 [P] Actualizar `docs/roadmap/INVENTARIO.md`: la ficha P03 implementada, con los estados P1–P5 y las dos diferencias anotadas.
- [X] T032 Puertas de calidad (quickstart §1–2):
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest` en una copia limpia del último commit;
  - la suite instrumentada (sin `@RequiresModels` ni `@RequiresNetwork`) en el emulador API 37, la AVD API 33 y el teléfono;
  - `NetworkConfinementTest` (JVM) en verde con el paquete nuevo (SC-009).

  Todo en verde, salvo las 4 de navegación conocidas de la API 33 (F06 §7).
- [X] T033 Recorrido manual en el teléfono (quickstart §3):
  - arranque en frío (SC-001);
  - cifras con documentos de prueba al importar, indexar, pausar, excluir, quitar del índice y eliminar;
  - accesos y doble toque;
  - privacidad: logs y `netstats`;
  - limpieza de los documentos y la carpeta de prueba.

  TalkBack, a cargo del usuario.
- [X] T034 Documentación de cierre:
  - `specs/009-home/report.md`: resumen y puertas, validación por historia y criterio, cifras (R8), diferencias frente al PNG, desviaciones y pendientes;
  - estado de F09 en `docs/roadmap/FEATURES.md`;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias. **T003 puede parar el trabajo** (riesgo 1).
- **Foundational (fase 2):** depende de la fase 1. **Bloquea todas las historias.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2, US3 y US4 (fases 4–6):** dependen de US1 (la pantalla y su ViewModel). Pueden ir en cualquier orden. Comparten `HomeViewModel.kt`, `HomeScreen.kt` y sus pruebas, así que se hacen una detrás de otra.
- **US5 (fase 7):** depende de US1–US4.
- **Polish (fase 8):** depende de todo.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Caso de uso o repositorio antes que ViewModel; ViewModel antes que pantalla.
- Las verificaciones en el dispositivo cierran su historia: T003, T017, T021 y T029.

### Parallel Opportunities

- **Fase 2:** T004, T005 y T008.
- **US1:** T009 y T010.
- **US2:** T014 y T015.
- **US3:** T018 y T019.
- **US4:** T022 y T023.
- **US5:** T025, T026 y T027.
- **Polish:** T030 y T031.

## Parallel Example: Foundational

```text
T004 ObserveLocalAiUseCaseTest   T005 DefaultLibraryRepositoryTest (summary)   T008 LisdataHeroCard / LisdataProgressCard
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1, con las cifras verificadas sobre SQLite real (T003).
2. Fase 2.
3. US1: Inicio real y navegable, sin los accesos provisionales. **Parar y validar** con `NavigationTest` y en el emulador.

### Incremental Delivery

1. **US2:** estado de la IA, verificado en el teléfono (T017).
2. **US3:** resumen, con el benchmark (T021).
3. **US4:** tarjeta de indexación.
4. **US5:** fuente grande, horizontal, accesibilidad y capturas.
5. **Polish:** capas, inventario, puertas, recorrido e informe.
