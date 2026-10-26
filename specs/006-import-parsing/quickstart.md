# Quickstart — validar F06

Requisitos:
- JBR de Android Studio: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`;
- el entorno `offline/.venv` de F04/F05;
- el teléfono de referencia (API 35) y el emulador `Pixel_10` (API 37) conectados por `adb`.

## 1. Pruebas Python (golden y fixtures)

```bash
offline/.venv/bin/python -I -m unittest discover -s offline/tests
offline/.venv/bin/python -I offline/gen_extraction_golden.py --check   # sin diferencias con lo versionado
offline/.venv/bin/python -I offline/gen_pdf_fixtures.py --check
```

**Esperado:** todo en verde. El golden y los fixtures regenerados son idénticos byte a byte.

## 2. Build, lint y pruebas JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

**Esperado:**
- `ChunkerGoldenTest` coincide en 27/27 casos y 162/162 fragmentos (SC-001);
- `ExtractionGoldenTest` coincide en el 100 % (SC-002);
- `StreamingEquivalenceTest`, `IngestRunnerTest`, `DefaultImportQueueRepositoryTest`, `IngestViewModelTest`, `LayeringTest` (con la nueva regla de `android.graphics.pdf`) y `RagParametersTest`, en verde.

## 3. Pruebas instrumentadas

Como en F04, con `ADB`, `EMU`, `PHONE` y `PKG=com.lisdatasolutions.microrag`. Con el teléfono conectado no se usa `connected*AndroidTest`, porque instala en todos los dispositivos.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
for D in $EMU $PHONE; do
  $ADB -s $D install -r -t app/build/outputs/apk/debug/app-debug.apk
  $ADB -s $D install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  $ADB -s $D shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner
done
```

Se ejecutan en el emulador y en el teléfono. `IngestBenchmarkTest` corre en la suite, pero solo aplica los umbrales en el teléfono de referencia; en los demás dispositivos solo registra. `CorpusPdfExtractionTest` se salta si no se ha copiado el corpus (§5). **Esperado:**
- **Golden en ART:** `ExtractionGoldenInstrumentedTest` y `ChunkerGoldenInstrumentedTest`, al 100 %.
- **`PlatformPdfOpenerTest`** con los fixtures:
  - texto multipágina: bloques con página, párrafos y guiones unidos;
  - escaneado: `NO_TEXT`;
  - parcialmente escaneado: páginas omitidas contadas;
  - cifrado: `ENCRYPTED`;
  - restringido: se lee;
  - truncado y falso PDF: `DAMAGED`.
- **`DocumentImporterTest`:**
  - copia, huella y registro;
  - duplicado: sin copia nueva;
  - límite de 100 MB y de 2.000 páginas;
  - sin espacio (espacio libre simulado);
  - cancelación;
  - limpieza de huérfanos.
- **`RagIndexMigrationTest`:** un índice v1 se abre como v2 con `skipped_pages` y conserva sus datos.
- **`IngestFlowTest`:**
  - importar los fixtures con URIs de un `ContentProvider` de prueba;
  - «Iniciar indexación»: los documentos acaban `parsed` con su `chunk_count`, y los inválidos, en su error (SC-003);
  - matar y relanzar el worker a mitad: cada documento termina una vez, con el mismo resultado (SC-006).
- **`IngestScreenTest`:** estados vacío, importando, listo, en curso, preparado y error. Hoja de detalle. Comprobaciones de accesibilidad (SC-008), en claro y en oscuro.
- **`IngestStrictModeTest`:** importación y procesamiento sin violaciones en el hilo principal (SC-009).
- **`ManifestInvariantsTest`:** sin permisos nuevos (SC-007).

## 4. Mediciones en el teléfono (SC-004 y SC-005)

```bash
$ADB -s $PHONE shell am instrument -w -e class com.lisdatasolutions.microrag.data.ingest.IngestBenchmarkTest \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE logcat -d -s MicroRAG | grep ingest-benchmark
```

**Esperado:**
- PDF de 10 MB «Listo para procesar» en menos de 3 s;
- PDF de 200 páginas extraído y troceado en menos de 30 s, con el PSS creciendo 100 MB o menos.

Las cifras van al informe de cierre.

## 5. Comparación con pypdf (informativa)

```bash
$ADB -s $PHONE shell mkdir -p /sdcard/Android/data/com.lisdatasolutions.microrag/files/corpus
$ADB -s $PHONE push offline/corpus/docs/*.pdf /sdcard/Android/data/com.lisdatasolutions.microrag/files/corpus/
$ADB -s $PHONE shell am instrument -w -e class com.lisdatasolutions.microrag.data.ingest.CorpusPdfExtractionTest \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE pull /sdcard/Android/data/com.lisdatasolutions.microrag/files/corpus/device-extraction.jsonl offline/build/
offline/.venv/bin/python -I offline/run_eval.py device-extraction offline/build/device-extraction.jsonl
```

**Esperado:** para los PDF del corpus, el porcentaje de evidencias del conjunto de prueba que aparecen en el texto del dispositivo frente al de pypdf, por documento y en total. Va al informe de cierre.

## 6. Recorrido manual en el teléfono

1. Inicio provisional → «Importar documentos (provisional)».
2. «Seleccionar archivos» → elegir un PDF, un TXT y un MD de Descargas (R4: comprobar que se puede elegir el `.md`).
3. Ver «Listo para procesar» y el recuento.
4. Elegir el mismo PDF otra vez → «Ya está en tu biblioteca».
5. «Iniciar indexación» → `index` (placeholder) → Atrás → las filas pasan por En cola, Extrayendo y Fragmentando, y acaban en «Preparado para indexar · N fragmentos».
6. Girar el teléfono, salir y volver, y forzar el cierre (`adb shell am kill`) con el trabajo en marcha. Al volver, el estado es coherente.
7. Con TalkBack: recorrer las filas y la hoja de detalle.

## 7. Evaluación offline tras el esquema v2

Si se vuelve a evaluar con F05, primero hay que reconstruir los índices, que están en v1:

```bash
offline/.venv/bin/python -I offline/run_eval.py build ref
offline/.venv/bin/python -I offline/run_eval.py build dim512   # y el resto de variantes que se necesiten
```
