# Research — F04 Núcleo de datos RAG

**Fecha:** 2026-10-26.
- Versiones y artefactos: verificados en Maven Central, Google Maven y PyPI.
- API: comprobada en los propios binarios y en la documentación de la versión fijada.
- Experimentos: en el emulador `Pixel_10` (API 37) y en el Mac del proyecto.

## R1 — Dependencias

| Artefacto | Versión | Verificación | Uso |
|---|---|---|---|
| `androidx.sqlite:sqlite-bundled` | **2.7.1** | Google Maven, release estable (2.8.0 está en alpha). El AAR `sqlite-bundled-android` trae `libsqliteJni.so` (arm64 1,28 MB; también armv7, x86 y x86_64) y depende de `androidx.sqlite:sqlite` 2.7.1 | SQLite propio de la app, con FTS5 y carga de extensiones |
| `ai.sqlite:vector` (SQLite-Vector) | **1.1.2** | Maven Central, la última (2026-09-11). Licencia **Apache-2.0**, según el POM y el `LICENSE.md` del tag 1.1.2. El AAR solo trae nativas: `jni/<abi>/vector.so` para arm64 (145 KB), armv7 y x86_64, con `minSdk` 26, sin dependencias y con `classes.jar` vacío | Búsqueda vectorial exacta |
| `litert-lm-api` (Python) | **0.18.1** | PyPI, ruedas `py3-none` para `macosx_12_0_arm64`, Linux x86_64/aarch64, Windows y Android. Requiere Python ≥ 3.10 | Golden de embeddings en el escritorio (D-012) |

**API de `BundledSQLiteDriver`** (con `javap` sobre el AAR 2.7.1):
- `open(String)` y `open(String, int flags)`;
- `addExtension(String)` y `addExtension(String, String entryPoint)`;
- `getThreadingMode()`.

**Punto de entrada de la extensión:** `sqlite3_vector_init`. Es el símbolo exportado de `vector.so` y coincide con el que SQLite deriva del nombre `vector`. Se pasa de forma explícita igualmente.

**Efecto en WorkManager.** WorkManager 2.12.0 trae Room 2.7.0 y `androidx.sqlite` 2.5.0; al añadir `sqlite-bundled` 2.7.1, Gradle sube `androidx.sqlite:sqlite` y `sqlite-framework` a 2.7.1. Las dos son de la misma línea estable de AndroidX y se espera compatibilidad. Se verifica con los tests de WorkManager (JVM, Robolectric) y con la prueba de humo de descarga en API 33 (riesgo R10-1).

**Descartados:**
- `sqlite-vec` (Asg017): el informe fija SQLite-Vector (constitución III, restricciones técnicas).
- SQLite del sistema: no permite cargar extensiones.
- `regex` (PyPI) para el normalizador en Python: basta la biblioteca estándar (R4).

## R2 — Carga de `vector.so` sin el prefijo `lib`

**Riesgo del informe y de FEATURES:** la extensión se carga desde una ruta del sistema de ficheros, pero por defecto Android deja las nativas dentro del APK (`extractNativeLibs=false`). Además, `vector.so` no lleva el prefijo `lib`.

**Experimento** (2026-10-26, emulador API 37). Con `ai.sqlite:vector:1.1.2` y `packaging.jniLibs.useLegacyPackaging = true`:
- el APK contiene `lib/arm64-v8a/vector.so`;
- tras instalar, `<app>/lib/arm64/` contiene **`vector.so`** junto a las demás nativas.

El sistema extrae la biblioteca aunque no lleve el prefijo `lib`. La guía Android del fabricante dice lo mismo («`extractNativeLibs="true"` … `nativeLibraryDir + "/vector"`»).

**Decisión:**
- `packaging { jniLibs { useLegacyPackaging = true } }`, que equivale a `extractNativeLibs="true"`;
- ruta de la extensión: `applicationInfo.nativeLibraryDir + "/vector.so"`, con el punto de entrada `sqlite3_vector_init`.

**Coste:** las nativas pasan a ir comprimidas en el APK (descarga más pequeña) y se extraen al instalar. El tamaño instalado crece en ≈ 23 MB, sobre todo por `liblitertlm_jni.so` extraída. Se mide al cerrar y se reevalúa en F15.

**Verificación temprana en la implementación:** cargar la extensión y ejecutar `SELECT vector_version()` en el teléfono de referencia (API 35) y en el emulador antes de construir el resto.

**Plan B**, si un dispositivo no extrae `vector.so`: renombrarla a `libvector.so` en el empaquetado. Una tarea Gradle copiaría las nativas del AAR a `jniLibs` con el prefijo y excluiría la original.

## R3 — SQL de SQLite-Vector 1.1.2

Fuente: `API.md` del tag 1.1.2.

| Función | Uso |
|---|---|
| `vector_init(table, column, 'dimension=256,type=FLOAT32,distance=COSINE,normalized=1')` | En **cada conexión**, como pide la documentación. `normalized=1` declara que los vectores son unitarios: el coseno se calcula como `1 − dot`, con las mismas distancias |
| `vector_as_f32(?)` | En los `INSERT`, con un BLOB float32 little-endian de 256 componentes |
| `vector_full_scan(table, column, ?, k)` | Tabla virtual `(rowid, distance)` con el top-k ordenado. No necesita `vector_as_*` para el vector de consulta |
| `vector_version()` | Comprobación de carga (devuelve `1.1.2`) |
| `vector_quantize*` | Fuera de F04 (D-030) |

## R4 — Normalizador `SpanishText` v1, idéntico en Kotlin y en Python

**Kotlin:** el código del informe, literalmente, en `rag/text/SpanishText.kt`, con la constante `VERSION = 1`.

**Python:** un port en `offline/normalizer.py`, solo con la biblioteca estándar. Hay cuatro sutilezas que se replican a propósito:
1. **Tokenización:** la regex de Kotlin `[\p{L}\p{N}]+(?:[-_./][\p{L}\p{N}]+)*` se replica con un escáner que usa `unicodedata.category(c)[0] in ('L','N')`. Consume rachas alfanuméricas, unidas por un separador `- _ . /` solo si lo sigue otro carácter alfanumérico, igual que el motor de regex.
2. **Longitudes:** `String.length` en Kotlin cuenta unidades UTF-16. Python usa `len(t.encode('utf-16-le')) // 2` en `length > 1` y `length < 5`, para que una letra fuera del plano básico cuente igual.
3. **Dígitos:** `Char.isDigit()` de Kotlin recorre unidades UTF-16 y solo acepta la categoría `Nd` del plano básico. Python usa `unicodedata.category(c) == 'Nd' and ord(c) <= 0xFFFF`, no `str.isdigit()`, que acepta superíndices.
4. **Minúsculas y descomposición:** `lowercase()` (`Locale.ROOT`) y `java.text.Normalizer` NFD frente a `str.lower()` y `unicodedata.normalize('NFD')`. Se eliminan las marcas `Mn`.

El conjunto de referencia evita caracteres que difieran entre versiones de Unicode (Android ICU, JDK 21 y Python 3.12, Unicode 15.0).

**Golden:**
- Python genera `normalizer_golden.jsonl` a partir de `normalizer_inputs.txt` (≥ 300 entradas).
- Kotlin lo comprueba en la JVM y en el dispositivo.
- Los ejemplos del informe tienen su salida esperada escrita a mano, en los tests de los dos lados, para validar el algoritmo y no solo la paridad.

## R5 — Golden de embeddings escritorio ↔ móvil

**Comprobado en el Mac** (2026-10-26):
- `EmbeddingEngine(model, backend=Backend.CPU(), max_input_length=512, cache_dir=…)`;
- `compute_embedding(texto, EmbeddingOptions(normalize=False))` devuelve `.embedding` con 768 floats sin normalizar (norma ≈ 679);
- la primera llamada tarda 0,26 s incluyendo la inicialización;
- `cache_dir` tiene que existir.

**Decisión:**
- **Mismo fichero:** `embeddinggemma-2-text-270m.litertlm`, con el SHA-256 del catálogo, que el generador verifica antes de embeber.
- **`max_input_length=512`:** como `LiteRtTextEmbedder` en Android, que elige la misma firma de entrada.
- **Entradas:** se construyen como `EmbeddingPrefixes`. Consulta: `QUERY + texto.strip()`. Documento: `DOCUMENT + "\n".join(x for x in (cabecera.strip(), contenido.strip()) if x)`. El golden guarda la entrada exacta, y la prueba de Android comprueba que su propia construcción da la misma cadena.
- **Truncado:** primeros 256 componentes, norma L2 en doble precisión y división, redondeando a float32 (`struct`), igual que `EmbeddingMath.truncateAndNormalize`. Python no usa numpy: menos dependencias y el mismo orden de operaciones.
- **Contenido:** 50 textos, 25 consultas y 25 fragmentos con cabecera. Son sintéticos en español y fragmentos del informe (Assumptions de la spec).
- **Prueba en el teléfono** (D-030): vuelve a embeber en CPU y en GPU (con `LiteRtEngineFactory`) y exige un coseno ≥ 0,99 por par en cada backend. Si la GPU no está disponible, la parte de GPU queda «no disponible».

## R6 — Una sola fuente para los golden

**Decisión:** los golden viven en `offline/golden/`. No hay copias:
- Python los lee directamente.
- Los tests JVM los leen por ruta relativa al módulo (`../offline/golden/…`).
- Los tests instrumentados los leen como *assets* de `androidTest`, añadiendo `../offline/golden` a `sourceSets["androidTest"].assets`. No viajan en el APK de producto.

`schema.sql` vive en `app/src/main/assets/schema.sql`, como dice el informe, y el Python offline lo leerá desde ahí en F05.

## R7 — `rag_meta`, compatibilidad y migraciones

**Al crear el índice** se escriben `schema_version=1`, el SHA-256 de EmbeddingGemma 2 270M (D-025), `embedding_dims=256`, los dos prefijos (`EmbeddingPrefixes`), `normalizer_version=1` y `quant_dirty=1`. `quant_dirty` empieza en 1 porque no hay cuantización hecha.

**Al abrir**, la función pura `IndexCompatibilityChecker` compara los valores con la configuración esperada de la app:
- clave ausente o modelo, dimensiones, prefijo o normalizador distintos: `Incompatible(campo, esperado, encontrado)`;
- `schema_version` mayor que la de la app: `Incompatible`;
- `schema_version` menor: se aplican las migraciones en orden, en una transacción, y se vuelve a validar. Si falta una migración, `Incompatible`.

Con el índice incompatible se rechaza toda lectura y escritura (`IndexUnavailableException`). Solo se puede consultar el estado y cerrar. La recuperación, recrear el índice y volver a embeber, es de F07/F12.

**Migraciones:** una lista ordenada de `(desde, sentencias)` en `RagMigrations`, vacía en la versión 1. `schema.sql` describe siempre la última versión, con `IF NOT EXISTS`, para los índices nuevos.

## R8 — Conexión, hilos y modo estricto

**Conexión:**
- una sola conexión para lectura y escritura;
- abierta perezosamente en `AppDispatchers.database`, un único hilo: constitución IV y FR-005.

**Al abrir**, en este orden:
1. `PRAGMA journal_mode=WAL`, `synchronous=NORMAL`, `temp_store=MEMORY` y `foreign_keys=ON`;
2. el esquema;
3. `vector_init`.

**Transacciones:** `BEGIN IMMEDIATE`, con `COMMIT` o `ROLLBACK`. El lote se valida (dimensiones) antes de abrirla.

**Normalización en un solo sitio:**
- `title_norm = SpanishText.normalize("nombre › ruta")`, uniendo lo que no esté vacío, como en el informe;
- `body_norm = normalize(contenido)`.

Las calcula el repositorio y no quien llama, para que nadie use otra normalización.

**Modo estricto en depuración:**
- `StrictMode.ThreadPolicy` con `detectDiskReads`, `detectDiskWrites` y `penaltyLog` en la `Application` de debug (stub en release);
- una prueba instrumentada pone `penaltyListener` en el hilo principal mientras ejecuta las operaciones del repositorio y exige 0 violaciones (SC-007).

## R9 — Medición de referencia (SC-008)

Una prueba instrumentada:
1. crea un índice temporal en `cacheDir`;
2. inserta 10.000 fragmentos sintéticos (vectores unitarios aleatorios con semilla fija y texto en español generado);
3. mide 100 búsquedas vectoriales (*k* = 50) y 100 léxicas;
4. registra p50 y p95.

Se ejecuta en el teléfono de referencia y no exige umbrales: es una medida de referencia para F10 y F14.

## R10 — Riesgos

1. **Subida de `androidx.sqlite` a 2.7.1 con Room 2.7.0 (WorkManager):** se verifica con los tests de WorkManager. Si fallara, se fija la línea compatible.
2. **`vector.so` no extraída en algún dispositivo:** plan B de R2.
3. **Unicode distinto entre plataformas:** el conjunto de referencia evita caracteres recientes; un fallo se investiga antes de ampliarlo.
4. **Coseno en GPU por debajo de 0,99 frente al escritorio:** posible, porque F02 midió un mínimo CPU↔GPU de 0,993. Si ocurre, se documenta y el teléfono indexa en CPU (D-030).
5. **Tamaño instalado:** +≈ 23 MB por la extracción de nativas (R2).
