# Quickstart: validar la viabilidad del RAG visual (F18)

Guía para comprobar F18 de extremo a extremo. Los detalles están en [research.md](research.md), [data-model.md](data-model.md) y los [contratos](contracts/).

## 0. Requisitos

- **Java para Gradle:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Modelos fijados** en `~/MicroRAG-models`, con enlaces en `offline/models/`. Hay que añadir el de la 440M:

  ```sh
  ln -s ~/MicroRAG-models/embeddinggemma-2-text-vision-440m.litertlm offline/models/
  ```
- **Python:** `offline/.venv` con `offline/requirements.txt`, que ahora incluye `pypdfium2==5.14.0` y `pillow==12.3.0`.

## 1. Escritorio: corpus y conjunto de prueba

```sh
cd offline
.venv/bin/python -I run_eval.py fetch-corpus corpus=visual        # verifica el SHA-256 de cada documento
.venv/bin/python -I run_eval.py visual-photos                     # fotos simuladas, deterministas
.venv/bin/python -I run_eval.py sources corpus=visual             # corpus_visual/SOURCES.md
.venv/bin/python -I run_eval.py visual-raster px=768,1024,1536
.venv/bin/python -I run_eval.py visual-build px=768,1024,1536 tokens=70,140
.venv/bin/python -I run_eval.py visual-validate                   # V1–V8: ≥ 100 aceptadas, ≥ 60 % visuales
```

**Esperado:**
- el corpus visual, en `offline/corpus_visual/`, tiene 18 PDF y 15 imágenes con al menos 300 páginas, más las fotos simuladas, cada uno con su licencia en `SOURCES.md`;
- la validación pasa.

## 2. Escritorio: regla congelada y ablaciones

```sh
.venv/bin/python -I run_eval.py visual-freeze                      # testset_sha256 en config/visual_rules.json
git add offline/config/visual_rules.json && git commit …           # antes de evaluar (FR-025)
.venv/bin/python -I run_eval.py visual-retrieve mode=texto,visual,ambos px=768,1024,1536 tokens=70,140 dim=256,512
.venv/bin/python -I run_eval.py visual-decide --stage retrieval     # resolución, tokens y dimensión
.venv/bin/python -I run_eval.py visual-generate answer=texto,paginas,mezcla pages=1,2,3 budget=70,140,280 tiles=no,si
.venv/bin/python -I run_eval.py visual-manual                      # comprobación del juez
.venv/bin/python -I run_eval.py visual-report
```

**Esperado:**
- sin `visual_rules.json` congelado, `visual-retrieve` y `visual-generate` se niegan a correr;
- con «Texto», las cifras de F05 no cambian (`run_eval.py ablations` con la configuración de D-034);
- el informe tiene las tablas por modo, variante y tipo de pregunta.

## 3. Escritorio: conjunto del dispositivo

```sh
.venv/bin/python -I run_eval.py visual-device-set                  # build/visual/device-set/ con manifest.json
```

## 4. App: build y pruebas sin teléfono

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:**
- **JVM y Python en verde**, incluido `LayeringTest` con las reglas de debug;
- **release sin nada de F18:**

  ```sh
  apkanalyzer dex packages app/build/outputs/apk/release/*.apk | grep -c feasibility.visual   # 0
  ```

  Los permisos son los de F17, la red solo se usa en la descarga (`NetworkConfinementTest`), y `ManifestInvariantsTest` sigue en verde;
- **emuladores** `Pixel_10` y `MicroRAG_API33`: la batería instrumentada completa, con `-s` y sin modelos. `DebugPageRasterizerTest` y las pruebas JVM del diagnóstico pasan.

## 5. Teléfono de referencia (última fase, con permiso del usuario)

Antes de empezar:
- anotar los ajustes del teléfono;
- quitar el bloqueo (`wm dismiss-keyguard`) y mantener la pantalla despierta;
- la salida, siempre en el teléfono (`nohup am instrument … > /data/local/tmp/…`).

1. **Modelos:** copiar la 440M a `dev-models` e importarla desde el diagnóstico, con el procedimiento de F02 (quickstart §3–4 de F02). Gemma y la 270M ya están instaladas.
2. **Conjunto del dispositivo:**

   ```sh
   adb push offline/build/visual/device-set/. /sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-visual/
   ```

   «Viabilidad visual» tiene que marcarlo como verificado.
3. **Golden e identidad:**

   ```sh
   am instrument -w -e class com.lisdatasolutions.microrag.rag.runtime.EmbeddingVariantGoldenTest …
   ```

   **Esperado:** las huellas comprobadas, ≥ 0,99 por par y un mínimo ≥ 0,9999 frente a la 270M, en `files/reports/variant-golden-<ts>.json`.
4. **Bloques visuales:**

   ```sh
   am instrument -e class …VisualFeasibilityBenchmarkTest -e blocks GEMMA_INFO,EMBED_TEXT,EMBED_IMAGE,PARITY,RASTER,IMAGE_DECODE,PROJECTION,GEMMA_PAGES,READING …
   ```

   Después, la etapa B: copiar el informe de la etapa A, calcular las candidatas en el escritorio y lanzarlas.

   ```sh
   offline/.venv/bin/python -I offline/run_eval.py visual-decide --stage candidates offline/build/visual/phone-report.json
   am instrument -e class …VisualFeasibilityBenchmarkTest -e blocks GEMMA_PAGES -e candidates <json> …
   ```
5. **Batería completa** de la app, como en cada fase del teléfono.
6. **Informe:**

   ```sh
   adb exec-out run-as com.lisdatasolutions.microrag cat files/reports/visual-<ts>.json > offline/build/visual/phone-report.json
   ```

   Lo mismo con el `.md`, que va a `specs/018-visual-rag-feasibility/benchmarks.md`.
7. **Limpieza:**
   - borrar `dev-visual` y las salidas de `/data/local/tmp`;
   - la 440M se queda o se borra según diga el usuario;
   - restaurar los ajustes anotados.

## 6. Escritorio: paridad y decisión

```sh
.venv/bin/python -I run_eval.py visual-parity build/visual/phone-report.json
.venv/bin/python -I run_eval.py visual-manual --decisive            # revisión decisiva (D-075)
.venv/bin/python -I run_eval.py visual-text-loss                    # 323 de F05, solo las que cambian
.venv/bin/python -I run_eval.py visual-decide build/visual/phone-report.json
.venv/bin/python -I run_eval.py visual-report
```

**Esperado:**
- **paridad:** coseno ≥ 0,99 con los mismos bytes (SC-005);
- **decisión:** aplica la regla de D-073 y da «sí» o «no», con los parámetros elegidos según R12;
- **si el primer token con 1 página pasa de 8 s:** se para y se pregunta al usuario.

Después:
- se registran las decisiones en DECISIONS (FR-028);
- si sale el sí, se redacta la propuesta de enmienda de la constitución (FR-029);
- se actualiza FEATURES (FR-030).
