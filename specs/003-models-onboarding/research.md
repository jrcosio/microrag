# Research — F03 Modelos y primer arranque

**Fecha:** 2026-10-26. Todas las versiones se han verificado ese día en `maven-metadata.xml` de Google Maven o Maven Central, y el comportamiento de Hugging Face con `curl` contra las URL fijadas del catálogo.

## R1 — Dependencias nuevas

| Artefacto | Versión | Verificación | Uso |
|---|---|---|---|
| `androidx.work:work-runtime-ktx` | **2.12.0** | Google Maven: release 2.12.0, publicada el 2026-09-23 | Descarga en Android 12–13 (R3). También la usará F07 (`IngestWorker`) |
| `androidx.work:work-testing` | **2.12.0** | Google Maven | Tests JVM del worker |
| `androidx.datastore:datastore-preferences` | **1.2.1** | Google Maven, publicada el 2026-03-11 | Progreso del onboarding y estado de la descarga (R6) |
| `com.squareup.okhttp3:mockwebserver3` | **5.5.1** | Maven Central: release 5.5.1 | **Solo tests** JVM del descargador (R2). No entra en el APK |

**Detalle de `work-runtime` 2.12.0** (manifiesto y POM del AAR):
- **minSdk:** 24.
- **Permisos que añade:** `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` y `FOREGROUND_SERVICE`.
- **Dependencias transitivas:** `room-runtime` 2.7.0, `kotlinx-coroutines-android` 1.9.0 (Gradle la resuelve a la 1.11.0 del proyecto), `core` 1.12.0 (resuelve a 1.19.1) y `startup-runtime` 1.1.1.
- **Servicio:** declara `SystemForegroundService` sin `foregroundServiceType`. El proyecto lo fusiona con `dataSync` (R3).

**Resueltas en el build** (T002, `:app:dependencies --configuration debugRuntimeClasspath`):
- `work-runtime` 2.12.0, con `room-runtime` 2.7.0 y `androidx.sqlite` 2.5.0, que F04 elevará con `sqlite-bundled` 2.7.1;
- `kotlinx-coroutines-android` 1.11.0;
- `core` 1.19.1;
- `startup-runtime` 1.1.1;
- `datastore-preferences` 1.2.1, con `okio` 3.9.1.

**Inicialización de WorkManager bajo demanda.** Se elimina el `WorkManagerInitializer` de App Startup y `MicroRagApplication` implementa `Configuration.Provider`, así:
- WorkManager no abre su base de datos en cada arranque (SC-007);
- en API ≥ 34, donde la descarga va por UIDT, no se inicializa nunca;
- tras reiniciar el teléfono, `RescheduleReceiver` lo inicializa al llamar a `getInstance`.

**DataStore 1.3.0** es la release más reciente (2026-10-21, cinco días antes de este plan). Se elige la 1.2.1, madura y con la misma API de `Preferences`. La 1.3.0 se reevalúa en F12.

**Sin cliente HTTP de terceros.** El descargador usa `HttpsURLConnection` de la plataforma (R2). Así no entra ninguna librería de red en el APK, y la comprobación de confinamiento (R9) es más simple.

**Descartados:**
- `core-splashscreen`: con minSdk 31 basta la API de splash de la plataforma (R10).
- OkHttp en producción: no aporta nada que haga falta para una sola descarga secuencial.

## R2 — Descarga desde Hugging Face: redirección, `Range` y URL firmadas

**Comprobación con `curl`** sobre la revisión fijada de EmbeddingGemma 2 270M:
- `HEAD https://huggingface.co/<repo>/resolve/<commit>/<fichero>` devuelve **302**, con:
  - `x-repo-commit` igual al commit fijado;
  - `x-linked-size: 164626432`;
  - `x-linked-etag` igual al SHA-256 del catálogo;
  - `accept-ranges: bytes`.
- La redirección va a `https://us.aws.cdn.hf.co/xet-bridge-us/…`, una URL **firmada** con `Expires` ≈ 1 hora y `Signature`.
- `GET` con `Range: bytes=1000-1999` sobre la URL del CDN devuelve **206**, con `content-range: bytes 1000-1999/164626432`.

**Decisión:**
- **Origen de cada petición:** siempre la URL fijada a commit (`resolve/<commit>/<fichero>`). Cada intento, incluida cada reanudación, la vuelve a resolver, porque la URL firmada del CDN caduca en ≈ 1 h y no se guarda nunca.
- **Redirecciones manuales** (`instanceFollowRedirects = false`), como máximo 5:
  - solo `https`;
  - solo hosts permitidos: `huggingface.co` y cualquier subdominio de `hf.co`;
  - cualquier otro destino falla con `DownloadError.ForbiddenHost`.
- **Reanudación** con `Range: bytes=<bytes en disco>-`:
  - con **206**, se comprueba que `Content-Range` empieza en ese offset y que el total coincide con el tamaño del catálogo; si no, se descarta lo parcial y se empieza de cero;
  - con **200**, el servidor ignoró el rango: se trunca el parcial y se escribe desde cero (FR-013);
  - con **416** y el parcial ya del tamaño completo, la descarga está terminada; si no, se descarta lo parcial.
- **Integridad:** con la revisión fijada el contenido es inmutable, así que no hace falta `If-Range`. La protección final es el SHA-256 (R4).
- **Tiempos:** conexión 15 s y lectura 30 s, con un búfer de 256 KiB.
- **Escritura:** `FileOutputStream` en modo *append*, con `fd.sync()` cada 64 MiB y al detenerse. Ante un corte de luz se pierden como mucho 64 MiB.
- **Cabeceras:** solo `Range`, `Accept-Encoding: identity` y un `User-Agent` fijo `MicroRAG/<versionName>`, sin identificadores del dispositivo (FR-028).

**Alternativas descartadas:**
- **`DownloadManager`:** solo descarga a almacenamiento externo, lo que obligaría a copiar 2,6 GB a `noBackupFilesDir`. Además no permite controlar los hosts de las redirecciones, y Android 16 también lo somete a las cuotas de jobs.
- **Seguir las redirecciones automáticamente:** no permite aplicar la lista de hosts.

## R3 — Trabajo largo en segundo plano: UIDT en Android 14+, WorkManager en 12–13

**Hechos verificados** (developer.android.com, «User-initiated data transfer», «Data transfer options», «Long-running workers», cambios de Android 15 y 16):
- **UIDT** (API 34+) es la opción recomendada para transferencias que inicia el usuario y que no deben interrumpirse:
  - `JobInfo.Builder.setUserInitiated(true)`, con un `JobService`;
  - permiso normal `RUN_USER_INITIATED_JOBS`;
  - notificación obligatoria mediante `setNotification`;
  - sin cuotas de App Standby y sin límite fijo de duración;
  - restricción de red obligatoria;
  - se programa mientras la app es visible.
- «There is currently no Jetpack library that supports UIDT jobs.» La guía oficial bifurca: WorkManager con servicio en primer plano por debajo de Android 14 y UIDT desde Android 14.
- **Android 15:** un servicio en primer plano `dataSync` puede ejecutarse 6 h de cada 24, y existen restricciones para arrancarlo desde segundo plano.
- **Android 16:** los workers largos pueden agotar la cuota de jobs. La guía es pasar las transferencias iniciadas por el usuario a UIDT.

**Decisión:**
- **Android 14 y superior** (incluye el teléfono de referencia, API 35, y el emulador, API 37): **UIDT** con `ModelDownloadJobService`.
  - `setRequiredNetwork` con `NET_CAPABILITY_INTERNET`, cualquier red (D-027).
  - `setEstimatedNetworkBytes(bytes pendientes)` y `setPersisted(true)`.
  - Política de notificación `JOB_END_NOTIFICATION_POLICY_REMOVE`.
- **Android 12 y 13** (API 31–33): **WorkManager**, con un `CoroutineWorker` único (`model-download`, `ExistingWorkPolicy.KEEP`), la restricción `NetworkType.CONNECTED` y `setForeground` de tipo `dataSync`. Si `setForeground` falla porque el proceso arrancó en segundo plano, el worker sigue sin servicio en primer plano y la descarga avanza en las ventanas del sistema. Como es reanudable, no se pierde nada.
- **Lógica común:** las dos rutas ejecutan el mismo `ModelDownloadRunner` (R5). La parte específica de cada plataforma se limita a programar, publicar la notificación y detener.
- **Desviación de la constitución II** («WorkManager para trabajo largo»): se justifica en Complexity Tracking. WorkManager no ofrece UIDT, y en Android 14+ es la API recomendada para este caso.

**Reconciliación al abrir la app** (FR-012, US4):
- La hace el ViewModel de la Preparación al hacerse visible, nunca desde `Application.onCreate`, porque el proceso también puede arrancar en segundo plano para el job.
- Si hay modelos pedidos sin pausa y no hay ningún job pendiente o en curso, se vuelve a programar. Así se cubren el «Stop» del Administrador de tareas (que no reprograma), `force-stop` y cualquier pérdida del job.

**A verificar en el teléfono durante la implementación (riesgo 1):**
- Que un UIDT persistido se reanuda tras reiniciar sin abrir la app.
- Si no ocurre, la reanudación llega al abrir la app (reconciliación), y se documenta como diferencia de US4-2.

**Alternativas descartadas:**
- **WorkManager en todas las versiones:** choca con las cuotas de Android 16 y con el límite de `dataSync` de Android 15, y su arranque desde segundo plano no es fiable.
- **Servicio en primer plano propio:** mismas restricciones de `dataSync` y más código de ciclo de vida.

## R4 — Verificación: hash tras la descarga y movimiento atómico

**Decisión:**
- **Ubicación del parcial:** `noBackupFilesDir/models/downloads/<fichero>.download`. Está en un subdirectorio, así que el `refresh()` de F02, que borra los `.part` de `models/`, no lo toca.
- **Verificación:** al terminar, `ModelFileRepository.adopt(id, file)` calcula el SHA-256 **en el sitio**, con progreso real en bytes. Si coincide, lo mueve con `renameTo` (mismo sistema de ficheros) a `models/<fichero>`; si no, lo borra.
  - No se copia: 2,6 GB no ocupan el doble ni se leen dos veces.
  - Con la lectura medida en F02 (≈ 280 MB/s), la verificación de Gemma tarda ≈ 10 s.
- **Por qué no se calcula el hash durante la descarga:** el estado de `MessageDigest` no sobrevive a la muerte del proceso, y la reanudación obligaría a volver a leer lo ya descargado. Releer al final es más simple y siempre correcto.
- **Importación SAF** (FR-024): `installFromStream(id, input)` generaliza la copia con hash de F02 (`.part` en `models/`, hash en streaming y `rename`). El `refresh()` limpia los `.part` de una importación interrumpida (US6-3).

## R5 — Cola, espacio y reintentos (`ModelDownloadRunner`)

**Decisión:**
- **Orden:** EmbeddingGemma 2 y después Gemma 4 (FR-014). Antes de cada modelo se comprueba que `StatFs(noBackupFilesDir).availableBytes ≥ bytes pendientes + 500 MB` (FR-016). Si no, se termina con `NoSpace(bytesNecesarios)` y el job acaba sin reintento.
- **Reintentos:** hasta 5 intentos seguidos por modelo, con espera exponencial de 2, 4, 8, 16 y 32 s (FR-019). Un intento que recibe al menos 1 MiB pone el contador a cero. Tras 5 fallos queda `Failed(motivo)` y el job termina con fallo. «Reintentar» lo vuelve a programar.
- **Sin red:** el runner no la espera. Si se pierde, la lectura falla, el job termina y el sistema lo relanza cuando se cumple la restricción de red. La UI muestra «Esperando conexión» cuando `ConnectivityManager` no tiene red con `INTERNET`.
- **Progreso:** un `StateFlow` de proceso con bytes escritos y total, publicado cada ≥ 0,5 % o cada 500 ms. La notificación se actualiza como mucho una vez por segundo (límite de frecuencia del sistema).
- **Pausa** (FR-018):
  - la acción «Detener» de la notificación o de la fila guarda `paused = true` y cancela el job (`JobScheduler.cancel` o `WorkManager.cancelUniqueWork`);
  - el runner cancela la corrutina, hace `sync` y conserva el parcial;
  - reanudar pone `paused = false` y vuelve a programar.

## R6 — Persistencia del onboarding

**Decisión:** un `DataStore<Preferences>` creado con `PreferenceDataStoreFactory`. El fichero está en `noBackupFilesDir/datastore/onboarding.preferences_pb`: almacenamiento privado y excluido de copias de seguridad (FR-004, constitución I).

| Clave | Tipo | Valores |
|---|---|---|
| `onboarding_stage` | String | `NOT_STARTED`, `STARTED` o `COMPLETED` |
| `download_requested` | Set\<String\> | `ModelId` pedidos por el usuario |
| `download_paused` | Boolean | Pausa explícita del usuario |
| `download_confirmed` | Boolean | El usuario ya confirmó el tamaño. No se vuelve a preguntar al reanudar ni al reintentar |

Los bytes descargados no se guardan aparte: el tamaño del fichero `.download` es la fuente de verdad.

## R7 — Registro de verificación y chequeo barato al arrancar

**Decisión:**
- **Fichero:** `models/verification.json` (kotlinx-serialization, escritura atómica `tmp` + `rename`).
- **Contenido por modelo:** `fileName`, `sha256`, `sizeBytes`, `lastModified`, `verifiedAt`, `preparedBackend` (`GPU`, `CPU` o null) y `preparedAt`.
- **Chequeo en `refresh()`**, sin calcular hashes (FR-022, SC-007). Si el fichero existe, su tamaño es el del catálogo, `lastModified` coincide con el registro y el SHA-256 del registro es el del catálogo, entonces:
  - queda `Verified`;
  - si además tiene `preparedBackend`, queda «Listo».
- En cualquier otro caso queda `Unverified` y se vuelve a verificar antes de usarlo (FR-023).
- **Coste:** un `stat` por fichero y la lectura de un JSON de < 1 KB. Cabe sin problema en el presupuesto de arranque.
- **Compatibilidad con F02:** la batería de diagnóstico sigue llamando a `verify()` explícitamente (FR-005 de F02). `installVerified`, `verify` y `adopt` actualizan el registro.

## R8 — Prueba de compatibilidad («Preparando»)

**Decisión.** `ModelCompatibilityChecker` vive en `rag.runtime` y solo usa `EngineFactory` y `EngineCoordinator`, sin importar LiteRT-LM.

1. **Metadatos:** `factory.metadata(path)`. Si el modelo no declara CPU (embeddings) o ni GPU ni CPU (Gemma), queda `Incompatible`.
2. **EmbeddingGemma 2:** se inicializa en CPU, en `withEngine`, y embebe una consulta fija. Comprueba que `rawDimensions` es 768 y que el vector truncado tiene 256 dimensiones y norma 1 ± 1e-3.
3. **Gemma 4:**
   - se inicializa en GPU, en `withEngine`, con `GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 8, greedy = true)`;
   - genera a partir de un prompt fijo y corto, y exige al menos 1 token sin excepción;
   - si falla la GPU (excepción o 0 tokens), libera y repite en CPU;
   - si funciona en CPU, queda `Ready(CPU)`; si no, `Incompatible`.

   La prueba detecta errores del runtime o de los kernels. No juzga la calidad del texto, y queda documentado como limitación: el caso fp16 de D-023 solo se vio con el fichero `-gpu`, que no se usa.
4. **Al terminar:** `coordinator.release(key)`. La regla de un solo motor en GPU la garantiza `EngineCoordinator`.
5. **Persistencia:** el backend resultante se guarda en el registro (R7), y F11 lo usa para cargar Gemma directamente.

**Duración esperada en el teléfono de referencia:**
- Gemma: ≈ 17 s en frío más 1–2 s de generación. Deja `cacheDir/litertlm` preparada, así que el primer chat carga en ≈ 5,4 s (SC-010).
- EmbeddingGemma: < 1 s.

**Dónde se ejecuta:** en el proceso de la app, lanzado por el caso de uso desde un `CoroutineScope` de aplicación, cuando la Preparación es visible y hay un modelo `Verified` sin backend preparado. No se ejecuta dentro del job: con la app en segundo plano, cargar 2,5 GB arriesga que el sistema mate el proceso, y `EngineMemoryCallbacks` libera los motores en `UI_HIDDEN`.

## R9 — Confinamiento de red (FR-026, SC-008)

**Decisión.** Dos comprobaciones:
1. **`NetworkConfinementTest`** (JVM, como `LayeringTest`): recorre `src/main/java` y `src/debug/java`. Falla si un fichero fuera de `data/download/` importa:
   - `java.net.`, excepto `java.net.URI`;
   - `javax.net.`;
   - `okhttp3.`;
   - `android.net.http.`;
   - `java.nio.channels.SocketChannel`.

   `android.net.Uri` y `ConnectivityManager` están permitidos: no abren conexiones.
2. **`NoTrafficInstrumentedTest`:** recorre Bienvenida, Preparación e Inicio con repositorios falsos y modelos «Listo». Comprueba que `TrafficStats.getUidTxBytes(uid)` y `getUidRxBytes(uid)` no cambian. Se complementa con el recorrido manual en modo avión del quickstart.

## R10 — Arranque y destino inicial

**Decisión:**
- `MainActivity` mantiene la splash de la plataforma (API 31+) con un `OnPreDrawListener` hasta que `ResolveStartDestinationUseCase` lee `onboarding_stage`. La lectura de DataStore tarda unos pocos ms.
- **Destino según la etapa:**
  - `COMPLETED`: `Home`;
  - `STARTED`: pila `[Welcome, Setup]`, para que Atrás en la Preparación lleve a la Bienvenida (FR-003);
  - `NOT_STARTED`: `Welcome`.
- **«Continuar»:** `navigate(Home) { popUpTo<Welcome> { inclusive = true } }`. Atrás desde Inicio cierra la app.
- La ruta de depuración `DebugRouting.initialRoute` sigue teniendo prioridad (F01).

## R11 — Permiso de notificaciones, red y RAM

**Decisión:**
- **Notificaciones (FR-010):** en API 33+, al pulsar «Descargar» en la confirmación, si `POST_NOTIFICATIONS` no está concedido:
  1. una línea de explicación dentro del cuerpo de la confirmación, sin diálogo adicional, para mantener los 4 toques de SC-005;
  2. al pulsar «Descargar», `ActivityResultContracts.RequestPermission`;
  3. se programa la descarga con cualquier resultado.

  Con el permiso denegado, la Preparación muestra un aviso persistente (`LisdataInfoCard`).
- **Canal:** `model_downloads`, «Descarga de modelos», importancia baja (sin sonido).
- **Red actual en la confirmación:** `ConnectivityManager.getNetworkCapabilities(activeNetwork)`, que da «Wi-Fi», «Datos móviles» (`TRANSPORT_CELLULAR` o red medida) o «Sin conexión». Con datos móviles se muestra el aviso (D-027).
- **RAM (G31):** `ActivityManager.MemoryInfo.totalMem < 7_500_000_000` bytes (decimal), con aviso no bloqueante. Un teléfono de 8 GB declara ≈ 7,6–7,9·10⁹ bytes y no lo muestra; uno de 6 GB (≈ 5,6·10⁹) sí. Así se respeta la intención del informe («inferior a unos siete gigabytes y medio»).

## R12 — Pruebas y dispositivos

- **Teléfono de referencia** (API 35): ruta UIDT, flujo completo con descarga real de ≈ 2,75 GB y pruebas de reanudación (quickstart).
- **Emulador `Pixel_10`** (API 37, arm64): ruta UIDT, tests instrumentados y accesibilidad.
- **Ruta WorkManager** (API 31–33):
  - tests JVM con `work-testing` y Robolectric (`sdk = 32` en esos tests);
  - en local solo hay imágenes de API 30 y 37. La verificación en un emulador API 33 (`system-images;android-33;google_apis;arm64-v8a`) queda como tarea de validación, para instalar la imagen si el usuario lo aprueba.
- **Tests de red en la JVM:** `mockwebserver3` con TLS desactivado y la lista de hosts inyectada. En producción la lista y el esquema `https` están fijados y no se configuran.

## R13 — Riesgos

1. **Reanudación de UIDT tras reiniciar** (R3): se verifica en el teléfono. Hay plan B documentado.
2. **Prueba de compatibilidad de Gemma en teléfonos con poca RAM:** 2,5 GB de RSS. Con < 7,5 GB ya hay aviso (G31). Si el sistema mata el proceso durante «Preparando», al volver se repite: no hay estado intermedio que se pueda dar por bueno.
3. **CDN de Hugging Face:** cambio de host o de política de rangos. Mitigación: la lista de hosts cubre `*.hf.co`, sin rangos se cae a descarga completa (200), y el error se muestra con «Reintentar». Si Hugging Face cambia de dominio, hace falta una versión nueva de la app (aceptable: constitución I).
4. **Tamaño del APK:** WorkManager (con Room) y DataStore añaden peso. Se mide al cerrar. R8 llega en F15.
5. **Android 17 (target 37):** no se han encontrado cambios documentados para UIDT. Se comprueba en el emulador API 37.
