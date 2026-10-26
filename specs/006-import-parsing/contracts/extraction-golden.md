# Contrato — Golden de extracción y fixtures PDF

Garantiza que la app extrae TXT y Markdown, y une las líneas de PDF en párrafos, igual que `offline/eval/extract.py`, que es el extractor con el que F05 evaluó (FR-020, SC-002). El troceado ya tiene su golden (`chunker_golden.jsonl`, F05).

## Python (`offline/`)

- **`eval/extract.py`:**
  - gana `decode_text(data: bytes) -> str` (research R8);
  - `extract()` lee los TXT y MD con `decode_text(path.read_bytes())`;
  - el resto de funciones no cambia.
- **`gen_extraction_golden.py`:** escribe `golden/extraction_golden.jsonl`. Es determinista: mismos casos, mismo orden y JSON con `ensure_ascii=False`.
- **`gen_pdf_fixtures.py`:** escribe los PDF de `app/src/androidTest/assets/pdf/` (research R12) con un `/ID` fijo. También es determinista.
- **Pruebas** (`tests/test_extraction_golden.py`):
  - regeneran en memoria el golden y los fixtures y exigen igualdad byte a byte con lo versionado;
  - comprueban que `decode_text` no cambia la extracción de los TXT y MD del corpus (frente a `build/extract`, si existe).

## Casos del golden

| Grupo | Casos | Origen |
|---|---|---|
| `decode` | UTF-8, UTF-8 con BOM, cp1252 con `ñ`/`€`, bytes cp1252 sin definir (`0x81`, `0x8D`, `0x8F`, `0x90`, `0x9D`), UTF-8 inválido a mitad, `\r\n`, `\r` solo, mezcla | A mano |
| `text` | Vacío, solo espacios (incluidos `\xa0` y `　`), párrafos con 1 y con 3 o más líneas en blanco, espacios finales, separadores `\x0b`, `\x0c`, `\x1c`, `\x85`, ` ` | A mano |
| `text` | Los 9 TXT de Wikilibros del corpus, recortados a 8.000 caracteres por un salto de párrafo | Corpus F05 |
| `markdown` | Niveles 1–6, 7 almohadillas, `#` sin espacio, `#\xa0Título`, `# Título ##`, `#   ` vacío, `# #`, subida y bajada de nivel, sección vacía, texto antes del primer encabezado, encabezado al final | A mano |
| `markdown` | Los 6 MD del BOE del corpus, recortados a 8.000 caracteres por un salto de párrafo | Corpus F05 |
| `pdf_page` | Guion de fin de línea (`pala-\nbra`, `X-\nY`, `-\nminúscula`), número de página suelto, superíndices (`²`) solos en una línea, viñetas (`-`, `•`, `·`, `▪`, `–`, `1.`, `2)`, `3º`, `a)`), fin de frase seguido de mayúscula, de dígito y de minúscula, líneas en blanco, espacios Unicode internos, primera letra fuera del BMP | A mano |
| `pdf_page` | Las 10 primeras páginas de cada PDF del corpus, como texto por página de pypdf | Corpus F05 |

## Uso en la app

- **JVM:** `ExtractionGoldenTest` lee `../offline/golden/extraction_golden.jsonl` y aplica el mismo pipeline Kotlin por tipo de caso: `decode` → `TextDecoding`; `text` y `markdown` → extractor sobre el texto; `pdf_page` → `pdfParagraphs(pySplitLines(input))`. Exige igualdad exacta en todos los casos.
- **Dispositivo:** `ExtractionGoldenInstrumentedTest` hace lo mismo leyendo el *asset* (`offline/golden` ya es un *asset* de `androidTest`), porque las tablas Unicode de ART pueden diferir de las del JDK.
- **Chunker:** `ChunkerGoldenTest` (JVM) y `ChunkerGoldenInstrumentedTest` (dispositivo) con `chunker_golden.jsonl` (27 casos y 162 fragmentos).
- **Streaming:** `StreamingEquivalenceTest` (JVM). Con `MAX_BLOCK_CHARS` reducido a 200, los fragmentos de cada caso `text` y `markdown` del golden son idénticos a los obtenidos sin partición.

## Reglas

| # | Regla |
|---|---|
| X1 | Un cambio en `extract.py` que altere la salida exige regenerar el golden en el mismo commit. Las pruebas Python fallan si el golden versionado no coincide con el generado |
| X2 | Un cambio en la extracción Kotlin que altere la salida hace fallar `ExtractionGoldenTest`. La extracción de la app y la de la evaluación no pueden divergir sin que se note |
| X3 | Los fixtures PDF son deterministas, pequeños (< 1 MB en total) y no contienen texto privado |
| X4 | El golden y los fixtures no entran en el APK de release, porque solo son *assets* de `androidTest` |
