# Informe de cierre — F06 Importación y parsing

**Rama:** `006-import-parsing`.
**Fecha:** 2026-10-26.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64, extensión S 22;
- AVD `MicroRAG_API33`, API 33, extensión S 5, que no tiene lector de PDF del sistema y sirve para probar ese camino;
- teléfono de referencia Nothing Phone (1) A063, API 35: PENDIENTE (§7).

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
  - acceso provisional desde Inicio.
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
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 216/216 (F04: 159) |
| Instrumentados en la AVD API 33 (mismos filtros) | ⚠️ 212/216. Las 4 que fallan (`SystemBackTest` y 3 de `OnboardingNavigationTest`) fallan igual con `main`: en Android 12–13, Atrás en la raíz manda la tarea al fondo en vez de cerrar la Activity. Previas a F06, ver §7 |
| Instrumentados en el teléfono | PENDIENTE (§7) |
| APK de release | ✅ Solo `schema.sql` en *assets*; sin fixtures ni golden (X4); sin bibliotecas nativas nuevas |

## 3. Validación por historia y criterio

| Historia o criterio | Resultado |
|---|---|
| **US1** | Importación real en el emulador con el selector del sistema: el `.md` se puede elegir con el filtro (R4), y la cola muestra «5 archivos» y «Listo para procesar» |
| **US2** | `DocumentImporterTest`, 15 pruebas: un caso por error, límites exactos, sin temporales, logs sin nombres. Repositorio: reintentar y quitar con confirmación |
| **US3** | En el emulador con el worker real: 4 documentos encolados, 3 preparados y el escaneado en «Sin texto». `IngestFlowTest` con SQLite real y el lector del sistema |
| **US4** | Interrupción a mitad de 200 páginas, reanudada con el mismo resultado (3/3 en cada emulador). Copia cancelada sin restos. Repositorio nuevo con la misma cola. ViewModel recreado sin reimportar |
| **US5** | `IngestScreenAccessibilityTest`: 32/32 (8 estados, claro y oscuro, fuente al 200 %). `IngestScreenTest`: 10/10 |
| **SC-001** | Troceado: 27/27 casos y 162/162 fragmentos, en la JVM y en ART (API 33 y 37) |
| **SC-002** | Extracción: 129/129 casos, en la JVM y en ART (API 33 y 37) |
| **SC-003** | `IngestFlowTest`: cada fixture en su estado, más duplicado y original borrado |
| **SC-004 / SC-005** | Emulador: 10 MB importados en 29 ms; 200 páginas en 526–594 ms, con el PSS creciendo 3,1–3,8 MB. Teléfono: PENDIENTE |
| **SC-006** | §US4 |
| **SC-007** | `ManifestInvariantsTest` (lista cerrada de permisos, sin almacenamiento) y `NetworkConfinementTest`, en verde en la suite |
| **SC-008** | §US5. Capturas del emulador en §5 |
| **SC-009** | `IngestStrictModeTest` con control negativo: 3/3 en cada emulador, tras corregir dos fallos que encontró (§6) |

## 4. Cifras medidas

PENDIENTE del teléfono (SC-004, SC-005 y la comparación con pypdf).

**Comparación con pypdf, emulador API 37:** de las 85 evidencias del conjunto de prueba en los PDF del corpus, el texto del dispositivo contiene 80 (pypdf, 85), con 544.693 caracteres frente a 544.866. Las 5 que faltan están en dos PDF de la AEAT y son defectos de pypdf que las evidencias copiaron, como «Borradores ,» o «s eleccionar». PDFium extrae el texto limpio («Borradores,», «seleccionar»). La extracción del dispositivo es igual o mejor que la que evaluó F05.

## 5. Diferencias frente al mockup

- **G14:** el título de la tarjeta es «Selecciona archivos» en lugar de «Arrastra o selecciona archivos».
- **Tamaños:** con el formateador de Android sale «2,8 kB»; el PNG pone «48 KB».
- **Estados nuevos:** los que el PNG no maqueta siguen las propuestas aprobadas P1–P6. En P5 se usa `LisdataEmptyState` con el icono de aviso y sin «Reintentar», porque reintentar no arregla un índice incompatible.
- **Hoja (G15):** `ModalBottomSheet` de M3 con el tirador ampliado a 48 dp de área táctil.

## 6. Desviaciones respecto al plan y fallos encontrados

1. **`IngestFlowTest` sin WorkManager.** El `IngestWorker` toma sus dependencias del Koin de la app, que apunta al índice real del dispositivo. La prueba usa el importador y el runner sobre un índice temporal. El encolado se prueba con `work-testing`, y el worker real, en el recorrido del emulador.
2. **Decodificación propia** (research R8): tabla cp1252 y validador UTF-8 propios.
3. **Corte duro** de párrafos sin líneas en blanco de más de 1 M de caracteres (research R10).
4. **Fallos encontrados y corregidos:**
   - la limpieza de huérfanos podía borrar la copia de una importación que terminaba a la vez (la encontró `IngestStrictModeTest`; hay prueba de regresión JVM);
   - el runner comprobaba la copia en el hilo del llamador;
   - `startProcessing` hacía su trabajo en el hilo principal;
   - un fallo al crear la copia se escapaba sin traducir a un error de la fila;
   - faltaba registrar `DocumentExtractor` en Koin.

## 7. Pendiente y observaciones

- **Teléfono** (no estaba conectado):
  - T004/T010, PDF de plataforma;
  - T060, recorrido de robustez;
  - T065, capturas;
  - T067, SC-004/SC-005;
  - T068, comparación con pypdf;
  - T071, suite;
  - T072, recorrido manual con TalkBack.
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
