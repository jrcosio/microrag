# Contrato: bancos en el teléfono (US1, US3, US5, US6, US7; R2, R4, R5, R10, R11)

## B1. Kit (`androidTest/…/testing/bench/`)

| Pieza | Responsabilidad |
|---|---|
| `DeviceConditions` | Estado térmico, margen, batería, temperatura, carga, RSS actual y pico (`VmRSS` y `VmHWM`), heap y tamaño de un fichero. Una foto por muestra y al principio y al final |
| `BenchReport` | Construye y escribe el JSON de data-model §4 en `filesDir/reports/f14-<banco>-<fecha>.json`, y un resumen numérico en `Log.i("MicroRAG", "f14 …")` |
| `BenchBank` | Verifica `dev-bench/<nombre>/manifest.json` (SHA-256), construye o reutiliza `cacheDir/bench/<nombre>.db` con `DefaultRagIndex` en su propio dispatcher, y da las preguntas |
| `DevFiles` | Rutas de `dev-bench` y `dev-models`, e instalación verificada de la 270M y E4B con `DevModelImporter` |
| `Percentiles` | Nearest-rank, como los bancos de F10 y F11 |

- **Ausencias:** todos los bancos se saltan con `assumeTrue` si falta su banco o su modelo, como `@RequiresModels`. En los emuladores no corren.

## B2. Bancos

| Prueba | Mide | Variantes |
|---|---|---|
| `SearchScaleBenchmarkTest` | Por banco de 10k, 50k y 100k: embedding solo y dentro de la búsqueda, vectorial, léxica, híbrida total, acotada y con excluidos (p50, p95 y p99); cuantizar, precargar, memoria, tamaño del `.db` y Recall@20 | `f32`, `int8`, `int8-rerank`, `int8-nopreload` |
| `PageSearchBenchmarkTest` | Búsqueda de páginas con 1k, 5k y 10k páginas | `f32`, `int8-rerank` |
| `EmbedderComparisonBenchmarkTest` | Consulta (p50 y p95) y RSS de la 440M y la 270M, en orden alterno | `440m`, `270m` |
| `GenerationBenchmarkTest` | Con 4, 6 y 8 bloques: primer token, prefill, decodificación, tokens reales y estimados, y RSS pico, con 20 preguntas | `base`, `prefix`, `mtp`, `fp32`, `v4-cifras`, `e4b` |
| `SustainedChatBenchmarkTest` | `DefaultChatSession.ask` con respuestas completas durante 25 min, con el desglose (G9), el estado térmico y la batería por pregunta | `sin-reposo`, `con-reposo` (30 s); configuración por argumento de instrumentación |
| `NumericFidelityBenchmarkTest` | Las 60 preguntas de cifras, con sus respuestas | `default`, `fp32`, `v4-cifras` |
| `VisualChatBenchmarkTest` (amplía) | El primer token por detalle y, nuevo, una sesión sostenida con Equilibrado y la recarga con y sin precarga por ámbito | `detalles`, `sostenido`, `recarga` |
| `PrefixCacheGoldenTest` | Las respuestas greedy de las goldens, idénticas con y sin prefijo | — |
| `BenchCleanupTest` | Borra `cacheDir/bench`, `dev-bench`, los modelos de `dev-models` y sus entradas de verificación, y los informes `f14-*` ya copiados | — |

- **Variantes por argumento** (`-e f14.variant …`, `-e f14.minutes …`): así la fase del teléfono lanza cada medida por separado con `am instrument`, y la salida va a `/data/local/tmp` (lecciones de F16 y F20).
- **Estados de un informe:**
  - `not-available`: falta el drafter, falla el clon o la API no tiene la función;
  - `does-not-fit`: E4B no cabe;
  - `no-space`: falta espacio para el banco;
  - `aborted-thermal`: la sesión llega a `SEVERE`.

## B3. Lo que nunca hacen

- Abrir, leer o escribir el `rag.db`, el `chat.db` o los documentos del usuario. Los bancos de texto usan bases propias. Los de chat usan el índice del corpus (`CorpusIndex`) y un `chat.db` temporal, como `ChatBenchmarkTest`.
- Usar la red.
- Cargar dos engines en GPU: cada variante libera el motor anterior.

## B4. Carpetas del teléfono

| Carpeta | Contenido | La crea |
|---|---|---|
| `getExternalFilesDir("dev-bench")` | bancos y preguntas copiados con `adb` | la prueba, si falta (lección de F18: la carpeta la crea la app antes del `adb push`) |
| `getExternalFilesDir("dev-models")` | la 270M y E4B | ya existe (F02 y F18) |
| `cacheDir/bench` | las bases de los bancos | `BenchBank` |
| `filesDir/reports` | los JSON | `BenchReport` |
