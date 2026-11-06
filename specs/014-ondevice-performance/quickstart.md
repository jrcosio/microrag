# Quickstart: validar F14

Guía para comprobar F14 de principio a fin. Los detalles están en los contratos (`contracts/`) y en `data-model.md`.

## 0. Entorno

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
cd offline && source .venv/bin/activate && pip install -r requirements.txt   # litert-lm-api 0.19.1, snowballstemmer
```

## 1. Runtimes (R1)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
cd offline && python -m pytest -q && python run_eval.py ablations        # mismas cifras que F20 (FR-038)
```

- En los emuladores, la batería instrumentada sin modelos, con `am instrument` y `-e notAnnotation …RequiresNetwork`. Incluye `RagIndexInt8Test`.
- En el escritorio, regenerar `v4` con 0.19.1 y juzgar las respuestas que cambian (D-090).
- **Sesión corta en el A063** (con permiso, D-090): `embedding_golden` y las goldens de respuesta, sin abrir la app ni tocar datos del usuario.
- **Resultado esperado:** todo en verde. Si no, se vuelve a 0.18.1 y 1.1.2 y se registra, antes de construir las palancas.

## 2. INT8 en la app (US2)

- **JVM:** `QuantizedIndexMaintainerTest` y la selección de ruta en `DefaultRagIndex`.
- **Emulador:** `RagIndexInt8Test` (contrato I5), los 7 casos.
- **Resultado esperado:** INT8 solo con el índice al día y precargado; cualquier cambio vuelve a la exacta; un fallo no rompe la búsqueda.

## 3. Escritorio (R2, R8–R13)

```bash
python run_eval.py bench-bank n=10000 && python run_eval.py bench-bank n=50000 && python run_eval.py bench-bank n=100000
python run_eval.py bench-pages m=1000 && python run_eval.py bench-pages m=5000 && python run_eval.py bench-pages m=10000
python run_eval.py numeric-set                       # revisión manual de las ~60 preguntas
git add offline/config/f14_rules.json && git commit  # reglas congeladas ANTES de evaluar
python run_eval.py token-review
python run_eval.py numeric-review variant=v4 precision=default   # y fp32, y v4-cifras
python run_eval.py template-review variants=v4,v4-cifras
python run_eval.py ablations --base max_blocks=3
python run_eval.py visual-followup-review variant=joined          # new-only, best-of-both
python run_eval.py crosslang-review variant=lang-normalizer       # lang-normalizer-weighted
python run_eval.py order-review
```

**Resultado esperado:** cada palanca con su cifra frente a su regla, en `offline/reports/eval-report.md` («Rendimiento (F14)»).

## 4. Teléfono (con permiso del usuario; R15)

1. **Preparación:**
   - anotar los ajustes;
   - `svc power stayon usb` y `wm dismiss-keyguard`;
   - `BenchDirsTest`, que crea `dev-bench` sin abrir la app.
2. **Copiar** con `adb push` los bancos a `…/files/dev-bench/` y la 270M y E4B a `…/files/dev-models/`.
3. **`PrefixCacheGoldenTest`** (las goldens de los runtimes ya pasaron en la sesión corta).
4. **Bancos,** uno por uno, con `nohup am instrument -w -r -e class … -e f14.variant … > /data/local/tmp/f14-….txt &`:
   - `SearchScaleBenchmarkTest`, `PageSearchBenchmarkTest`, `EmbedderComparisonBenchmarkTest` y `StartupBenchmarkTest`;
   - `IndexingBenchmarkTest` y `VisualIngestBenchmarkTest` (ritmo de la ingesta);
   - `GenerationBenchmarkTest` (base, prefix, mtp, fp32, v4-cifras y e4b);
   - `NumericFidelityBenchmarkTest`;
   - `SustainedChatBenchmarkTest`: la base, y después con las palancas adoptadas;
   - `VisualChatBenchmarkTest` (detalles, sostenido y recarga).
5. **Copiar los informes:** `run-as … cat files/reports/f14-*.json > offline/build/device/…`, y después `python run_eval.py device-report in=offline/build/device/`.
6. **Decidir** con `f14_rules.json`:
   - poner los interruptores adoptados en `RagParameters`;
   - repetir la batería instrumentada y la sesión sostenida final.
7. **Limpieza:**
   - `BenchCleanupTest`;
   - comprobar que no quedan `dev-bench`, `cacheDir/bench`, la 270M, E4B ni informes (`f14-*` y `visual-chat-*`);
   - restaurar los ajustes y `stay_on = 0`.

**Resultado esperado:** `benchmarks.md` completo (SC-001), SC-002–SC-010 con cifras o decisiones, y el teléfono como estaba, con el modo activado.

## 5. Cierre

- **DECISIONS:** INT8, bloques, perfil, primer token (D-057), fp16, estimador, seguimiento con páginas, recarga y cada palanca.
- **Constitución 1.2.1,** si se adoptan los runtimes.
- **Documentos:** la nota en el informe del RAG móvil, FEATURES y `report.md`.
