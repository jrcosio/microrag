# Informe de viabilidad on-device (F02)

## 1. Sesión

| Campo | Valor |
|---|---|
| Fecha | 2026-10-26T00:13:54+01:00 |
| Dispositivo | Nothing A063 · QTI SM7325 · 11236 MB RAM |
| Android | 15 (API 35) |
| Versiones | app 0.1.0 · LiteRT-LM 0.18.1 |
| Térmico inicial | NONE (margen 0.54) |
| Batería | 100 % · 33.0 °C · cargando |

## 2. Modelos

| Id | Fichero | MB | SHA-256 | Revisión | Estado |
|---|---|---:|---|---|---|
| GEMMA4_E2B | `gemma-4-E2B-it.litertlm` | 2468 | `181938105e0e…` | `1caa527c` | VERIFIED |
| GEMMA4_E2B_GPU | `gemma-4-E2B-it-gpu.litertlm` | 1915 | `a53a59001894…` | `1caa527c` | VERIFIED |
| EG2_740M | `embeddinggemma-2-740m.litertlm` | 462 | `e7a8a2204b91…` | `24d962e9` | VERIFIED |
| EG2_440M | `embeddinggemma-2-text-vision-440m.litertlm` | 369 | `92dcbea10889…` | `e301f74d` | VERIFIED |
| EG2_270M | `embeddinggemma-2-text-270m.litertlm` | 157 | `2d079ee2f6f0…` | `9be6e8b9` | VERIFIED |

## 3. Embeddings

| Modelo | Backend | Init frío / caché (ms) | Consulta p50 / p95 (ms) | Documento p50 / p95 (ms) | Dims | Norma | Contexto máx. | RSS pico (MB) | Térmico | Estado |
|---|---|---:|---:|---:|---|---:|---:|---:|---|---|
| EG2_740M | CPU | 825 / 309 | 119.8 / 124.8 | 886.4 / 889.5 | 768 → 256 | 1.000000 | 8192 | 625 | NONE → NONE | completada |
| EG2_740M | GPU | 6186 / 1069 | 121.1 / 124.0 | 319.7 / 323.4 | 768 → 256 | 1.000000 | 8192 | 562 | NONE → NONE | completada |
| EG2_440M | CPU | 817 / 304 | 119.8 / 122.6 | 903.0 / 1035.4 | 768 → 256 | 1.000000 | 8192 | 656 | NONE → NONE | completada |
| EG2_440M | GPU | 6164 / 1060 | 133.3 / 140.7 | 326.5 / 331.1 | 768 → 256 | 1.000000 | 8192 | 559 | NONE → NONE | completada |
| EG2_270M | CPU | 782 / 271 | 119.1 / 120.3 | 873.9 / 881.0 | 768 → 256 | 1.000000 | 8192 | 652 | NONE → NONE | completada |
| EG2_270M | GPU | 6818 / 1152 | 138.8 / 141.1 | 354.9 / 356.5 | 768 → 256 | 1.000000 | 8192 | 547 | NONE → NONE | completada |

## 4. Compatibilidad CPU↔GPU

| Modelo | Textos | Coseno mínimo | Coseno medio | Compatible (≥ 0,99) | Estado |
|---|---:|---:|---:|---|---|
| EG2_740M | 20 | 0.99321 | 0.99744 | sí | completada |
| EG2_440M | 20 | 0.99321 | 0.99744 | sí | completada |
| EG2_270M | 20 | 0.99321 | 0.99744 | sí | completada |

## 5. Generación

| Modelo | Backend | Activaciones | Init frío / caché (ms) | Tokens prompt | TTFT bench / pared (ms) | Prefill (tok/s) | Decode (tok/s) | RSS pico (MB) | Térmico | Plan B | Estado |
|---|---|---|---:|---:|---:|---:|---:|---:|---|---|---|
| GEMMA4_E2B | GPU | por defecto | 16876 / 5403 | 1986 | 4528.4 / 4745 | 450.0 | 8.8 | 2478 | NONE → NONE | no | completada |
| GEMMA4_E2B | GPU | FLOAT32 | 16832 / 5496 | 1986 | 7361.1 / 7599 | 274.2 | 8.9 | 2735 | NONE → NONE | no | completada |
| GEMMA4_E2B_GPU | GPU | por defecto | 12685 / 12507 | 1986 | 4779.3 / 4944 | 424.5 | 9.9 | 2147 | NONE → NONE | no | completada |
| GEMMA4_E2B_GPU | GPU | FLOAT32 | 12266 / 11379 | 1986 | 13478.1 / 13634 | 148.6 | 9.1 | 2330 | NONE → NONE | no | completada |

## 6. Criterios de salida de la fase 0

- TTFT medido con prompt de ≈ 2.000 tokens: **sí**
- Mejor consulta p95 en CPU: **120.3 ms**
- Consulta < 150 ms p95 en CPU: **sí**

## 7. Muestra de salida

### GEMMA4_E2B · GPU · activaciones por defecto

> En la fase cero se mide lo siguiente:
> *   El tiempo de inicialización de cada motor [6].
> *   La latencia del embedding de una consulta en CPU y en GPU [6].
> *   El tiempo hasta el primer token con un prompt de unos dos mil tokens [6].
> *
> Los criterios de salida son:
> *   Tener medido el tiempo hasta el primer token con ese tamaño de prompt [6].
> *   Conseguir que el embedding de una consulta tarde menos de 150 milisegundos en el percentil 95 en CPU [6].
> *

### GEMMA4_E2B · GPU · activaciones FLOAT32

> En la fase cero se mide lo siguiente: el tiempo de inicialización de cada motor, la latencia del embedding de una consulta en CPU y en GPU, el tiempo hasta el primer token con un prompt de unos dos mil tokens y la velocidad de decodificación [6].
> 
> Los criterios de salida son: tener medido el tiempo hasta el primer token con ese tamaño de prompt y conseguir que el embedding de una consulta tarde menos de 150 milisegundos en el percentil 95 en CPU [6].
> 
> El embedding de una consulta se ejecuta en la CPU porque una consulta es un texto corto, y

### GEMMA4_E2B_GPU · GPU · activaciones por defecto

> La fase de la faseतरह[6] comprЦена
> 
> Los (criter[6]) son:
> - La faseतरह[6] compr[1] prueba con el dispositivo [6] para medir el tiempo de inicialDet[6] y la lat[6] del [6] en la CPU.
> - Los criterios de salida[6] son:
>     - El tiempo de la primera vez obten[6] con ese conc[6] prompt de dos milful, y la velocidad de decЦена[6].
>     - El tiempo de decમના [6] en el percent[95] en

### GEMMA4_E2B_GPU · GPU · activaciones FLOAT32

> En la fase cero se mide lo siguiente: el tiempo de inicialización de cada motor, la latencia del embedding de una consulta en CPU y en GPU, el tiempo hasta el primer token con un prompt de unos dos mil tokens y la velocidad de decodificación [6].
> 
> Los criterios de salida son: tener medido el tiempo hasta el primer token con ese tamaño de prompt y conseguir que el embedding de una consulta tarde menos de 150 milisegundos en el percentil 95 en CPU [6].
> 
> El embedding de una consulta se ejecuta en la CPU porque una consulta es un texto corto, y

_Notas: «Init frío» borra la caché de kernels de LiteRT-LM; la caché de páginas del sistema no se puede vaciar sin root. RSS pico marcado con * no se pudo reiniciar y cubre desde el arranque del proceso._
