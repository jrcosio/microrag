# Research: Inicio (F09)

Fecha: 2026-10-28. Todas las decisiones parten del código actual (F01–F08) y de la spec aclarada (D-046). No hay dependencias nuevas.

## R1. Estado de la IA local sin recalcular huellas

**Decisión:** dos casos de uso en `domain/onboarding`, sobre `ModelFileRepository` (F03):

- **`ObserveLocalAiUseCase()`:** `Flow<LocalAiStatus>`.
  - Primero llama a `refresh()`, que lee el registro de verificación y compara tamaño y fecha sin calcular huellas (F03 SC-007).
  - Después combina `states` y `preparedBackends` y emite `READY` o `SETUP_PENDING`, con `distinctUntilChanged`.
- **`RefreshLocalAiUseCase()`:** solo `refresh()`. Inicio lo llama al volver a primer plano (R5); el flujo anterior recoge el cambio.

La regla «listo» es la que completa el onboarding: todos los modelos de `ModelCatalog.required` con `ModelFileState.Verified` y con backend preparado. Se extrae a una función de dominio que comparten `CompleteOnboardingUseCase` y el caso de uso nuevo, para que no puedan divergir.

**«Comprobando»:**
- el ViewModel parte de `null` (sin chip) hasta la primera emisión;
- las comprobaciones posteriores, al volver a primer plano, mantienen el último valor hasta que el flujo emite otro. Así el chip no parpadea en cada vuelta, y el valor anterior salió de una comprobación real (constitución V).

**Verificado en el código:**
- tras un arranque en frío, `states` vale `Missing` para todos hasta el primer `refresh()`. Por eso no se puede usar el valor inicial;
- `refresh()` ya se usa así en `ReconcileDownloadsUseCase` y en el runner;
- `preparedBackends` se restaura desde el registro.

**Inicio no prepara ni verifica:**
- un fichero `Unverified` (tamaño o fecha distintos) o sin backend da «Configuración pendiente»;
- la Preparación hace el resto (`ReconcileDownloadsUseCase` y `PrepareModelsUseCase`).

**Alternativas descartadas:**
- **`ReconcileDownloadsUseCase` al abrir Inicio:** vuelve a calcular la huella de los ficheros sin registro (2,6 GB) y puede reprogramar descargas. Va contra F03 SC-007.
- **Una marca «escaneado» en el repositorio:** cambia la interfaz de F03 sin necesidad; el flujo del caso de uso ya sabe cuándo terminó su `refresh()`.
- **Mostrar «Configuración pendiente» desde el valor inicial:** mentiría unos milisegundos tras cada arranque.

## R2. Cifras del resumen: una consulta sobre `documents`

**Decisión:** operación nueva `RagIndex.librarySummary(): LibrarySummary`, en el dispatcher de la base de datos:

```sql
SELECT count(*),
       COALESCE(SUM(CASE WHEN status = 'done' THEN chunk_count END), 0)
FROM documents
WHERE replaces_id IS NULL
```

- **Documentos:** todos sin sombras, el mismo filtro que `libraryCount` con todos los estados (la Biblioteca en «Todos»).
- **Fragmentos:** la suma de `chunk_count` de los documentos `done`, incluidos o excluidos (D-046).
  - `chunk_count` es lo que se guardó al cerrar el documento (`updateStatus(DONE, chunkCount = produced)`) y lo que fija `publishReindex`. Por tanto, coincide con las filas de `chunks` de los documentos indexados.
  - Una prueba instrumentada lo compara con `count(*)` de `chunks` unido a `documents` en todos los estados (SC-004).
- **Coste:** recorre `documents` (1.000 filas) y nunca lee `chunks`, así que cumple el objetivo con margen (R8).

**Repositorio:** `LibraryRepository.summary(): Flow<SummaryResult>`, con el mismo patrón que `page()`:
- `open()`; si el índice no es `Compatible`, emite `Unavailable`;
- `documentChanges.mapLatest { librarySummary() }`, `distinctUntilChanged`, en `io`;
- `IndexUnavailableException` → `Unavailable`;
- cualquier otro error se propaga, y el ViewModel lo muestra como error con «Reintentar» (R4).

**Alternativas descartadas:**
- **`RagIndex.counts()`:** cuenta las sombras y los fragmentos de documentos a medias. Sirve para diagnóstico, no para el resumen.
- **`count(*)` sobre `chunks` con `JOIN`:** recorre 50.000 filas de índice para obtener lo mismo que ya está guardado.
- **`libraryCount` más `storedChunkCounts`:** dos consultas y una lista de ids.

## R3. Tarjeta de indexación

**Decisión:** el ViewModel observa `ObserveIndexingUseCase()` (F07) y deriva:

| Estado de F07 | Tarjeta |
|---|---|
| `Active(progress, pause = null)` | «Indexando documentos». Si `total` se conoce, `index_notification_chunks` («320 de 860 fragmentos») con barra determinada; si no, «Preparando los documentos» con barra indeterminada |
| `Active(pause = USER, THERMAL, SYSTEM_LIMIT o NO_SPACE)` | El título es el chip de la pausa de F07 (`PauseReason.chip()`: «En pausa», «Pausa por temperatura», «Esperando al sistema», «Sin espacio»), con las mismas cifras |
| `Active(pause = MODEL_MISSING)` | «Falta el modelo de embeddings» (`index_model_missing_title`), con las mismas cifras |
| `Idle`, `Loading`, `IndexUnavailable` | Sin tarjeta (D-046) |

- **Cifras:** las de la tanda, `RunProgress.done` y `total`, las mismas que el Estado de indexación y la notificación (D-042). Los textos se reutilizan de `feature/indexing` (`IndexTexts.kt`), como F08 reutiliza los de `feature/ingest`.
- **Componente:** `LisdataProgressCard`, con un parámetro nuevo `onClick`/`onClickLabel` que pasa a `LisdataCard` (cambio mínimo en el sistema de diseño). Tocarla abre `index`, con la etiqueta «Ver estado de la indexación».
- **Posición:** entre el héroe y la rejilla (D-046).

**Alternativa descartada:** una tarjeta propia con anillo, que duplica el anillo del Índice y ocupa más que la barra.

## R4. Navegación desde Inicio

| Control | Acción |
|---|---|
| «Nuevo chat →» | `navController.openNewChat()` (nuevo, en `NavigationActions.kt`): `navigate(Chat())` con `popUpTo<Home> { saveState = true }`, `launchSingleTop = true` y **sin** `restoreState`. Es la pestaña Chat con una conversación nueva, no la última que se dejó abierta |
| Documentos | `navigateToTopLevel(LIBRARY)`, igual que la pestaña |
| Icono de Ajustes | `navigateToTopLevel(SETTINGS)`, igual que la pestaña |
| Importar, Índice local, Idiomas | `navigate(Ingest / Index / Languages) { launchSingleTop = true }` |
| «Completar configuración» | `navigate(Setup) { launchSingleTop = true }` |
| «Importar primer documento» | `navigate(Ingest)` |
| Tarjeta de indexación | `navigate(Index) { launchSingleTop = true }` |

- **Dos toques seguidos (FR-006):** cada acción solo navega si la entrada de Inicio está `RESUMED`. El segundo toque llega con Inicio ya en `STARTED` y se ignora. Junto con `launchSingleTop`, abre un solo destino.
- **Preparación abierta desde Inicio:** hoy, `SetupRoute.onCompleted` hace `navigate(Home) { popUpTo<Welcome> { inclusive = true } }`. Con Inicio debajo y sin Bienvenida en la pila, `popUpTo` no hace nada y se apila un segundo Inicio. Cambio: si `Home` está en la pila, `popBackStack<Home>(inclusive = false)`; si no, como ahora. Atrás en la Preparación ya hace `popBackStack()` y vuelve a Inicio.
- **Accesos provisionales:** se retiran de `MicroRagNavHost` (F06 FR-025 y F07 FR-034), con sus textos `ingest_provisional_entry` e `index_provisional_entry`, y el parámetro `actions` de `PendingDestination`, que solo usaba Inicio.

**Alternativa descartada:** `navigateToTopLevel(CHAT)` para «Nuevo chat». Restauraría el estado guardado de la pestaña Chat, que en F11 será la conversación anterior; el MD 03 pide una nueva.

## R5. Comprobar al volver a primer plano

**Decisión:** `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` en `HomeRoute` llama a `viewModel.onResume()`, que lanza `RefreshLocalAiUseCase`. Es el mismo patrón que `SetupScreen` (F03).
- `ON_RESUME` de la entrada de Inicio cubre las dos situaciones: volver de otra app y volver de otra pantalla, por ejemplo de la Preparación.
- El primer `ON_RESUME` coincide con el `refresh()` de `ObserveLocalAiUseCase`. Son dos lecturas baratas y no se evitan.

## R6. Composición y diseño adaptable

- **Cabecera:**
  - una fila propia: `LisdataLogo` a la izquierda (altura medida contra el PNG al implementar, del orden de 36–40 dp, como en la Bienvenida) e `IconButton` de 48 dp con `ic_settings` a la derecha;
  - no es `LisdataTopBar`, que no tiene hueco para el logotipo y lleva título.
- **Chip:** `LisdataStatusChip(kind = Ready)` «IA local disponible», o `kind = Warning` «Configuración pendiente» junto a un `TextButton` «Completar configuración». Ningún chip mientras se comprueba, con su alto reservado para que el contenido no salte.
- **H1:** `headlineLarge`, como el resto de pantallas.
- **Héroe:**
  - `LisdataHeroCard(eyebrow, title)` con «Una respuesta.» y «Todas tus fuentes.» en blanco, en dos líneas: el texto lleva el salto de línea, porque `titleAccent` pintaría la segunda en el color de acento y el PNG la pinta blanca;
  - `ic_sparkle` en el centro de las órbitas, con un parámetro nuevo `decorationIcon` en `LisdataHeroCard`, oculto a TalkBack como las órbitas;
  - `LisdataGradientButton(trailingIcon = ArrowRight)`.
- **Rejilla:**
  - 2×2 de `LisdataQuickActionTile`, con las dos tarjetas de cada fila a la misma altura (`IntrinsicSize.Min`, como la rejilla del detalle);
  - con la fuente al 150 % o más, una columna, el mismo umbral que las acciones del detalle (F08 A6). Así el título y el subtítulo, de hasta dos líneas, no se cortan.
- **Resumen:**
  - `HomeSummaryCard` (en `feature/home`): `LisdataCard` con el eyebrow «TU BIBLIOTECA» y dos cifras en dos columnas: «24» con «documentos» y «12.400» en `brandBlue` con «fragmentos»;
  - el PNG pone «documentos» al lado de la cifra y «fragmentos» debajo; aquí las dos van debajo, por coherencia y para que quepan con la fuente grande (diferencia anotada en el contrato);
  - con la fuente al 150 % o más, las dos cifras se apilan;
  - TalkBack lee una sola frase (FR-021).
- **Desplazamiento:** una columna desplazable con el margen de pantalla, en vertical y en horizontal, con `rememberScrollState` guardado (se conserva al girar).

## R7. Textos, cifras y plurales

- **Ficheros:** `res/values{,-en}/strings_home.xml`.
- **Plurales:** `home_documents` y `home_chunks`, para las etiquetas («1 documento», «24 documentos»).
- **Frase de TalkBack:** `home_summary_description` («%1$s y %2$s»).
- **Separador de miles:** `grouped()`, la misma función de `feature/library` que usan la Biblioteca y el detalle, para que las cifras coincidan.
- **Textos maquetados:** los literales del PNG 03 (INVENTARIO P03).
- **Textos sin mockup** (propuestas, se aprueban en la parada tras analyze): «Configuración pendiente», «Completar configuración», «Tu biblioteca está vacía», «Importar primer documento», el error del resumen y los títulos de la tarjeta de indexación.

## R8. Mediciones

- **Resumen (SC-005):** `HomeSummaryBenchmarkTest`, instrumentada, con los mismos datos sintéticos que `LibraryBenchmarkTest` (1.000 documentos y 50.000 fragmentos). Mide 50 llamadas a `librarySummary()` tras 5 de calentamiento; p95 ≤ 50 ms. Se ejecuta en el teléfono y se informa.
- **Arranque (SC-001):** `am start -W` en frío, 10 veces, mediana de `TotalTime`, con el onboarding completado, como en F03. El chip y el resumen se cronometran con dos líneas de log de Inicio, solo con milisegundos (constitución I).
- **Coherencia (SC-004):** prueba instrumentada sobre SQLite real con documentos en todos los estados, un excluido, uno fuera del índice y una sombra a medias. Compara `librarySummary()` con `libraryCount(todos)` y con `count(*)` de `chunks` de los documentos `done`.
