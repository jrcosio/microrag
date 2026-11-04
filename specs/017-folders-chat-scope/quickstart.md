# Quickstart: validar carpetas y ámbito del chat (F17)

Guía de validación de extremo a extremo. Los detalles están en los contratos (`contracts/`) y en el modelo de datos.

## Requisitos

- **JBR de Android Studio:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **`adb`:** `$HOME/Library/Android/sdk/platform-tools/adb`.
- **Emuladores:** `MicroRAG_API33` y `Pixel_10`. API 32 solo si hace falta.
- **Escritorio:** `offline/.venv`.
- **Teléfono A063:** **solo en la última fase**, con las reglas de F11–F16 (`f11-phone-phase` y `f16-phone-pending` en la memoria). No se usan nunca los documentos ni la conversación del usuario: se trabaja con carpetas y documentos de prueba, que se borran al terminar.

## 1. Escritorio

```bash
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:** todo pasa, con `schema_version == 4`. Los índices del harness se siguen construyendo.

## 2. JVM

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

**Esperado:**
- **dominio:**
  - `FolderNamesTest`;
  - los casos de uso de carpetas (crear, renombrar, eliminar con mover o con borrar, y mover);
  - `ResolveScopeUseCaseTest` (guarda de D-065, lo que cae y la vuelta a «Toda la biblioteca»);
  - el último ámbito;
  - la disponibilidad dentro del ámbito;
- **datos:** `RagSchemaTest` (v4), el esquema de `chat.db` v2, y la sesión con el ámbito (`DefaultChatSessionTest`);
- **pantallas y ViewModels:** carpetas, carpeta, diálogos, detalle, Ingesta, ficha, selector y chat;
- **resto:** `NavigationTest`, `StringsParityTest`, `LayeringTest`, `AppModulesTest` y `DesignSystemConformanceTest`;
- **lint:** sin errores nuevos.

## 3. Emulador

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <emulador> install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s <emulador> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulador> shell am instrument -w -r -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner > build/instrumented.txt
```

Con el teléfono conectado no se usa nunca `connectedDebugAndroidTest`: siempre `-s` con el emulador.

**Esperado:**
- **instrumentadas:**
  - `ScopedSearchTest`, la puerta de R3: el modo en streaming, el top-k acotado igual al calculado a mano, y «Toda la biblioteca» igual que hoy;
  - `RagIndexMigrationTest` 3 → 4 con datos;
  - `RagIndexFoldersTest`;
  - `HybridRetrieverVisibilityTest` con ámbitos;
  - `SqliteChatRepositoryTest` con la migración 1 → 2;
  - ATF de las pantallas nuevas;
  - la batería de F01–F16.
- **recorrido con documentos de prueba:**
  1. Biblioteca con «General» tras actualizar.
  2. Crear «Legal» y «Motores», validar nombres, renombrar y ordenar.
  3. Mover documentos, e importar a una carpeta desde dentro y desde la Ingesta.
  4. Eliminar una carpeta moviendo sus documentos, y otra borrándolos.
  5. Chat: ficha, selector, carpeta entera, documento suelto y excluidos en gris.
  6. Las respuestas citan solo el ámbito.
  7. Historial: el ámbito se conserva. «Nuevo chat» sale con el último ámbito.
  8. Ámbito sin documentos listos, y avisos de K10.
  9. TalkBack, letra al 200 %, horizontal y modo oscuro.

## 4. Medidas (R11)

`RetrievalBenchmarkTest` con «Toda la biblioteca», con una carpeta de unos 1.000 fragmentos y con 5 documentos sueltos, sobre 10.000 fragmentos sintéticos. Se ejecuta en el emulador y en el teléfono (última fase), y el informe recoge el p50 y el p95. `RagIndexLibraryTest` mide la lista de carpetas con 1.000 documentos (SC-007).

## 5. Teléfono de referencia (última fase)

**Antes de empezar:**
- anotar los ajustes;
- quitar la pantalla de bloqueo y comprobarlo;
- escribir la salida de la batería en el teléfono.

**Durante la fase:**
- batería completa y medidas;
- recorrido acordado con el usuario, con carpetas y documentos de prueba. Su documento queda en «General» y su conversación con «Toda la biblioteca», sin abrirlos.

**Al terminar:**
- borrar lo de prueba;
- restaurar los ajustes;
- comprobar el idioma por app y la red.
