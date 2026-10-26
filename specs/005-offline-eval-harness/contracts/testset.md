# Contrato — Conjunto de prueba

Formato en data-model §4. Este contrato fija las reglas que valida `run_eval.py validate-testset` (FR-009); un conjunto que no las cumple no se evalúa.

## Reglas

| # | Regla |
|---|---|
| T1 | `id` único. `type` ∈ {`answerable`, `unanswerable`, `followup`} |
| T2 | Recuentos sin las `discarded`: `answerable` ≥ 300, `unanswerable` ≥ 50 y `followup` ≥ 40, salvo un ajuste acordado con el usuario y anotado en el informe |
| T3 | Revisadas (`ok` o `fixed`) ≥ 100 |
| T4 | `answerable` y `followup`: `doc` existe en el manifest; `evidence` aparece literalmente en el texto extraído de ese documento (normalizando solo los espacios); `key_facts` tiene de 1 a 4 elementos y cada uno aparece en `expected_answer` |
| T5 | `answerable` y `followup`: `relevant_ref` no está vacío y coincide con lo que se deriva de la evidencia en el índice `ref` (research R8) |
| T6 | `unanswerable`: sin `doc`, `evidence`, `key_facts` ni `relevant_ref` |
| T7 | `followup`: `previous` apunta a una pregunta existente. Al menos 5 seguimientos encadenan con una `unanswerable`, para medir si la regla empeora ese caso (spec, casos límite) |
| T8 | La pregunta no copia de la evidencia ninguna secuencia de 8 o más palabras seguidas (tras normalizar) |
| T9 | Estratificación: ningún documento aporta más del 15 % de las `answerable`, y cada `content_type` presente en el corpus tiene al menos 10 preguntas |

## Flujo de revisión

1. `run_eval.py review-sheet` crea `review.csv` con al menos 100 preguntas. La muestra es aleatoria con semilla y estratificada por tipo y documento, y no repite las ya revisadas.
2. El usuario rellena `veredicto` y `comentario`.
3. `run_eval.py review-apply`:
   - `ok` → `review = ok`;
   - `descartar` → `discarded`;
   - `corregir` → queda `pending`, con la nota. El asistente la rehace según el comentario y la marca `fixed`, que cuenta como revisada.
4. Se repite hasta cumplir T3.
