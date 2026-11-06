# Rendimiento en dispositivo y decisión INT8 (F14): cifras

Cifras reales del Nothing Phone (1) A063 (QTI SM7325, Adreno 642L, 12 GB, Android 15) y del escritorio (macOS en Apple Silicon), con LiteRT-LM 0.19.1 y SQLite-Vector 1.2.0, adoptados en T005 (research R1). Sustituyen los presupuestos de `docs/RAG_Movil/informe-rag-movil-v2.md` («Latencia (presupuesto, no medición)» y «Benchmarks en el dispositivo»).

- **Escritorio:** las secciones 1–5 se escriben a mano a partir de los informes de `offline/build/f14/`.
- **Teléfono:** las tablas de la sección 6 las escribe `run_eval.py device-report` a partir de los JSON de `files/reports`. Las respuestas y los textos solo van en esos JSON, que no van a git.

## 1. Runtimes (R1, T003–T005)

| Comprobación | Resultado |
|---|---|
| Golden de la 270M en el escritorio, 0.18.1 frente a 0.19.1 | 50 vectores, coseno mínimo 1,00000 |
| Respuestas de `v4` en la GPU del Mac, 0.18.1 frente a 0.19.1 | 426 de 426 idénticas |
| JVM y lint | 1.355/1.355; lint sin los avisos de versión |
| Batería sin modelos en los emuladores | Pixel_10 (API 37) 792/792; MicroRAG_API33 con los 4 fallos conocidos de Atrás (F13) |
| Sesión corta en el A063 | 19/19: `EmbeddingGoldenTest` (440M), humo de Gemma en texto y con visión (primer token con página 7,5 s), goldens de prompt, normalizador e ingesta, extensión 1.2.0 y `RagIndexInt8Test` |

## 2. Estimación de tokens (R9, T035–T036)

Tokenizador de Gemma 4 E2B frente a la estimación, real/estimado:

| Texto | Estimador de F11 (4,554 caracteres por token) | Estimador adoptado (4,5644 · 0,5242 · 4,4305) |
|---|---|---|
| 426 prompts de la app | media 1,045 · p95 1,173 · máximo 1,544 | media 0,958 · p95 1,063 · máximo 1,103 |
| 1.913 fragmentos del corpus | media 1,003 · p95 1,282 · máximo 2,182 | media 0,929 · p95 1,074 · máximo 1,667 |
| Textos con ≥ 10 % de dígitos (13) | media 1,795 · **p95 2,182** | media 0,926 · **p95 1,000** |

- Con el límite de 2.000 tokens del prompt cambian **2 de 426** prompts (q0088 y q0131, un bloque menos cada uno). Sus dos respuestas nuevas son correctas: la calidad de `v4` no cambia (81,1 % correctas, 91,1 % negativas y 94,3 % citas).
- Se cumple la regla de `f14_rules.json`, y se adopta.

## 3. Cifras deformadas con fp16 (R8, T032–T033)

62 preguntas sobre bloques llenos de cifras (`offline/testset/numeric`, revisadas). Una respuesta cuenta si tiene alguna cifra que no está en sus fuentes ni en la pregunta (`numeric.deformed`, comprobado a mano en las marcadas):

| Plantilla | GPU del Mac, fp16 | GPU del Mac, FP32 | A063, fp16 | A063, FP32 |
|---|---:|---:|---:|---:|
| `v4` | 12 de 62 | 0 de 62 | (fase del teléfono) | (fase del teléfono) |
| `v4-cifras` | 18 de 62 | 0 de 62 | (fase del teléfono) | — |

En el Mac, FP32 elimina todas las deformaciones y la instrucción reforzada no las reduce (D-088: `v4-cifras` debe dejar como mucho la mitad que `v4` en el teléfono).

## 4. Regla de seguimiento con páginas (R10, T041)

40 pares sobre las 302 páginas del corpus visual de F18 (768 px, 140 tokens, 256D, la 440M), con la página que entraría en la respuesta (la primera):

| Variante | Cambios de documento (24) | Seguimientos reales (16) |
|---|---:|---:|
| Unida, la de hoy | 8,3 % | 56,2 % |
| Solo la pregunta nueva | 45,8 % | 18,8 % |
| La mejor de las dos | 29,2 % | 56,2 % |

Ninguna llega al 90 % de la regla (SC-007): la regla de hoy se queda. La búsqueda de texto de los seguimientos no cambia en ninguna variante.

## 5. Búsqueda entre idiomas (R12, T044)

Recall@bloques (preguntas con un fragmento con la evidencia en el contexto):

| Combinación | Híbrida, la de hoy | Solo por significado | Referencia en el mismo idioma |
|---|---:|---:|---:|
| ES·EN (preguntas en inglés sobre documentos en español) | 0,579 | 0,789 | ES·ES 0,889 |
| EN·ES (preguntas en español sobre documentos en inglés) | 0,780 | 0,840 | EN·EN 0,900 |

La búsqueda por significado sola es el máximo peso que puede darle cualquier reordenación. Sus respuestas se juzgan para comparar con la regla de 10 puntos (D-062).

## 6. Teléfono

<!-- device-report:start -->

(Se rellena en la fase del teléfono con `run_eval.py device-report`.)

<!-- device-report:end -->
