# Contrato: evaluación visual en el escritorio (`offline/`)

Amplía el harness de F05 sin cambiar lo que ya hace. El corpus de texto (`es`), su conjunto de prueba y sus cifras no cambian (FR-024).

## 1. Dependencias

`offline/requirements.txt`, con versiones fijadas (R7):

```text
litert-lm-api==0.18.1
pypdf==6.20.1
sqliteai-vector==1.1.2
pypdfium2==5.14.0
pillow==12.3.0
```

**Modelos:** `offline/models/embeddinggemma-2-text-vision-440m.litertlm` es un enlace al fichero fijado (`EG2_440M`), y `models.verify("EG2_440M")` comprueba su SHA-256.

## 2. Módulos nuevos (`offline/eval/`)

| Módulo | Responsabilidad |
|---|---|
| `vision_runtime.py` | Gemma con imágenes y el embedder de la 440M. Presupuesto visual por `ctypes` (R1), `Contents` intercalado y `BenchmarkInfo` |
| `raster.py` | `render_page(pdf, page, px, fmt, quality)`, `tiles(image, overlap=0.10)` y caché en `build/visual/pages` |
| `photos.py` | `simulate_photo(image, seed, recipe)`: giro, perspectiva, sombra, ruido y JPEG, deterministas por semilla |
| `visual_corpus.py` | Corpus `visual` (registro en `CORPORA`), formatos de imagen y tipos nuevos |
| `visual_testset.py` | Carga, validación V1–V8, hoja de revisión y aplicación de veredictos (D-033) |
| `visual_index.py` | Índice de páginas (`visual-pages-<px>-<tokens>.db`), de fragmento a página (`visual-chunk-pages.json`) e índice de texto del corpus visual |
| `visual_retrieval.py` | Listas por página, RRF (k = 60) de dos o tres listas, y métricas por página y por tipo |
| `visual_prompt.py` | Plantilla v5-visual: fuentes `[n]` de texto o de página, mezcla y regla de duplicados |
| `visual_judge.py` | El juez de F05, con citas a página (R11) |
| `visual_parity.py` | Exporta el conjunto del dispositivo y compara el informe del teléfono (R13) |
| `visual_rules.py` | Lee `config/visual_rules.json`, comprueba la huella del conjunto y aplica la regla de sí o no y las de elección (R12) |
| `visual_report.py` | Genera `reports/visual-eval-report.md` |

## 3. Subcomandos de `run_eval.py`

| Subcomando | Qué hace |
|---|---|
| `fetch-corpus corpus=visual [--record]` | Descarga y recorta el corpus visual, y registra o verifica su SHA-256 |
| `sources corpus=visual` | Genera `corpus/visual/SOURCES.md` |
| `visual-photos` | Genera las fotos simuladas a partir del manifiesto (`simulated_from`) |
| `visual-raster px=768,1024,1536` | Rasteriza y codifica todas las páginas |
| `visual-build px=… tokens=70,140` | Índice de texto, índice de páginas y de fragmento a página |
| `visual-validate [--pre-review]` | Valida el conjunto de prueba (V1–V8) |
| `visual-review-sheet` / `visual-review-apply` | Hoja de revisión y aplicación de veredictos (D-033) |
| `visual-freeze` | Escribe `testset_sha256` en `config/visual_rules.json` y se niega a cambiarlo después |
| `visual-retrieve mode=texto,visual,ambos px=… tokens=… dim=256,512` | Ablaciones de recuperación |
| `visual-generate answer=texto,paginas,mezcla pages=1,2,3 budget=70,140,280 tiles=no,si` | Ablaciones de respuesta, que se reanudan |
| `visual-manual` | Exporta la muestra manual (30 por variante principal) |
| `visual-device-set` | Exporta `build/visual/device-set/` |
| `visual-parity <informe.json>` | Cosenos teléfono ↔ escritorio y juez de la prueba de lectura |
| `visual-decide <informe.json>` | Aplica `visual_rules.json` a las cifras del escritorio y del teléfono, y escribe el resultado |
| `visual-report` | Genera el informe |

Todos son deterministas: semillas fijas y decodificación greedy (`top_k = 1`, `seed = 0`), como en F05.

## 4. Reglas de las ablaciones

- **Antes de `visual-retrieve` y `visual-generate`:** `visual_rules.json` ha de existir, con `testset_sha256` igual al del conjunto. Si no, el comando se niega (FR-025).
- **Huellas:** cada ejecución guarda su huella como en F05, y los campos nuevos van en `LATE_FIELDS`, para no invalidar ejecuciones anteriores.
- **Tandas parciales:** una tanda que no cubre todo el conjunto queda marcada como parcial y `visual-decide` no la usa.

## 5. Informe (`reports/visual-eval-report.md`)

1. Corpus y conjunto de prueba: documentos, páginas, tipos y revisión.
2. Recuperación por página: tabla por modo × px × tokens × dimensión y por tipo.
3. Respuestas: tabla por variante y por tipo (correctas, negativas y citas), con los tokens y la muestra manual.
4. Paridad con el teléfono y prueba de lectura.
5. Regla de sí o no aplicada y parámetros elegidos, con la cifra de cada uno y las alternativas.
6. Duración de las tandas.

## 6. Pruebas (`offline/tests/`)

| Fichero | Qué cubre |
|---|---|
| `test_raster.py` | Píxeles por lado largo, fondo blanco, formatos, trozos con solape y caché |
| `test_photos.py` | Misma semilla, mismos bytes; recetas dentro de límites |
| `test_visual_testset.py` | Reglas V1–V8 y veredictos |
| `test_visual_index.py` | De fragmento a página con solape; vectores truncados y normalizados (`fake_embedding`) |
| `test_visual_retrieval.py` | Listas por página, RRF de 2 y 3 listas, empates y métricas |
| `test_visual_prompt.py` | Numeración, intercalado de imágenes, regla de duplicados y recuento de tokens |
| `test_visual_judge.py` | Citas a página y a bloque con páginas |
| `test_visual_parity.py` | Manifiesto, cosenos y lectura |
| `test_vision_runtime.py` | Presupuesto por `ctypes` con una librería falsa, y `Contents` |
| `test_visual_rules.py` | Huella congelada, regla de sí o no y reglas de elección |
| `test_appconfig.py` | Amplía la comprobación: la 440M de `ModelCatalog.kt` coincide con `models.py` |
