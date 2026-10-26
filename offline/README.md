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
