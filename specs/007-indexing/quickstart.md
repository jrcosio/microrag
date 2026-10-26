# Quickstart — validar F07

Requisitos:
- **JBR de Android Studio:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Entorno `offline/.venv`** de F04/F05.
- **Teléfono de referencia** (API 35) con los modelos instalados y verificados (F03), y emulador `Pixel_10` (API 37), conectados por `adb`.
- **Variables:** `ADB`, `EMU`, `PHONE` y `PKG=com.lisdatasolutions.microrag`, como en F06.

## 1. Pruebas Python

```bash
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:** en verde, incluido el caso nuevo de `EmbeddingOverflow` generado en Python (research R6).

## 2. Build, lint y pruebas JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

**Esperado:**
- **`IngestRunnerTest` ampliado:** fases 1 y 2, reanudación, desajuste, pausa, temperatura, espacio, documento quitado y fallo del engine.
- **`EmbeddingOverflowTest`:** igual que Python.
- **`DocumentTitleTest`.**
- **`DefaultIndexingRepositoryTest`:** progreso N/M, total desconocido, tiempo restante, pausas y reconciliación.
- **`IndexViewModelTest` e `IndexScreenTest`** (Robolectric): todos los estados.
- **`EngineCoordinatorTest`** con engines retenidos.
- **`IngestViewModelTest`** con los estados nuevos.
- **`LayeringTest`.**

## 3. Pruebas instrumentadas

Como en F06: se instalan los dos APK en cada dispositivo y se lanza `am instrument`, sin `connected*AndroidTest`.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
for D in $EMU $PHONE; do
  $ADB -s $D install -r -t app/build/outputs/apk/debug/app-debug.apk
  $ADB -s $D install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  $ADB -s $D shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner
done
```

**Esperado:**
- **`RagIndexVisibilityTest`:** las búsquedas no devuelven fragmentos de documentos que no están en `done` ni de excluidos, con *k* + *m*, sobre SQLite-Vector real.
- **`IndexingFlowTest`:** la tanda completa con un embedder determinista sobre un índice temporal:
  - cancelación a mitad y reanudación con los mismos fragmentos;
  - quitar un documento a mitad;
  - temperatura simulada.
- **`IndexScreenAccessibilityTest`:** todos los estados, en claro y en oscuro, con la fuente al 200 %.
- **`IndexingStrictModeTest`:** sin violaciones en el hilo principal.
- **`ShellInsetsTest` y las de F06:** siguen en verde.

### Con modelos (solo el teléfono)

```bash
$ADB -s $PHONE shell am instrument -w -e annotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels \
  -e class $PKG.data.indexing.EmbeddingOverflowDeviceTest,$PKG.data.indexing.EmbeddingBatchProbeTest,$PKG.data.indexing.IndexingBenchmarkTest \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE logcat -d -s MicroRAG | grep -E "indexing-benchmark|overflow-probe|batch-probe"
```

**Esperado:**
- **Recorte de D-036:** la excepción del texto largo queda registrada y el recorte la resuelve (research R6).
- **Lote frente a serie:** cifras de los dos modos. Si el lote acelera al menos un 30 %, se para y se consulta (R5).
- **SC-001:** 200 páginas indexadas en 5 min o menos en GPU, con los fragmentos por segundo de GPU y de CPU.

## 4. Verificación temprana de la ejecución (research R1–R2)

En el teléfono y en el emulador, con la app real:

1. Importar `text_200_pages.pdf` y pulsar «Iniciar indexación». Anotar la latencia hasta el primer progreso con `setGreedySchedulerEnabled(false)` (umbral: 5 s). Repetir pulsando Inicio justo después de «Iniciar indexación»: el trabajo empieza en tramos si no llega al primer plano, y lo recupera al volver a la app.

   En el emulador hacen falta los modelos instalados (onboarding de F03 o `adb push` como en F02); la ingesta usa la CPU, porque la GPU del emulador se bloquea (research R4).
2. Comprobar que la notificación muestra el progreso y que el proceso es un servicio en primer plano:

   ```bash
   $ADB shell dumpsys activity services $PKG | grep -i foreground
   ```
3. Pulsar Inicio y matar el proceso a mitad de los embeddings:

   ```bash
   $ADB shell "run-as $PKG kill -9 \$(pidof $PKG)"
   ```

   **No abrir la app.** Esperar y comprobar en el log `ingest done id=…` y que el proceso no queda congelado:

   ```bash
   $ADB shell dumpsys activity processes | grep isFrozen
   ```
4. Repetir matando durante el troceado de un PDF de 2.000 páginas y entre dos documentos. El cierre del documento dura milisegundos: lo cubre la interrupción determinista de `IndexingFlowTest`.
5. En la AVD API 33, ejecutar la prueba de descarga de F03 (`ModelDownloadSmokeTest`, con red) para comprobar que funciona sin el planificador en proceso.
6. En el emulador API 37:
   - simular la cuota de Android 16 con `am compat enable OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS $PKG` y comprobar que el trabajo sigue en tramos;
   - acortar el límite de `dataSync` con `device_config put activity_manager data_sync_fgs_timeout_duration 60000` y comprobar que el trabajo continúa.

**Esperado:**
- el documento acaba `done` con 430 fragmentos, sin duplicados ni huecos (SC-002);
- el trabajo nunca queda parado indefinidamente.

## 5. Mediciones de búsqueda

```bash
$ADB -s $PHONE shell am instrument -w -e class $PKG.data.rag.RagIndexBenchmarkTest $PKG.test/androidx.test.runner.AndroidJUnitRunner
```

**Esperado:** p95 vectorial y BM25 con 10k fragmentos y *m* = 0, como mucho 2 ms por encima de F04 (23,1 y 26,5 ms). Con *m* = 4.300 se registra la cifra (R7).

## 6. Recorrido manual en el teléfono

1. Importar un PDF, un TXT y un MD → «Iniciar indexación» → `Index`:
   - el anillo y «N / M» avanzan;
   - las etapas cambian;
   - aparece «Procesando ahora» y el tiempo restante tras el primer lote.
2. «Pausar indexación» → «En pausa» y «Reanudar». Cerrar la app y volver: sigue en pausa. Reanudar: continúa sin repetir.
3. Temperatura grave simulada:

   ```bash
   $ADB shell cmd thermalservice override-status 3
   ```

   Se pausa con la causa. Con `reset`, continúa sola.
4. Girar, salir y volver, bloquear el teléfono y forzar la detención desde Ajustes. Al abrir, la tanda se reprograma (R12).
5. Cola de Ingesta: «Indexando · N de M fragmentos», y los documentos indexados salen.
6. TalkBack en la pantalla de estado: anillo, etapas y anuncios.
7. Capturas en claro y en oscuro de los estados, para compararlas con el PNG 08.
