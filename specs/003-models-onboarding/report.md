# Informe de cierre — F03 Modelos y primer arranque

**Fecha:** 2026-10-26

**Rama:** `003-models-onboarding`

**Estado:** implementada; pendiente de la revisión del usuario.

**Abierto:** recorrido manual con TalkBack (T071).

## 1. Qué se ha implementado

**Pantallas de producto** (sustituyen los marcadores de F01):
- **P01 Bienvenida** (PNG 01): logo oficial, chip, ilustración en Compose (solo tokens y Lucide, sin semántica), textos literales, CTA «Comenzar» y pie. Se adapta a pantallas compactas y fuente al 200 %.
- **P02 Preparación inicial** (PNG 02): una sola pantalla cuyo «PASO N DE 3» sigue el estado real (D-027). Incluye:
  - 12 estados de fila con texto, detalle y descripción verbal (FR-007);
  - tarjeta de almacenamiento;
  - aviso si las notificaciones están denegadas;
  - texto que explica por qué «Continuar» está deshabilitado (FR-008).
- **Confirmación de descarga:** modelos y tamaños, total, espacio, red actual con aviso de datos móviles, aviso de RAM < 7,5 GB, línea del permiso de notificaciones y «Seleccionar archivo del dispositivo».
- **Arranque:** la splash de la plataforma se mantiene hasta leer la etapa. Destino según la etapa: Bienvenida, Preparación sobre Bienvenida o Inicio. «Continuar» deja Inicio como raíz.
- **Idioma (D-029):** la app está en español aunque el teléfono esté en otro idioma. El inglés es secundario y se elige por app (`localeConfig`).

**Descarga** (`data.download`, el único paquete con red, D-028):
- **`ModelDownloader`:**
  - usa `HttpsURLConnection`;
  - sigue redirecciones a mano, solo a `huggingface.co` y `*.hf.co`, y solo por https;
  - reanuda con `Range` y cubre los casos 206, 200, 416 y rango erróneo;
  - hace `fsync` cada 64 MiB;
  - se cancela en menos de 2 s aunque la lectura esté bloqueada.
- **`ModelDownloadRunner`:**
  - cola EmbeddingGemma 2 → Gemma 4;
  - comprueba el espacio (pendiente + 500 MB) antes de cada modelo y al quedarse sin disco;
  - hace 5 intentos con espera creciente;
  - espera a la red en vez de fallar;
  - verifica en el sitio (`adopt`).
- **Planificadores:**
  - UIDT (`ModelDownloadJobService`) en API ≥ 34;
  - WorkManager con servicio en primer plano `dataSync` en API 31–33, con WorkManager inicializado bajo demanda.
- **Notificación:** progreso real y acción «Detener».

**Verificación y compatibilidad:**
- **Repositorio de ficheros:**
  - `adopt` calcula el hash en el sitio y hace `rename`, sin copiar;
  - `installFromStream` sirve para importar con SAF;
  - registro `verification.json` para el chequeo barato al arrancar.
- **`ModelCompatibilityChecker`:**
  - EmbeddingGemma 2 en CPU, con 768 → 256 dimensiones y norma 1;
  - Gemma 4 en GPU, con CPU como alternativa y aviso «Modo CPU, más lento».

**Casos de uso:** observar, confirmar, iniciar, pausar, reanudar, reintentar, reconciliar, preparar, importar, almacenamiento y completar. Los ViewModels de Bienvenida y Preparación solo hablan con ellos.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 180/180 (F02: 90) |
| Instrumentados en el emulador `Pixel_10` (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 128/128 |
| Instrumentados en el teléfono (`am instrument`, mismos filtros) | ✅ 128/128 |
| `ModelDownloadSmokeTest` (`@RequiresNetwork`) | ✅ EmbeddingGemma 2 descargada de Hugging Face y verificada por las dos rutas: **UIDT** en el emulador API 37 (14,1 s) y **WorkManager** en una AVD API 33 creada para ello (12,5 s, worker en primer plano, resultado `SUCCESS`) |
| `GemmaWarmInitTest` (`@RequiresModels`, teléfono) | ✅ |
| Release | ✅ Lista cerrada de 9 permisos, componentes sin exportar y sin diagnóstico. Detalle en `checklists/release-verification.md` |

Tests nuevos destacados:
- **SC-001:** la resolución de estados probada de forma exhaustiva (5.760 combinaciones): «Listo» solo aparece con el fichero verificado y la prueba superada.
- **`NetworkConfinementTest`:** comprueba que solo `data.download` usa APIs de red.
- **`NoTrafficInstrumentedTest`:** 0 bytes enviados y recibidos al recorrer la app con los modelos listos.

## 3. Validación en el teléfono de referencia

Nothing Phone (1), Android 15. Descarga por Wi-Fi, con los datos móviles desactivados durante las pruebas.

| Escenario | Resultado |
|---|---|
| **US1:** primer arranque | Bienvenida → «Comenzar» → Preparación. Relanzar con el onboarding empezado abre la Preparación. Con el onboarding completado se abre Inicio, y Atrás sale de la app |
| **US3:** ficheros de F02 ya presentes | Se verifican sin descargar (Gemma: ≈ 4,3 s de hash) y pasan la prueba real. Resultado: «Listo» los dos |
| **US2:** descarga completa (2,75 GB) | 6 min 44 s de reloj desde «Descargar» hasta Gemma verificada, con ≈ 1 min 50 s de pausas provocadas por las pruebas. Media ≈ 9 MB/s |
| **FR-018:** «Detener» en la notificación | La notificación mostraba «71 % · 1,9 GB de 2,6 GB». Al detener, el fichero deja de crecer y la fila pasa a «En pausa · 1,9 GB de 2,6 GB». Al tocarla, se reanuda |
| **US4-1:** `kill -9` del proceso a 1,95 GB | El sistema relanza el proceso y el job sigue desde 1,95 GB en < 5 s, sin abrir la app |
| **US4-3 / SC-003:** modo avión 60 s | La fila muestra «Esperando conexión · 2,4 GB de 2,6 GB». Se reanuda **6 s** después de volver la red |
| **US4-2:** reinicio con EmbeddingGemma 2 al 27 % | Al desbloquear, el sistema arranca la app para el job (`am_proc_start … ModelDownloadJobService`) y termina la descarga sin abrir la app (riesgo R13-1 resuelto) |
| **SC-002:** sobrecoste de bytes | 2.857.461.402 bytes recibidos frente a 2.752.774.144 de los ficheros: **+3,8 %**, con pausa, muerte del proceso y modo avión incluidos (límite: 5 %). Ningún byte por datos móviles |
| **US6:** importación | Un fichero ajeno (la variante 440M) se rechaza al instante por tamaño, como «Incompatible · El archivo no corresponde a EmbeddingGemma 2». El correcto (165 MB) queda importado, verificado y probado en ≈ 1 s |
| **SC-005:** toques | 4: «Comenzar», fila, «Descargar» y «Continuar», más el diálogo de permiso del sistema |
| **SC-008:** sin red | `NoTrafficInstrumentedTest` pasa en el teléfono. Antes de la descarga, `netstats` no tenía ninguna entrada del UID de la app. Recorrido en modo avión sin errores |

## 4. Cifras medidas

| Medida | Valor | Criterio |
|---|---:|---|
| Prueba de compatibilidad de Gemma 4 (GPU) | ≈ 19 s: 17,1 s de init en frío + 1,8 s de generación (dos ejecuciones) | SC-010: < 30 s ✅ |
| Init de Gemma después de la prueba (caché de kernels) | **5,13 s** (frente a 17,1 s en frío) | SC-010: ≈ 5,4 s ✅ |
| Prueba de compatibilidad de EmbeddingGemma 2 (CPU) | 0,29–0,44 s | — |
| Arranque en frío de la release con el onboarding completado (10 medidas) | **mediana 820 ms** (629–1.092 ms) | SC-007: < 1 s ✅ (mediana) |
| Arranque en frío en F02 | 680 ms | +140 ms: lectura de la etapa antes del primer frame y grafo DI mayor |
| Reanudación tras volver la red | 6 s | SC-003: ≤ 30 s ✅ |
| Sobrecoste de bytes con interrupciones | +3,8 % | SC-002: ≤ 5 % ✅ |
| APK release sin R8 | 57,4 MB (+3,1 MB) | Se reevalúa en F15 |

## 5. Diferencias frente a los mockups

**P01 Bienvenida (PNG 01):**
- **Logo:** el PNG muestra una aproximación «lisdata ›» en una sola tinta. Se usa el PNG oficial `logo_lisdata.png`, en degradado (G20, no se reconstruye la marca). Sigue pendiente el SVG oficial.
- **Ilustración:** es una reconstrucción decorativa en Compose con la misma composición (órbitas, documento, tile con el destello, tile con el escudo y puntos). Las posiciones y tamaños son aproximados.
- **Textos, CTA y pie:** literales.
- **Pantallas compactas:** la ilustración se reduce o se oculta (MD 01).

**P02 Preparación (PNG 02):**
- **Fila en estado «Listo»:** añade una línea de detalle con el tamaño («2,6 GB», «165 MB»). En los estados no maquetados la línea muestra bytes o el motivo, y aparece la barra de progreso (propuesta aprobada, D-027).
- **Barra inferior:** sobre «Continuar» hay un texto que explica por qué está deshabilitado (FR-008). La barra es fija, así que en pantallas de 2.400 px la tarjeta de privacidad queda bajo el pliegue y hay que desplazarse. En el PNG se ve sin desplazar.
- **Tarjeta de almacenamiento:** con el chip «Offline» a la derecha, «Almacenamiento local» pasa a dos líneas en pantallas de ≈ 411 dp. En el PNG cabe en una.
- **Modo oscuro:** derivado de los tokens (D-016). El teléfono de referencia está en oscuro y se ha validado así.

## 6. Desviaciones respecto al plan

- **Idioma por defecto (D-029):** pedido por el usuario durante la implementación. Añade `AppLocale`, `localeConfig` y bundle sin división por idioma.
- **Interfaces `FileDownloader` y `ModelCompatibility`:** se añadieron para poder probar el runner y los casos de uso con fakes.
- **Importación:** el hash se calcula durante la copia, así que la fila pasa de «Importando» a «Preparando» sin un «Verificando» separado. US6 lo esperaba como paso propio.
- **Descripción accesible de descargas e importaciones:** el porcentaje va en tramos de 10 y sin bytes, para que la región viva no sature TalkBack. El contrato lo pedía con bytes; está actualizado.
- **Reintentos:** 4 esperas (2, 4, 8 y 16 s) entre 5 intentos, no 5 esperas. Si el fallo de red coincide con estar sin red, el job se relanza al volver la red en vez de contarlo como fallo.
- **Aviso «Modo CPU, más lento»:** solo para Gemma. EmbeddingGemma 2 va en CPU por diseño (D-024).
- **Error corregido en la validación API 33:** el worker capturaba la cancelación como si fuera un rechazo del servicio en primer plano (`CancellationException` hereda de `IllegalStateException`). Ahora la cancelación se propaga.

## 7. Pendiente

- **T071:** recorrido manual con TalkBack y fuente al 200 % en el teléfono. Las comprobaciones automáticas (ATF, 48 dp, filas fusionadas, región viva y motivo de «Continuar») pasan en todos los estados, en claro y en oscuro.
