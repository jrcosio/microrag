# Quickstart: validar el Historial de chats (F13)

Guía de validación de extremo a extremo. Los detalles están en los contratos (`contracts/`), en el modelo de datos y en el wireframe aprobado.

## Requisitos

**Entorno de compilación:**
- JBR de Android Studio:

  ```
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```

- `adb`: `$HOME/Library/Android/sdk/platform-tools/adb`.

**Emuladores:** `MicroRAG_API33` (API 33) y `Pixel_10` (API 37), arrancados sin ventana solo mientras hagan falta y cerrados al terminar.

**Teléfono de referencia** (A063): **solo en la última fase**, con el recorrido acordado antes con el usuario. Allí:
- Historial muestra el título de la conversación del usuario: no se captura ni se vuelca (research R12);
- su conversación no se abre ni se borra;
- la conversación de prueba de F12 se borra desde Historial solo con su permiso.

## 1. JVM

```
./gradlew :app:testDebugUnitTest :app:lintDebug
offline/.venv/bin/python -I -m unittest discover -s offline/tests
offline/.venv/bin/python -I offline/gen_answer_golden.py --check
```

**Esperado:**
- **Historial:**
  - `HistoryGroupingTest`: límites de «Hoy», «Ayer», «Últimos 7 días» y meses; medianoche; fecha futura; zonas;
  - `HistoryTextsTest`: formatos en español e inglés, y en 12 y 24 h;
  - `HistoryViewModelTest`: tandas, estados, borrado y su fallo, peticiones de abrir y de «Nuevo chat», y recálculo de «hoy»;
  - `HistoryScreenTest`: estados, frases de TalkBack (U3), diálogo con y sin la línea extra, 200 % y horizontal;
- **chat:**
  - `DefaultChatSessionTest`: `openConversation`, `delete` bajo el cerrojo, `deletions`, y el mismo prompt al continuar (SC-004);
  - `ChatViewModelTest`: petición de Historial, `deletions`, borrado fallido y fichas inertes;
  - `ChatScreenTest`: «Historial» en «⋯» y fichas grises sin enlace;
- **resto:**
  - `NavigationTest`: «⋯» → Historial → abrir, «＋», Atrás y sin barra inferior;
  - `LayeringTest` (U1), `AppModulesTest` y `StringsParityTest`;
  - `AnswerGoldenTest` (426 casos) sin cambios;
- **escritorio:** las pruebas de Python pasan y el golden sigue al día. F13 no toca el escritorio.
- **Lint:** sin avisos nuevos. Queda el de LiteRT-LM 0.19.0, que ya estaba.

## 2. Emulador

```
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork
```

Lo mismo con `emulator-5556`.

**Esperado:**
- `SqliteChatRepositoryTest` ampliada: orden, empates, sin conversaciones vacías, título, total, `listChanges` y borrado aislado;
- `HistoryScreenAccessibilityTest` (ATF) en claro y en oscuro, al 200 % y en horizontal;
- `HistoryBenchmarkTest`: escribe su línea `history_bench` y borra su fichero;
- `SystemBackTest`: Atrás desde Historial vuelve al chat;
- las de F01–F12 pasan. En API 33 siguen las 4 fallos previos de Atrás desde la raíz (memoria de F12).

**Recorrido manual** (ruta de depuración `history`, o «⋯» → «Historial»):
1. Crear tres conversaciones de prueba sobre los documentos de prueba. Comprobar el orden, los grupos, el título y «N preguntas».
2. Abrir la más antigua: la conversación entera, en su última respuesta. Preguntar: sube a «Hoy».
3. Con una respuesta generándose, abrir otra: la primera queda «Generación cancelada».
4. Borrar la abierta en el chat, primero con «Cancelar» y después con «Borrar»: el chat queda vacío y la siguiente pregunta se guarda.
5. Borrar un documento citado: «Fuente ya no disponible» en la fila, y la ficha `[n]` gris y sin enlace.
6. «Borrar historial» en Ajustes: Historial queda vacío.
7. Repetir con la app en inglés, en modo oscuro, al 200 % y en horizontal. Capturas frente al wireframe.

## 3. Teléfono de referencia (última fase)

1. **Antes:** acordar el recorrido con el usuario. Despertar la pantalla y mantenerla despierta (`KEYCODE_WAKEUP` cada 60 s).
2. **Batería:**

   ```
   ANDROID_SERIAL=P31281001066 ./gradlew -q :app:installDebug :app:installDebugAndroidTest
   adb shell am instrument -w -e notAnnotation … com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
   ```

3. **Medida** (SC-001, SC-010): `HistoryBenchmarkTest` de forma individual. Leer solo su línea `history_bench` del log de la app.
4. **Recorrido acordado:**
   - con conversaciones nuevas sobre documentos de prueba;
   - sin capturar ni volcar Historial mientras liste la conversación del usuario;
   - solo comprobando etiquetas propias de la app.
5. **Red y registros:** 0 bytes para el uid de la app. En los registros solo hay ids y cifras.
6. **Limpieza:**
   - borrar las conversaciones y los documentos de prueba;
   - la de F12, solo con permiso;
   - dejar el teléfono como estaba (giro, letra, modo oscuro, idioma por app `[]`).
