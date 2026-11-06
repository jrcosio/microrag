# Contrato: harness del escritorio (R2, R8, R9, R10, R12, R13)

## H1. Dependencias

- **`litert-lm-api==0.19.1`** y **`sqliteai-vector==1.2.0`** (R1, a la par que la app). Al subirlas se comprueba:
  - que `embedding_golden` y el resto de pruebas de `offline/tests` pasan;
  - que la recuperación de `ablations` da las mismas cifras de F20 (FR-038). Si no, se vuelve a 0.18.1 y 1.1.2 y se registra.
- **`v4` de referencia regenerada (D-090):** las 426 respuestas de `v4` se generan otra vez con 0.19.1, y las que cambian se juzgan a mano (D-033). Esa es la base de las palancas, para no mezclar el efecto del runtime con el de cada una.
- **`snowballstemmer==3.1.1`** (nueva, puro Python, comprobada en PyPI), solo para la variante inglesa (R12).

## H2. Subcomandos nuevos de `run_eval.py`

| Subcomando | Qué hace | Salida |
|---|---|---|
| `bench-bank n=<N> [seed=2026]` | Construye el banco de N fragmentos (R2), con caché de embeddings por fragmento | `offline/build/bench/bank-<N>/` |
| `bench-pages m=<M> [sigma=…]` | Construye el banco de páginas (R10) | `offline/build/bench/pages-<M>/` |
| `numeric-set` | Propone las preguntas de cifras desde los fragmentos con más cifras; la revisión las fija | `offline/testset/numeric/questions.jsonl` |
| `numeric-review variant=<v4\|v4-cifras> precision=<default\|fp32>` | Genera en el Mac, detecta cifras deformadas y escribe la revisión | `offline/reports/numeric-review.csv` |
| `token-review [a= b= c=]` | Ajusta el estimador, informa el error de los dos estimadores y los prompts y respuestas que cambian | sección del informe y `token-review.csv` |
| `visual-followup-review variant=<joined\|new-only\|best-of-both>` | La regla de seguimiento con páginas sobre `followups.jsonl` | sección del informe |
| `crosslang-review variant=<lang-normalizer\|lang-normalizer-weighted>` (amplía) | Las variantes de R12 | sección del informe y CSV de revisión |
| `order-review` (sin cambios) | Se repite tras las palancas que cambian el contexto | sección del informe |
| `ablations --base max_blocks=3` (amplía) | La variante de 3 bloques (R13) | sección del informe |
| `template-review variants=v4,v4-cifras` (amplía) | La plantilla de cifras con la regla de D-088; con `v4-corta` solo si el prefijo reutilizado no se adopta (D-090) | sección del informe |
| `device-report in=offline/build/device/` | Lee los JSON del teléfono y escribe las tablas de `benchmarks.md` | `specs/014-ondevice-performance/benchmarks.md` (secciones generadas) |

## H3. Reglas (`offline/config/f14_rules.json`, en git antes de evaluar)

```json
{
  "first_token_lever": { "decided_by": "short-bench-20-alternating", "confirmed_by": "sustained-final",
                          "min_p95_gain_s": 0.3, "max_correct_drop_points": 1.0, "reference": "v4 regenerated with the adopted runtime",
                          "min_refusal": 0.85, "min_citations": 0.90 },
  "fp32": { "max_p95_rise_s": 0.5 },
  "numeric_template": { "max_deformed_ratio_vs_v4": 0.5, "quality": "first_token_lever" },
  "token_estimate": { "max_p95_real_over_estimated_numeric": 1.0, "quality": "first_token_lever" },
  "speculative": { "min_decode_gain": 0.20, "max_p95_rise_s": 0.3, "greedy_identical": true },
  "prewarm_by_scope": { "min_first_answer_gain_s": 0.3, "max_rss_peak_rise_mb": 200 },
  "page_follow_up": { "min_switch_hit": 0.90, "max_follow_drop_points": 5, "min_text_followup_recall20": 0.98 },
  "crosslang": { "max_gap_points": 10 },
  "order": { "min_recall_blocks_gain_points": 2.0 },
  "int8": { "min_recall20": 0.97, "vector_alone_p95_ms": 20 }
}
```

- Reglas aprobadas por el usuario en el clarify (D-088) y en la parada del analyze (D-090).

## H4. Módulos

| Módulo | Contenido |
|---|---|
| `eval/bench_bank.py` | Recombinación de frases por documento con semilla, cabecera contextual, reparto en documentos y carpetas, exclusiones, embeddings con caché, y escritura y verificación del manifiesto |
| `eval/numeric.py` | Extracción y normalización de cifras (miles, decimales y fechas), y el detector de cifras deformadas |
| `eval/token_estimate.py` | El estimador por clases, su ajuste y la paridad con Kotlin |
| `eval/english_text.py` | Palabras vacías y raíces del inglés para la variante de R12 |
| `eval/device_bench.py` | Lectura de los JSON del teléfono y tablas Markdown |
| `eval/visual_retrieval.py` (amplía) | Las variantes de la regla de seguimiento con páginas |

## H5. Pruebas (`offline/tests/`)

- **`test_bench_bank.py`:** determinismo con la semilla, longitudes dentro del troceado, reparto y exclusiones, y verificación del manifiesto.
- **`test_numeric.py`:** cifras con separadores, fechas y porcentajes; y el detector con casos de «2020» → «20000».
- **`test_token_estimate.py`:** paridad con los valores de Kotlin (mismos textos fijos), y que con a = b = c = 4,554 da el de hoy.
- **`test_english_text.py`:** palabras vacías y raíces conocidas.
- **`test_device_bench.py`:** un JSON de ejemplo produce las tablas esperadas.
- **`test_visual_followup.py`:** las tres variantes con un índice pequeño.

## H6. Juicio

- Las respuestas que cambian en `token-review`, `template-review variants=v4,v4-cifras`, `ablations --base max_blocks=3` y `crosslang-review` se juzgan a mano por el asistente (D-033), con veredictos en CSV. Las que no cambian conservan su veredicto, como en F11 y F12.
