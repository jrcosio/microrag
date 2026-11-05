# Viabilidad visual en el teléfono de referencia (F18)

Cifras reales del Nothing Phone (1) A063 (QTI SM7325, Android 15, LiteRT-LM 0.18.1), del 2026-11-05. Las tablas de las secciones 1–10 son el informe que escribe el diagnóstico «Viabilidad visual» (`VisualFeasibilityBenchmarkTest`, etapa A con todos los bloques). Las respuestas y los vectores van solo en el JSON, en `offline/build/visual/phone-report-a.json` y `phone-report-b.json`, que no van a git.

## Resumen

| Medida | Cifra | Objetivo |
|---|---|---|
| Golden de la 440M frente al escritorio | CPU 1,00000 · GPU 0,99299 (mínimos, 50 textos) | ≥ 0,99 ✅ |
| Identidad de la 440M con la 270M | mínimo 1,00000 | ≥ 0,9999 ✅ |
| Consulta con la 440M en CPU | p95 121,1 ms (270M: 120,3 ms) | < 150 ms ✅ |
| Embedding de una página con la 440M | CPU: p50 1,29 s a 70 tokens y 3,22 s a 140. En GPU no está: el fichero solo declara visión en CPU | — |
| Proyección de un PDF de 200 páginas (140 tokens) | p50 657 s, p95 836 s | — |
| Primer token, solo texto D-034 (etapa A, 1 contexto) | p95 3,3 s | — |
| Primer token, 1 página a 280 (etapa A) | p95 11,4 s solo páginas · 14,5 s mezcla | — |
| Primer token compuesto con páginas, etapa B (20 preguntas) | mejor candidata: 9,8 s (`mezcla-p1-b70-t0`) | ≤ 8 s ❌ |
| RSS pico con Gemma y visión | 3,5–3,9 GB | — |
| Paridad de vectores, mismos bytes | 40 vectores: mínimo 0,98823, medio 0,99931; por debajo de 0,99, `foto-boe-fiestas-2026-p2` a 140 | ≥ 0,99 (1 de 40 no) |
| Paridad de páginas convertidas en cada lado | 20 vectores: mínimo 0,930, medio 0,979 | — |
| Lectura de 12 páginas a 280 | 3 correctas en el teléfono y 3 en el escritorio, mismo veredicto en las 12 | — |

**Primer token compuesto** (R4, D-075; se marca como compuesto porque no se mide de una vez): 206,5 ms de recuperación de F17, más 53,0 ms de cota para la lista de páginas, más el exceso de la consulta con la 440M frente a la 270M (0,7 ms), más el p95 de Gemma.

## Etapa B: las candidatas con 20 preguntas

Las candidatas salen de la etapa A: son las mezclas cuyo primer token compuesto queda por debajo de 8.800 ms (8 s más el 10 % de margen). Con las 20 preguntas reales (contextos de 1.700–2.700 tokens de prefill), ninguna baja de 8 s:

| Etapa | Combinación | Muestras | Primer token p50 / p95 (ms) | Total p50 (ms) | Tokens del prefill | Prefill (tok/s) | Decodificación (tok/s) | RSS pico (MB) | Térmico | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| B | mezcla-p1-b140-t0 · visión CPU | 20 | 8959.0 / 11263.0 | 10303.0 | 2166 | 360.9 | 8.9 | 3851 | NONE → NONE · cargando | completada |
| B | mezcla-p1-b70-t0 · visión CPU | 20 | 7192.0 / 9502.0 | 8567.0 | 2103 | 354.2 | 8.7 | 3948 | NONE → NONE · cargando | completada |
| B | mezcla-p2-b70-t0 · visión CPU | 20 | 8860.0 / 10684.0 | 9771.0 | 2194 | 358.1 | 8.9 | 3583 | NONE → NONE · cargando | completada |

| Candidata | p95 de Gemma (ms) | p95 compuesto (ms) |
|---|---:|---:|
| `mezcla-p1-b140-t0` | 11.263 | 11.523 |
| `mezcla-p1-b70-t0` | 9.502 | 9.762 |
| `mezcla-p2-b70-t0` | 10.684 | 10.944 |

## Cifras deformadas con fp16

Gemma corre en la GPU del teléfono con las activaciones por defecto (fp16, D-023). En 2 de las 60 respuestas de la etapa B salen años deformados: «septiembre de 20206» y «mismo mes de 202025». En el Mac, con fp16, pasaba en muchas más respuestas, y con FP32 desaparece (`offline/reports/visual-eval-report.md` §3).

---

## 1. Sesión (etapa A)

| Campo | Valor |
|---|---|
| Fecha | 2026-11-05T11:19:17+01:00 |
| Dispositivo | Nothing A063 · QTI SM7325 · 11236 MB RAM |
| Android | 15 (API 35) |
| Versiones | app 0.1.0 · LiteRT-LM 0.18.1 |
| Conjunto del dispositivo | 132 ficheros verificados |

## 2. Modelos

| Id | Fichero | SHA-256 | Estado |
|---|---|---|---|
| GEMMA4_E2B | `gemma-4-E2B-it.litertlm` | `181938105e0e…` | VERIFIED |
| GEMMA4_E2B_GPU | `gemma-4-E2B-it-gpu.litertlm` | `a53a59001894…` | MISSING |
| EG2_740M | `embeddinggemma-2-740m.litertlm` | `e7a8a2204b91…` | MISSING |
| EG2_440M | `embeddinggemma-2-text-vision-440m.litertlm` | `92dcbea10889…` | VERIFIED |
| EG2_270M | `embeddinggemma-2-text-270m.litertlm` | `2d079ee2f6f0…` | VERIFIED |

- **Gemma:** imágenes sí, presupuesto máximo 280, firmas 70/140/280, texto en CPU/GPU, visión en CPU.
- **440M:** imágenes sí, presupuesto máximo 140, firmas 70/140, texto en CPU/GPU, visión en CPU.

## 3. Gemma con páginas

| Etapa | Combinación | Muestras | Primer token p50 / p95 (ms) | Total p50 (ms) | Tokens del prefill | Prefill (tok/s) | Decodificación (tok/s) | RSS pico (MB) | Térmico | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| load | carga-vision · visión CPU | — | — | — | — | — | — | 2390 | NONE → NONE · cargando | completada |
| load | carga-texto · visión — | — | — | — | — | — | — | 1946 | NONE → NONE · cargando | completada |
| A | paginas-p1-b280-t0 · visión GPU | — | — | — | — | — | — | 476 | NONE → NONE · cargando | not_available · START · vision backend GPU not declared by the model |
| A | texto · visión CPU | 3 | 3289.0 / 3299.0 | 4224.0 | 1103 | 368.2 | 8.3 | 2880 | NONE → NONE · cargando | completada |
| A | paginas-p1-b70-t0 · visión CPU | 3 | 2832.0 / 2837.0 | 3708.0 | 338 | 252.3 | 8.9 | 3215 | NONE → NONE · cargando | completada |
| A | paginas-p1-b140-t0 · visión CPU | 3 | 5019.0 / 5032.0 | 5928.0 | 401 | 225.1 | 8.5 | 3474 | NONE → NONE · cargando | completada |
| A | paginas-p1-b280-t0 · visión CPU | 3 | 11364.0 / 11427.0 | 12170.0 | 541 | 224.6 | 8.4 | 3927 | NONE → NONE · cargando | completada |
| A | paginas-p1-b280-t1 · visión CPU | 3 | 23415.0 / 23456.0 | 24318.0 | 782 | 318.9 | 8.8 | 3915 | NONE → NONE · cargando | completada |
| A | paginas-p2-b70-t0 · visión CPU | 3 | 5126.0 / 5150.0 | 6005.0 | 428 | 239.0 | 8.8 | 3897 | NONE → NONE · cargando | completada |
| A | paginas-p2-b140-t0 · visión CPU | 3 | 10103.0 / 10141.0 | 11030.0 | 554 | 230.2 | 8.4 | 3899 | NONE → NONE · cargando | completada |
| A | paginas-p2-b280-t0 · visión CPU | 3 | 23229.0 / 23403.0 | 24098.0 | 834 | 338.0 | 8.9 | 3895 | NONE → NONE · cargando | completada |
| A | paginas-p2-b280-t1 · visión CPU | 3 | 45120.0 / 45419.0 | 46017.0 | 1316 | 340.1 | 9.0 | 3921 | NONE → NONE · cargando | completada |
| A | paginas-p3-b70-t0 · visión CPU | 3 | 7196.0 / 7198.0 | 7965.0 | 517 | 214.4 | 10.2 | 3731 | NONE → NONE · cargando | completada |
| A | paginas-p3-b140-t0 · visión CPU | 3 | 13805.0 / 13853.0 | 14685.0 | 706 | 289.0 | 8.9 | 3715 | NONE → NONE · cargando | completada |
| A | paginas-p3-b280-t0 · visión CPU | 3 | 33927.0 / 37561.0 | 34873.0 | 1126 | 381.0 | 9.0 | 3731 | NONE → NONE · cargando | completada |
| A | paginas-p3-b280-t1 · visión CPU | 3 | 66866.0 / 67530.0 | 67803.0 | 1849 | 362.8 | 8.9 | 3378 | NONE → NONE · cargando | completada |
| A | mezcla-p1-b70-t0 · visión CPU | 3 | 4831.0 / 4846.0 | 5694.0 | 1111 | 371.3 | 8.8 | 3528 | NONE → NONE · cargando | completada |
| A | mezcla-p1-b140-t0 · visión CPU | 3 | 7452.0 / 7463.0 | 8370.0 | 1174 | 342.0 | 8.5 | 3538 | NONE → NONE · cargando | completada |
| A | mezcla-p1-b280-t0 · visión CPU | 3 | 14430.0 / 14451.0 | 15294.0 | 1314 | 336.6 | 8.8 | 3537 | NONE → NONE · cargando | completada |
| A | mezcla-p1-b280-t1 · visión CPU | 3 | 25812.0 / 27443.0 | 26764.0 | 1555 | 307.5 | 8.2 | 3539 | NONE → NONE · cargando | completada |
| A | mezcla-p2-b70-t0 · visión CPU | 3 | 6899.0 / 7125.0 | 7671.0 | 1201 | 344.7 | 9.4 | 3488 | NONE → NONE · cargando | completada |
| A | mezcla-p2-b140-t0 · visión CPU | 3 | 11892.0 / 11966.0 | 12779.0 | 1327 | 334.4 | 8.6 | 3454 | NONE → NONE · cargando | completada |
| A | mezcla-p2-b280-t0 · visión CPU | 3 | 25830.0 / 29679.0 | 26691.0 | 1607 | 317.1 | 9.0 | 3462 | NONE → NONE · cargando | completada |
| A | mezcla-p2-b280-t1 · visión CPU | 3 | 46918.0 / 47031.0 | 47803.0 | 2089 | 370.6 | 8.8 | 3469 | NONE → NONE · cargando | completada |
| A | mezcla-p3-b70-t0 · visión CPU | 3 | 10008.0 / 10010.0 | 10842.0 | 1710 | 331.4 | 9.1 | 3523 | NONE → NONE · cargando | completada |
| A | mezcla-p3-b140-t0 · visión CPU | 3 | 16594.0 / 16610.0 | 17523.0 | 1899 | 367.9 | 8.4 | 3474 | NONE → NONE · cargando | completada |
| A | mezcla-p3-b280-t0 · visión CPU | 3 | 37612.0 / 37626.0 | 38490.0 | 2319 | 349.3 | 8.9 | 3512 | NONE → NONE · cargando | completada |
| A | mezcla-p3-b280-t1 · visión CPU | 3 | 69646.0 / 69829.0 | 70546.0 | 3042 | 377.0 | 9.0 | 3528 | NONE → NONE · cargando | completada |

## 4. Lectura de páginas

| Preguntas | Primer token p50 / p95 (ms) | Estado |
|---:|---:|---|
| 12 | 12990.0 / 14537.0 | completada |

_Las respuestas van solo en el JSON; las juzga el escritorio (`run_eval.py visual-parity`)._

## 5. Embeddings de texto

| Modelo | Backend | Carga frío / caché (ms) | Consulta p50 / p95 (ms) | Fragmento p50 / p95 (ms) | RSS pico (MB) | Térmico | Estado |
|---|---|---:|---:|---:|---:|---|---|
| EG2_270M | CPU | 779 / 256 | 119.7 / 120.3 | 892.8 / 897.8 | 646 | NONE → NONE · cargando | completada |
| EG2_440M | CPU | 768 / 295 | 120.0 / 121.1 | 900.3 / 1101.7 | 652 | NONE → NONE · cargando | completada |

## 6. Embeddings de imagen

| Backend | Tokens por imagen | Carga frío / caché (ms) | Página p50 / p95 (ms) | Dimensión y norma | RSS pico (MB) | Térmico | Estado |
|---|---:|---:|---:|---|---:|---|---|
| CPU | 70 | 1103 / 344 | 1292.6 / 1443.4 | 768: 379.867880, 256: 1.000000, 512: 1.000000 | 870 | NONE → NONE · cargando | completada |
| GPU | 70 | — | — | — | 352 | NONE → NONE · cargando | not_available · START · vision backend not declared by the model |
| CPU | 140 | 1435 / 360 | 3219.5 / 3284.6 | 768: 268.592889, 256: 1.000000, 512: 1.000000 | 1063 | NONE → NONE · cargando | completada |
| GPU | 140 | — | — | — | 331 | NONE → NONE · cargando | not_available · START · vision backend not declared by the model |

## 7. Paridad con el escritorio

| Tokens por imagen | Vectores | Estado |
|---:|---:|---|
| 70 | 30 | completada |
| 140 | 30 | completada |

_Los vectores van solo en el JSON; los cosenos los calcula el escritorio._

## 8. Conversión de páginas

| Documento | px | Formato | Página p50 / p95 (ms) | Peso p50 (KB) | Tamaño | RSS pico (MB) | Térmico | Estado |
|---|---:|---|---:|---:|---|---:|---|---|
| pdfs/ine-ipc-2026-09.pdf | 768 | WEBP | 86.1 / 95.9 | 34 | 542×768 | 464 | NONE → NONE · cargando | completada |
| pdfs/ine-ipc-2026-09.pdf | 768 | JPEG | 29.0 / 31.0 | 54 | 542×768 | 289 | NONE → NONE · cargando | completada |
| pdfs/ine-ipc-2026-09.pdf | 1024 | WEBP | 128.7 / 143.4 | 52 | 723×1024 | 286 | NONE → NONE · cargando | completada |
| pdfs/ine-ipc-2026-09.pdf | 1024 | JPEG | 34.9 / 37.5 | 85 | 723×1024 | 292 | NONE → NONE · cargando | completada |
| pdfs/ine-ipc-2026-09.pdf | 1536 | WEBP | 251.3 / 271.7 | 89 | 1085×1536 | 299 | NONE → NONE · cargando | completada |
| pdfs/ine-ipc-2026-09.pdf | 1536 | JPEG | 50.7 / 54.6 | 151 | 1085×1536 | 300 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 768 | WEBP | 119.0 / 122.0 | 105 | 572×768 | 295 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 768 | JPEG | 28.0 / 29.2 | 135 | 572×768 | 290 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 1024 | WEBP | 190.8 / 200.1 | 171 | 763×1024 | 295 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 1024 | JPEG | 30.2 / 30.8 | 224 | 763×1024 | 295 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 1536 | WEBP | 311.8 / 317.6 | 311 | 1145×1536 | 313 | NONE → NONE · cargando | completada |
| pdfs/gazeta-1931-12-10.pdf | 1536 | JPEG | 50.5 / 51.2 | 404 | 1145×1536 | 302 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 768 | WEBP | 98.5 / 111.6 | 39 | 768×543 | 278 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 768 | JPEG | 46.4 / 63.9 | 63 | 768×543 | 289 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 1024 | WEBP | 140.1 / 150.7 | 62 | 1024×724 | 302 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 1024 | JPEG | 52.4 / 70.2 | 100 | 1024×724 | 291 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 1536 | WEBP | 235.1 / 248.9 | 110 | 1536×1087 | 312 | NONE → NONE · cargando | completada |
| pdfs/boe-fiestas-2026.pdf | 1536 | JPEG | 66.7 / 82.9 | 189 | 1536×1087 | 303 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 768 | WEBP | 89.2 / 134.6 | 52 | 543×768 | 308 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 768 | JPEG | 39.1 / 87.8 | 80 | 543×768 | 314 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 1024 | WEBP | 130.4 / 175.1 | 82 | 724×1024 | 308 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 1024 | JPEG | 46.1 / 96.8 | 124 | 724×1024 | 315 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 1536 | WEBP | 242.0 / 275.6 | 142 | 1085×1536 | 325 | NONE → NONE · cargando | completada |
| pdfs/aemet-original-2026-07.pdf | 1536 | JPEG | 62.4 / 117.5 | 225 | 1085×1536 | 316 | NONE → NONE · cargando | completada |

## 9. Decodificación de imágenes

| Imagen | px | Formato | p50 / p95 (ms) | Peso p50 (KB) | Tamaño | RSS pico (MB) | Estado |
|---|---:|---|---:|---:|---|---:|---|
| decode/panel-letur.jpg | 768 | JPEG | 111.5 / 111.7 | 144 | 768×578 | 335 | completada |
| decode/panel-letur.jpg | 1024 | JPEG | 126.1 / 127.2 | 249 | 1024×771 | 369 | completada |
| decode/panel-letur.jpg | 1536 | JPEG | 141.2 / 141.9 | 495 | 1536×1157 | 429 | completada |
| decode/cartel-precios-mercado.jpg | 768 | JPEG | 81.4 / 82.5 | 111 | 576×768 | 410 | completada |
| decode/cartel-precios-mercado.jpg | 1024 | JPEG | 95.1 / 95.9 | 175 | 768×1024 | 407 | completada |
| decode/cartel-precios-mercado.jpg | 1536 | JPEG | 109.2 / 109.6 | 307 | 1152×1536 | 421 | completada |
| decode/alpha.png | 768 | JPEG | 0.4 / 0.5 | 0 | 100×50 | 386 | completada |
| decode/alpha.png | 1024 | JPEG | 0.4 / 0.5 | 0 | 100×50 | 392 | completada |
| decode/alpha.png | 1536 | JPEG | 0.4 / 0.5 | 0 | 100×50 | 267 | completada |
| decode/exif_rotated.jpg | 768 | JPEG | 2.5 / 2.6 | 1 | 200×400 | 283 | completada |
| decode/exif_rotated.jpg | 1024 | JPEG | 2.5 / 2.6 | 1 | 200×400 | 298 | completada |
| decode/exif_rotated.jpg | 1536 | JPEG | 2.5 / 2.6 | 1 | 200×400 | 314 | completada |
| decode/cmyk.jpg | 768 | JPEG | 0.3 / 0.3 | 0 | 80×40 | 319 | completada |
| decode/cmyk.jpg | 1024 | JPEG | 0.3 / 0.4 | 0 | 80×40 | 325 | completada |
| decode/cmyk.jpg | 1536 | JPEG | 0.3 / 0.4 | 0 | 80×40 | 269 | completada |

## 10. Proyección de un PDF de 200 páginas

| Páginas medidas | Por página p50 / p95 (ms) | Proyección a 200 p50 / p95 (s) | Térmico inicio → mitad → fin | Estado |
|---:|---:|---:|---|---|
| 50 | 3286.5 / 4181.9 | 657.3 / 836.4 | NONE → NONE → NONE | completada |

_La proyección multiplica el tiempo por página medido; no es una ingesta real (R14)._
