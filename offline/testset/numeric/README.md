# Preguntas con cifras (`questions.jsonl`)

> **Borrador.** Lo redactó un agente asistente y hay que revisarlo (D-033) antes de usarlo: cada pregunta, con sus
> bloques a la vista, y las dudas de abajo.

## Para qué

En la GPU del teléfono, las activaciones fp16 de Gemma a veces deforman las cifras cuando el contexto está lleno de
números («2020» sale como «20000», «36,4» como «366,4»). Este conjunto fija 62 preguntas cuya respuesta exige copiar
de 1 a 3 cifras. Sirve para contar cuántas respuestas deforman alguna cifra con cada precisión y cada plantilla de
prompt. El contexto de cada pregunta es **fijo** (los bloques van en el propio fichero, no se recuperan), así que la
medida aísla la generación.

## Cómo se hizo

- Fuente: el corpus de evaluación de F05 ya troceado en `offline/build/index/ref.db` (tablas `documents` y `chunks`).
- Selección por densidad de cifras: se contaron los números de cada chunk y se leyeron los más densos. Se eligieron
  tramos de multas, porcentajes de actualización de rentas, plazos, fechas, números de normas, códigos NC,
  referencias BOE, partidas presupuestarias y altitudes.
- Cada pregunta lleva el chunk de la respuesta y de 1 a 3 chunks más, también densos en cifras, como distractores:
  54 preguntas con 4 bloques, 5 con 3 y 3 con 2. Así el prompt se parece al de la app (4 bloques de unos 350
  tokens).
- Los distractores son a propósito parecidos: el tramo de multa contiguo, la misma escala en otra ley, fechas casi
  iguales (11 y 9 de marzo de 1971), leyes con la misma fecha (Ley 42/1997 y Ley 41/2002, ambas de 14 de
  noviembre), códigos NC vecinos o las referencias BOE de otras normas.
- 62 preguntas de 15 documentos. Las preguntas se redactaron como las haría un usuario, sin copiar el texto, y
  ninguna se puede contestar sin las cifras.

## Campos

| Campo | Contenido |
|---|---|
| `id` | `n001`… en orden |
| `question` | la pregunta, en español |
| `doc` | `display_name` del documento de la respuesta |
| `chunk_ids` | ids de `chunks` en `ref.db`. **El primero es el chunk de la respuesta**, los demás son distractores |
| `blocks` | `{document_name, heading_path, page, text}` en el mismo orden que `chunk_ids`, con `text` tal cual está guardado |
| `expected_figures` | las cifras que la respuesta debe copiar, literales del bloque de la respuesta |
| `expected_answer` | respuesta corta en español, que contiene todas las `expected_figures` |
| `source` | `f05` |

Convenciones de `expected_figures`:

- Se copian con los separadores de la fuente. Los BOE usan los españoles (`491.866`, `65,01`). Algunos Wikilibros
  no: `4,660` msnm es separador de miles, y `19.3` km, `197.5`, `50.5%` y Gecko `1.9` llevan punto decimal. Se
  dejaron tal cual.
- Las fechas van enteras, como en la fuente (`1 de octubre de 2021`, `21 de julio`). Las normas van por su número
  (`689/2020`), y los códigos, enteros (`9018 11 00`, `BOE-A-2021-11472`, `20.09.422B.782`).
- Los importes van sin la unidad (`3.750`, no `3.750 euros`). Los porcentajes llevan `%` solo si la fuente usa el
  signo (`20 %`, `6%`). Si la fuente dice «por 100» o «por ciento», va el número solo (`24`).

## Comprobación

Un script comprobó en las 62 preguntas que cada cifra esperada:

- aparece literal en el texto del bloque de la respuesta, sin formar parte de un número más largo;
- aparece también en `expected_answer`;
- no aparece en la pregunta.

No falló ninguna.

## Dudas para la revisión

- **Cifras cortas** (`2`, `16`, `18`, `20`, `40`): n045, n041, n021 y n005. Una comparación por subcadena las da por
  buenas casi siempre. Hay que comparar por número entero.
- **Separadores no españoles**: n054–n058. Un normalizador que lea `4,660` como decimal español las interpretará mal.
- n009: el texto del anexo del BOE está desordenado al extraerse («2207 10: sin desnaturalizar, con Vol. alcohol
  etílico del 80% o más»), y la pregunta depende de leerlo bien.
- n013 y n014: los distractores son índices del BOE, más largos que un bloque normal. n014 suma unos 1.730 tokens
  estimados (la mediana del conjunto es 1.250).
- n048: el texto de la guía de la AEAT tiene palabras partidas por la extracción («regu la»). Las cifras están
  intactas.
