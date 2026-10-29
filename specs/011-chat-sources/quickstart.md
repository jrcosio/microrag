# Quickstart: validar el chat con fuentes (F11)

Guía de verificación de extremo a extremo. Los detalles están en los contratos (`contracts/`) y en el modelo de datos.

**Entorno:**

| Herramienta | Ruta o valor |
|---|---|
| Gradle | `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` |
| adb | `$HOME/Library/Android/sdk/platform-tools/adb` |
| Python | `offline/.venv/bin/python -I` |
| Emulador | `emulator-5554` (Pixel_10, API 37) |
| Teléfono de referencia | A063 (`P31281001066`), en la última fase |

## 1. Escritorio: plantilla y golden

```bash
offline/.venv/bin/python -I offline/run_eval.py template-review     # ≈ 11 min con dos variantes; reanudable
offline/.venv/bin/python -I offline/run_eval.py report              # regenera eval-report.md con la sección nueva
offline/.venv/bin/python -I offline/gen_answer_golden.py            # escribe golden/answer_golden.jsonl
offline/.venv/bin/python -I offline/gen_answer_golden.py --check    # sin cambios
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Resultado esperado:**
- la tabla de la revisión de la plantilla en `offline/reports/eval-report.md`, y su decisión en DECISIONS;
- el golden al día y todas las pruebas de Python en verde.

**Si una variante se adopta:**
- `prompt_template` cambia en el JSON y en `RagParameters`;
- se vuelve a generar el golden.

## 2. Build, lint y pruebas JVM

```bash
./gradlew assembleDebug assembleRelease lintDebug lintRelease testDebugUnitTest
```

**Resultado esperado:**
- lint: «No issues found»;
- todas las pruebas JVM en verde. Entre ellas:
  - `PromptBuilderTest`, `AnswerReadingTest` y `AnswerGoldenTest` (las 426 filas);
  - `DefaultChatSessionTest`, `ChatDuringIndexingTest` y `EngineAnswerEngineTest`;
  - `ObserveChatAvailabilityUseCaseTest` y `ResolveSourcesUseCaseTest`;
  - `ChatViewModelTest`, `AnswerFormatterTest` y `ChatScreenTest`;
  - `NavigationTest`, `DocumentScreenTest`, `EngineMemoryCallbacksTest`, `RagParametersTest`, `AppModulesTest` y `LayeringTest`.

## 3. Pruebas instrumentadas en el emulador

Con los dos APK instalados, sin modelos ni red:

```bash
adb -s emulator-5554 shell am instrument -w \
  -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

**Resultado esperado:** todas en verde. Las nuevas son:
- `SqliteChatRepositoryTest`;
- `RagIndexChatStateTest`;
- `ChatScreenAccessibilityTest`: todos los estados, en claro y en oscuro, horizontal y fuente al 200 %.

`ChatScreenLayoutTest` se salta fuera del A063.

**Con modelos** (`ChatBenchmarkTest`): solo si Gemma está preparada en el emulador; si no, se salta. Hace 3 preguntas en CPU, sin umbrales, para comprobar que el camino funciona.

## 4. Recorrido en el emulador

Con la biblioteca de prueba del emulador:
- **Sin Gemma:** el chat muestra el bloqueo de modelos (C5), y «Completar configuración» abre la Preparación.
- **Biblioteca vacía** (C3): «Importar documentos» abre la Ingesta.
- **Documentos sin indexar y sin tanda** (C4): «Ver indexación».
- **Tanda en marcha** (C14): el aviso y el compositor desactivado. Al pausar la tanda, el chat queda disponible.
- **«⋯»:**
  - «Nuevo chat» abre uno vacío;
  - «Borrar conversación» pide confirmación (C15).
- **Teclado:** el compositor queda encima y la barra inferior se oculta, en vertical y en horizontal.
- **Registro:** filtrado a la etiqueta `MicroRAG`, solo cifras.

Si Gemma está preparada en el emulador, también:
- una pregunta completa con fuentes;
- detener;
- copiar;
- una fuente → detalle con el fragmento visible → Atrás;
- la negativa.

## 5. Release

- `assembleRelease`, sin permisos ni clientes de red nuevos.
- En release no se registra nada (`DefaultAppLogger(false)`).
- Los textos del chat están en recursos, en español y en inglés.

## 6. Última fase: teléfono de referencia

En este orden:
1. **Instalar** los dos APK, sin borrar datos (`install -r`), y ejecutar las instrumentadas sin modelos (§3). Deben quedar en verde.
2. **`ChatBenchmarkTest`** (`@RequiresModels`), con el corpus de evaluación en un índice temporal. Se lee la línea `chat-benchmark` del registro, filtrado a `MicroRAG`.
   - Esperado: p95 del primer token ≤ 5.000 ms (SC-004). La primera pregunta, con Gemma sin cargar, se informa aparte.
   - Si pasa de 5 s, se para y se consulta (FR-045).
3. **`ChatScreenLayoutTest`:** el compositor visible con el teclado y la barra inferior real.
4. **Recorrido del chat en el teléfono:** solo con el acuerdo del usuario sobre cómo hacerlo (research R15), porque su biblioteca tiene un documento propio.
   - Capturas sin la barra de estado.
   - Ficheros de prueba en una carpeta propia, que se borra al terminar.
   - Nunca se muestra su documento.
5. **Cierre:**
   - informe con las cifras, el dispositivo y las versiones (constitución VI);
   - diferencias frente al PNG 04;
   - FEATURES y DECISIONS al día.
