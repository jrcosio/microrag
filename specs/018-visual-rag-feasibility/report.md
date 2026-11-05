# Informe de cierre — F18 Viabilidad del RAG visual (fase 0 visual)

> **Estado:** aprobada por el usuario el 2026-11-05. En la revisión de cierre decidió construir F19–F20 como modo opcional (D-078).

**Rama:** `018-visual-rag-feasibility`, creada desde `6b89467`.
**Fecha:** 2026-11-05.
**Dispositivos:**
- escritorio: Mac con Apple Silicon, LiteRT-LM 0.18.1 para Python, Gemma en GPU con activaciones FP32;
- emuladores `Pixel_10` (API 37) y `MicroRAG_API33` (API 33);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1), sin dependencias nuevas en la app y sin cambios de esquema;
- escritorio: `pypdfium2==5.14.0` y `pillow==12.3.0`;
- lint avisa de las dos versiones más nuevas de siempre, LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0.

**Resultado:** la regla congelada da **«no»** a F19–F20, porque ninguna configuración con páginas cumple el primer token en el teléfono (D-077). En la revisión de cierre, el usuario decidió construirlas igualmente como **modo «Multimodal» opcional**, desactivado por defecto y sin regla de sí o no (D-078).

## 1. Qué se ha implementado

**Diagnóstico «Viabilidad visual»** (solo en `src/debug`, R3):
- **Bloques:**
  - `GEMMA_INFO`: `ModelInfo` de Gemma y de la 440M;
  - `EMBED_TEXT`: la 270M y la 440M;
  - `EMBED_IMAGE` y `PARITY`;
  - `RASTER`, `IMAGE_DECODE` y `PROJECTION`;
  - `GEMMA_PAGES`, con las etapas A y B;
  - `READING`.
- **Cómo corre cada escenario:**
  - con una sola configuración de engine por escenario;
  - con la espera térmica y la liberación de engines de F02;
  - con el informe reescrito tras cada resultado.
- **Adaptadores** `LiteRtVisionEngines` y `DebugPageRasterizer`, en los paquetes de debug de `rag/runtime/litert` y `data/ingest`, con `LayeringTest` ampliado.
- **Conjunto del dispositivo** en `dev-visual`: se verifica fichero a fichero contra su SHA-256.
- **Pantalla:**
  - la ventana de Gemma con páginas es de 8.192 tokens;
  - cada bloque desactivado dice lo que le falta.

**Harness del escritorio** (`offline/eval/visual_*.py`, `vision_runtime.py`, `raster.py` y `photos.py`):
- **Corpus visual:** 44 documentos públicos y 302 páginas en `offline/corpus_visual/`. Incluye escaneados reconstruidos como PDF solo de imagen y 10 fotos simuladas deterministas.
- **Conjunto de prueba:** 147 preguntas redactadas por agentes separados. La sesión principal revisó cada una con la página a la vista (D-033) y aceptó 143, un 66 % visuales. Está congelado en `config/visual_rules.json`.
- **Recuperación por página:** texto, visual y ambos; 3 resoluciones × 2 presupuestos de tokens × 2 dimensiones.
- **Respuestas:** 17 variantes con la plantilla v5-visual, juzgadas por el juez de F05 con citas a página.
- **Comandos:**
  - `visual-manual`, `visual-text-loss` y `visual-device-set`;
  - `visual-parity`, con los cosenos entre el teléfono y el escritorio;
  - `visual-decide`, en sus tres pasos;
  - `visual-report`.

**Documentos:** `benchmarks.md`, `offline/reports/visual-eval-report.md`, `checklists/release-verification.md` y las decisiones D-073 a D-077.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.163 de 1.163 (35 más que al cerrar F17) |
| Lint (`lintDebug`) | 0 errores y los 2 avisos de versiones de siempre. Se corrigieron 5 avisos nuevos del diagnóstico: plurales y `createBitmap` |
| Escritorio (Python) | 285 pruebas OK |
| `Pixel_10` (API 37), instrumentadas | 934 pruebas en 32 min; 933 en verde |
| `MicroRAG_API33` (API 33), instrumentadas | 934 pruebas en 33 min; fallan los 4 de siempre de Atrás desde la raíz |
| `ModelDownloadSmokeTest` | Falló en los dos emuladores por el tiempo de espera de la descarga real. Es `@RequiresNetwork`, que la batería excluye desde F03; esta vez no se filtró |
| Teléfono A063, instrumentadas | 931 de 931 en 64 min, sin `@RequiresNetwork` y sin las dos pruebas visuales, que se lanzaron aparte |
| `EmbeddingVariantGoldenTest` en el A063 | 1 de 1 |
| `VisualFeasibilityBenchmarkTest` en el A063 | Etapa A con todos los bloques, y después la etapa B con 3 candidatas |
| Release (T050) | Sin el diagnóstico, con los 9 permisos de P4' y el mismo manifiesto que F17 (`checklists/release-verification.md`) |

El aviso de lo que le falta a cada bloque (T053) llegó después de las baterías instrumentadas. Es código de debug, cubierto por la JVM y comprobado a mano en `Pixel_10`.

## 3. Validación por criterio

| Criterio | Resultado |
|---|---|
| SC-001 `benchmarks.md` | ✅ 25 combinaciones de Gemma en la etapa A y 3 en la B; la 440M en consulta, fragmento y página a 70 y 140 tokens; conversión a 768, 1.024 y 1.536 px en WebP y JPEG; decodificación de imágenes. La visión en GPU no está porque el fichero solo la declara en CPU, y queda anotado |
| SC-002 golden de la 440M | ✅ CPU 1,00000 y GPU 0,99299 (mínimos de 50 textos); identidad con la 270M 1,00000 |
| SC-003 consulta < 150 ms | ✅ p95 de 121,1 ms en CPU (270M: 120,3 ms) |
| SC-004 primer token | ✅ Etapa A con 1–3 páginas × 70/140/280, mitades, solo páginas y mezcla. Etapa B con 20 preguntas para las 3 candidatas |
| SC-005 paridad con los mismos bytes | ⚠️ 39 de 40 vectores ≥ 0,99, medio 0,99931; uno da 0,98823 (`foto-boe-fiestas-2026-p2` a 140). La lectura da el mismo veredicto en las 12 páginas |
| SC-006 conjunto de prueba | ✅ 143 aceptadas, 66,4 % visuales, todas con sus páginas y su veredicto |
| SC-007 informe de evaluación | ✅ Ablaciones de recuperación y de respuesta por tipo, con las cifras de cada parámetro |
| SC-008 sí o no en DECISIONS | ✅ «No» en D-077, al aplicar la regla congelada. Las cifras de calidad no salen de la revisión manual decisiva: no hubo configuración que cumpliera el primer token (ver §6) |
| SC-009 un engine en GPU y release | ✅ `VisualSuiteTest` y los registros del coordinador; release verificada |
| SC-010 «Texto» como en F05 y F10 | ✅ Las 17 variantes de recuperación y las 6 de `order-review` dan las mismas cifras. Las baterías pasan como al cerrar F17 |

## 4. Cifras medidas

**Recuperación por página** (130 respondibles, Recall@5 visual):
- «Texto»: 0,416;
- «Visual»: de 0,516 a 0,747 según resolución y tokens;
- «Ambos» con la RRF de tres listas: de 0,368 a 0,447.

La RRF de tres listas deja los escaneados en 0 y las imágenes en 0,25 (D-076). Con la regla congelada se eligen 768 px, 140 tokens y 256 dimensiones.

**Respuestas en el escritorio** (juez automático, FP32):

| Variante | Visuales | Controles de texto | Primer token en el A063 (etapa A) |
|---|---|---|---|
| Texto | 18,9 % | 85,7 % | 3,3 s |
| Mezcla, 1 página a 70 | 14,7 % | 28,6 % | 4,8 s |
| Mezcla, 1 página a 140 | 18,9 % | 35,7 % | 7,5 s |
| Mezcla, 1 página a 280 | 21,1 % | 42,9 % | 14,5 s |
| Mezcla, 3 páginas en mitades (la mejor) | 27,4 % | 53,6 % | 69,8 s |

Comprobación del juez: coincide con la revisión a mano en 30 de 30 respuestas de «Texto», 30 de 30 de «Páginas» y 28 de 30 de la mejor mezcla.

**Teléfono** (`benchmarks.md`):
- **Etapa B:** la mejor candidata da 9,8 s de p95 compuesto, frente a los 8 s de la regla.
- **Embedding de una página con la 440M:** 1,3 s a 70 tokens y 3,2 s a 140, solo en CPU. 200 páginas llevarían unos 11 minutos.
- **Memoria:** RSS pico de 3,5–3,9 GB con Gemma y la visión.
- **Conversión de páginas:** 28–67 ms por página en JPEG, también con el PDF de AEMET protegido con contraseña de propietario.

## 5. Decisiones (D-077 y D-078)

- **D-077:**
  - la regla da «no»;
  - la 270M sigue siendo el embedder por defecto;
  - dentro de F18 no hay enmienda de la constitución, porque FR-029 solo la pide con el «sí».
- **D-078,** del usuario en la revisión de cierre: F19–F20 se construyen como modo «Multimodal» opcional, desactivado por defecto:
  - la 440M se descarga al activarlo y embebe también el texto;
  - no hay regla de duplicados;
  - el usuario elige el detalle: 1 página a 70, 140 o 280 tokens;
  - no hay ablaciones comparativas.

  La enmienda de la constitución (III, IV y Restricciones) se hace al empezar F19.
- **Hallazgos para F14:**
  - las cifras deformadas con fp16;
  - la estimación de tokens en texto lleno de cifras.

## 6. Desviaciones respecto al plan

| Desviación | Motivo | Aprobación |
|---|---|---|
| Las páginas de la respuesta salen de «Visual», no de la RRF de tres listas, y la elección de parámetros usa el Recall@5 de «Visual» | La RRF de tres listas entierra las páginas sin texto | Usuario, D-076, después de las ablaciones de recuperación |
| Sin revisión decisiva ni pérdida en texto en F05 | Ninguna configuración cumple el primer token, así que no hay configuración elegida que revisar, y esas cifras no cambiarían el «no» | Usuario, D-077 |
| Gemma con páginas usa una ventana de 8.192 tokens, no de 4.096 | El texto del corpus visual gasta unas 2,2 veces lo estimado, y la mezcla no cabía | Decisión técnica, en el contrato del diagnóstico |
| Gemma en el Mac con activaciones FP32 | Con fp16 deformaba cifras. Las 17 variantes se generaron de nuevo; la tanda fp16 queda en `build/visual/runs-fp16` | Decisión técnica, contada al usuario |
| La pérdida en texto de F05 se prepara con fp16 | Es la precisión de la base de F05, y en ese corpus no deforma cifras | Decisión técnica (no llegó a correr) |
| Regla V4: las páginas de otro documento con el mismo contenido cuentan como relevantes | La foto y su original, y el folleto de la BNE y la Gaceta de 1931 | Revisión del conjunto (D-033) |
| El corpus pesa 51 MB, no unos 40 MB | Los escaneados reconstruidos ocupan más | Queda fuera de los assets de las pruebas, así que no infla el APK |
| El folleto de la BNE se reconstruyó como escaneado | El original tenía una capa de OCR | Revisión del corpus |
| El índice de páginas de los 6 PDF de F05 pasa de `visual-build` a `visual-text-loss` | Solo hace falta con la configuración elegida | — |
| Las pruebas del juez van en `test_visual_prompt.py`, no en `test_visual_judge.py` | Comparten las fuentes de prueba | — |

## 7. Fallos encontrados y corregidos

- **Activaciones fp16 en la GPU del Mac:** deformaban cifras en contextos llenos de números («20000» por 2020). Se pasa a FP32 (`0fe8681`).
- **La mezcla no cabía en 4.096 tokens:** sus 5.189 tokens reales superaban la ventana. Se amplía a 8.192 en el escritorio y en el diagnóstico (`d3c8caf`).
- **El índice de páginas no notaba un documento cambiado:** se reanudaba por documento y página. Ahora borra los vectores de un documento cuyo hash ha cambiado (`6cce214`).
- **WebP:** LiteRT-LM no lo decodifica como entrada, así que a los modelos se les pasa JPEG.
- **El folleto de la BNE:** tenía una capa de OCR y se reconstruyó como escaneado (`d471d76`).
- **La decisión completa no leía las consultas:** `visual-decide` las lee ahora del informe de la etapa A, y el informe lee `parity.json` con su forma real (`9aae7f7`).
- **Avisos nuevos de lint en el diagnóstico,** corregidos (`438ebb7`).
- **Los bloques desactivados no decían por qué** (`2a1abba`).
- **Correo del usuario:** un subagente lo puso en el User-Agent de una petición a Wikimedia Commons. Se le contó al usuario y las tareas lo prohíben.

## 8. Pendiente

- **Para F14:**
  - las cifras deformadas con fp16 en el teléfono: 2 de 60 respuestas de la etapa B. Afectan al chat de hoy, y FP32 cuesta casi 3 s de primer token (D-023);
  - la estimación de tokens en texto lleno de cifras.
- **F19 y F20:** el modo multimodal opcional (D-078), con la enmienda de la constitución al empezar F19.
- **En el teléfono quedan, como antes de F18:** Gemma y la 270M. La 440M se borró por decisión del usuario.
