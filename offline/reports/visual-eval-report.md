# Informe de evaluación del RAG visual (F18)

Generado con `run_eval.py visual-report`. Regla congelada en `config/visual_rules.json` (D-073, D-075, D-076).

## 1. Corpus y conjunto de prueba

44 documentos públicos con 302 páginas, en `corpus_visual/` (fuentes y licencias en `corpus_visual/SOURCES.md`).

| Tipo | Documentos | Páginas |
|---|---|---|
| escaneado | 5 | 100 |
| estadistica | 8 | 79 |
| imagen | 25 | 25 |
| informe | 3 | 50 |
| normativa | 3 | 48 |

Formatos: jpg 18, pdf 19, png 7.

147 preguntas redactadas por agentes separados y revisadas por el asistente con la página a la vista (D-033): 143 aceptadas y 4 descartadas, 66.4 % visuales.

| Tipo | Aceptadas |
|---|---|
| Tabla | 25 |
| Gráfico | 26 |
| Escaneado | 22 |
| Imagen | 22 |
| Mixta | 7 |
| Control | 28 |
| Sin respuesta | 13 |

Una foto y su página original son relevantes la una para la otra, igual que el folleto de la BNE y la Gaceta de 1931. De las aceptadas, 25 tienen páginas relevantes en más de un documento.

## 2. Recuperación por página

130 preguntas respondibles. Recall@5 por página: la parte de las páginas relevantes que salen entre las 5 primeras.

| Modo | px | Tokens | Dim. | Recall@5 visual | MRR visual | Recall@5 de todas | nDCG@10 de todas |
|---|---|---|---|---|---|---|---|
| texto | — | — | — | 0.416 | 0.367 | 0.550 | 0.484 |
| visual | 768 | 70 | 256 | 0.516 | 0.412 | 0.496 | 0.440 |
| ambos | 768 | 70 | 256 | 0.400 | 0.370 | 0.481 | 0.427 |
| visual | 768 | 70 | 512 | 0.537 | 0.408 | 0.527 | 0.449 |
| ambos | 768 | 70 | 512 | 0.411 | 0.374 | 0.500 | 0.433 |
| visual | 768 | 140 | 256 | 0.716 | 0.563 | 0.750 | 0.647 |
| ambos | 768 | 140 | 256 | 0.437 | 0.427 | 0.562 | 0.511 |
| visual | 768 | 140 | 512 | 0.700 | 0.556 | 0.735 | 0.654 |
| ambos | 768 | 140 | 512 | 0.437 | 0.424 | 0.562 | 0.508 |
| visual | 1024 | 70 | 256 | 0.563 | 0.446 | 0.538 | 0.445 |
| ambos | 1024 | 70 | 256 | 0.368 | 0.372 | 0.473 | 0.438 |
| visual | 1024 | 70 | 512 | 0.542 | 0.448 | 0.527 | 0.459 |
| ambos | 1024 | 70 | 512 | 0.411 | 0.384 | 0.508 | 0.454 |
| visual | 1024 | 140 | 256 | 0.726 | 0.599 | 0.758 | 0.655 |
| ambos | 1024 | 140 | 256 | 0.447 | 0.437 | 0.562 | 0.506 |
| visual | 1024 | 140 | 512 | 0.742 | 0.586 | 0.765 | 0.661 |
| ambos | 1024 | 140 | 512 | 0.447 | 0.425 | 0.565 | 0.498 |
| visual | 1536 | 70 | 256 | 0.547 | 0.429 | 0.523 | 0.451 |
| ambos | 1536 | 70 | 256 | 0.411 | 0.386 | 0.512 | 0.445 |
| visual | 1536 | 70 | 512 | 0.563 | 0.420 | 0.550 | 0.453 |
| ambos | 1536 | 70 | 512 | 0.421 | 0.386 | 0.523 | 0.451 |
| visual | 1536 | 140 | 256 | 0.732 | 0.625 | 0.765 | 0.685 |
| ambos | 1536 | 140 | 256 | 0.447 | 0.419 | 0.569 | 0.506 |
| visual | 1536 | 140 | 512 | 0.747 | 0.628 | 0.769 | 0.693 |
| ambos | 1536 | 140 | 512 | 0.447 | 0.425 | 0.573 | 0.508 |

Elegido con la regla congelada (R12, D-076): **768 px, 140 tokens por imagen y 256 dimensiones**, con el Recall@5 visual de «Visual».

Por tipo de pregunta, con lo elegido (Recall@5):

| Modo | Tabla | Gráfico | Escaneado | Imagen | Mixta | Control |
|---|---|---|---|---|---|---|
| texto | 0.440 | 0.885 | 0.000 | 0.250 | 0.929 | 0.911 |
| visual | 0.520 | 0.942 | 0.636 | 0.750 | 0.929 | 0.821 |
| ambos | 0.480 | 0.923 | 0.000 | 0.250 | 0.929 | 0.893 |

La RRF de las tres listas («ambos») deja los escaneados y las imágenes donde los deja «Texto»: una página sin texto solo está en la lista de páginas, y la RRF premia las que aparecen en varias. Por eso las páginas de la respuesta salen de «Visual» (D-076).

## 3. Respuestas

Juez automático de F05 con citas a página (R11), sobre las preguntas aceptadas. Tokens: medianas del texto estimado y de los visuales; el primer token es la mediana en el escritorio. Gemma corre en la GPU del Mac con activaciones FP32: con las fp16 por defecto deformaba cifras («20000» por 2020), y esa tanda queda aparte en `build/visual/runs-fp16`.

| Variante | Completa | Visuales | Tabla | Gráfico | Escaneado | Imagen | Mixta | Control | Negativa correcta | Negativas indebidas | Citas | Tokens de texto | Tokens visuales | Primer token |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| texto | sí | 18.9 % | 32.0 % | 7.7 % | 0.0 % | 36.4 % | 100.0 % | 85.7 % | 92.3 % | 38.5 % | 75.9 % | 1447 | 0 | 0.81 s |
| paginas-p1-b280-t0 | sí | 12.6 % | 4.0 % | 7.7 % | 4.5 % | 36.4 % | 57.1 % | 14.3 % | 92.3 % | 63.1 % | 75.0 % | 219 | 280 | 1.12 s |
| paginas-p2-b280-t0 | sí | 17.9 % | 4.0 % | 19.2 % | 9.1 % | 40.9 % | 71.4 % | 21.4 % | 100.0 % | 52.3 % | 81.5 % | 234 | 560 | 1.88 s |
| paginas-p3-b280-t0 | sí | 18.9 % | 8.0 % | 19.2 % | 13.6 % | 36.4 % | 71.4 % | 32.1 % | 84.6 % | 40.0 % | 79.3 % | 250 | 840 | 2.32 s |
| paginas-p1-b280-t1 | sí | 15.8 % | 4.0 % | 7.7 % | 9.1 % | 45.5 % | 57.1 % | 32.1 % | 92.3 % | 59.2 % | 52.7 % | 219 | 560 | 1.64 s |
| mezcla-p1-b70-t0 | sí | 14.7 % | 20.0 % | 0.0 % | 4.5 % | 36.4 % | 28.6 % | 28.6 % | 84.6 % | 53.1 % | 57.1 % | 1471 | 70 | 0.82 s |
| mezcla-p1-b140-t0 | sí | 18.9 % | 24.0 % | 3.8 % | 4.5 % | 45.5 % | 57.1 % | 35.7 % | 76.9 % | 42.3 % | 57.5 % | 1471 | 140 | 1.01 s |
| mezcla-p1-b280-t0 | sí | 21.1 % | 24.0 % | 11.5 % | 0.0 % | 50.0 % | 85.7 % | 42.9 % | 84.6 % | 32.3 % | 70.0 % | 1471 | 280 | 1.45 s |
| mezcla-p2-b70-t0 | sí | 13.7 % | 24.0 % | 0.0 % | 4.5 % | 27.3 % | 14.3 % | 17.9 % | 84.6 % | 54.6 % | 49.3 % | 1477 | 140 | 1.00 s |
| mezcla-p2-b140-t0 | sí | 15.8 % | 20.0 % | 0.0 % | 4.5 % | 40.9 % | 28.6 % | 25.0 % | 84.6 % | 43.8 % | 43.8 % | 1477 | 280 | 1.32 s |
| mezcla-p2-b280-t0 | sí | 22.1 % | 24.0 % | 23.1 % | 0.0 % | 40.9 % | 71.4 % | 42.9 % | 84.6 % | 24.6 % | 61.9 % | 1477 | 560 | 2.27 s |
| mezcla-p3-b70-t0 | sí | 12.6 % | 20.0 % | 0.0 % | 9.1 % | 22.7 % | 14.3 % | 10.7 % | 84.6 % | 60.8 % | 41.5 % | 1478 | 210 | 1.19 s |
| mezcla-p3-b140-t0 | sí | 17.9 % | 24.0 % | 3.8 % | 13.6 % | 31.8 % | 28.6 % | 21.4 % | 92.3 % | 41.5 % | 45.7 % | 1478 | 420 | 1.65 s |
| mezcla-p3-b280-t0 | sí | 24.2 % | 24.0 % | 19.2 % | 9.1 % | 45.5 % | 57.1 % | 42.9 % | 84.6 % | 29.2 % | 65.5 % | 1478 | 840 | 2.92 s |
| mezcla-p1-b280-t1 | sí | 25.3 % | 32.0 % | 11.5 % | 4.5 % | 54.5 % | 85.7 % | 57.1 % | 84.6 % | 25.4 % | 74.1 % | 1471 | 560 | 2.27 s |
| mezcla-p2-b280-t1 | sí | 26.3 % | 28.0 % | 15.4 % | 9.1 % | 54.5 % | 71.4 % | 57.1 % | 84.6 % | 19.2 % | 68.6 % | 1477 | 1120 | 3.64 s |
| mezcla-p3-b280-t1 | sí | 27.4 % | 28.0 % | 15.4 % | 13.6 % | 54.5 % | 71.4 % | 53.6 % | 69.2 % | 22.3 % | 72.0 % | 1478 | 1680 | 5.21 s |

### Comprobación del juez

| Variante | Juzgadas | La regla coincide |
|---|---|---|
| mezcla-p3-b280-t1 | 30 | 93.3 % |
| paginas-p3-b280-t0 | 30 | 100.0 % |
| texto | 30 | 100.0 % |

## 4. Paridad con el teléfono y prueba de lectura

- Mismos bytes: 40 vectores, coseno mínimo 0.9882 y medio 0.9993 (umbral 0.99); por debajo: images/foto-boe-fiestas-2026-p2.jpg@140.
- Páginas rasterizadas en cada lado: 20 vectores, coseno mínimo 0.9302 y medio 0.9790.
- Lectura: 3 de 12 correctas en el teléfono y 3 en el escritorio; mismo veredicto en 12.

## 5. Regla de sí o no y parámetros

Parámetros de recuperación elegidos antes de las respuestas: 768 px, 140 tokens por imagen y 256 dimensiones (R12, D-076).

Candidatas de la etapa A (primer token compuesto ≤ 8.800 ms): `mezcla-p1-b140-t0`, `mezcla-p1-b70-t0`, `mezcla-p2-b70-t0`.

| Condición | Cifra | Cumple |
|---|---|---|
| +15 puntos en las visuales | sin revisión decisiva | no |
| −2 puntos como mucho en F05 | sin revisión decisiva | no |
| p95 compuesto ≤ 8 s con al menos 1 página | ninguna configuración cabe | no |
| 440M: golden ≥ 0,99 e identidad ≥ 0,9999 | golden 0.99299, identidad 1.00000 | sí |

Resultado de la regla: **no**; decide el usuario (D-073).

Sin una configuración que cumpla el primer token no hay configuración elegida que revisar a mano. Con el juez automático, las candidatas dan 18.9 % (`mezcla-p1-b140-t0`), 14.7 % (`mezcla-p1-b70-t0`), 13.7 % (`mezcla-p2-b70-t0`) en las visuales, frente a 18.9 % de «Texto»: ninguna se acerca a los +15 puntos.

## 6. Duración de las tandas

| Variante | Respuestas | Segundos en total | Por respuesta |
|---|---|---|---|
| texto | 143 | 180 | 1.26 |
| paginas-p1-b280-t0 | 143 | 197 | 1.38 |
| paginas-p2-b280-t0 | 143 | 306 | 2.14 |
| paginas-p3-b280-t0 | 143 | 370 | 2.58 |
| paginas-p1-b280-t1 | 143 | 256 | 1.79 |
| mezcla-p1-b70-t0 | 143 | 156 | 1.09 |
| mezcla-p1-b140-t0 | 143 | 182 | 1.27 |
| mezcla-p1-b280-t0 | 143 | 249 | 1.74 |
| mezcla-p2-b70-t0 | 143 | 179 | 1.25 |
| mezcla-p2-b140-t0 | 143 | 227 | 1.59 |
| mezcla-p2-b280-t0 | 143 | 357 | 2.50 |
| mezcla-p3-b70-t0 | 143 | 202 | 1.42 |
| mezcla-p3-b140-t0 | 143 | 271 | 1.90 |
| mezcla-p3-b280-t0 | 143 | 467 | 3.27 |
| mezcla-p1-b280-t1 | 143 | 360 | 2.52 |
| mezcla-p2-b280-t1 | 143 | 573 | 4.01 |
| mezcla-p3-b280-t1 | 143 | 789 | 5.52 |
