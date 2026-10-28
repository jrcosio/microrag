# Quickstart: validar la Biblioteca y el detalle (F08)

Requisitos:
- el teléfono de referencia (A063) conectado, con los modelos de F03 y los documentos de prueba de F07;
- el emulador `Pixel_10` (API 37) y la AVD `MicroRAG_API33`;
- `JAVA_HOME` apuntando al JBR de Android Studio.

## 1. Pruebas Python

```bash
cd offline && python -m unittest discover -s tests
```

`test_appconfig` espera el esquema v3 (R1). Antes de evaluar, reconstruir los índices de evaluación (README de `offline/`).

## 2. Build, lint y pruebas JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

Nuevas o ampliadas:
- `DocumentNamesTest`;
- `DefaultLibraryRepositoryTest`;
- `LibraryViewModelTest` y `DocumentViewModelTest`;
- `LibraryScreenTest` y `DocumentScreenTest`;
- `IngestRunnerTest` (sombras);
- `DefaultImportQueueRepositoryTest`;
- `RagSchemaTest`.

## 3. Pruebas instrumentadas

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

En el emulador API 37, en la AVD API 33 y en el teléfono. Incluye:
- `RagIndexMigrationTest`, ampliada (v2 → v3 con datos reales y relleno de `name_norm`);
- `RagIndexLibraryTest` (página, recuento, filtros, búsqueda y sombras sobre SQLite real);
- `ReindexFlowTest` (sombra, búsquedas durante el reindexado y sustitución atómica);
- `LibraryStrictModeTest`;
- `LibraryScreenAccessibilityTest` y `DocumentScreenAccessibilityTest`;
- `LibraryBenchmarkTest` y `DocumentDeleteBenchmarkTest` (cifras en el teléfono, R11).

## 4. Verificación temprana en el teléfono (antes de las pantallas)

1. **Migración:** instalar la versión con el esquema v3 sobre los datos de F07. La app abre la biblioteca sin incompatibilidad, y `name_norm` queda relleno en todos los documentos.
2. **Borrado grande:** `DocumentDeleteBenchmarkTest`: eliminar y quitar del índice 4.300 fragmentos. **Si pasa de 5 s, se informa en el cierre.**
3. **Reindexado seguro:**
   1. reindexar un documento indexado de 430 fragmentos;
   2. mientras dura, buscar y comprobar que devuelve la versión anterior;
   3. matar el proceso a mitad, sin abrir la app;
   4. al terminar, el documento tiene los mismos fragmentos y nunca hubo una mezcla.

## 5. Recorrido manual en el teléfono

1. **Biblioteca:** todos los documentos, del más reciente al más antiguo. Buscar «anual» y «guia» (encuentra «Guía»). Filtrar «Indexados» y «Pendientes». Abrir uno, volver y girar: búsqueda, filtro y posición se conservan.
2. **Inclusión:** apagar un documento; hacer una búsqueda en la que antes aparecía (diagnóstico de F02 o prueba instrumentada); ya no aparece. Encenderlo; vuelve.
3. **Detalle de un PDF indexado:** cifras reales y vista previa con «Página N». En un TXT, sin chip de página.
4. **Cita:** abrir el detalle con un `chunkId` mediante la ruta de depuración:

   ```bash
   adb shell am start -n com.lisdatasolutions.microrag/.MainActivity \
     --es com.lisdatasolutions.microrag.debug.EXTRA_ROUTE document --el com.lisdatasolutions.microrag.debug.EXTRA_ID <id>
   ```

   El `chunkId` va en un extra nuevo de la ruta de depuración. El fragmento aparece resaltado, con las frases vecinas.
5. **Quitar del índice:** el documento queda «Fuera del índice», no aparece en la cola de Ingesta, e «Iniciar indexación» no lo toca. Después, «Reindexar» lo deja indexado.
6. **Eliminar los documentos de prueba de F07** (ids 2–9) desde la app. Medir el de 4.300 fragmentos. La Biblioteca, la Ingesta y el Estado de indexación ya no los muestran.
7. **TalkBack** en las dos pantallas, a cargo del usuario.
8. **Capturas** en claro y en oscuro de los estados B1–B8 y D1–D9, para compararlas con los PNG 06 y 07.
