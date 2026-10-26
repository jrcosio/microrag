# Quickstart — validación de F04

Las reglas están en `contracts/`; aquí solo se indica cómo ejecutar y qué esperar.

## 0. Preparación

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG=com.lisdatasolutions.microrag
PHONE=P31281001066   # Nothing Phone (1): tiene EmbeddingGemma 2 270M instalada (F03)
EMU=emulator-5554
```

> **Teléfono:** nunca `connected*AndroidTest`. Hay que usar `installDebug`, `installDebugAndroidTest` y `am instrument` (F02 y F03).

## 1. Pipeline offline (Mac)

```bash
python3.12 -m venv offline/.venv
offline/.venv/bin/pip install -r offline/requirements.txt
mkdir -p offline/models && ln -sf $HOME/MicroRAG-models/embeddinggemma-2-text-270m.litertlm offline/models/

offline/.venv/bin/python -I offline/gen_normalizer_golden.py   # → offline/golden/normalizer_golden.jsonl (≥ 300)
offline/.venv/bin/python -I offline/gen_embedding_golden.py    # → offline/golden/embedding_golden.jsonl (50)
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:**
- los generadores son deterministas: volver a ejecutarlos no cambia `git diff`;
- todas las pruebas Python pasan.

## 2. Build, lint y tests JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

**Esperado:**
- todo en verde;
- `NormalizerGoldenTest` al 100 %;
- los tests de WorkManager siguen en verde con `androidx.sqlite` 2.7.1.

## 3. Instrumentadas

```bash
# Emulador. Con el teléfono conectado, no usar connected*AndroidTest: instala en todos los dispositivos
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
$ADB -s $EMU install -r -t app/build/outputs/apk/debug/app-debug.apk
$ADB -s $EMU install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s $EMU shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner

# Teléfono: suite habitual
./gradlew :app:installDebug :app:installDebugAndroidTest
$ADB -s $PHONE shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner

# Teléfono: golden de embeddings (CPU + GPU) y medición
$ADB -s $PHONE shell am instrument -w -e class com.lisdatasolutions.microrag.rag.runtime.EmbeddingGoldenTest $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE shell am instrument -w -e class com.lisdatasolutions.microrag.data.rag.RagIndexBenchmarkTest $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE logcat -d -s MicroRAG | grep -E "golden|benchmark"
```

**Esperado:**
- `VectorExtensionLoadTest` devuelve `1.1.2` en los dos dispositivos;
- las pruebas de escritura, búsqueda, compatibilidad y modo estricto están en verde;
- `NormalizerGoldenInstrumentedTest` coincide al 100 %;
- `EmbeddingGoldenTest`: coseno mínimo ≥ 0,99 en CPU y en GPU, con el mínimo y la media en el log;
- la medición registra el p50 y el p95 de las búsquedas vectorial y léxica con 10k fragmentos.

## 4. Comprobaciones manuales

- **Extracción:** con `$ADB shell pm path $PKG` y `ls …/lib/arm64/`, en el teléfono aparece `vector.so`.
- **Índice real:** el índice de la app no se crea hasta que alguna feature lo usa. F04 no tiene pantalla, así que en el teléfono `no_backup/index/` puede no tener `rag.db`. Las pruebas usan ficheros temporales.
- **Release:**
  - `apkanalyzer` muestra `lib/arm64-v8a/vector.so` y `libsqliteJni.so`;
  - los permisos siguen siendo los 9 de F03;
  - `schema.sql` va en `assets/`;
  - los golden no están en el APK de release.
