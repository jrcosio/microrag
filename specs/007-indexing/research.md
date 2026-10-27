# Research — Indexación (F07)

Fecha: 2026-10-26. Fuentes oficiales consultadas ese día. Las firmas de bibliotecas se han comprobado con `javap` sobre los artefactos que ya usa el proyecto (caché de Gradle).

## R1. Ejecución larga: worker de WorkManager en primer plano, tipo `dataSync`

**Hechos verificados:**
- **Tipos de servicio en primer plano** (developer.android.com, «Foreground service types»):
  - `dataSync` lista expresamente «Local file processing» entre sus usos. No tiene requisitos en tiempo de ejecución y ya está declarado en el manifiesto (F03, para las descargas en API 31–33).
  - `mediaProcessing` es para «operaciones largas sobre recursos multimedia, como convertir formatos».
  - `specialUse` exige justificar el uso en Play Console.
  - `shortService` dura unos 3 minutos.
- **Límite de tiempo** («Foreground service timeouts»):
  - con `targetSdk` ≥ 35, `dataSync` y `mediaProcessing` pueden ejecutarse 6 h de cada 24, contadas por tipo;
  - al agotarse, el sistema llama a `Service.onTimeout(int, int)` y el servicio tiene unos segundos para pararse;
  - **el contador se reinicia cuando el usuario trae la app al primer plano**.
- **WorkManager 2.12.0** (notas de versión):
  - desde 2.10.0-alpha03 para los workers en primer plano de tipo `dataSync` cuando se agota el tiempo;
  - desde 2.10.0-alpha04 añade la causa de parada `STOP_REASON_FOREGROUND_SERVICE_TIMEOUT`.
- **Android 16** («Behavior changes: all apps», *JobScheduler quota optimizations*):
  - los jobs que se ejecutan a la vez que un servicio en primer plano pasan a respetar la cuota de ejecución de jobs;
  - afecta a WorkManager;
  - se recomienda leer `WorkInfo.getStopReason()`.
- **Arranque desde segundo plano** («Restrictions on starting a foreground service from the background»): no hay excepción para el código que se ejecuta dentro de un job o de un worker. Fuera de las excepciones (transición desde un estado visible, acción del usuario sobre una notificación, alarma exacta pedida por el usuario, `BOOT_COMPLETED`…), el sistema lanza `ForegroundServiceStartNotAllowedException`.
- **`BOOT_COMPLETED`:** con `targetSdk` ≥ 35 no se puede lanzar un `dataSync` desde ese receptor.
- **UIDT:** exige una restricción de red (F03 R3), y la guía lo reserva para transferencias. No encaja con un trabajo local.
- **Guía «Alternatives to data sync foreground services»:** para procesamiento local de más de unos minutos, remite a WorkManager con un *long-running worker*. Advierte que este queda sujeto a las restricciones de arranque desde segundo plano y a los límites de los jobs: el sistema reprograma los que pasan de 10 minutos.

**Decisión:**
- **Un único `CoroutineWorker`** (el `IngestWorker` de F06, ampliado) que llama a `setForeground(ForegroundInfo(id, notificación, FOREGROUND_SERVICE_TYPE_DATA_SYNC))` al empezar.
- **Al iniciar o reanudar el usuario** (con la app visible), el arranque en primer plano está permitido. El proceso queda como servicio en primer plano y el sistema no lo congela.
- **Si `setForeground` falla** (el proceso arrancó en segundo plano tras una muerte, se agotaron las 6 h, o Android 16 paró el job por cuota y lo relanza en segundo plano):
  - el worker sigue como job normal, en las ventanas que dé el sistema;
  - publica la misma notificación como notificación normal;
  - avanza en tramos y WorkManager lo reprograma cuando el sistema lo para. Como el trabajo se reanuda donde se quedó (R9), no se pierde nada;
  - vuelve a intentar `setForeground` en cada lote. Con la app visible el arranque está permitido, así que recupera el primer plano en cuanto el usuario abre la app.
- **Si el trabajo está esperando al sistema** (`ENQUEUED`) al abrir la app, la reconciliación (R12) lo vuelve a encolar para que arranque ya, en primer plano y con el contador de 6 h reiniciado.
- **Estado «Esperando al sistema»:** la pantalla lo deriva de `WorkInfo` (`ENQUEUED` con una causa de parada del sistema) y lo muestra como pausa automática (FR-019).

**Alternativas descartadas:**
- **Servicio en primer plano propio:** las mismas restricciones de `dataSync` y de arranque, más código de ciclo de vida, y aun así necesita un job para reanudar tras una muerte.
- **`specialUse`:** necesita justificación en Play y no se ajusta: el caso está cubierto por `dataSync`.
- **`mediaProcessing`:** su uso declarado es multimedia.
- **UIDT:** exige red.
- **`shortService`:** dura 3 minutos.

## R2. Trabajo congelado tras matar el proceso (observación de F06)

**Análisis de los logs de F06** (`report.md` §7, teléfono, WorkManager 2.12.0):
1. El sistema relanza el proceso para el job (`onStartJob`).
2. `ForceStopRunnable` encuentra el trabajo en `RUNNING` (quedó así al matar el proceso) y lo vuelve a programar con el mismo id. JobScheduler para el job en curso (`onStopJob`).
3. A la vez, el planificador oportunista dentro del proceso (`GreedyScheduler`) arranca el worker.
4. Al terminar la ejecución interrumpida, WorkManager cancela el trabajo en todos sus planificadores, incluido el job de JobScheduler (`Cancelling work ID … Job ID 10004`), y no lo vuelve a programar porque ya está `RUNNING`.
5. Queda el worker corriendo dentro del proceso sin ningún job ni servicio que lo sostenga. Android lo trata como proceso en caché y lo congela (`isFrozen=true`).

**Hecho verificado:**
- WorkManager 2.12.0-alpha01 añadió una API experimental «para desactivar la planificación oportunista dentro del proceso».
- En el `classes.jar` de `work-runtime` 2.12.0 es `Configuration.Builder.setGreedySchedulerEnabled(boolean)`, anotada `@ExperimentalConfigurationApi`.
- La app ya define su configuración (`MicroRagApplication : Configuration.Provider`).

**Decisión:** `setGreedySchedulerEnabled(false)`, con `@OptIn(ExperimentalConfigurationApi::class)`. Todo el trabajo de WorkManager pasa por JobScheduler, así que nunca hay un worker en el proceso sin un job del sistema detrás.

**Coste:** el trabajo arranca cuando JobScheduler lo ejecuta, no al instante dentro del proceso. Afecta también a F06 (el trabajo `ingest`) y a las descargas de F03 en API 31–33.

**Verificación temprana** (riesgo 1): en el teléfono y en el emulador, matar el proceso a mitad de un documento con la app en segundo plano y sin abrirla. Hay que comprobar:
- que el trabajo termina solo;
- que el proceso no queda congelado;
- la latencia desde «Iniciar indexación» hasta que arranca el worker, también pulsando Inicio justo después. Android solo deja arrancar el primer plano unos segundos después de que la app deja de verse; si el worker llega tarde, la tanda empieza en tramos y recupera el primer plano al volver a la app (R1);
- que la descarga de modelos de F03 en API 31–33 sigue funcionando sin el planificador en proceso.

**Si sigue congelándose o la latencia supera 5 s:** se para y se consulta. La alternativa es un job propio de JobScheduler para la reanudación, como hizo F03 con las descargas.

**Alternativas descartadas:**
- **Dejar WorkManager como está:** reproduce el fallo de F06.
- **Un vigilante con alarmas:** no puede descongelar un proceso congelado ni arrancar un servicio en primer plano.

### Resultados de la verificación temprana (T045, emulador API 37, 2026-10-27)

App real, CPU, documentos de 200 y 2.000 páginas, un TXT corto y otro PDF de 2.000 páginas.

- **Latencia:** 25 ms desde «Iniciar indexación» hasta que el worker pide el primer plano, con Inicio pulsado 0,3 s después. Umbral de 5 s: cumplido.
- **Primer plano:** al iniciar con la app visible, el proceso queda como servicio `dataSync` y la notificación avanza.
- **Muerte del proceso en primer plano** (`kill -9` tres veces: durante el troceado del PDF de 2.000 páginas, a mitad de los embeddings y justo al terminar un documento):
  - Android relanza el proceso al momento para `SystemJobService` y el trabajo sigue solo en 1–60 s, sin abrir la app;
  - sigue como trabajo normal, porque Android no deja pasar a primer plano desde segundo plano;
  - ningún proceso congelado.
- **Muerte con el trabajo corriendo como trabajo normal** (`kill -9`, actualización de la app y `am compat`):
  - JobScheduler reprograma el job y lo deja `ready`, pero no lo ejecuta. Se observó más de 40 minutos, también con cambios de batería y pasado el agrupamiento de jobs «solo CPU» de 31 minutos (`max_cpu_only_job_batch_delay_ms`);
  - una vez volvió a los 2 minutos, porque Android relanzó el `SystemForegroundService` de WorkManager;
  - **al abrir la app**, WorkManager reprograma el trabajo y la reconciliación (R12) lo relanza: arranca en 2 s, en primer plano, y sigue desde lo guardado.
- **Límite de `dataSync` a 60 s** (`device_config`): Android corta el servicio y WorkManager relanza el trabajo en 70 ms, en el mismo proceso, como trabajo normal. No se pierde nada.
- **Cuota de Android 16** (`OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS`): 17,5 minutos seguidos en primer plano con la app en segundo plano, sin paradas.
- **Recuperar el primer plano:** al abrir la app con el trabajo en segundo plano, vuelve al siguiente lote (9 y 15 s).
- **Coherencia (SC-002):** tras siete interrupciones, los cuatro documentos tienen sus ordinales 0..n−1 una vez cada uno (430, 4.293, 37 y 4.301). Tras cada muerte solo se recalcula el lote a medias.
- **Logs:** ninguna de las 247 líneas de la app lleva nombres de archivo.
- **Descarga de F03 en la AVD API 33** sin el planificador en proceso (`ModelDownloadSmokeTest`): modelo verificado en 13,6 s.

**Fallos encontrados y corregidos:**
1. **`setForeground` volvía sin error, pero el servicio no llegaba a primer plano.** Pasaba con el `SystemForegroundService` de WorkManager relanzado en segundo plano tras una muerte: su `startForeground` fallaba dentro de WorkManager. El worker se creía en primer plano, no publicaba la notificación normal y reintentaba cada segundo. Ahora consulta la importancia del proceso (`ProcessState`).
2. **El worker solo reintentaba el primer plano por documento o etapa**, no en cada lote como decía R1. Con un documento largo, abrir la app no lo devolvía hasta acabarlo. Corregido.

### Resultados en el teléfono (T045, A063, Android 15, 2026-10-27)

App real, GPU, documentos de 200 y 2.000 páginas.

- **Latencia:** unos 100 ms desde la pulsación hasta que el worker pide el primer plano. Con Inicio pulsado 0,3 s después, el servicio arranca a los 206 ms, aún con la app arriba, y sigue con la app en segundo plano.
- **Primer plano:** servicio `dataSync` (`isForeground=true`, tipo `0x1`). La notificación muestra el progreso.
- **Muerte en primer plano** (`kill -9`, dos veces, a mitad de los embeddings): el proceso vuelve en 1–2 s y el trabajo sigue en 4–6 s desde lo guardado (192 y 832 fragmentos), sin abrir la app.
  - Sigue sin primer plano. La primera vez, Android 15 registra «FGS (dataSync) timed out» sobre el servicio del proceso muerto y rechaza el nuevo con «Time limit already exhausted for foreground service type dataSync», aunque la app llevaba minutos de `dataSync`. Al abrir la app el contador se reinicia y el primer plano vuelve en el siguiente lote.
  - El `SystemForegroundService` de WorkManager reintenta su `startForeground` decenas de veces en 150 ms y Android descarta una actualización de la notificación por exceso de ritmo. No tiene más efecto.
- **Muerte sin primer plano:** igual que en el emulador. El job queda `Ready: true`, sin encolar, y el proceso no vuelve. La observación se cortó a los 2,5 min, porque se abrió la app: arrancó en 2 s desde los 1.216 fragmentos guardados.

**Causa** (código de `JobSchedulerService` de Android 15):
- tras la muerte, el job se reprograma listo y sin espera, pero solo se encola en la siguiente revisión general (`MSG_CHECK_JOB`), que disparan cambios globales (constantes, temperatura, batería, desbloqueo). Un job sin restricciones nunca «cambia» por sí solo;
- un job recién programado que está listo sí se encola y se ejecuta en el acto (`scheduleAsPackage`);
- al arrancar el proceso, WorkManager (`ForceStopRunnable`) vuelve a programar el trabajo que quedó en marcha («Found unfinished work, scheduling it»). Por eso abrir la app lo arregla.

**Decisión (D-041):** alarma inexacta mientras la tanda está sin terminar, y caducidad de la notificación normal.
- **Verificación en el emulador API 37:** muerte sin primer plano a las 16:45:49. La alarma sonó a las 17:02:36, dentro de su ventana; el proceso arrancó «for broadcast» y el trabajo siguió 2 s después desde los 475 fragmentos guardados, sin abrir la app. La notificación normal desapareció sola unos 5 min después de la muerte: 3 de caducidad y el retraso con que Android la aplica.
- En esa prueba, una muerte en primer plano tampoco relanzó el proceso de inmediato: Android relanzó el servicio de WorkManager a los 60 s y, con él, el trabajo.

## R3. Notificación

**Decisión:**
- Canal nuevo `indexing` («Indexación de documentos»), de importancia baja y sin sonido.
- **Contenido:**
  - «Indexando documentos»;
  - progreso determinado con «N de M fragmentos» si se conoce el total, o indeterminado si no;
  - en pausa automática, su causa;
  - **sin nombres de archivo** (constitución I: la notificación se ve en la pantalla de bloqueo).
- **Acción «Pausar»:** un `PendingIntent` explícito e inmutable a un receptor no exportado (`IndexingActionReceiver`), como el de F03 para las descargas.
- **Tocarla:** abre `MainActivity` con una acción interna explícita (`ACTION_OPEN_INDEXING`), que navega a `Index`. No es un deep link: no hay `intent-filter` con esquema ni host.
  - `MainActivity` pasa a `launchMode="singleTop"` y atiende la acción también en `onNewIntent`, para no crear otra instancia con la app abierta;
  - la acción solo se atiende si el onboarding está completado (destino de arranque `HOME`). Como la actividad está exportada, otra app podría enviarla; así no puede saltarse el onboarding, y solo abriría esa pantalla, sin datos ni efectos.
- **Actualización:** con cada lote, mediante `setForeground` (con servicio en primer plano) o `NotificationManager.notify` (sin él), con el mismo id.
- **Notificaciones denegadas:** el trabajo sigue igual. Android 13+ sigue mostrando el servicio en primer plano en el Administrador de tareas.

## R4. Backend y sesión del engine

**Decisión** (D-039):
- **Backend:** la ingesta pide el engine `(EMBEDDINGGEMMA, GPU)` solo si la GPU está **validada en este dispositivo**. Si no, usa `(EMBEDDINGGEMMA, CPU)` y lo registra con el motivo (solo el nombre de la excepción).
- **Validación de la GPU** (D-024 y D-030: si en un teléfono la GPU no llega a coseno ≥ 0,99, ese teléfono indexa en CPU):
  - **emulador** (`Build.HARDWARE` `ranchu` o `goldfish`): nunca GPU. Su inicialización de LiteRT en GPU se bloquea en código nativo más de 10 minutos (F04, `report.md` §6.4) y `withTimeout` no puede interrumpir una llamada JNI;
  - **primera sesión de ingesta en un dispositivo real:** se carga la GPU y se embeben 3 textos fijos en GPU y en CPU (CPU y GPU a la vez está permitido: solo hay una GPU). Si el coseno de los 3 es ≥ 0,99, la GPU queda validada;
  - **resultado:** se guarda en `IngestRunStore` junto al SHA-256 del modelo y la versión de la app, y se repite si cambia alguno. Un fallo al cargar la GPU cuenta como no validada.
- **Engine retenido:** `EngineCoordinator` gana `withHeldEngine`, solo para engines de embedding. El engine de la ingesta queda marcado como **en uso por la ingesta** y no se cierra al ocultarse la UI.
- **Otra petición de GPU mientras la ingesta la retiene** (en F07, la comprobación del LLM de F03; en F11, el chat): espera a que la ingesta libere la GPU **sin tener el `loadLock`**, así que el resto del coordinador (por ejemplo, el embedding de consulta en CPU) sigue funcionando. F11 decidirá si el chat pausa la ingesta.
- **Liberación por memoria:** `EngineMemoryCallbacks` pasa a liberar solo los engines no retenidos. Hoy libera todos en `TRIM_MEMORY_UI_HIDDEN`, lo que cerraría el engine de la ingesta al salir de la app.
- **El LLM nunca se retiene en la ingesta**, así que «liberar el LLM durante la ingesta y en UI_HIDDEN» (constitución IV) se cumple:
  - al empezar la fase de embeddings, la ingesta libera explícitamente los engines de generación;
  - la regla de «nunca dos engines en la GPU» del coordinador sigue vigente.
- **Liberación del engine:** al terminar, al pausar (por el usuario o por temperatura) y al detenerse el worker.
- **Reintento:** si el engine falla a mitad (`EmbeddingEngineException`, R6), se libera y se vuelve a cargar una vez. Si vuelve a fallar y era la GPU, se pasa a la CPU. Si la CPU también falla, el documento acaba `error` con `embedding_failed` y la tanda sigue (N11).

## R5. `computeEmbeddingBatch` en LiteRT-LM 0.18.1

**Hecho verificado** (`javap` sobre `litertlm-android-0.18.1`): `EmbeddingEngine` expone `computeEmbeddingBatch(List<List<InputData>>, EmbeddingOptions)` y su versión asíncrona. El informe y FR-014 suponen llamadas en serie de un texto, porque la versión que conocía el informe no tenía lotes.

**Decisión:** F07 empieza en serie (FR-014).

**Verificación temprana** (riesgo 3): en el teléfono, con 64 fragmentos reales, medir:
- en serie frente a lotes de 8 y 16;
- en GPU y en CPU;
- el coseno entre los vectores de los dos modos.

**Si el lote acelera al menos un 30 % con coseno ≥ 0,9999:** se para y se consulta al usuario. Usarlo cambia FR-014 y el ritmo de SC-001.

**Resultado** (T003, 2026-10-26, teléfono A063, batería al 100 %, 24 °C). Los 64 primeros fragmentos de `text_200_pages.pdf`, con su cabecera:

| Backend | Serie | Lotes de 8 | Lotes de 16 | Coseno mínimo |
|---|---|---|---|---|
| CPU | 19,3 s (3,31 fragmentos/s) | 19,3 s (×1,00) | 19,3 s (×1,00) | 1,000000 |
| GPU | 12,8 s (5,00 fragmentos/s) | 12,8 s (×1,00) | 12,8 s (×1,00) | 1,000000 |

**El lote no acelera nada**: el engine los procesa en serie por dentro. **F07 sigue en serie** (FR-014) y no hace falta consultar.

Con estos fragmentos, la GPU va 1,5 veces más rápida que la CPU, menos que las 2,5 veces de F02, que se midieron con fragmentos de unos 350 tokens.

## R6. Entradas demasiado largas (D-036)

**Hechos:**
- **Máximo de entrada:** el escritorio (`offline/eval/builds.py`) crea el engine con `max_input_length = 512` y la app también (`LiteRtTextEmbedder.MAX_INPUT_LENGTH`).
- **Regla del escritorio:** ante el error, recorta **todo el texto embebido** (prefijo, cabecera y contenido) a `int(len(text) × 0,9)` y después hasta el último espacio. Reintenta hasta 12 veces.
- **Longitudes:** `len` de Python cuenta puntos de código, no unidades UTF-16. El troceado y la estimación de tokens sí usan UTF-16 (`utf16_len`).
- **Excepción en Android:** el único tipo de excepción propio del AAR es `LiteRtLmJniException`.

**Decisión:**
- **Función pura `EmbeddingOverflow.cut(text)`** (en `rag/runtime`), port exacto de la regla con longitudes en **puntos de código**. Una prueba JVM la compara con casos generados en Python, con acentos, emoji y textos sin espacios.
- **Dónde se aplica:** en `TextEmbedder.embedDocumentFitting`, el único método que usa la ingesta, con hasta **12 intentos** como el escritorio (`RETRIES = 12`). Devuelve el vector y el número de recortes, para el log y el informe.
- **Excepciones propias** (`rag/runtime`): `LiteRtTextEmbedder` traduce la excepción de LiteRT a `EmbeddingInputTooLongException` (agotados los 12 intentos) o a `EmbeddingEngineException` (cualquier otro fallo). `data/indexing` nunca ve tipos de LiteRT (constitución II).

**Verificación temprana** (riesgo 2): en el dispositivo, un texto de unos 700 tokens. Hay que fijar:
- qué excepción y qué mensaje da;
- que el recorte la resuelve.

**Resultado** (T002, 2026-10-26, teléfono A063, CPU y GPU):
- **Excepción:** un texto de 3.707 caracteres lanza `com.google.ai.edge.litertlm.LiteRtLmJniException` (subclase de `RuntimeException`). Su mensaje es:

  ```text
  ComputeEmbedding failed: INVALID_ARGUMENT: ERROR: [third_party/odml/litert_lm/runtime/core/embedding_engine_impl.cc:1582]
  └ Input sequence length (704) exceeds maximum supported signature length (512).
  ```
- **Recorte:** el texto entra tras **3 recortes** (2.684 caracteres), en los dos backends.
- **Engine:** sigue funcionando después del rechazo.

**Decisión:** el mensaje sí distingue «demasiado largo». `LiteRtTextEmbedder` recorta solo cuando el mensaje contiene `exceeds maximum supported signature length`. Cualquier otra excepción es `EmbeddingEngineException`.

**Si la excepción permite distinguir «demasiado largo» de otros fallos,** solo se recorta en ese caso, y un fallo del engine se trata enseguida como `EmbeddingEngineException`. Si no, se recorta ante cualquier `LiteRtLmJniException`, como el escritorio con `RuntimeError`, y un engine roto acaba en `EmbeddingInputTooLongException` tras los 12 intentos (mismo resultado: `embedding_failed`).

## R7. Solo se encuentran los documentos indexados (FR-011)

**Hechos:**
- **Búsqueda vectorial:** `vector_full_scan` en modo top-k devuelve los *k* más cercanos de toda la tabla `chunks`.
- **Modo *streaming*:** SQLite-Vector 1.1.2 lo tiene (omitiendo *k*), que admite `JOIN`, `WHERE` y `LIMIT`. Verificado en `API.md` de la etiqueta 1.1.2.
- **Latencia de F04 en el teléfono:** búsqueda vectorial exacta de 10k fragmentos con p95 de 23,1 ms; BM25, 26,5 ms.
- **Presupuesto de consulta:** 150 ms, con 120 ms de embedding de consulta.

**Decisión:** top-(*k* + *m*) y filtro, en las dos búsquedas.
- ***m*:** número de fragmentos guardados de documentos que no son buscables (`status <> 'done'` o `included = 0`). Se calcula con un `COUNT` sobre el índice `(document_id, ordinal)` de `chunks`, agrupado por los pocos documentos no buscables.
- **Búsqueda vectorial:** `vector_full_scan(..., k + m)` unido a `chunks` y `documents`, con `WHERE status = 'done' AND included = 1`, `ORDER BY distance` y `LIMIT k`.
- **BM25:** el mismo esquema, con `LIMIT k + m` dentro y el filtro fuera.
- **Por qué es exacto:** si hay *m* fragmentos no buscables, los *k* mejores buscables están entre los *k* + *m* mejores.
- **Coste:** cuando no hay nada a medias ni excluido (*m* = 0), es el mismo coste que hoy más un `JOIN` de *k* filas. Durante una indexación, *m* es como mucho lo guardado de los documentos en curso.

**Alcance:** este filtro también resuelve la exclusión de F08 (`included = 0`), así que F10 hereda las búsquedas ya filtradas.

**Orden de ejecución:** la búsqueda vectorial va en una CTE `MATERIALIZED` (`WITH v AS MATERIALIZED (SELECT rowid, distance FROM vector_full_scan(...))`), para que SQLite no la ponga como bucle interno del `JOIN` y repita el escaneo. Una prueba comprueba con `EXPLAIN QUERY PLAN` que el escaneo vectorial se hace una vez.

**Verificación:** `RagIndexBenchmarkTest` se repite con el filtro.
- **Con *m* = 0:** el p95 no puede subir más de 2 ms frente a F04.
- **Con *m* = 4.300** (un PDF de 2.000 páginas a medias): se mide y se reporta. Si con el embedding de consulta (≈ 120 ms) supera el presupuesto de 150 ms, se consulta antes de cerrar.
- **Exclusiones de F08:** pueden hacer *m* grande; F10 vuelve a medir con su volumen.

**Alternativas descartadas:**
- **Modo *streaming* con `ORDER BY` y `LIMIT`:** ordena todo el índice en cada consulta.
- **Tabla de preparación movida a `chunks` al publicar:** un esquema v3, el doble de escrituras, y aun así haría falta el filtro de inclusión de F08.

## R8. La tanda y el progreso

**Decisión:**
- **`IngestRunStore`** (DataStore de preferencias en `noBackupFilesDir/datastore`, como el de F03). Guarda la tanda:
  - los ids de sus documentos, en orden;
  - si está pausada por el usuario;
  - la causa de una pausa persistente: usuario, sin espacio o modelo ausente.
- **La temperatura y el límite del sistema no se persisten:** se derivan en vivo del estado térmico y de `WorkInfo`.
- **Fase 1 (troceado previo):** el runner extrae y trocea primero todos los documentos `pending` de la tanda, y los deja `parsed` con su número de fragmentos (lo que ya hace F06). Así el total M se conoce en segundos (FR-022): 200 páginas tardan 0,7 s en el teléfono, y 2.000 páginas, 4,1 s.
- **Fase 2 (embeddings):** documento a documento, vuelve a extraer y trocear en *streaming* (determinista) y embebe. No se guardan los fragmentos en memoria entre las dos fases.
- **Progreso:**
  - M = Σ `chunk_count` de los documentos de la tanda que no están en error;
  - N = Σ fragmentos guardados de esos mismos documentos, con un `COUNT` agrupado (método nuevo `RagIndex.storedChunkCounts(ids)`);
  - si algún documento de la tanda sigue `pending`, M es desconocido y se muestra «Procesando...»;
  - se recalcula con `documentChanges` (una vez por lote), así que sobrevive al cierre de la app (FR-024).
- **Ritmo y tiempo restante:**
  - el worker publica los fragmentos por segundo de los dos últimos lotes guardados (128 fragmentos);
  - la estimación se muestra si los dos lotes difieren menos de un 25 % («ritmo estable», FR-023), como «≈ 12 min restantes»;
  - no se muestra antes del primer lote, al cambiar de backend ni durante una pausa.
- **Documentos añadidos con la tanda en marcha:** el runner vuelve a leer la tanda entre documentos. Si hay `pending` nuevos, les hace la fase 1 antes de seguir, así el total vuelve a conocerse en segundos. La petición que se encoló detrás los encuentra ya hechos y no hace nada.
- **Fin de la tanda:** cuando ningún documento de la tanda está `pending`, `parsed` ni `embedding`, el **worker** vacía la tanda al terminar, en una sola edición de DataStore. `start()` también descarta los documentos ya terminados antes de añadir, por si el worker no llegó a hacerlo. La pantalla muestra «Todo actualizado», o los documentos con error si los hay.

## R9. Reanudación y coherencia (FR-008, FR-009)

**Decisión:**
- **Inicio de la fase 2 de un documento:** el runner lee los fragmentos guardados (`ordinal → content`) con un método nuevo de `RagIndex`.
- **Mientras trocea:**
  - un fragmento ya guardado se compara con el nuevo; si coinciden, se salta sin embeber;
  - si alguno difiere, o hay guardados más allá del último ordinal, se borran los fragmentos del documento (método nuevo `deleteChunks`) y se empieza desde 0 (FR-009).
- **Escritura:** lotes de 64 con `insertChunks` (`INSERT OR IGNORE` por `(document_id, ordinal)`).
- **Al parar** (pausa del usuario, cancelación o parada del sistema): el lote parcial ya embebido se guarda con `withContext(NonCancellable)` antes de salir, con un tope de 5 s. Así una reanudación recalcula como mucho el fragmento en curso (SC-005 admite hasta 63).
- **Cierre del documento:** se comprueba que los guardados son exactamente `chunk_count`, con ordinales 0..n-1. Si es así, `updateStatus(DONE)`. Si no, error `incomplete`, que se puede reintentar.
- **Escrituras de un documento quitado** (FR-029): antes de cada lote se comprueba que el documento sigue existiendo y en `embedding`. `insertChunks` ya rechaza un documento desconocido. Si no existe, el runner lo salta sin error.

## R10. Pausas (FR-025 a FR-028)

**Decisión:**
- **Usuario:** `pause()` guarda la pausa en `IngestRunStore` y cancela el trabajo único `ingest`. El worker termina el fragmento en curso, guarda el lote parcial (R9) y libera el engine. `resume()` borra la pausa y encola los documentos pendientes de la tanda.
- **Temperatura** (D-039):
  - **lectura:** `PowerManager.addThermalStatusListener` (API 29) en `data/device/ThermalStatusSource`, como un `Flow`;
  - **pausa:** con `THERMAL_STATUS_SEVERE` o superior, el runner guarda el lote, libera el engine, publica «pausa térmica» y espera dentro del worker;
  - **reanudación:** al bajar a `THERMAL_STATUS_MODERATE` o menos, vuelve a cargar el engine y continúa;
  - **pruebas:** `adb shell cmd thermalservice override-status 3` y `reset`, verificado en el emulador y en el teléfono.
- **Sin espacio:**
  - **antes de cada lote:** espacio libre ≥ 64 MiB, el mismo margen que F06 (`ImportLimits.SPACE_MARGIN_BYTES`). Un lote ocupa unos 0,5 MB, y 10k fragmentos ocuparon 46 MB en F04;
  - **error de escritura:** `DefaultRagIndex` traduce el fallo de SQLite por disco lleno (`SQLITE_FULL`, código 13) a `IndexStorageFullException`, declarada en el contrato de `RagIndex` (`domain/rag`). El runner captura esa, no la de SQLite (`LayeringTest`: `androidx.sqlite` solo en `data/rag`);
  - **qué pasa:** el documento queda en `embedding` sin publicar, la tanda queda pausada con causa «sin espacio» y la pantalla orienta a liberar espacio. «Reanudar» vuelve a comprobarlo.
- **Modelo ausente:** antes de la fase 2, si el modelo de embeddings no está `Verified` (`ModelFileRepository`), la tanda queda pausada con esa causa, y la pantalla ofrece ir a Preparación (ruta `Setup` de F03).
- **Límite del sistema:** derivado de `WorkInfo` (R1; tabla en data-model §7). No requiere acción del usuario.
- **Temperatura sin primer plano:** JobScheduler restringe los jobs con temperatura grave salvo que la app tenga un servicio en primer plano (código de AOSP, a confirmar en el teléfono). En ese caso el sistema para el worker en vez de dejarlo esperar dentro, y lo relanza cuando levanta la restricción. La pantalla lo muestra igualmente como pausa térmica: con temperatura grave, la causa es `THERMAL` aunque el trabajo esté parado por el sistema.

  *Comprobado en el emulador API 37 (2026-10-27)*, con el trabajo como job normal:
  - JobScheduler para el job 26 ms después de la temperatura grave;
  - el runner guarda el lote a medias, libera el engine y espera;
  - al volver la temperatura normal, JobScheduler arranca el job otra vez y la tanda sigue sin huecos (2.150 fragmentos, ordinales 0..2149).

  **Limitación de WorkManager 2.12.0** (`Processor`): un worker que pidió el primer plano queda en el mapa de trabajo en primer plano aunque Android se lo niegue o se lo quite. Desde entonces WorkManager ignora las paradas de JobScheduler («Ignored stopWork … is in foreground»). Si el sistema para el job (temperatura, cuota o el límite de 30 min), el worker sigue sin job ni servicio, hasta que el proceso se congela o JobScheduler vuelve a arrancar el job. Se recupera solo y sin perder datos; en 20 min seguidos como job normal no hubo ninguna parada. Evitarlo obligaría a no pedir el primer plano en segundo plano, y eso perdería los casos en que Android lo permite (por ejemplo, con la optimización de batería desactivada). Se deja así.

## R11. Título de la cabecera contextual y del FTS

**Hecho:**
- **Escritorio:** usa `doc.title › heading_path` para la cabecera embebida y para el `title_norm` del FTS. Esos títulos no llevan extensión.
- **App hoy:** `DefaultRagIndex.insertChunks` calcula `title_norm` con `display_name`, que es el nombre del archivo con extensión.

**Decisión:**
- **Función compartida `DocumentTitle.of(displayName)`** (`domain/rag`): quita la última extensión si tiene de 1 a 10 caracteres alfanuméricos y queda algo delante. Es la misma regla de extensión que F06 en `detectFormat`.
- **Dónde se usa:** en la cabecera del embedding (runner) y en `insertChunks` para el `title_norm`, así las dos coinciden con el escritorio.
- **Casos de prueba:** «Manual técnico.pdf» da «Manual técnico»; «notas» queda igual; «.bashrc» queda igual; «a.b.pdf» da «a.b».

## R12. Reconciliación al abrir la app (FR-018)

**Decisión:**
- `ReconcileIndexingUseCase` se llama desde `MainActivity.onStart`, en el *scope* de la aplicación. Nunca desde `Application.onCreate`, porque el proceso también arranca en segundo plano para el job (F03 R3).
- **Si el índice no está disponible**, no hace nada (evita encolar y fallar en cada arranque).
- **Si la tanda no está pausada, tiene documentos sin terminar y no hay trabajo `ingest` sin terminar** (por ejemplo, tras «Forzar detención», que cancela los jobs), vuelve a encolarlos.
- **Si hay trabajo pero está `ENQUEUED` esperando al sistema**, lo vuelve a encolar con `REPLACE` para que arranque ya con la app visible (R1). Un trabajo `RUNNING` no se toca: recupera el primer plano él solo en el siguiente lote.

## R13. Mediciones (FR-040, SC-001)

**Decisión:** `IndexingBenchmarkTest` (instrumentada, `@RequiresModels`), solo en el teléfono. Indexa `text_200_pages.pdf` (430 fragmentos) con el runner real sobre un índice temporal:
- **GPU:** tiempo total y fragmentos por segundo de la fase 2;
- **CPU:** lo mismo, forzando el backend.

Registra con la etiqueta `indexing-benchmark` y aplica el umbral de SC-001 (≤ 5 min en GPU) solo en el A063.

**Además:** `RagIndexBenchmarkTest` con el filtro (R7), y el recorrido manual con la app real.

## R14. Pruebas sin modelos

**Decisión:**
- **Fakes para la JVM y para las instrumentadas sin `@RequiresModels`:**
  - un `TextEmbedder` determinista (vector derivado del hash del texto, normalizado, con un fallo de longitud programable);
  - un `ThermalStatusSource` controlable;
  - espacio libre inyectable, como `DocumentImporter`.
- **Pruebas del runner** (JVM, con `FakeRagIndex`): reanudación, desajuste, pausa, temperatura, espacio, documento quitado y error del engine.
- **Pruebas de visibilidad** (instrumentadas): sobre SQLite real con SQLite-Vector.
- **Muertes del proceso** (SC-002): con `adb` en el teléfono, en tres momentos (quickstart §4). Lo complementa una prueba instrumentada determinista que cancela el runner a mitad y reanuda, como `IngestFlowTest` de F06.
