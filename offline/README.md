# Pipeline offline (Python)

Herramienta de escritorio de MicroRAG. Solo sirve para evaluar, ajustar parámetros y generar los ficheros *golden* que comparten el móvil y el escritorio (constitución III y VI, D-002). No construye ni distribuye índices para la app.

En F04 genera los dos golden de compatibilidad:
- `golden/normalizer_golden.jsonl`: el normalizador español, idéntico en Kotlin y en Python;
- `golden/embedding_golden.jsonl`: vectores de EmbeddingGemma 2 270M de referencia.

Las reglas están en `specs/004-rag-data-core/contracts/offline-golden.md`.

## Entorno

- Python 3.12, en un entorno virtual propio.
- Una sola dependencia, fijada en `requirements.txt`: `litert-lm-api==0.18.1` (se importa como `litert_lm`).
- Se ejecuta siempre con `offline/.venv/bin/python -I`.

```bash
python3.12 -m venv offline/.venv
offline/.venv/bin/pip install -r offline/requirements.txt
```

El modelo no se versiona. Se enlaza o se copia en `offline/models/` desde la copia local de los modelos verificados:

```bash
mkdir -p offline/models
ln -sf "$HOME/MicroRAG-models/embeddinggemma-2-text-270m.litertlm" offline/models/
```

## Regenerar y comprobar

```bash
offline/.venv/bin/python -I offline/gen_normalizer_golden.py   # → golden/normalizer_golden.jsonl (≥ 300 pares)
offline/.venv/bin/python -I offline/gen_embedding_golden.py    # → golden/embedding_golden.jsonl (50 vectores)
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

- Los dos generadores son deterministas: volver a ejecutarlos no cambia `git diff`.
- `gen_embedding_golden.py` comprueba antes el SHA-256 del modelo y falla si no es el del catálogo de la app.
- Las pruebas Python no necesitan el modelo.

La app comprueba los mismos ficheros:
- `NormalizerGoldenTest` (JVM) y `NormalizerGoldenInstrumentedTest` (dispositivo): coincidencia exacta del normalizador;
- `EmbeddingGoldenFileTest` (JVM): modelo, entradas y normas del golden de embeddings;
- `EmbeddingGoldenTest` (teléfono, CPU y GPU): coseno ≥ 0,99 por par.

## Cambiar el normalizador

`normalizer.py` es un port exacto de `SpanishText.kt`. Un cambio que altere la salida obliga a:
1. cambiar los dos lados y subir `VERSION` en ambos (`SpanishText.VERSION` y `normalizer.VERSION`);
2. regenerar `golden/normalizer_golden.jsonl`;
3. fijar su nueva huella en `NormalizerGoldenTest` y en `tests/test_normalizer.py` (regla O6).

Los índices construidos con la versión anterior quedan incompatibles hasta reindexar (`rag_meta.normalizer_version`).

## Cambiar el modelo de embeddings o LiteRT-LM

Se regenera `golden/embedding_golden.jsonl` con el nuevo modelo o la nueva versión de `litert-lm-api`, y se vuelve a ejecutar `EmbeddingGoldenTest` en cada teléfono de prueba (constitución VI).
