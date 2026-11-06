# Pares de seguimiento con páginas (`followups.jsonl`)

> **Borrador.** Lo redactó un agente asistente y hay que revisarlo (D-033) antes de usarlo en
> `visual-followup-review`: cada par, con la página a la vista, y el veredicto en la nota.

## Para qué

F14, research R10 («Regla de seguimiento con páginas») y SC-007. La regla de seguimiento une la pregunta anterior
con la nueva cuando la nueva tiene menos de 8 tokens normalizados o empieza por un conector. Con la consulta unida
se busca también la página. En F20 se vio que una pregunta corta sobre **otro** documento arrastra la página de la
pregunta anterior. Estos pares miden las variantes de R10 (hoy, solo la nueva, la mejor de las dos) frente a la regla
de SC-007: la página del documento nuevo en al menos el 90 % de los cambios de documento, y en los seguimientos
reales no más de 5 puntos por debajo de hoy.

## Cómo se hicieron

- 40 pares: 24 `switch` y 16 `follow`, sobre 30 documentos del corpus visual de F18.
- `first` es siempre una pregunta de `questions.jsonl` con `review: ok`, copiada tal cual. `first_page` es su
  primera página.
- **`switch`:** `second` es una pregunta corta sobre la página de **otro** documento que también tiene pregunta en
  `questions.jsonl`. Nombra lo que pregunta con una palabra del título o un término propio de esa página («EPA»,
  «FRONTUR», «Köppen», «Astorga»…). La respuesta sale de la `expected_answer` de la pregunta de esa página o de su
  texto.
- Se buscaron a propósito cambios difíciles entre documentos parecidos: IPC y EPA, FRONTUR y la coyuntura hotelera,
  los dos BOE de calendario, los dos resúmenes de AEMET, AEAT y los módulos, el Anuario y «España en cifras», Gacetas
  de 1900 entre sí y mapas del IGN entre sí.
- **`follow`:** `second` sigue en la misma página («¿Y en Galicia?», «¿Y Teruel?», «¿Y el más seco?»), y
  `expected_page` es igual a `first_page`.
  - En los PDF con texto, la respuesta se comprobó en el texto extraído de la página (`offline/build/extract/`).
  - En los escaneados no hay texto. Solo se usaron páginas con dos preguntas de `questions.jsonl`, y la segunda del
    par es la versión corta de la otra.
- Se evitaron los pares de un documento con su foto simulada (`foto-*`), porque es la misma página. Cuando la
  respuesta también está en otra página, la nota lo dice: por ejemplo, la Constitución de 1931 en la Gaceta del
  10-12-1931.

## Campos

| Campo | Contenido |
|---|---|
| `id` | `f001`… en orden (los `switch` primero) |
| `kind` | `switch` (cambia de documento) o `follow` (sigue en la misma página) |
| `first` | primera pregunta, copiada de `questions.jsonl` |
| `second` | segunda pregunta, corta o con conector |
| `first_page` | `{doc, page}` de la primera, con el `doc` de `questions.jsonl` |
| `expected_page` | `{doc, page}` que debe acompañar a la respuesta de la segunda |
| `note` | respuesta esperada, pregunta de origen (`v0…`) y dudas |

## Comprobación de la regla

Cada `second` activa la regla de `offline/eval/retrieval.py`. Se comprobó con `is_follow_up` y `search_text`
(importados con `sys.path.insert(0, 'offline')`), y ningún par falla. Los tokens se cuentan con `normalizer.tokens`,
y los conectores son `y `, `¿y `, `pero `, `entonces`, `¿y si` y `y si `.

- 33 empiezan por «¿Y», 2 por «Pero» y 1 por «Entonces».
- 4 no llevan conector y la activan por longitud: f007, f012, f019 y f023.
- f005 (9 tokens) y f006 (8 tokens) la activan solo por el conector.
