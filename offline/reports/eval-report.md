# Informe de evaluación offline (F05)

Generado con `run_eval.py report`. Umbrales del informe técnico (§Evaluación) y presupuesto de prompt de la constitución IV (D-026).

## Corpus

21 documentos públicos en español (D-032). Fuentes y licencias en `corpus/SOURCES.md`.

| Documento | Tipo | Formato | Licencia |
|---|---|---|---|
| Ley 29/1994, de 24 de noviembre, de Arrendamientos Urbanos | normativa | md | BOE: reutilización con atribución |
| Ley 49/1960, de 21 de julio, sobre propiedad horizontal | normativa | md | BOE: reutilización con atribución |
| Ley 31/1995, de 8 de noviembre, de Prevención de Riesgos Laborales | normativa | md | BOE: reutilización con atribución |
| Ley 34/2002, de 11 de julio, de servicios de la sociedad de la información y de comercio electrónico | normativa | md | BOE: reutilización con atribución |
| Ley Orgánica 3/2018, de 5 de diciembre, de Protección de Datos Personales y garantía de los derechos digitales | normativa | md | BOE: reutilización con atribución |
| Ley 20/2007, de 11 de julio, del Estatuto del trabajo autónomo | normativa | md | BOE: reutilización con atribución |
| Real Decreto 1619/2012, de 30 de noviembre, por el que se aprueba el Reglamento por el que se regulan las obligaciones de facturación | normativa | pdf | BOE: reutilización con atribución |
| Ley 10/2021, de 9 de julio, de trabajo a distancia | normativa | pdf | BOE: reutilización con atribución |
| Ley 7/1996, de 15 de enero, de Ordenación del Comercio Minorista | normativa | pdf | BOE: reutilización con atribución |
| Guía de usuario de la aplicación gratuita de facturación de la AEAT (VERI*FACTU) | guia | pdf | AEAT: RD 1495/2011 |
| Tutorial sobre el uso de cuadernos ATA en formato digital para operadores | guia | pdf | AEAT: RD 1495/2011 |
| Manual práctico de facturación y libros registro del IVA (2011) | guia | pdf | AEAT: RD 1495/2011 |
| Excursionismo (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Administración de tiempo (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Montañismo (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Dirección de Proyectos (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Telegram Messenger (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Mozilla Firefox (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Análisis Sensorial de Alimentos (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Apuntes de Natación (Wikilibros) | manual | txt | CC BY-SA 4.0 |
| Ajedrez (Wikilibros) | manual | txt | CC BY-SA 4.0 |

Por tipo: {'normativa': 9, 'guia': 3, 'manual': 9}. Por formato: {'md': 6, 'pdf': 6, 'txt': 9}.

## Índices de evaluación

| Variante | Fragmentos | Objetivo / máximo (caracteres) | Caracteres/token | Por encima del máximo | Tiempo |
|---|---|---|---|---|---|
| ref | 1913 | 1594 / 2113 | 4.554 | 7 | 186.3 s |
| chunk250 | 2594 | 1138 / 1626 | 4.554 | 18 | 210.9 s |
| chunk500 | 1397 | 2277 / 2113 | 4.554 | 435 | 134.5 s |
| nohdr | 1913 | 1594 / 2113 | 4.554 | 7 | 170.7 s |
| dim128 | 1913 | 1594 / 2113 | 4.554 | 7 | derivada de ref (dims) |
| dim512 | 1913 | 1594 / 2113 | 4.554 | 7 | derivada de ref (dims) |
| dim768 | 1913 | 1594 / 2113 | 4.554 | 7 | derivada de ref (dims) |
| fts_raw | 1913 | 1594 / 2113 | 4.554 | 7 | derivada de ref (fts) |
| fts_stop | 1913 | 1594 / 2113 | 4.554 | 7 | derivada de ref (fts) |

## Conjunto de prueba

- Preguntas: 320 con respuesta, 56 sin respuesta y 50 de seguimiento.
- Revisión: 109 aceptadas, 0 corregidas, 3 descartadas y 317 sin revisar.
- Tipos de contenido (con respuesta): {'texto': 171, 'dato': 72, 'procedimiento': 39, 'lista': 38}.

## Recuperación

La referencia es la configuración de partida del informe técnico: 6 bloques, con vecino para los 2 mejores. Cada ablación cambia una sola cosa. El presupuesto de estas filas se deriva de la plantilla; la generación usa el presupuesto calibrado con el tokenizador real para que el p95 del prompt quepa en 2.000 tokens.

| Variante | R@20 híbrido | R@20 vector | R@20 FTS | MRR híbrido | MRR vector | MRR FTS | nDCG@10 vector | Recall@bloques | R@20 seguimientos | Presupuesto | Tiempo |
|---|---|---|---|---|---|---|---|---|---|---|---|
| referencia | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.862 | 0.980 | 1691 | 1.1 s |
| solo vector | 0.981 | 0.981 | — | 0.776 | 0.776 | — | 0.806 | 0.805 | 1.000 | 1691 | 0.8 s |
| solo FTS | 0.965 | — | 0.965 | 0.743 | — | 0.743 | — | 0.808 | 0.980 | 1691 | 0.6 s |
| FTS sin normalizar | 0.973 | 0.981 | 0.882 | 0.750 | 0.776 | 0.655 | 0.806 | 0.808 | 0.950 | 1691 | 2.0 s |
| FTS solo palabras vacías | 0.992 | 0.981 | 0.931 | 0.777 | 0.776 | 0.719 | 0.806 | 0.835 | 0.990 | 1691 | 1.1 s |
| 128 dimensiones | 0.980 | 0.947 | 0.965 | 0.779 | 0.719 | 0.743 | 0.752 | 0.811 | 0.980 | 1691 | 1.2 s |
| 512 dimensiones | 0.991 | 0.992 | 0.965 | 0.811 | 0.794 | 0.743 | 0.828 | 0.859 | 0.980 | 1691 | 1.6 s |
| 768 dimensiones | 0.992 | 0.985 | 0.965 | 0.813 | 0.802 | 0.743 | 0.834 | 0.857 | 0.980 | 1691 | 1.9 s |
| sin cabecera contextual | 0.980 | 0.957 | 0.965 | 0.787 | 0.732 | 0.743 | 0.766 | 0.832 | 0.980 | 1691 | 1.2 s |
| fragmentos de 250 tokens | 0.980 | 0.961 | 0.946 | 0.786 | 0.765 | 0.715 | 0.801 | 0.854 | 0.970 | 1691 | 1.4 s |
| fragmentos de 500 tokens | 0.991 | 0.985 | 0.976 | 0.797 | 0.753 | 0.742 | 0.795 | 0.849 | 1.000 | 1691 | 0.9 s |
| sin regla de seguimiento | 0.958 | 0.928 | 0.915 | 0.756 | 0.700 | 0.699 | 0.733 | 0.805 | 0.790 | 1691 | 2.7 s |
| 4 bloques | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.843 | 0.980 | 1719 | 1.1 s |
| 4 bloques sin vecinos | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.889 | 0.980 | 1719 | 1.1 s |
| 6 bloques sin vecinos | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.892 | 0.980 | 1691 | 1.1 s |
| 8 bloques | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.859 | 0.980 | 1662 | 1.1 s |
| 8 bloques sin vecinos | 0.984 | 0.981 | 0.965 | 0.810 | 0.776 | 0.743 | 0.806 | 0.892 | 0.980 | 1662 | 1.1 s |

## Respuesta de extremo a extremo

Gemma 4 E2B en la GPU del Mac, juzgado con la regla automática calibrada (el juicio manual va más abajo).

| Variante | Correctas | Negativa correcta | Citas correctas | Presupuesto | Prompt p50 / p95 (tokens) | s/respuesta |
|---|---|---|---|---|---|---|
| E2B, 4 bloques | 65.1 % | 94.6 % | 77.9 % | 1719 | 1512 / 1994 | 0.82 |
| E2B, 4 bloques sin vecinos | 68.9 % | 96.4 % | 76.4 % | 1719 | 1267 / 1630 | 0.76 |
| E2B, 6 bloques | 67.0 % | 94.6 % | 77.9 % | 1541 | 1547 / 1973 | 0.83 |
| E2B, 6 bloques sin vecinos | 69.2 % | 96.4 % | 74.7 % | 1541 | 1567 / 1970 | 0.85 |
| E2B, 8 bloques | 66.5 % | 94.6 % | 78.0 % | 1512 | 1525 / 1958 | 0.82 |
| E2B, 8 bloques sin vecinos | 69.5 % | 100.0 % | 75.3 % | 1462 | 1531 / 1946 | 0.84 |

## Configuración elegida

Fuente única: `config/rag_parameters.json`, igual a `RagParameters.kt` (decisiones D-034, D-035, D-036).

| Parámetro | Valor |
|---|---|
| `dims` | 256 |
| `contextual_header` | true |
| `normalizer_version` | 1 |
| `chunk_target_chars` | 1594 |
| `chunk_max_chars` | 2113 |
| `chars_per_token` | 4.554 |
| `embedding_overflow_cut` | 0.9 |
| `candidates` | 50 |
| `rrf_k` | 60 |
| `follow_up_rule` | true |
| `max_blocks` | 4 |
| `max_per_document` | 3 |
| `budget_tokens` | 1719 |
| `expand_top` | 0 |
| `prompt_limit_tokens` | 2000 |
| `prompt_template` | "informe" |
| `llm_model` | "GEMMA4_E2B" |
| `max_output_tokens` | 512 |

## Umbrales frente a la configuración elegida

Recuperación de «4 bloques sin vecinos» y respuestas del juicio manual de «E2B, 4 bloques sin vecinos».

| Métrica | Valor | Umbral | Cumple | Nota |
|---|---|---|---|---|
| Recall@20 híbrido | 0.984 | ≥ 0.90 | ✓ |  |
| Recall@bloques (tras la selección) | 0.889 | ≥ 0.80 | ✓ |  |
| MRR híbrido | 0.810 | ≥ vector (0.776) y ≥ FTS (0.743) | ✓ |  |
| Respuestas correctas (manual) | 78.4 % | ≥ 80 % | ✗ | aceptado por el usuario (D-035) |
| Negativa correcta (manual) | 96.4 % | ≥ 85 % | ✓ |  |
| Citas correctas (manual) | 94.2 % | ≥ 90 % | ✓ |  |
| Prompt p95 (tokens) | 1630 | ≤ 2000 (constitución IV) | ✓ |  |
| nDCG@10 vectorial, 256 frente a 768 | −3.3 % | ≤ 1 % | ✗ | 256 por decisión del usuario; el híbrido no pierde (D-034) |

## Juicio manual de la configuración elegida

426 respuestas juzgadas una a una (`reports/manual-judgement.csv`). Una respuesta parcial cuenta como no correcta.

| Tipo | Correctas | Parciales | Incorrectas |
|---|---|---|---|
| Con respuesta | 257 | 27 | 36 |
| Seguimiento | 33 | 4 | 13 |
| Sin respuesta | 54 | 0 | 2 |

- Respuestas correctas: 78.4 % de 370; 26 son negativas indebidas.
- Negativa correcta: 96.4 % de 56.
- Citas correctas: 94.2 % de 539.
- La regla automática coincide con el juicio manual en 89.4 % de las respuestas.

## Análisis de fallos

Respuestas no correctas del juicio manual (con respuesta y seguimiento), según si algún fragmento relevante llegó al contexto.

| Juicio | Relevante en el contexto | Responde | Se niega |
|---|---|---|---|
| parcial | sí | 24 | 0 |
| parcial | no | 7 | 0 |
| incorrecta | sí | 9 | 11 |
| incorrecta | no | 14 | 15 |

36 de 80 fallos no tenían el fragmento relevante en el contexto, aunque el Recall@20 híbrido es 0.984: el relevante está entre los candidatos, pero no entre los bloques que caben en el presupuesto (de media 3.0 bloques). El resto falla con el relevante delante: negativas indebidas y respuestas incompletas.

## Palancas probadas para el umbral de respuestas

Misma recuperación y presupuesto que la configuración elegida, cambiando una sola cosa (regla automática).

| Variante | Correctas | Negativa correcta | Citas correctas | Presupuesto | Prompt p50 / p95 (tokens) | s/respuesta |
|---|---|---|---|---|---|---|
| Elegida: E2B, 4 bloques sin vecinos | 68.9 % | 96.4 % | 76.4 % | 1719 | 1267 / 1630 | 0.76 |
| E2B, 512 dimensiones | 70.3 % | 96.4 % | 79.5 % | 1719 | 1250 / 1633 | 0.75 |
| E4B (Gemma 4 E4B) | 75.4 % | 98.2 % | 80.1 % | 1719 | 1266 / 1630 | 1.74 |
| E2B, plantilla v2 | 68.9 % | 91.1 % | 76.6 % | 1719 | 1334 / 1697 | 0.77 |

## Limitaciones

- Las preguntas se redactaron leyendo fragmentos concretos, así que la recuperación sale optimista: un usuario real no conoce las palabras del documento.
- Los relevantes se derivan solo de la evidencia literal; otro fragmento que también responda cuenta como fallo.
- Las tablas no se evalúan: el corpus tiene 4 fragmentos de tabla y la única pregunta de ese tipo se reclasificó como dato.
- La revisión del conjunto y el juicio manual los hizo el asistente a petición del usuario (D-033), no una persona.
- Los tiempos son del Mac (GPU M5 Pro): la latencia del teléfono la mide F14, incluida la de E4B.
- El corpus son 21 documentos públicos (D-032); la calidad con documentos privados del usuario puede ser otra.

