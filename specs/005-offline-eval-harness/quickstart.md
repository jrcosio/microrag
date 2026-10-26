# Quickstart — validación de F05

Las reglas están en `contracts/`; aquí solo se indica cómo ejecutar y qué esperar.

## 0. Preparación (Mac)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
python3.12 -m venv offline/.venv
offline/.venv/bin/pip install -r offline/requirements.txt
mkdir -p offline/models
ln -sf $HOME/MicroRAG-models/embeddinggemma-2-text-270m.litertlm offline/models/
ln -sf $HOME/MicroRAG-models/gemma-4-E2B-it.litertlm offline/models/
ln -sf $HOME/MicroRAG-models/gemma-4-E4B-it.litertlm offline/models/   # tras descargarlo (research R7)
E=offline/.venv/bin/python
```

## 1. Corpus y pruebas unitarias

```bash
$E -I offline/run_eval.py fetch-corpus      # solo descarga lo que falta; verifica huellas
$E -I offline/run_eval.py sources           # regenera corpus/SOURCES.md
$E -I -m unittest discover -s offline/tests
```

**Esperado:**
- todas las huellas coinciden;
- todas las pruebas Python pasan: las de F04 más las de troceado, recuperación, selector, prompt, juicio, métricas y conjunto de prueba.

## 2. Índices y conjunto de prueba

```bash
$E -I offline/run_eval.py build ref
$E -I offline/run_eval.py build ref        # segunda vez: mismo resultado (SC-001)
$E -I offline/run_eval.py validate-testset
```

**Esperado:**
- `rag_meta` del índice igual al que espera la app (SC-002);
- 0 errores de validación y recuentos ≥ 300 / 50 / 40, con ≥ 100 revisadas (SC-003).

## 3. Ablaciones y generación

```bash
$E -I offline/run_eval.py ablations         # recuperación, bloques y vecinos
$E -I offline/run_eval.py generate chosen --llm E4B
$E -I offline/run_eval.py report            # → offline/reports/eval-report.md
```

**Esperado:**
- cada variante de recuperación tarda < 10 min (SC-006);
- el informe tiene todas las ablaciones frente a los umbrales (SC-004);
- la configuración elegida tiene un prompt p95 ≤ 2.000 tokens (SC-005).

## 4. App

```bash
./gradlew :app:testDebugUnitTest --tests '*RagParametersTest' :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

**Esperado:**
- `RagParameters` coincide con `offline/config/rag_parameters.json` (SC-007);
- todo en verde.

**Si cambia `dims`:** se regenera el golden de embeddings y se ejecuta `EmbeddingGoldenTest` en el teléfono (F04 quickstart §3).
