# Arquitectura UI y contratos — LISDATA

## Capas
`Compose Screen → ViewModel → UseCase → Repository Interface → Implementación local`.

La UI no debe importar JNI, motores de embeddings, SQLite vectorial ni SDKs de LLM. Los adaptadores deben poder cambiar sin reescribir pantallas.

## Módulos lógicos sugeridos
- `core/designsystem`: tema, tokens y componentes.
- `core/navigation`: rutas y shell de navegación.
- `core/model`: modelos de dominio y estados.
- `core/data`: contratos y repositorios.
- `feature/onboarding`, `feature/home`, `feature/chat`, `feature/ingestion`, `feature/library`, `feature/indexing`, `feature/settings`.
- `rag/runtime`: adaptadores locales de Gemma.
- `rag/retrieval`: embeddings, búsqueda y citas.
- `rag/storage`: SQLite, ficheros y metadatos.

Puede empezar como un solo módulo Gradle con paquetes separados y modularizar después; evitar modularización prematura.

## Contratos de dominio propuestos
`Document(id, displayName, mimeType, sizeBytes, sha256, importedAt, pageCount?, chunkCount, indexingState, includedInSearch, localPathOrUri)`

`IndexingState = Queued | Extracting | Chunking | Embedding | Persisting | Ready | Paused | Failed(reason)`

`ModelState = Missing | Importing(progress?) | Validating | Ready | Incompatible(reason) | Failed(reason)`

`Citation(documentId, documentName, pageNumber?, chunkId, excerpt?)`

`ChatMessage(id, role, text, citations, generationState, createdAt)`

`GenerationState = Idle | Retrieving | Generating | Completed | Cancelled | Failed(reason)`

Usar clases y nombres idiomáticos de Kotlin. Los campos opcionales deben representarse como nullable; no usar página 0 ni cadenas ficticias para representar ausencia.

## Contratos de repositorio
- `DocumentRepository`: observar documentos, importar metadatos, activar/desactivar, eliminar, obtener detalle.
- `IngestionRepository`: encolar archivos, observar trabajos, reintentar, cancelar.
- `IndexRepository`: observar estado global, estadísticas, iniciar/reanudar.
- `ChatRepository`: observar conversaciones, enviar pregunta, cancelar, obtener fuentes.
- `ModelRepository`: observar instalación y compatibilidad.
- `SettingsRepository`: idioma, privacidad, rendimiento y preferencias.

Exponer `Flow`/`StateFlow` para estados persistentes y eventos explícitos para acciones. Evitar operaciones pesadas en Main.

## Privacidad y rendimiento
Sin dependencias de red en el flujo principal. Importación mediante Storage Access Framework, procesamiento y persistencia privados. Con 10k–100k fragmentos, usar paginación, consultas limitadas y operaciones de fondo; nunca cargar todos los embeddings en memoria de Compose.

## Datos falsos
Los fakes son válidos solo en previews, tests o una build de demostración. En producción, los conteos, estados, nombres, fuentes y porcentajes deben venir de los repositorios.

## Pruebas
ViewModels con repositorios fake; pruebas de navegación; tests Compose de estados vacíos, errores y acciones; pruebas de integración con documentos pequeños y corpus grande.
