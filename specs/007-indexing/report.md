# Informe de cierre — F07 Indexación

**Rama:** `007-indexing`.
**Fecha:** 2026-10-27.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64;
- AVD `MicroRAG_API33`, API 33;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), con los modelos de F03 instalados y verificados.

**Versiones:** sin dependencias nuevas. WorkManager 2.12.0 (`setGreedySchedulerEnabled(false)`, experimental), DataStore 1.3.0 y LiteRT-LM 0.18.1, ya fijadas. En el manifiesto, solo el receptor no exportado de la notificación y `singleTop` en `MainActivity`.

## 1. Qué se ha implementado

- **Ejecución** (D-040, D-041):
  - `IngestWorker` largo de WorkManager, en primer plano `dataSync` cuando Android lo permite y, si no, como job normal con la misma notificación;
  - reintenta el primer plano en cada documento, etapa y lote guardado, y comprueba el estado real del proceso (`ProcessState`), porque `setForeground` puede volver sin error aunque el servicio no llegue a arrancar;
  - sin planificador en proceso;
  - reconciliación al abrir la app;
  - **alarma inexacta** mientras la tanda está sin terminar, que devuelve el trabajo tras una muerte sin primer plano (§6), y **caducidad** de la notificación, que Android no aplica mientras es la de un servicio en primer plano.
- **Runner en dos fases:**
  - primero trocea todo lo pendiente, para conocer el total en segundos;
  - después, por documento, vuelve a trocear, embebe con la cabecera «título › encabezados» y guarda en lotes de 64;
  - al reanudar compara lo guardado y sigue donde estaba; publica (`done`) solo al final.
- **Embeddings:**
  - sesión retenida en el `EngineCoordinator`, que libera el LLM antes de empezar;
  - GPU solo si el dispositivo la valida (coseno ≥ 0,99 frente a la CPU, 3 textos fijos), con respaldo a CPU;
  - recorte de D-036 con su golden de Python.
- **Búsquedas:** solo devuelven documentos publicados e incluidos: *k* + *m* candidatos y filtro (R7). F10 lo hereda.
- **Tanda en DataStore:** documentos, pausa y causa persistente (usuario, sin espacio, modelo ausente). Temperatura y límite del sistema se derivan en vivo.
- **Pausas:**
  - del usuario, desde la pantalla y la notificación;
  - térmica (`THERMAL_STATUS_SEVERE`), con reanudación sola;
  - sin espacio y modelo ausente.

  «Reintentar» por documento.
- **Pantalla** `index` (PNG 08):
  - héroe con anillo y «N / M fragmentos», etapas, «Procesando ahora» y tiempo restante, en las variantes compactas del PNG (D-042);
  - estados P1–P12;
  - textos en español e inglés.
- **Ingesta (F06):** la cola muestra «Indexando · N de M» y «En pausa», y los documentos indexados salen.
- **Notificación:**
  - canal propio y sin nombres de archivo;
  - las cifras de toda la tanda, como la pantalla (D-042);
  - acción «Pausar»;
  - al tocarla, abre `Index` (`singleTop`), solo con el onboarding completado.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 114/114 (F06: 111) |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 415/415 (F06: 308) |
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 291/291 (F06: 217). `IndexScreenLayoutTest` solo se mide en el A063 |
| Instrumentados en la AVD API 33 (mismos filtros) | ⚠️ 287/291. Fallan las 4 de navegación de F06 §7 (Atrás en la raíz en Android 12–13), igual que con `main` |
| Instrumentados en el teléfono (mismos filtros) | ✅ 291/291, con `IndexScreenLayoutTest` |
| `@RequiresModels` en el teléfono | ⚠️ 9/10 seguidas. `GemmaWarmInitTest` (F03) pasó de 10 s (16,5 s) al ejecutarse tras ≈ 17 min de benchmarks, con el teléfono a 36 °C. Sola, tras 2 min de reposo: 5,2 s y 4,5 s ✅. El benchmark de indexación repite sus cifras (CPU: 134,7 s) y cumple SC-001 en GPU. Repetido tras los cambios del cierre: GPU 88,6 s (5,01 fragmentos/s) y CPU 134,3 s, sin el LLM cargado ✅ |
| APK de release | ✅ Solo `schema.sql` y los perfiles de arranque en *assets*; sin fixtures |

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Indexar de principio a fin | `IngestRunnerTest`, `IndexingFlowTest` (runner real y SQLite-Vector); T027 en el teléfono con un PDF, un TXT, un MD y un PDF propio de 174 páginas | ✅ Los cuatro en `done`, ordinales consecutivos desde 0, 207 vectores de 256 componentes con norma 1, título sin extensión. Prefijo y cabecera contextual con su golden; recorte D-036 con el suyo (0 recortes en los documentos reales) |
| **US2** Sobrevive a todo | T045 en el emulador API 37 y en el teléfono (§4) | ✅ Muertes durante el troceado, a mitad de los embeddings, entre documentos y como job normal; reinicio a mitad. En todos los casos sigue sin abrir la app y sin huecos ni duplicados. La alarma de D-041 devuelve el trabajo que Android dejaba parado |
| **US3** Progreso real | `DefaultIndexingRepositoryTest`, `IndexViewModelTest`, `IndexScreenTest`; capturas del teléfono | ✅ «N / M» de toda la tanda, igual a lo guardado; «Procesando...» sin total; el tiempo restante solo con ritmo estable (desaparece tras una pausa hasta medir otra vez); al reabrir, el progreso real |
| **US4** Pausar, reanudar, reintentar y pausas automáticas | `IngestRunnerTest`, `DefaultIndexingRepositoryTest`, `IndexScreenTest`; T053 en el emulador y en el teléfono | ✅ Pausa y reanudación desde la pantalla y desde la notificación, sin recalcular nada; temperatura con la app visible y como job normal; «Reintentar». Sin espacio y modelo ausente, solo en pruebas (no se pueden provocar en el teléfono sin riesgo) |
| **US5** Pantalla fiel, accesible y coherente con la Ingesta | `IndexScreenTest`, `IndexScreenAccessibilityTest`, `IngestScreenTest`; capturas T060 | ✅ con las diferencias de §5. La cola de Ingesta muestra «Indexando · N de M fragmentos» y «En pausa», y los indexados salen. TalkBack: ⏳ T066 |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001: 200 páginas en ≤ 5 min con GPU | ✅ 93,6 s (T063) |
| SC-002: muertes en 3 momentos, sin duplicados ni huecos | ✅ Teléfono: muertes durante el troceado de 2.000 páginas, a mitad de los embeddings (en primer plano y como job normal) y entre documentos, más un reinicio, pausas, temperatura y forzar la detención. Los cinco documentos acaban con ordinales 0..n-1, sin duplicados y norma 1. Son idénticos fragmento a fragmento (texto, página y encabezados) entre sí y con los del emulador, cada uno con otro patrón de interrupciones. Mismo título, GPU frente a CPU: coseno mínimo 0,9945. La interrupción en el cierre, en `IndexingFlowTest` |
| SC-003: búsquedas solo sobre indexados | ✅ `RagIndexVisibilityTest` y `IndexingFlowTest` (búsqueda durante la fase 2) |
| SC-004: «N / M» a menos de un lote de lo guardado | ✅ N sale de los fragmentos guardados, sin estimar. Pruebas del repositorio |
| SC-005: pausa en ≤ 5 s y ≤ 1 fragmento recalculado | ✅ Teléfono: 0,07 s hasta parar el worker y 0,40 s hasta liberar el engine; 0 recalculados (el lote parcial se guarda al pausar) |
| SC-006: temperatura grave, pausa en ≤ 5 s y vuelta sola | ✅ Teléfono: 0,48 s con la app visible, 1,95 s como job normal; vuelta a los 53–54 ms |
| SC-007: el LLM nunca cargado durante la tanda | ✅ T063, muestreo del coordinador |
| SC-008: accesibilidad en todos los estados | ✅ `IndexScreenAccessibilityTest` en el teléfono y en los emuladores API 37 y 33 |
| SC-009: ritmos y sin disco en el hilo principal | ✅ 4,73 fragmentos/s (GPU) y 3,23 (CPU); `IndexingStrictModeTest` con control negativo |
| SC-010: sin red y sin nombres en los logs | ✅ 235 líneas de la app en el teléfono durante T045 y T053: solo ids, cifras y causas. `netstats` no tiene ninguna entrada del UID de la app. `NetworkConfinementTest` (F03) sigue en verde |

## 4. Cifras medidas

### Indexación (T063, `IndexingBenchmarkTest`, teléfono, Gemma precargado en la GPU, batería al 91 % cargando, 23 °C)

| Medida | Objetivo | GPU | CPU |
|---|---|---|---|
| 200 páginas (430 fragmentos), de principio a fin | SC-001: ≤ 5 min en GPU | **93,6 s** | 134,9 s |
| Ritmo de embedding | Se mide (SC-009) | 4,73 fragmentos/s | 3,23 fragmentos/s |
| LLM cargado durante la tanda | Nunca (SC-007) | No | No |

Emulador API 37, CPU: 430 fragmentos en 103 s (4,21 fragmentos/s).

Con la app real, en el teléfono, un PDF de 174 páginas con fragmentos más largos va a 3,14 fragmentos/s en GPU (202 en 64,3 s). D-024 estimaba ≈ 3 fragmentos/s en GPU y ≈ 1,1 en CPU con fragmentos de 350 tokens. Los de `text_200_pages.pdf` son más cortos y la CPU rinde mucho más de lo previsto.

**Validación de la GPU** (T027): coseno mínimo GPU↔CPU de 0,99751 → GPU.

**Lotes de 64** (163 lotes en el teléfono durante T045 y T053): guardar uno tarda 200 ms de mediana y 1.062 ms en el p95 (máximo 1.458 ms). Los lentos son de cuando el trabajo corre como job normal con el proceso en segundo plano. Embeberlo en la GPU, ≈ 13 s (≈ 4,9 fragmentos/s con la app real y fragmentos cortos).

### Búsquedas con documentos sin publicar (T062, `RagIndexBenchmarkTest`, teléfono, 10.000 fragmentos)

| Caso | p95 vectorial | p95 BM25 |
|---|---|---|
| F04 (referencia) | 23,1 ms | 26,5 ms |
| F07, *m* = 0 (4 ejecuciones) | 23,5–26,2 ms | 27,2–30,6 ms |
| F07, *m* = 4.300 | 44,0–45,7 ms | 43,3–43,5 ms |

- **Coste del filtro con *m* = 0:** la variación entre ejecuciones del mismo código (hasta 3 ms) supera el umbral de 2 ms, así que se midió con una prueba A/B temporal, sin commit: 200 consultas por lado, intercaladas en la misma conexión, tres veces. La consulta de F07 cuesta **+0,8 ms** (vectorial) y **+1,1 a +1,5 ms** (BM25) frente a la de F04. Contar los fragmentos ocultos: 0,06 ms.
- ***m* = 4.300** (un PDF de 2.000 páginas a medias): con el embedding de consulta (≈ 120 ms), la búsqueda vectorial suma ≈ 165 ms, por encima de 150. Aceptado en el cierre (D-042).

### Ejecución (T045)

| Medida | Emulador API 37 | Teléfono |
|---|---|---|
| Latencia de «Iniciar indexación» al worker | 25 ms | ~100 ms |
| Muerte durante el troceado (PDF de 2.000 páginas) → trabajo de nuevo | — | 48 s, como job normal; vuelve a trocear y sigue |
| Muerte en primer plano, a mitad de los embeddings → trabajo de nuevo | 1–60 s | 2–34 s (5 muertes) |
| Muerte entre documentos (0,26 s tras publicar uno) → trabajo de nuevo | — | 9,5 s; el siguiente empieza desde 0 y el publicado no cambia |
| Muerte sin primer plano → trabajo de nuevo, sin abrir la app | > 40 min (antes de D-041); 16 min 48 s con la alarma | 20 min 6 s con la alarma (límite de `dataSync` a 60 s durante la prueba) |
| Muerte sin primer plano → notificación retirada | ≈ 5 min | 3 min 5 s |
| Forzar la detención → al abrir la app, trabajo de nuevo | — | 4 s, un solo worker |
| Reinicio a mitad → trabajo de nuevo tras desbloquear, sin abrir la app | 18 s (sin bloqueo) | 43 s, como job normal (Android no deja empezar `dataSync` desde el arranque) |
| Al abrir la app → trabajo de nuevo | 2 s | 2 s |
| Al abrir la app → primer plano recuperado | Al siguiente lote | Al siguiente lote (≤ 13 s) |

### Pausas (T053)

| Medida | Objetivo | Emulador API 37 | Teléfono |
|---|---|---|---|
| Temperatura grave → pausa | ≤ 5 s (SC-006) | 0,33 s (progreso) y 0,57 s (engine liberado) | 0,29 s (lote guardado) y 0,48 s (engine liberado) |
| Temperatura normal → reanudación | Sola | 18 ms | 53 ms |
| «Pausar» en la pantalla → worker parado | ≤ 5 s (SC-005) | 0,11 s | 0,07 s (engine liberado a los 0,40 s) |
| «Pausar» en la notificación → worker parado | ≤ 5 s (SC-005) | 0,14 s | 0,05 s (engine liberado a los 0,32 s) |
| Fragmentos recalculados al reanudar | ≤ 1 (SC-005) | ≤ 1: el lote parcial se guarda al pausar (32 y 24) | 0: lotes parciales de 46 y 1 guardados al pausar |
| Temperatura grave sin primer plano | Pausa y reanudación solas | JobScheduler para el job a los 26 ms; el runner guarda el lote (18) y espera. Al volver la temperatura normal, el job arranca otra vez en 76 ms | JobScheduler pide parar a los 51 ms y WorkManager lo ignora (§6); el runner guarda el lote (31) y se pausa a los 1,95 s. Sigue a los 54 ms de volver la temperatura normal |

## 5. Diferencias frente al mockup

Capturas del teléfono (T060), en claro y en oscuro: en curso, en pausa, pausa por temperatura, con error y sin tareas. Con una tanda en marcha, la pantalla coincide con el PNG 08 en estructura, textos, colores, iconos y formato de cifras («5.390 / 6.450 fragmentos»), salvo esto:

- **Altura (resuelta en el cierre, D-042):** las filas de etapas medían ≈ 72 dp, frente a ≈ 48 en el PNG 08, y el héroe ≈ 318 dp, frente a ≈ 240. «Procesando ahora» quedaba bajo el pliegue en el teléfono. Ahora llevan las variantes compactas: filas de 56 dp con textos de 16 y 13 sp, héroe con márgenes de 16 dp y anillo de 154 dp. La tarjeta se ve sin desplazar en el teléfono (`IndexScreenLayoutTest`, con control negativo). Con tiempo restante y otros documentos en cola, se corta la línea de la cola. Las filas siguen algo más altas que en el PNG, por la escala de texto del sistema de diseño.
- **Círculos decorativos del héroe:** `LisdataHeroCard` lleva los dos círculos de la esquina, como Inicio y el detalle. El PNG 08 no los tiene.
- **Tiempo restante:** «≈ N min restantes» bajo los fragmentos, según la propuesta P9. El PNG no lo maqueta.
- **Estados nuevos:** P1–P12 aprobados. «Todo actualizado» con `LisdataEmptyState`; los errores, en «DOCUMENTOS CON ERROR» con la causa, y «Reintentar» solo si sirve (en «Sin texto» no aparece).
- **Notificación (resuelto en el cierre, D-042):** mostraba los fragmentos del documento en curso y la pantalla, los de la tanda. Ahora muestra la tanda: en el emulador, «727 de 4.730 fragmentos» durante el segundo documento, igual que la pantalla.

## 6. Desviaciones respecto al plan y fallos encontrados

### Decisiones nuevas

- **D-041, alarma de reanudación:** no estaba en el plan. Se añadió con la regla acordada para T045, tras reproducir en el emulador y en el teléfono que un trabajo muerto mientras corría como job normal no vuelve hasta abrir la app. Detalle y causa en research R2.
- **Límite de `dataSync` forzado en el teléfono:** para reproducir a voluntad el job sin primer plano, con permiso del usuario se bajó `data_sync_fgs_timeout_duration` a 60 s durante la prueba y se borró después, como en el emulador.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| Un proceso abierto directamente en Ingesta, o arrancado por el sistema, veía el modelo verificado como ausente y pausaba la tanda | Emulador | `be737de`: la sesión refresca el repositorio de modelos antes de decidir |
| La fila de un documento con error no se leía como una frase con TalkBack (botón sin nombre) | Pruebas | `63b1e46` |
| Tras una muerte, el servicio de WorkManager no llegaba al primer plano pero `setForeground` volvía sin error: sin notificación y reintentando cada segundo | Emulador | `7d352cc`: se comprueba la importancia del proceso (`ProcessState`) y se publica la notificación normal |
| Con la app abierta a mitad de un documento largo, el primer plano no volvía hasta el documento siguiente | Emulador | `3b40c23`: se reintenta en cada lote guardado, como decía R1 |
| Un trabajo muerto como job normal no volvía hasta abrir la app, y la notificación seguía diciendo «Indexando» | Emulador y teléfono | `1839b41` (D-041): alarma inexacta y notificación que caduca |
| Tras un reinicio, la notificación la publicaba WorkManager como normal y sin caducidad: Android no deja empezar `dataSync` desde `BOOT_COMPLETED`, pero el proceso queda con importancia de primer plano | Teléfono | `8b71faf`: la caducidad va en todas las notificaciones; Android nunca la aplica a la de un servicio en primer plano. Comprobado en el emulador tras un reinicio |
| En pausa por el usuario, las cuatro etapas salían «Pendiente» | Teléfono (T060) | `aefde2c`: sin trabajo en marcha, el documento a medias sigue siendo el actual y su etapa dice «En pausa» (P3) |

### Comportamientos de Android anotados (sin cambio)

- **WorkManager 2.12.0 ignora las paradas de JobScheduler** de un worker que pidió el primer plano aunque Android se lo negara. Con temperatura grave, el runner se pausa solo; el resultado es el mismo (research R10).
- **Android 15, tras matar un proceso en primer plano:** a veces registra «FGS (dataSync) timed out» sobre el servicio muerto, y al reiniciarse responde «Time limit already exhausted» hasta abrir la app. El trabajo sigue como job normal.
- **Troceado en segundo plano:** como job normal, con el proceso en segundo plano, el PDF de 2.000 páginas tarda 21,6 s en trocearse, frente a 4,2 s en primer plano. Los embeddings en la GPU no se ven afectados (≈ 4,5 fragmentos/s).

## 7. Pendiente y decisiones del cierre

**Pendiente:**
- **T066:** recorrido con TalkBack en la pantalla de estado, a cargo del usuario, como en F01, F03 y F06. El resto del recorrido de quickstart §6 se hizo durante T045, T053 y T060: pausa y reanudación, temperatura, girar, salir y volver, forzar la detención, reiniciar y la cola de Ingesta. Bloquear el teléfono a mitad queda para ese recorrido.
- **Documentos de prueba en el teléfono:** los ids 2–9 son documentos de prueba indexados (PDF de 200, 1.000 y 2.000 páginas, y sus copias). Hasta F08 no hay forma de borrarlos desde la app. La carpeta `Download/MicroRAG-F07` ya está borrada.

**Decisiones del cierre (D-042), aceptadas por el usuario:**
1. **Búsqueda con un documento grande a medias (T062):** con *m* = 4.300 fragmentos ocultos, la búsqueda suma ≈ 165 ms con el embedding de consulta, por encima de los 150 ms de la constitución IV. Se acepta y se registra: solo ocurre mientras se indexa un documento grande. F10 vuelve a medir y F14 evalúa INT8.
2. **Cifras de la notificación:** muestra las de la tanda, como la pantalla. Implementado y verificado (§5).
3. **Pantalla compacta:** filas de etapas compactas y, por elección del usuario al ver que no bastaban, también el héroe y el anillo. Implementado y verificado en el teléfono (§5).
