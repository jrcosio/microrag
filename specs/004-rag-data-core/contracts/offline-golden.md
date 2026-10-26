# Contrato — Pipeline offline mínimo y ficheros golden

## Entorno (`offline/`)

| Elemento | Valor |
|---|---|
| Python | 3.12 (`python3.12 -m venv offline/.venv`; probado en macOS arm64) |
| Dependencias | `requirements.txt` con **una sola** línea: `litert-lm-api==0.18.1` |
| Modelo | `offline/models/embeddinggemma-2-text-270m.litertlm` (ignorado por git). Se copia o se enlaza desde `$HOME/MicroRAG-models`. El generador verifica su SHA-256 contra el valor de `ModelCatalog` antes de usarlo |
| Ejecución | Siempre `offline/.venv/bin/python -I` (aislado) |

## Ficheros

```text
offline/
├── README.md                    # cómo regenerar y comprobar
├── requirements.txt             # litert-lm-api==0.18.1
├── normalizer.py                # port exacto de SpanishText v1 (solo biblioteca estándar)
├── embedding_text.py            # construcción de entradas (prefijos y cabecera) y truncado a 256 con L2
├── gen_normalizer_golden.py     # inputs → golden/normalizer_golden.jsonl
├── gen_embedding_golden.py      # embedding_texts → golden/embedding_golden.jsonl
├── tests/
│   ├── test_normalizer.py       # ejemplos del informe y golden (coincidencia exacta)
│   └── test_embedding_text.py   # construcción de entradas y truncado
└── golden/
    ├── normalizer_inputs.txt
    ├── normalizer_golden.jsonl  # ≥ 300 pares
    ├── embedding_texts.jsonl    # 50 textos (25 consultas y 25 documentos con cabecera)
    └── embedding_golden.jsonl
```

## Reglas

| # | Regla |
|---|---|
| O1 | `normalizer.py` replica `SpanishText` v1, incluidas las sutilezas de research R4: unidades UTF-16 para las longitudes, `Nd` del plano básico para los dígitos y el escáner de tokens |
| O2 | `gen_normalizer_golden.py` es determinista: el mismo `normalizer_inputs.txt` produce exactamente el mismo `.jsonl`, con orden estable y sin entradas duplicadas. Falla si salen menos de 300 pares |
| O3 | `gen_embedding_golden.py` verifica el SHA-256 del modelo. Usa `Backend.CPU()`, `max_input_length=512` y `EmbeddingOptions(normalize=False)`. Construye las entradas como `EmbeddingPrefixes`, trunca a 256 con L2 como `EmbeddingMath` y escribe la cabecera `meta` |
| O4 | Los golden son ficheros de texto en el repositorio. Las pruebas de Android los leen como *assets* de `androidTest` (`sourceSets.androidTest.assets` añade `../offline/golden`) y la JVM, por ruta. No hay copias |
| O5 | Las pruebas Python se ejecutan con `offline/.venv/bin/python -I -m unittest discover -s offline/tests` y no necesitan el modelo, salvo `gen_embedding_golden.py` |

## Pruebas en la app que consumen los golden

| Prueba | Dónde | Criterio |
|---|---|---|
| `SpanishTextTest` | JVM | Ejemplos del informe y propiedades (dígitos intactos, palabras vacías, escapado FTS) |
| `NormalizerGoldenTest` | JVM | 100 % de coincidencia exacta con `normalizer_golden.jsonl` |
| `NormalizerGoldenInstrumentedTest` | Dispositivo (emulador y teléfono) | 100 % de coincidencia exacta (ICU de Android) |
| `EmbeddingGoldenTest` | Dispositivo, `@RequiresModels` | La entrada construida por `EmbeddingPrefixes` es igual a `input`. Coseno ≥ 0,99 por par en CPU y en GPU (D-030). Informa del mínimo y la media por backend. Sin GPU: «no disponible» |
