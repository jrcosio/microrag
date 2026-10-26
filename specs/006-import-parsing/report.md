# Informe de cierre — F06 Importación y parsing

**Rama:** `006-import-parsing`.
**Fecha:** 2026-10-26.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64, extensión S 22;
- AVD `MicroRAG_API33`, API 33, extensión S 5, que no tiene lector de PDF del sistema y sirve para probar ese camino;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), extensión S 23.

**Versiones:** sin dependencias nuevas. API de plataforma `android.graphics.pdf` (`PdfRendererPreV`, extensión S ≥ 13), WorkManager 2.12.0, `activity-compose` 1.13.0, Compose BOM 2026.09.00. Python: `pypdf` 6.20.1, ya fijada.

## 1. Qué se ha implementado

- **Importación** (`data/ingest`):
  - selector del sistema con PDF, TXT y Markdown, sin permisos de almacenamiento;
  - copia privada en `noBackupFilesDir/documents/<sha256>.<ext>`, escrita en un temporal y renombrada solo cuando está completa;
  - validación por archivo con 12 motivos de error (`IngestError`);
  - duplicados detectados por SHA-256 antes de renombrar;
  - límites de 100 MB y 2.000 páginas (D-037).
- **Cola:** combina intentos en memoria, documentos no indexados del índice y el trabajo de WorkManager. Persiste en el índice; la limpieza de huérfanos corre una vez por proceso.
- **Procesamiento:** trabajo único `ingest` (`APPEND_OR_REPLACE`, etiquetas por documento) con `IngestRunner`. Cada documento queda `parsed` con su número de fragmentos y de páginas sin texto, o `error` con su código. Es reanudable e idempotente.
- **Extracción y troceado** (`rag/ingest`, Kotlin puro):
  - port exacto de `offline/eval/extract.py`, con la semántica de Python (espacios, saltos de línea, dígitos, regex de encabezados y viñetas) en tablas generadas desde Python;
  - lectura UTF-8/cp1252 en streaming;
  - PDF página a página con el lector del sistema;
  - `Chunker` del informe con estado.
- **Índice:** esquema v2 con `documents.skipped_pages` y su migración 1 → 2 (D-038). `RagIndex` gana `documents`, `documentIdByHash` y `documentChanges`.
- **Pantalla** `ingest` (PNG 05):
  - G14 y G15 (hoja inferior);
  - propuestas P1–P6;
  - textos en español e inglés;
  - nuevo componente `LisdataBottomSheet`;
  - acceso provisional desde Inicio;
  - el contenedor de la app ya no suma dos veces la barra de navegación del sistema (§6).
- **Python** (`offline/`):
  - `decode_text`;
  - golden de extracción (129 casos);
  - tablas `PyTables.kt`;
  - 8 fixtures PDF deterministas;
  - comando `device-extraction`.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 111/111 (F05: 98) |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 308/308 (F05: 214) |
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 217/217, con `ShellInsetsTest` (F04: 159) |
| Instrumentados en la AVD API 33 (mismos filtros) | ⚠️ 213/217. Las 4 que fallan (`SystemBackTest` y 3 de `OnboardingNavigationTest`) fallan igual con `main`: en Android 12–13, Atrás en la raíz manda la tarea al fondo en vez de cerrar la Activity. Previas a F06, ver §7 |
| Instrumentados en el teléfono A063 (mismos filtros) | ✅ 217/217 con la versión final, incluida `ShellInsetsTest` (216/216 antes de la corrección de márgenes, §6) |
| APK de release | ✅ Solo `schema.sql` en *assets*; sin fixtures ni golden (X4); sin bibliotecas nativas nuevas |

## 3. Validación por historia y criterio

| Historia o criterio | Resultado |
|---|---|
| **US1** | Teléfono, con el selector del sistema: PDF, TXT y `.md` se eligen con el filtro (R4; MediaStore da `text/markdown`); la cola muestra «4 archivos» y «Listo para procesar». Un PDF de 2.000 páginas (el límite) se acepta. Antes, lo mismo en el emulador |
| **US2** | `DocumentImporterTest`, 15 pruebas: un caso por error, límites exactos, sin temporales, logs sin nombres. Repositorio: reintentar y quitar con confirmación. En el teléfono: el mismo PDF otra vez da «Ya está en tu biblioteca» y uno cifrado, «Archivo cifrado», sin bloquear al resto; quitar un documento pide confirmación, borra su copia y permite volver a importarlo |
| **US3** | Teléfono, con el worker real: «Extrayendo · página N de 2000» con su barra; TXT, MD y PDF acaban «Preparado para indexar · N fragmentos» y el escaneado, «Sin texto». 2.000 páginas en 4,1 s en primer plano (4.300 fragmentos). `IngestFlowTest` con SQLite real y el lector del sistema |
| **US4** | Interrupción a mitad de 200 páginas, reanudada con el mismo resultado (3/3 en cada emulador y en el teléfono). Copia cancelada sin restos. Repositorio nuevo con la misma cola. ViewModel recreado sin reimportar. Recorrido en el teléfono en §3.1 |
| **US5** | `IngestScreenAccessibilityTest`: 32/32 (8 estados, claro y oscuro, fuente al 200 %). `IngestScreenTest`: 10/10 |
| **SC-001** | Troceado: 27/27 casos y 162/162 fragmentos, en la JVM y en ART (API 33 y 37) |
| **SC-002** | Extracción: 129/129 casos, en la JVM y en ART (API 33 y 37) |
| **SC-003** | `IngestFlowTest`: cada fixture en su estado, más duplicado y original borrado |
| **SC-004 / SC-005** | ✅ Teléfono: 10 MB listos en 56–65 ms (< 3 s); 200 páginas en 675–692 ms (< 30 s), con el PSS creciendo 23–27 MB (≤ 100 MB). Cifras en §4 |
| **SC-006** | §US4 |
| **SC-007** | `ManifestInvariantsTest` (lista cerrada de permisos, sin almacenamiento) y `NetworkConfinementTest`, en verde en la suite |
| **SC-008** | §US5. Capturas del teléfono, en claro y en oscuro, comparadas con el PNG en §5. TalkBack manual: pendiente (§7) |
| **SC-009** | `IngestStrictModeTest` con control negativo: en verde en los dos emuladores y en el teléfono, tras corregir dos fallos que encontró (§6) |

### 3.1 Recorrido de robustez en el teléfono (T060)

- **Girar** con la cola llena: se conservan las filas y la posición del desplazamiento.
- **Salir y `am kill`** con documentos listos: al volver, la app abre Importar con los 5 documentos del índice. El intento duplicado desaparece, como está diseñado (los intentos viven en memoria, D-038).
- **Matar el proceso** (`kill -9`) nada más pulsar «Iniciar indexación»: WorkManager repite el trabajo en segundo plano unos 50 s después, y los 5 documentos terminan una vez cada uno (4 preparados y el escaneado en «Sin texto»). El de 200 páginas da los mismos 430 fragmentos que la medición.
- **Matar el proceso a mitad de un documento** (página ~150 de 2.000, app en segundo plano): el sistema relanza el trabajo unos 2 min después, pero se para en la página 818 porque Android congela el proceso (observación en §7). Al abrir la app sigue y termina con 4.300 fragmentos, los mismos que sin interrupción.
- **Logs:** solo identificadores, cifras y códigos de error; ningún nombre de archivo (constitución I).

## 4. Cifras medidas

**Teléfono A063** (`IngestBenchmarkTest`, 3 ejecuciones seguidas, batería al 81 %, 22 °C):

| Medida | Objetivo | Teléfono | Emulador API 37 |
|---|---|---|---|
| SC-004: PDF de 10.014.866 bytes, de la URI a «Listo para procesar» | < 3 s | 56, 65 y 59 ms | 29 ms |
| SC-005: 200 páginas extraídas y troceadas (430 fragmentos) | < 30 s | 675, 685 y 692 ms | 526–594 ms |
| SC-005: crecimiento máximo del PSS | ≤ 100 MB | 26,9, 23,1 y 23,2 MB | 3,1–3,8 MB |

El PSS crece más en el teléfono que en el emulador, pero queda a un cuarto del límite.

**Comparación con pypdf** (`CorpusPdfExtractionTest` y `run_eval.py device-extraction`). El texto por página del teléfono es idéntico byte a byte al del emulador API 37.

| Documento | Evidencias | pypdf | Dispositivo | Caracteres pypdf | Caracteres dispositivo |
|---|---|---|---|---|---|
| aeat-cuadernos-ata | 5 | 5 | 3 | 23.492 | 23.466 |
| aeat-facturacion-2011 | 25 | 25 | 25 | 157.802 | 157.728 |
| aeat-verifactu | 5 | 5 | 2 | 16.737 | 16.639 |
| boe-comercio | 11 | 11 | 11 | 76.960 | 76.961 |
| boe-facturacion | 15 | 15 | 15 | 103.947 | 103.947 |
| boe-teletrabajo | 24 | 24 | 24 | 165.928 | 165.952 |
| **Total** | **85** | **85** | **80** | **544.866** | **544.693** |

Las 5 evidencias que faltan están en los dos PDF de la AEAT. Son defectos de pypdf que las evidencias copiaron, como «Borradores ,» o «s eleccionar»; PDFium extrae el texto limpio («Borradores,», «seleccionar»). La extracción del dispositivo es igual o mejor que la que evaluó F05.

## 5. Diferencias frente al mockup

Capturas del teléfono (T065), en claro y en oscuro: vacío, listo (como el PNG), en curso, preparado, errores, hoja de detalle y confirmación de quitar. Con la cola del PNG, la pantalla coincide con el PNG 05 en estructura, textos, colores y medidas, salvo esto:

- **G14:** el título de la tarjeta es «Selecciona archivos» en lugar de «Arrastra o selecciona archivos».
- **Tamaños:** con el formateador de Android sale «2,5 kB» y «207 kB»; el PNG pone «2,4 MB» y «48 KB».
- **Textos largos:** «Preparado para indexar · 430 fragmentos» y «Extrayendo · página 576 de 2000» ocupan dos líneas en el teléfono. El PNG solo maqueta «Listo para procesar», que cabe en una.
- **Contador:** «N archivos» cuenta todas las filas, también las que fallaron.
- **Estados nuevos:** los que el PNG no maqueta siguen las propuestas aprobadas P1–P6. En P5 se usa `LisdataEmptyState` con el icono de aviso y sin «Reintentar», porque reintentar no arregla un índice incompatible.
- **Hoja (G15):** `ModalBottomSheet` de M3 con el tirador ampliado a 48 dp de área táctil.
- **Barra inferior:** con la navegación de tres botones del teléfono, «Iniciar indexación» quedaba 48 dp por encima de la barra del sistema. Corregido (§6).

## 6. Desviaciones respecto al plan y fallos encontrados

1. **`IngestFlowTest` sin WorkManager.** El `IngestWorker` toma sus dependencias del Koin de la app, que apunta al índice real del dispositivo. La prueba usa el importador y el runner sobre un índice temporal. El encolado se prueba con `work-testing`, y el worker real, en los recorridos del emulador y del teléfono.
2. **Decodificación propia** (research R8): tabla cp1252 y validador UTF-8 propios.
3. **Corte duro** de párrafos sin líneas en blanco de más de 1 M de caracteres (research R10).
4. **Fallos encontrados y corregidos:**
   - la limpieza de huérfanos podía borrar la copia de una importación que terminaba a la vez (la encontró `IngestStrictModeTest`; hay prueba de regresión JVM);
   - el runner comprobaba la copia en el hilo del llamador;
   - `startProcessing` hacía su trabajo en el hilo principal;
   - un fallo al crear la copia se escapaba sin traducir a un error de la fila;
   - faltaba registrar `DocumentExtractor` en Koin;
   - **márgenes de la barra inferior** (encontrado en el teléfono): el `Scaffold` del contenedor ya reserva la barra de navegación del sistema y la barra «Iniciar indexación» la volvía a sumar con `navigationBarsPadding()`. Con la navegación de tres botones quedaban 48 dp de hueco bajo el botón, y en horizontal la barra acababa antes que las tarjetas. Le pasaba también a la barra «Continuar» de Preparación (F03). El contenedor ahora consume esos márgenes (`consumeWindowInsets`). `ShellInsetsTest` falla sin la corrección (hueco = margen + 2 × barra) y pasa con ella en los dos emuladores y en el teléfono.

## 7. Pendiente y observaciones

- **TalkBack** (T072, que queda abierta): no se puede recorrer de forma fiable por `adb`. Queda para un recorrido manual, como los de F01 (T059) y F03 (T071). La semántica está cubierta por `IngestScreenAccessibilityTest` (32/32 en los tres dispositivos) y por las descripciones que muestra el volcado de accesibilidad del teléfono («Manual de prueba.pdf, 2,5 kB, Listo para procesar»).
- **Trabajo congelado tras matar el proceso** (observación del recorrido, §3.1). Al relanzar el trabajo, WorkManager canceló su tarea de JobScheduler mientras el trabajo seguía corriendo dentro del proceso. En los logs, `Cancelling work ID … Job ID 10004` aparece justo después de `Starting work`. Sin esa tarea ni un servicio, Android trató el proceso como caché y lo congeló a mitad del documento. No se pierde nada: el documento sigue pendiente y termina con el mismo resultado al abrir la app, o al volver a arrancar el proceso. **F07** ejecuta el trabajo en primer plano con notificación (FEATURES, F07), y eso impide la congelación. F07 debe repetir este escenario: matar a mitad de un documento con la app en segundo plano y no abrirla.
- **F07:**
  - amplía `IngestRunner` con los embeddings y la inserción, y procesa también los documentos `parsed`;
  - añade el servicio en primer plano;
  - aplica D-036.
- **F08:** puede mostrar `skipped_pages` en el detalle.
- **Pruebas de navegación en la API 33:** `SystemBackTest` y `OnboardingNavigationTest` esperan que Atrás en la raíz destruya la Activity, pero en Android 12–13 queda en segundo plano. Fallan igual con `main`. Se propone aceptar los dos comportamientos en F15.
- **Abiertos de antes:**
  - TalkBack de F01 (T059) y F03 (T071);
  - el SVG del logo (G20);
  - las violaciones de StrictMode al arrancar (F09 o F15).
