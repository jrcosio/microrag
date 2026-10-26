# Informe de cierre — F05 Harness offline de evaluación

**Rama:** `005-offline-eval-harness`.
**Fecha:** 2026-10-26.
**Equipo:** Mac con M5 Pro y 48 GB; Python 3.12.13 en `offline/.venv`.
**Versiones:** `litert-lm-api` 0.18.1, `pypdf` 6.20.1 y `sqliteai-vector` 1.1.2, las tres fijadas en `offline/requirements.txt` y sin dependencias transitivas.
**Modelos**, todos verificados por SHA-256:
- EmbeddingGemma 2 270M;
- Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, D-023);
- Gemma 4 E4B (`gemma-4-E4B-it.litertlm`), solo para la comparación.

Las 50 tareas están hechas. La revisión de 50 juicios por el usuario que preveía T041 se sustituye por la revisión del asistente, a petición del usuario (D-033).

El informe con todas las cifras está en `offline/reports/eval-report.md` y se regenera con `run_eval.py report`.

## 1. Qué se ha implementado

- **Corpus** (`offline/corpus/`, D-032): 21 documentos públicos en español, 4,8 MB, versionados con su manifiesto y `SOURCES.md`:
  - 6 leyes consolidadas del BOE, pasadas de XML a Markdown;
  - 3 PDF consolidados del BOE y 3 PDF de la AEAT;
  - 9 libros de Wikilibros en revisiones fijadas.

  Licencias: BOE con atribución, AEAT en la modalidad básica del RD 1495/2011, Wikilibros CC BY-SA 4.0. `fetch-corpus` vuelve a descargar todo y comprueba cada SHA-256.
- **Pipeline** (`offline/eval/`, 21 módulos, unas 2.900 líneas, y `offline/run_eval.py` con 14 comandos):
  - extracción de PDF, TXT y MD;
  - troceado portado literalmente del `Chunker` del informe, con longitudes en UTF-16 como Kotlin;
  - índice con el `schema.sql` de la app, cabecera contextual, prefijos y normalizador de F04, y vectores completos de 768 para derivar las otras dimensiones;
  - recuperación vectorial, FTS5 con bm25(3, 1) y RRF k = 60, más la regla de seguimiento;
  - selección de contexto del informe y plantilla de prompt;
  - generación reanudable con LiteRT-LM;
  - juez automático y métricas.

  Cada ejecución guarda su configuración y su huella en `build/runs/<huella>/`, así que se puede repetir y reanudar.
- **Conjunto de prueba** (`offline/testset/questions.jsonl`), validado contra las reglas T1–T9:
  - 320 preguntas con respuesta, 56 sin respuesta y 50 de seguimiento;
  - 109 revisadas y 3 descartadas;
  - los relevantes se derivan de la evidencia literal, así que sobreviven a cambios de tamaño de fragmento.
- **Evaluación:**
  - la referencia y 16 ablaciones de recuperación;
  - 6 variantes de contexto generadas con E2B;
  - 3 comparaciones: 512 dimensiones, E4B y plantilla v2;
  - juicio manual de las 426 respuestas de la configuración elegida (`offline/reports/manual-judgement.csv`).
- **Decisiones y propagación:**
  - D-034 (parámetros), D-035 (umbral aceptado) y D-036 (entradas de embedding largas);
  - `offline/config/rag_parameters.json` como fuente única;
  - `domain/rag/RagParameters.kt` con `RagParametersTest`;
  - el golden del troceado para F06 (`offline/golden/chunker_golden.jsonl`: 27 casos, 162 fragmentos), regenerado con los parámetros decididos.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 98/98 (F04: 17) |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 214/214 (F04: 212); `RagParametersTest` nuevo |
| Control negativo de `RagParametersTest` | ✅ con `budget_tokens` cambiado en el JSON, la prueba falla en ese campo |
| Instrumentados | No se ejecutan: `dims` sigue en 256 y no cambia nada en el dispositivo (regla P3 sin activar) |

## 3. Validación por criterio de éxito

| Criterio | Resultado |
|---|---|
| **SC-001:** índice reproducible | ✅ Al reconstruir `ref` (1.913 fragmentos, 186 s) sale la misma huella (`09735c92…`), con los mismos fragmentos, ids y métricas |
| **SC-002:** mismo espacio vectorial | ✅ El `rag_meta` del índice coincide con el que espera la app, leído de las fuentes Kotlin; los golden de F04 siguen pasando |
| **SC-003:** conjunto de prueba | ✅ 320 / 56 / 50, con 109 revisadas y el 100 % de las evidencias encontradas en su documento |
| **SC-004:** umbrales | ⚠️ Se cumplen todos salvo respuestas correctas: 78,4 % frente al 80 %. El usuario aceptó la diferencia (D-035) tras ver las palancas |
| **SC-005:** p95 del prompt ≤ 2.000 tokens | ✅ 1.630 tokens con 4 bloques sin vecinos. Todas las variantes calibradas quedan ≤ 1.994 |
| **SC-006:** iteración de recuperación < 10 min | ✅ 1,1 s por variante con la caché de embeddings de consulta; construir un índice de variante cuesta de 2 a 3,5 min |
| **SC-007:** decisiones y constantes | ✅ D-034 a D-036 con sus cifras; `RagParametersTest` en verde |

## 4. Cifras medidas

**Recuperación** de la configuración elegida, con 370 preguntas con relevante:

| Métrica | Valor | Umbral |
|---|---|---|
| Recall@20 híbrido | 0,984 | ≥ 0,90 ✅ |
| Recall@bloques (4 bloques sin vecinos) | 0,889 | ≥ 0,80 ✅ |
| MRR híbrido / vector / FTS | 0,810 / 0,776 / 0,743 | híbrido ≥ cada uno ✅ |
| Recall@20 de seguimientos, con regla / sin regla | 0,98 / 0,79 | — |

**Respuestas:** juicio manual de E2B con 4 bloques sin vecinos y presupuesto de 1.719 tokens.

| Métrica | Valor | Umbral |
|---|---|---|
| Respuestas correctas | 78,4 % (290/370; 31 parciales y 49 incorrectas) | ≥ 80 % ❌, aceptado (D-035) |
| Negativa correcta | 96,4 % (54/56) | ≥ 85 % ✅ |
| Citas correctas | 94,2 % (508/539) | ≥ 90 % ✅ |
| Acuerdo entre la regla automática y el juicio manual | 89,4 % | — |

**Análisis de fallos:**
- 36 de las 80 respuestas no correctas no tenían el fragmento relevante entre los bloques del contexto, aunque estaba entre los 20 primeros candidatos.
- Las otras 44 tenían el fragmento relevante delante:
  - 11 negativas indebidas;
  - 33 respuestas incorrectas o incompletas.

**Ablaciones** (en el informe de evaluación, con la regla automática):

| Ablación | Resultado |
|---|---|
| Dimensión | nDCG@10 vectorial 0,752 (128), 0,806 (256), 0,828 (512) y 0,834 (768). La regla del informe (perder ≤ 1 % frente a 768) llevaría a 512; el usuario mantuvo 256 porque el híbrido no pierde (MRR 0,810 frente a 0,811) |
| Cabecera contextual | nDCG@10 vectorial 0,806 con cabecera y 0,766 sin ella |
| Tamaño de fragmento | MRR híbrido 0,786 (250 tokens), 0,810 (350) y 0,797 (500) |
| Normalizador FTS | Sin normalizar, Recall@20 FTS 0,882; solo palabras vacías, MRR híbrido 0,777 |
| Contexto | Con 4, 6 y 8 bloques, entre 65,1 % y 69,5 % de respuestas correctas. Sin vecinos siempre es mejor |
| LLM | E4B da 75,4 % frente a 68,9 % de E2B, con 1,74 s frente a 0,76 s por respuesta en el Mac |

**Tiempos en el Mac:**
- construir `ref`: 186 s, incluidos los embeddings en CPU;
- conjunto completo con E2B en GPU: 324 s (426 respuestas);
- E4B: 2,3 veces más lento por respuesta que E2B.

**Medidas del troceado:**
- densidad del corpus: 4,554 caracteres por token con el tokenizador de Gemma (453.087 tokens);
- el prefijo de documento ocupa 8 tokens;
- el 2,0 % de los fragmentos de `ref` (38) necesitó el recorte de D-036.

## 5. Diferencias frente a los mockups

No aplica: F05 no tiene UI.

## 6. Desviaciones respecto al plan

1. **Entradas de embedding demasiado largas (D-036).** LiteRT-LM 0.18.1 rechaza las entradas de más de 512 tokens. Se añade una regla: recortar al 90 %, por un espacio, y reintentar. Pasa al JSON como `embedding_overflow_cut`, y F07 debe aplicarla.
2. **Presupuesto calibrado con el tokenizador real.** El presupuesto derivado de la plantilla daba un p95 de 2.241 tokens con 6 bloques. `calibrate` lo ajusta por variante (6 bloques, 1.541; 8 bloques, 1.512 o 1.462), y la de 4 bloques no lo necesita (1.719).
3. **Juez calibrado.** Al compararla con el juicio manual, la primera regla de datos clave subestimaba las respuestas. Ahora compara por prefijo de 5 letras y acepta cualquier orden y la coma como separador de miles. Con eso el acuerdo es del 89,4 %. El comando `rejudge` vuelve a juzgar sin generar.
4. **Revisión hecha por el asistente (D-033)**, a petición del usuario, tanto la del conjunto como la de los 50 juicios de T041.
5. **Manuales de la AEAT cifrados:** quedan fuera del corpus, porque leerlos exigiría añadir `cryptography`.
6. **Tablas:** el corpus solo tiene 4 fragmentos de tabla, y la única pregunta de ese tipo se reclasificó como dato. Ese tipo de contenido queda sin evaluar.
7. **Palancas de FR-022.**
   - Se añadieron:
     - la plantilla v2 (`prompt_template`), que se descarta;
     - la comparación a 512 dimensiones.
   - No se probaron el FTS con trigramas ni más candidatos. Con 50 candidatos el relevante ya está entre los 20 primeros en el 98,4 % de los casos; el margen está en el orden de los primeros puestos, que pasa a F10 (D-035).
8. **Contrato de parámetros:** gana tres campos (`embedding_overflow_cut`, `prompt_limit_tokens` y `prompt_template`), y `RagParameters.kt` los refleja.

## 7. Pendiente y observaciones

- **F06** debe reproducir `chunker_golden.jsonl` con `CHUNK_TARGET_CHARS` 1.594 y `CHUNK_MAX_CHARS` 2.113.
- **F07** aplica la regla de D-036 al embeber y la cabecera contextual.
- **F10:** la mitad de los fallos de respuesta vienen de que el relevante no entra en los bloques del presupuesto. Se proponen trigramas como tercera lista y reordenar dentro del presupuesto.
- **F11:** las negativas indebidas (26) y las respuestas incompletas son la otra mitad. La plantilla se ajusta con respuestas reales.
- **F14:**
  - medir E4B en el teléfono (primer token y memoria) antes de proponer un cambio de modelo;
  - INT8, que sigue pendiente desde F04.
- **Limitaciones** del conjunto y del juicio: en el informe de evaluación, sección «Limitaciones».
- **Abiertos de antes:**
  - TalkBack manual de F01 (T059) y de F03 (T071);
  - el SVG oficial del logo (G20);
  - las violaciones de StrictMode al arrancar (F09 o F15).
