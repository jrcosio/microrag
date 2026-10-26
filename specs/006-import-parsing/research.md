# Research — F06 Importación y parsing

Fecha: 2026-10-26. Cada punto: decisión, motivo y alternativas.

## R1. Dependencias y versiones

**Decisión:** F06 no añade dependencias a la app. Usa las que ya están en `gradle/libs.versions.toml`, verificadas en features anteriores:

| Uso | Artefacto | Versión |
|---|---|---|
| Selector del sistema (`ActivityResultContracts.OpenMultipleDocuments`) | `androidx.activity:activity-compose` | 1.13.0 |
| Trabajo en segundo plano | `androidx.work:work-runtime-ktx` | 2.12.0 |
| Hoja inferior (`ModalBottomSheet`) | `androidx.compose.material3`, por el BOM | 2026.09.00 |
| `@RequiresExtension` para las APIs de PDF | `androidx.annotation:annotation-jvm`, transitiva | 1.9.1, con `androidx/annotation/RequiresExtension.class` comprobado en el JAR de la caché de Gradle |
| Lectura de los golden en las pruebas | `kotlinx-serialization-json` | 1.12.0 |

En Python (`offline/`) tampoco hay dependencias nuevas:
- `pypdf==6.20.1` (ya fijada) genera los PDF de prueba. Su cifrado RC4-128 funciona sin `cryptography`, comprobado el 2026-10-26; AES exigiría `cryptography` y no se usa.

**Alternativas:** `pdfbox-android` y `androidx.pdf`, descartadas en el clarify (D-037).

## R2. Extracción de texto de PDF con la API del sistema (D-037)

**Decisión:** usar `android.graphics.pdf.PdfRendererPreV` en todos los dispositivos que la tengan:
- está disponible en Android 12–14 con la extensión S ≥ 13 y en Android 15+;
- la clase `PdfRenderer` solo tiene la extracción de texto desde API 35, así que con `PdfRendererPreV` basta un camino de código.

Verificado en las fuentes del SDK 36.1 (`sources/android-36.1/android/graphics/pdf`) y en `api-versions.xml` del SDK 37.1:
- **Disponibilidad:** `PdfRendererPreV`, `PdfRendererPreV$Page.getTextContents()`, `LoadParams` y `PdfPageTextContent` llevan `sdks="31:13,33:13,34:13,35:13,0:35"`.
- **Constructor** `PdfRendererPreV(ParcelFileDescriptor)`. Lanza:
  - `IOException` si hay un error de lectura (archivo dañado o que no es PDF);
  - `SecurityException` si el archivo pide contraseña o usa un cifrado no soportado;
  - `IllegalArgumentException` si el descriptor no admite búsqueda. Se abre con `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` sobre la copia privada, que sí la admite.
- **Texto:** `Page.getTextContents()` delega en `PdfProcessor.getPageTextContents`, que devuelve **un único** `PdfPageTextContent` con el texto de toda la página (`PdfDocumentProxy.getPageText`, nativo PDFium). Una página escaneada da texto vacío. Los saltos de línea los genera PDFium; se tratan con la división de líneas de Python (`splitlines`, R8), que acepta `\r\n`, `\r` y `\n`.
- **Recorrido:** se abre una página cada vez (`openPage(i)`) y se cierra tras leerla. Así la memoria no depende del número de páginas.
- **Seguridad:** la documentación recomienda aislar el renderer en otro proceso si el archivo viene de una fuente no confiable. Se acepta procesarlo en el proceso de la app:
  - son documentos que elige el propio usuario;
  - PDFium es el del módulo *mainline*, que se actualiza con el sistema.

  Se anota como riesgo para F15.

**Alternativas:** `PdfRenderer` en API 35+ con `PdfRendererPreV` por debajo (dos caminos con el mismo resultado); `androidx.pdf` (D-037).

## R3. Comprobar en tiempo de ejecución si se pueden leer PDF

**Decisión:** `SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13` (API 30+), en un único punto (`PdfSupport`), con `@RequiresExtension(extension = S, version = 13)` en el adaptador para que lint lo vigile. En Android 15 la extensión S ya es ≥ 13.

Sin soporte:
- cada PDF elegido termina en `PDF_UNAVAILABLE` antes de copiarse;
- la línea de formatos de la pantalla lo indica (contracts/ingest-ui.md, propuesta P4).

**Verificación temprana:** leer `getprop build.version.extensions.s` en el teléfono (API 35), en el emulador `Pixel_10` (API 37) y en la AVD API 33. Si la AVD API 33 no llega a 13, se usa para probar el camino «sin soporte».

## R4. Selector, tipos y permisos

**Decisión:**
- **Selector:** `OpenMultipleDocuments` con `["application/pdf", "text/plain", "text/markdown", "text/x-markdown"]`. No hay permisos de almacenamiento: el permiso temporal del selector basta para copiar en el momento. `ManifestInvariantsTest` (lista cerrada de permisos de F03) lo comprueba.
- **Formato:** se decide por la extensión del nombre visible (`.pdf`, `.txt`, `.md`, `.markdown`) y, si no la hay, por el tipo MIME. Después se valida el contenido:
  - un PDF debe empezar por `%PDF-` en su primer KB;
  - un TXT o MD no puede contener bytes nulos en sus primeros 8 KB.

  Lo que no encaja termina en `UNSUPPORTED_FORMAT` o `DAMAGED`.
- **Permiso persistente:** no se pide (`takePersistableUriPermission`). La app copia en cuanto vuelve del selector y no guarda acceso al original (G22).

**Alternativas:**
- **`text/*`:** dejaría elegir CSV, HTML, etc. sin soportarlos.
- **Permisos persistentes:** permitirían reintentar tras la muerte del proceso, pero dejan concesiones abiertas y no aportan nada si se copia al momento.

**Verificar en el dispositivo:** que un `.md` de Descargas se puede elegir con el filtro. Si algún proveedor lo ofrece como `application/octet-stream`, se añade ese tipo al filtro y la validación por extensión lo resuelve.

## R5. Copia, huella y atomicidad

**Decisión:** la importación de cada archivo, en orden:
1. leer de `OpenableColumns` el nombre visible y el tamaño, si se conoce;
2. si el tamaño se conoce:
   - más de 100 MB es `TOO_LARGE`;
   - si el espacio libre es menor que tamaño + `ImportLimits.SPACE_MARGIN_BYTES` (64 MiB), es `NO_SPACE`;
3. si es un PDF y no hay soporte, `PDF_UNAVAILABLE`;
4. copiar a `documents/.import-<uuid>.tmp` con un `DigestInputStream` (SHA-256) y un contador:
   - pasar de 100 MB durante la copia es `TOO_LARGE`;
   - un error de escritura por espacio es `NO_SPACE`;
   - `SecurityException` o `FileNotFoundException` del proveedor es `ACCESS_LOST`;
   - otro `IOException` de lectura es `READ_FAILED`;
5. 0 bytes es `NO_TEXT`;
6. validar el contenido (R4). Para los PDF, abrirlo (R2):
   - `SecurityException` es `ENCRYPTED`;
   - `IOException` es `DAMAGED`;
   - más de 2.000 páginas es `TOO_LARGE`;
   - 0 páginas es `DAMAGED`;
7. si `RagIndex.documentIdByHash(sha256)` existe, es `DUPLICATE` y solo se borra el temporal. **Nunca** se renombra sobre la copia de un documento existente, que tiene el mismo nombre (`<sha256>.<ext>`);
8. renombrar el temporal a `documents/<sha256>.<ext>`. Si ya existe, es un huérfano: se sustituye, porque tiene el mismo contenido;
9. `RagIndex.addDocument`. Una `DuplicateDocumentException` en este punto solo puede venir de una carrera, imposible con la importación secuencial. Si ocurre, es `DUPLICATE` y no se borra nada, porque el fichero es el del documento existente.

Ante cualquier error se borra el temporal.

El orden «renombrar y después registrar» garantiza que nunca haya un documento registrado sin su copia completa (SC-006). Si el proceso muere entre los dos pasos, queda un fichero huérfano, y lo limpia `DocumentFileStore.cleanOrphans` al observar la cola por primera vez en cada proceso. Se borran:
- los `*.tmp`;
- los ficheros cuyo nombre no es la ruta local de ningún documento.

Los archivos se importan **de uno en uno**, en orden de selección, en el *scope* de aplicación y en `AppDispatchers.io`. Así:
- un duplicado dentro de la misma selección lo detecta el índice;
- la E/S queda acotada.

**Alternativas:**
- **Registrar primero:** puede dejar un documento sin copia.
- **Importar en paralelo:** compite por la E/S y complica los duplicados.

## R6. La cola: tres fuentes y qué persiste

**Decisión:** la cola que ve el usuario combina tres fuentes:
1. **Intentos de importación** (`ImportAttempt`), en memoria en un *store* de aplicación:
   - mientras se copian, en estado `Importing`;
   - si fallan, en estado `Failed(error)`;
   - al registrarse el documento desaparecen, porque ya es la fuente 2.

   Sobreviven a la recreación de la Activity, pero no a la muerte del proceso. La spec lo admite (US4-2: «aparece con un error que se puede reintentar o no aparece»), y lo que no llegó a ser documento no tiene copia que conservar. Se registra en D-038.
2. **Documentos no indexados** del índice: `pending`, `parsed` y `error`, ordenados por creación. Persisten y se reconstruyen tras la muerte del proceso (FR-008).
3. **Trabajo en segundo plano** (WorkManager): qué documentos están en cola y en qué etapa va el actual (R7).

Para observar la fuente 2, `RagIndex` gana `documents(statuses)` y un flujo de cambios (contracts/ingest-api.md). Así la cola se actualiza sin sondear.

**Alternativas:**
- **Persistir los intentos** (DataStore o una tabla nueva): añade estado sin valor, porque un intento fallido no tiene copia y la URI no se puede releer tras la muerte del proceso.
- **Sondear el índice:** gasta batería y responde con retraso.

## R7. Trabajo en segundo plano

**Decisión:**
- **`IngestWorker`** (`CoroutineWorker`, con dependencias de Koin como `ModelDownloadWorker`) delega en `IngestRunner`, que se puede probar en la JVM.
- **Encolado:** `enqueueUniqueWork("ingest", APPEND_OR_REPLACE, request)`. La petición lleva:
  - `inputData`: los ids de los documentos «Listo para procesar», en orden de creación;
  - las etiquetas `ingest` e `ingest-doc:<id>` por documento.

  `APPEND_OR_REPLACE` encadena las pulsaciones sucesivas; un trabajo anterior fallido o cancelado se sustituye.
- **«En cola»:** un documento `pending` cuya etiqueta aparece en un trabajo `ENQUEUED`, `BLOCKED` o `RUNNING` de `getWorkInfosForUniqueWorkFlow("ingest")`. Los que no aparecen son «Listo para procesar». Esto cumple el caso borde de la spec: lo importado durante el proceso espera a la siguiente pulsación.
- **Progreso:** `setProgress` con `docId`, `stage` (`extracting` o `chunking`), `page` y `pages`, como mucho cada 250 ms. En PDF es determinado (páginas hechas de N) y en el troceado, indeterminado.
- **Por documento:**
  1. si el documento ya no existe o no está `pending`, se salta;
  2. se extrae y se trocea en streaming, contando fragmentos y páginas omitidas;
  3. si el documento sigue existiendo, `updateStatus(PARSED, chunkCount, skippedPages)`;
  4. si no hay ningún bloque, `ERROR` con `no_text`.

  Los errores de PDF (`encrypted`, `damaged`, `pdf_unavailable`) y los de E/S (`extraction_failed`) se guardan como código en `documents.error`, y se sigue con el siguiente.
- **Parada:** `isStopped` se comprueba entre páginas. El documento a medias sigue `pending` y se reprocesa entero en la siguiente ejecución, con el mismo resultado, porque es determinista (FR-013). El worker no devuelve `failure` por errores de documento.
- **Sin servicio en primer plano en F06:** extraer 200 páginas cuesta segundos (SC-005). F07 añade `setForeground` al sumar los embeddings, que son lo largo.
- **«Quitar»:** borra el registro y el fichero. El worker lo detecta en el paso 1 o al escribir, porque el `UPDATE` no afecta a ninguna fila.

**Alternativas:**
- **`ExistingWorkPolicy.KEEP`:** perdería las pulsaciones durante un proceso.
- **Un worker por documento:** más sobrecarga, y el orden se complica.

## R8. Extracción idéntica al escritorio (golden compartido)

**Decisión:** la extracción de TXT y Markdown, y la unión de líneas en párrafos de PDF, se portan de `offline/eval/extract.py` con su semántica Python exacta. Un golden generado desde Python lo garantiza (contracts/extraction-golden.md).

Detalles que el port debe reproducir:

| Elemento | Python | Kotlin |
|---|---|---|
| Espacios | `str.isspace` (incluye `\x1c–\x1f`, `\x85`, `\xa0`, ` – `, ` `, ` `, `　`…) | Tabla `isPySpace` explícita, generada y probada desde Python |
| Fin de línea | `splitlines()`: `\n`, `\r`, `\r\n`, `\x0b`, `\x0c`, `\x1c`, `\x1d`, `\x1e`, `\x85`, ` `, ` ` | `pySplitLines` |
| Lectura | `read_text(encoding="utf-8")` traduce `\r\n` y `\r` a `\n` | Función compartida `decode_text` (abajo) |
| `strip`, `rstrip`, `split()` | Espacios Python | Funciones con `isPySpace` |
| `isdigit` | Numeric_Type Digit o Decimal (incluye superíndices) | Por punto de código: `Nd` más la tabla de otros dígitos generada desde `unicodedata` |
| `isupper`, `islower`, `isalpha` | Primer **punto de código** | `Character.isUpperCase/isLowerCase/isLetter(codePoint)` |
| Regex `HEADING` y `BULLET` (`\s`, `\d` Unicode) | Regex | Analizadores manuales equivalentes, sin regex Java, que tiene otra semántica de `\s` y `\d` |

**Lectura compartida:** `extract.py` pasa a leer con `decode_text(bytes)`:
```text
try: text = data.decode("utf-8-sig")              # el BOM se ignora (caso borde de la spec)
except UnicodeDecodeError: text = data.decode("cp1252", errors="replace")
text = text.replace("\r\n", "\n").replace("\r", "\n")
```
Verificado el 2026-10-26: los 9 TXT y los 6 MD del corpus de F05 son UTF-8 sin BOM ni `\r`, así que el cambio no altera su extracción. Una tarea temprana lo vuelve a comprobar contra la caché de extracción (`build/extract`). En Kotlin:
- UTF-8 con un `CharsetDecoder` en modo `REPORT`, en una primera pasada en streaming;
- si falla, `windows-1252` con `REPLACE`.

Los bytes sin definir de cp1252 (`0x81`, `0x8D`, `0x8F`, `0x90`, `0x9D`) dan U+FFFD en los dos lados; el golden lo comprueba.

**Alternativa:** simplificar la semántica en Python (espacios ASCII). Cambiaría la extracción de F05 e invalidaría su evaluación.

## R9. Chunker: port literal y streaming

**Decisión:** `Chunker` en Kotlin con el código del informe. Las sutilezas ya están documentadas en `offline/eval/chunker.py`:
- `String.length` en UTF-16;
- `trim()` de Kotlin;
- `\s` ASCII en las regex de Java;
- los trozos de frase conservan el espacio final salvo el último.

Se estructura como un acumulador con estado (`add(block)` y `finish()`) que emite fragmentos de forma perezosa. `chunk(List<RawBlock>)` lo envuelve para el golden.

Prueba: los 27 casos y 162 fragmentos de `chunker_golden.jsonl` (FR-021, SC-001), en la JVM y en el dispositivo. El golden se lee de `offline/golden`, que ya es un *asset* de `androidTest`.

## R10. Memoria con documentos grandes

**Decisión:** todo en streaming, con la memoria acotada sin importar el tamaño:
- **PDF:** página a página (R2). Un bloque por página.
- **TXT y MD:** dos pasadas por el fichero, una para validar UTF-8 y otra para decodificar línea a línea con `pySplitLines`. Las secciones se emiten como bloques. Si una sección pasa de `ExtractionLimits.MAX_BLOCK_CHARS` (256 KiB de caracteres), se parte **por un límite de párrafo** en varios bloques con la misma ruta y sin página.

  El `Chunker` produce exactamente los mismos fragmentos: con la misma ruta no hay `flush` por cambio de ruta, y los párrafos le llegan en el mismo orden. Una prueba con el umbral reducido lo comprueba sobre el golden y sobre muestras del corpus. Con las entradas del golden (≤ 8.000 caracteres) no hay partición, así que los bloques son idénticos a los de Python.
- **Fragmentos:** en F06 solo se cuentan; no se guardan.

SC-005 mide el crecimiento de la memoria (PSS) en el teléfono con un PDF de 200 páginas.

**Alternativa:** leer el texto entero. Un TXT de 100 MB ocuparía unos 200 MB como `String` UTF-16, con riesgo de OOM.

## R11. Páginas sin texto: columna `skipped_pages` (esquema v2, D-038)

**Decisión:** US3-4 exige que el resultado indique cuántas páginas se omitieron, y debe seguir viéndose tras reiniciar (FR-008). Por eso:
- `documents` gana `skipped_pages INTEGER` (NULL en TXT y MD);
- `SCHEMA_VERSION` pasa a 2, con la migración `1 → 2`: `ALTER TABLE documents ADD COLUMN skipped_pages INTEGER`;
- `schema.sql` describe la v2.

Impacto en el resto:
- F04 tiene la infraestructura de migraciones probada; se añade su primera migración real con su prueba instrumentada.
- En `offline/`, `appconfig.schema_version()` leerá 2 de Kotlin; se ajustan `test_appconfig`, y la inserción de `index.py` usa columnas explícitas, así que no cambia.
- Los índices de evaluación ya construidos (v1) se reconstruyen si se vuelve a evaluar (quickstart). Su huella cambia, porque incluye `rag_meta`; la de F05 (`09735c92…`) queda como referencia de la v1.
- F08 podrá mostrar el dato en el detalle.

**Alternativas:**
- **Guardarlo en `documents.error` con estado `parsed`:** mezcla aviso y error.
- **No persistirlo:** se perdería al reiniciar, y la salida de WorkManager se purga.

## R12. Fixtures y mediciones

**Decisión:**
- **PDF pequeños en git:** `offline/gen_pdf_fixtures.py` escribe objetos PDF a mano (Helvetica estándar, `WinAnsiEncoding`, contenido determinista) y cifra con `pypdf` (RC4-128). Los guarda en `app/src/androidTest/assets/pdf/`:
  - texto en 3 páginas, con encabezados, guiones de fin de línea, números de página y viñetas;
  - escaneado, con páginas que solo tienen una imagen;
  - parcialmente escaneado;
  - cifrado con contraseña de apertura;
  - restringido, con contraseña de propietario solamente;
  - truncado;
  - texto con extensión `.pdf`;
  - 200 páginas de texto (SC-005).

  El generador es determinista (un `/ID` fijo) y su prueba Python lo regenera y compara.
- **PDF de 10 MB (SC-004):** se genera en la propia prueba con `android.graphics.pdf.PdfDocument`, con texto más un mapa de bits de ruido por página hasta 10 MB, para no versionar 10 MB.
- **Mediciones en el teléfono** (`IngestBenchmarkTest`, informativa y con cifras al log):
  - SC-004: tiempo desde la entrega de la URI hasta «Listo para procesar»;
  - SC-005: tiempo de extraer y trocear 200 páginas, y crecimiento del PSS (`Debug.getPss()` muestreado).
- **Comparación con pypdf** (Assumption de la spec):
  - una prueba instrumentada extrae los PDF del corpus de F05, copiados con `adb push` a `getExternalFilesDir("corpus")`, y escribe el texto por página en un JSONL;
  - `run_eval.py device-extraction` mide qué parte de las evidencias del conjunto de prueba de esos documentos aparece en el texto del dispositivo, frente al de pypdf.

  Es una cifra informativa para el informe de cierre.

## R13. Estados de UI sin mockup

El PNG solo maqueta la cola con tres archivos «Listo para procesar». La constitución VII exige proponer el resto y aprobarlo antes de implementarlo. Las propuestas están en contracts/ingest-ui.md (P1–P6) y se presentan en la parada tras analyze:
- cola vacía;
- fila en curso y fila con error;
- hoja de detalle (G15);
- sin soporte de PDF;
- biblioteca no disponible;
- acceso provisional desde Inicio.
