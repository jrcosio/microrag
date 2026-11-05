# Quickstart: validar el chat con páginas (F20)

**Requisitos:**
- `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` para Gradle y `apkanalyzer`;
- los emuladores `Pixel_10` (API 37) y `MicroRAG_API33`, y `MicroRAG_API32` para el recorrido con el modo activo (el procedimiento de F19);
- el teléfono A063 solo en la última fase, con permiso del usuario. Ya está en la 440M con el modo activado (D-082) y no tiene la 270M.

## 1. Batería sin dispositivo

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
offline/.venv/bin/python -I -m unittest discover -s offline/tests
offline/.venv/bin/python -I offline/gen_visual_prompt_golden.py --check
```

**Esperado:**
- JVM en verde, con las pruebas nuevas de R21, `VisualPromptGoldenTest` incluida;
- lint solo con los 2 avisos de versiones de siempre;
- Python en verde, y la golden de la v5-visual sin cambios.

## 2. Regresión del texto (FR-023, SC-001)

```sh
cd offline && .venv/bin/python -I run_eval.py ablations && .venv/bin/python -I run_eval.py order-review
```

**Esperado:** `ablations.json` y `order_review.json` idénticos a los del cierre de F19, salvo los segundos.

## 3. Instrumentadas en los emuladores

Siempre con `adb -s <emulador>`: el teléfono puede estar conectado y no hay que tocarlo. Se instalan los dos APK con `adb -s … install -r -t` y se lanza:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e notAnnotation com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

**Esperado:**
- en `Pixel_10`, todo en verde;
- en API 33, solo los 4 fallos de siempre de Atrás desde la raíz;
- entre las pruebas, la migración de `chat.db` 2 → 3, `pageSearch`, las fuentes de página y la accesibilidad;
- las pruebas con Gemma (`GemmaVisionSmokeTest`, `VisualChatBenchmarkTest`) se saltan, porque los emuladores no tienen Gemma.

## 4. Recorrido en el emulador

Sin Gemma, el chat no responde. El recorrido cubre lo que no necesita el modelo:
1. **Ajustes:**
   - con el modo apagado, no se ve «Detalle de las páginas»;
   - con el modo activo (el procedimiento de F19 en el API 32), se ve con «Equilibrado · unos 11,5 s»;
   - en la pantalla de detalle, cambiar y guardar.
2. **La página en el detalle y el visor,** sobre un PNG indexado, con la ruta de depuración `document` y un `EXTRA_PAGE` nuevo:
   - la vista previa de la página;
   - el visor con el zoom, el doble toque y «Atrás»;
   - el giro.
3. **La tarjeta con miniatura, el Historial y las fuentes que ya no están** se comprueban con las pruebas de pantalla e instrumentadas. La conversación real se comprueba en el teléfono (§5).
4. **Al 200 % y en oscuro,** con TalkBack: Ajustes, «Detalle de las páginas», la tarjeta, la vista previa y el visor.

## 5. Teléfono de referencia (última fase, con permiso)

**Antes de empezar:**
- anotar los ajustes del teléfono;
- `wm dismiss-keyguard`;
- la salida, siempre en `/data/local/tmp`;
- el documento y la conversación del usuario nunca se abren, se vuelcan ni se capturan.

**Pasos:**
1. **La batería completa,** sin `@RequiresNetwork`, con `GemmaVisionSmokeTest`.
2. **`VisualChatBenchmarkTest`:** el primer token p50 y p95 con cada detalle y con el modo activo sin páginas, más el pico de RSS (SC-003). Si las esperas difieren de las de F18, se corrigen `PageDetail` y los textos, con su entrada en DECISIONS.
3. **SC-004:**
   - un escaneado y una imagen de prueba, copiados a `Download` e importados;
   - una pregunta sobre cada uno, comprobando la cita a su página, la miniatura y el visor;
   - se buscan solo las etiquetas de prueba.
4. **Limpieza:**
   - se borran los documentos y la conversación de prueba, y los ficheros de `Download`, `/data/local/tmp` y `files/reports`;
   - el modo se deja activado, como está;
   - se restauran los ajustes.
