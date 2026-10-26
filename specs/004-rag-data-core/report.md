# Informe de cierre — F04 Núcleo de datos RAG

**Rama:** `004-rag-data-core`.
**Fecha:** 2026-10-26.
**Dispositivos:**
- teléfono de referencia Nothing Phone (1) A063, API 35;
- emulador `Pixel_10`, API 37, ARM64;
- AVD `MicroRAG_API33`, API 33.

**Versiones:** `androidx.sqlite:sqlite-bundled` 2.7.1, `ai.sqlite:vector` 1.1.2 (SQLite-Vector), LiteRT-LM 0.18.1 y `litert-lm-api` 0.18.1 (Python 3.12, macOS ARM64).

Las 52 tareas están hechas.

## 1. Qué se ha implementado

- **Esquema** `app/src/main/assets/schema.sql` v1: el del informe con la ampliación G27 (D-030). Lo comparte el Python offline.
- **Índice** (`domain/rag`, `data/rag`):
  - `RagIndex`, con 15 operaciones, sobre una conexión de SQLite bundled con SQLite-Vector, en `AppDispatchers.database`;
  - lotes idempotentes y atómicos, búsqueda vectorial exacta, BM25 3/1, carga por id y vecino;
  - validación de `rag_meta` con bloqueo, migraciones por `schema_version` y estados `Incompatible` e `Unreadable`;
  - registrado en Koin sobre `no_backup/index/rag.db`, con apertura perezosa.
- **Normalizador** `SpanishText` v1 (`rag/text`): literal del informe, más `VERSION = 1`.
- **Pipeline offline** (`offline/`): `normalizer.py` (port exacto), `embedding_text.py` y los dos generadores, con 17 pruebas `unittest` y una sola dependencia (`litert-lm-api==0.18.1`).
- **Golden:**
  - `normalizer_golden.jsonl`: 367 pares, con la huella fijada por versión (regla O6);
  - `embedding_golden.jsonl`: 50 vectores (25 consultas y 25 fragmentos con cabecera; tres de unas 220 palabras).
  - Los dos generadores son deterministas.
- **Calidad:**
  - modo estricto en depuración (`DebugStrictMode`);
  - regla de capas: solo `data/rag` importa `androidx.sqlite`;
  - empaquetado con extracción de nativas (D-031).

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 212/212 (F03: 180) |
| Pruebas Python (`unittest`) | ✅ 17/17 |
| Instrumentados en el emulador `Pixel_10` (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 159/159 (F03: 128) |
| Instrumentados en el teléfono (`am instrument`, mismos filtros) | ✅ 159/159 |
| `EmbeddingGoldenTest` (`@RequiresModels`) | ✅ teléfono (CPU y GPU) y emulador (CPU; GPU no disponible) |
| Pruebas de F04 en la AVD API 33 (paquete `data.rag` y golden del normalizador) | ✅ 30/30 |
| `ModelDownloadSmokeTest` por WorkManager en la AVD API 33, con `androidx.sqlite` 2.7.1 (riesgo R10-1) | ✅ EmbeddingGemma 2 verificada en 13,0 s |
| Release | ✅ Los 9 permisos de F03; `vector.so` y `libsqliteJni.so` en `lib/arm64-v8a/`; `schema.sql` en `assets/`; sin golden |

## 3. Validación por historia

| Historia | Resultado |
|---|---|
| **US1:** índice local | `RagIndexWriteTest`, 10 pruebas en los tres dispositivos: esquema y `rag_meta`, documentos y duplicados, lote atómico con `RAISE(ABORT)` a mitad, idempotencia (SC-004), `quant_dirty`, triggers con `integrity-check`, cascada (SC-005), reapertura y transacción sin confirmar |
| **US2:** búsquedas | `RagIndexSearchTest`, 8 pruebas. El vector idéntico sale primero en 20/20 casos en el teléfono y en el emulador (SC-003). El título pesa más que el cuerpo. Palabras vacías, comillas y `R-1234`. Carga exacta y vecino. Índice vacío. Ningún texto, consulta ni nombre en los logs (A10) |
| **US3:** normalizador | Los 367 pares coinciden al 100 % en Python 3.12, en la JVM (JDK 21), en el emulador API 37, en el teléfono API 35 y en la AVD API 33 (SC-001). Incluye sigma final, `İ`, coreano en NFD, devanagari, emojis y dígitos fuera del plano básico |
| **US4:** embeddings | Teléfono: CPU mínimo 1,000000, media 1,000000; GPU mínimo **0,992992**, media 0,997812. Los 50 pares superan 0,99 en los dos backends (SC-002) |
| **US5:** protección | `RagIndexCompatibilityTest`, 8 pruebas: cada metadato alterado o ausente da `Incompatible(campo, esperado, encontrado)` y bloquea las 12 operaciones, con el fichero intacto (SHA-256 antes y después, SC-006). Esquema más nuevo; migración aplicada, ausente o fallida (con *rollback*); fichero que no es una base de datos; extensión ausente |
| **SC-007:** hilo principal | `RagIndexStrictModeTest`: todas las operaciones llamadas desde el hilo principal dan 0 violaciones. Un control con la misma política sí detecta una lectura en el hilo principal |

## 4. Cifras medidas

| Medida | Valor | Nota |
|---|---:|---|
| Búsqueda vectorial exacta, 10k fragmentos, *k* = 50 (teléfono) | p50 **21,5 ms**, p95 **23,1 ms** | Supera el umbral de 20 ms que activa INT8 (constitución IV). Dato de entrada de F14 |
| Búsqueda BM25, 10k fragmentos, *k* = 50 (teléfono) | p50 23,9 ms, p95 26,5 ms | Peor caso: vocabulario sintético de unas 150 palabras, así que casi todos los fragmentos coinciden |
| Inserción de 10k fragmentos de 120 palabras, en lotes de 64 | 24,4 s (≈ 410/s, normalización incluida) | — |
| Tamaño de la base con 10k fragmentos | 46,0 MB | Vectores F32 de 256 más textos y FTS |
| `EmbeddingGoldenTest` completo en el teléfono | ≈ 27 s con la instalación de los APK: hash del modelo y 50 textos en CPU y en GPU | — |
| APK de release (sin R8) | **45,7 MB** (F03: 57,4 MB) | Las nativas van comprimidas |
| Descarga estimada (`apkanalyzer download-size`) | 24,0 MB | — |
| Tamaño instalado estimado | ≈ 69,1 MB: APK + 23,4 MB de nativas extraídas (F03: 57,4 MB) | +11,7 MB por la extracción (D-031). Se reevalúa en F15 |

## 5. Diferencias frente a los mockups

No aplica: F04 no tiene UI (D-030).

## 6. Desviaciones respecto al plan

1. **SQLite-Vector crea su propia tabla.** Al cargarse en una conexión, crea `_sqliteai_vector`, donde persiste la configuración de `vector_init`. Esto responde a la duda del informe sobre la persistencia; se sigue llamando a `vector_init` en cada apertura. Consecuencias:
   - «índice nuevo» significa «sin tablas propias» y no «`rag_meta` vacío»;
   - abrir con la extensión un fichero SQLite ajeno le añade esa tabla, mientras que los índices de la app ya la tienen.

   Anotado en research R3 y en data-model §2.
2. **Orden de apertura** (contrato A9): se valida `rag_meta` **antes** de aplicar el esquema y `journal_mode=WAL`, porque ambos pueden escribir. Así un índice incompatible no se modifica.
3. **T041 y T042** (migraciones y estado `Unreadable`) se implementaron con la apertura (T016), porque comparten el mismo flujo. T040 los valida.
4. **`EmbeddingGoldenTest` en el emulador:** la inicialización de LiteRT-LM en GPU se bloquea en código nativo (más de 10 minutos) y el emulador acabó cerrándose. La prueba marca la GPU del emulador como «no disponible», el caso que FR-021 ya prevé.
5. **Control negativo del modo estricto.** La primera versión de la prueba restauraba la política antes de que StrictMode entregara las violaciones, que en el hilo principal llegan al terminar el mensaje. Con un control añadido se vio que no detectaba nada. Ahora la política sigue activa hasta que el hilo principal queda inactivo, y el control demuestra que sí detecta.
6. **Constructor de `DefaultRagIndex`:** no recibe `Context`. Recibe una función que lee el esquema dentro de la apertura, más las migraciones y un reloj inyectables. El contrato se ha actualizado.

## 7. Pendiente y observaciones

- **Violaciones de StrictMode al arrancar, ajenas a F04.** Lecturas de disco en el hilo principal, del orden de 55–80 ms:
  - en la creación de `AppDirs` (`noBackupFilesDir`);
  - en `DataStoreOnboardingStore` y `AndroidDeviceResources`;
  - en `DefaultModelDownloadController.partialBytes` y `deleteOrphanPartials`.

  Vienen de F01–F03 y no se han corregido aquí. Se propone moverlas fuera del hilo principal en F09 o F15.
- **INT8:** con 10k fragmentos, el p95 vectorial (23,1 ms) ya supera los 20 ms. F14 tendrá que implementar INT8 con precarga y medir Recall@20 frente a F32, como prevé su criterio 2.
- **G28** (excluir documentos en la búsqueda vectorial): F04 guarda `included`; el filtro es de F10.
- **Abiertos de antes:** TalkBack manual de F01 (T059) y F03 (T071), y el SVG oficial del logo (G20).
