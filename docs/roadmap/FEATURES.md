# MicroRAG — Roadmap de features

**Fecha:** 2026-10-25
**Estado:** ✅ aprobado por el usuario el 2026-10-25. Ampliado el 2026-11-03 con F16–F20, que se ejecutan antes de F14 (D-066).
**Referencias:** `INVENTARIO.md` (pantallas, tokens, huecos G01–G47), `DECISIONS.md` (D-001…), `.specify/memory/constitution.md` (principios I–VIII).

Cada feature recorre el ciclo SDD de Spec Kit en su rama `NNN-nombre`:

1. `/speckit-specify`
2. `/speckit-clarify` — **parada de revisión**
3. `/speckit-plan`
4. `/speckit-tasks`
5. `/speckit-analyze` — **parada de revisión**
6. `/speckit-implement`

Skills complementarias:
- `/speckit-checklist`: listas de requisitos por dominio (a11y, privacidad, rendimiento).
- `/speckit-converge`: completa tareas pendientes si la implementación diverge.
- `/speckit-taskstoissues`: solo si se quiere pasar las tareas a issues de GitHub.

Al cerrar cada feature se entrega:
- el resumen;
- build, lint y tests;
- las diferencias frente al mockup;
- las cifras medidas;
- la actualización de esta tabla.

**Tipos:**
- **Producto**: UI final según el PNG.
- **Técnica**: sin UI de producto, con diagnóstico interno solo en debug y nunca UI de producto inventada.

**Estados:** ⬜ pendiente · 🟨 en curso (se indica la fase SDD) · ✅ cerrada · ⏸ bloqueada.

## Resumen

| ID | Feature | Tipo | Depende de | Estado |
|---|---|---|---|---|
| F01 | Fundaciones y design system | Producto (shell y DS) | — | ✅ cerrada (T059 TalkBack manual abierta) |
| F02 | Viabilidad on-device (fase 0) | Técnica | F01 | ✅ cerrada |
| F03 | Modelos y primer arranque | Producto | F01, F02 | ✅ cerrada (T071 TalkBack manual abierta) |
| F04 | Núcleo de datos RAG | Técnica | F02 | ✅ cerrada |
| F05 | Harness offline de evaluación | Técnica | F04 | ✅ cerrada |
| F06 | Importación y parsing | Producto | F04 | ✅ cerrada |
| F07 | Indexación | Producto | F02, F06 | ✅ cerrada |
| F08 | Biblioteca y detalle de documento | Producto | F07 | ✅ cerrada |
| F09 | Inicio | Producto | F03, F07, F08 | ✅ cerrada |
| F10 | Recuperación híbrida | Técnica | F05, F07 | ✅ cerrada |
| F11 | Chat con fuentes | Producto | F08, F10 | ✅ cerrada |
| F12 | Ajustes e Idiomas | Producto | F03, F11 | ✅ cerrada |
| F13 | Historial de chats | Producto (UI propuesta) | F11, F12 | ✅ cerrada |
| F14 | Rendimiento en dispositivo y decisión INT8 | Técnica | F10, F11, F17, F20 | ⬜ |
| F15 | Pulido y release | Producto | F01–F20 | ⬜ |
| F16 | Apariencia: tema e icono de Ajustes | Producto (UI propuesta) | F12 | 🟨 implementada; pendiente de la revisión de cierre |
| F17 | Carpetas y ámbito del chat | Producto (UI propuesta) | F08, F11, F13 | ⬜ |
| F18 | Viabilidad del RAG visual (fase 0 visual) | Técnica | F05, F10, F11 | ⬜ |
| F19 | Indexación visual | Producto (UI propuesta) | F17, F18 | ⬜ |
| F20 | Chat con páginas | Producto (UI propuesta) | F19 | ⬜ |

**Orden de ejecución (D-066):** F01–F13 → **F16 → F17 → F18 → F19 → F20** → F14 → F15. F14 y F15 conservan su número porque las decisiones cerradas las citan así. Las ramas nuevas se crean con `--number` (`016-…` a `020-…`), y F14 se lanzará con `--number 14`.

```mermaid
flowchart LR
    F01 --> F02 --> F03
    F02 --> F04 --> F05
    F04 --> F06 --> F07
    F02 --> F07
    F07 --> F08
    F03 --> F09
    F08 --> F09
    F05 --> F10
    F07 --> F10
    F10 --> F11
    F08 --> F11
    F11 --> F12 --> F13
    F11 --> F13
    F10 --> F14
    F11 --> F14
    F12 --> F15
    F13 --> F15
    F14 --> F15
    F12 --> F16
    F08 --> F17
    F11 --> F17
    F13 --> F17
    F05 --> F18
    F10 --> F18
    F11 --> F18
    F17 --> F19
    F18 --> F19
    F19 --> F20
    F17 --> F14
    F20 --> F14
    F16 --> F15
```

### Por qué este orden (D-005)

1. **F02 va antes que F03.** Que Gemma 4 E2B y EmbeddingGemma 2 rindan en el teléfono de referencia es el mayor riesgo. Se comprueba con `adb push` sin construir la descarga, y deja los ficheros, hashes y backends que F03 necesita.
2. **F05 se adelanta.** La dimensión, el tamaño de chunk y la variante de embedding definen el espacio vectorial, y cambiarlos más tarde obliga a reindexar. Se fijan en escritorio antes de indexar en el móvil. Los benchmarks en el dispositivo y la decisión de INT8 quedan en F14.
3. **F08 va antes que F11.** Una cita del chat abre el detalle del documento.
4. **F09 es una feature propia.** Inicio agrega el estado de modelos, biblioteca e indexación. Hasta F09, el destino `home` es un marcador del shell, solo en debug.
5. **F16–F20 van antes que F14 (D-066).** Cambian la ingesta, el esquema, la recuperación y el prompt, así que F14 tiene que medir el sistema final. Lo pequeño va primero. F17 deja hecho el filtro de ámbito que reutiliza el RAG visual. F18 decide si F19 y F20 siguen adelante, igual que F02 hizo con el resto.

---

## F01 — Fundaciones y design system

- **Tipo:** Producto. Shell y design system; sin pantallas de producto finales.
- **Estado:** ✅ cerrada: implementada el 2026-10-25 en la rama `001-foundations-design-system` y aprobada por el usuario el mismo día. Informe de cierre en `specs/001-foundations-design-system/report.md`. Queda abierto el recorrido manual con TalkBack (T059).

**Objetivo.** Dejar una base compilable y verificable sobre la que montar el resto:
- configuración del proyecto;
- paquetes por capa (D-009);
- DI;
- navegación con las rutas de `01-navigation.md` y la barra inferior;
- design system completo con tokens, tipografía, iconos y componentes `Lisdata*`, con previews y un catálogo interno.

**Pantallas y mockups.**
- Los 10 PNG como referencia de tokens y componentes.
- Rutas y shell según `docs/disegn_app/docs/01-navigation.md`.
- Componentes según `03-components.md` e `INVENTARIO.md` §4.
- Tokens según `00-design-system.md` e `INVENTARIO.md` §2.

**Secciones del informe.** Configuración base (ABI) · Privacidad y copias de seguridad (`allowBackup=false`) · Memoria y ciclo de vida (dispatchers dedicados) · Estructura del proyecto (adaptada a un solo módulo).

**Dependencias.** Ninguna.

**Criterios de aceptación.**
1. `./gradlew assembleDebug lintDebug testDebugUnitTest` en verde.
2. Manifiesto y build:
   - `allowBackup="false"`;
   - `dataExtractionRules` excluye todo;
   - sin permiso `INTERNET`, que llega en F03;
   - minSdk acordado (≥ 26, D-006);
   - `abiFilters` = `arm64-v8a` (más `x86_64` en debug).
3. Koin arranca con un módulo por capa, y un test verifica el grafo de dependencias.
4. NavHost con las rutas `welcome`, `setup`, `home`, `chat/{conversationId?}`, `ingest`, `library`, `document/{documentId}`, `index`, `settings` y `languages`:
   - la barra inferior aparece solo en `home`, `chat`, `library` y `settings`;
   - Atrás no duplica pilas de pestañas (test de navegación);
   - la flecha Atrás de las pestañas sigue G08.
5. `LisdataTheme`:
   - tokens claro y oscuro de `INVENTARIO.md` §2 (G05, G06, G07 y G42 resueltos);
   - Google Sans estática;
   - `Typography` mapeada;
   - sin hex fuera de `core.designsystem` (comprobación automática);
   - un test unitario de contraste de los pares texto/fondo: ≥ 4,5:1, o 3:1 en texto grande.
6. Los iconos `ic_*` de `INVENTARIO.md` §2.6 están como vector drawables de Lucide, con la licencia ISC incluida.
7. Todos los componentes de `INVENTARIO.md` §4 tienen previews en claro, oscuro, texto largo, `fontScale` 2.0 y estado de error.
8. Catálogo de componentes interno navegable, solo en debug.
9. Hay dispatchers dedicados de un solo hilo para SQLite, LLM y embeddings, inyectables.
10. Strings en `strings.xml` (es), con `values-en` preparado.

**Riesgos.**
- Compatibilidad de AGP 9 (Kotlin integrado) con el plugin de Compose y Koin.
- Elegir entre Navigation Compose 2.10.2 y Navigation 3 (1.2.0, estable); se decide en el plan.
- Fuentes variables en Compose (se usan las estáticas) y peso del APK.
- Fidelidad de M3 frente al PNG (G18).

---

## F02 — Viabilidad on-device (fase 0)

- **Tipo:** Técnica. Pantalla de diagnóstico interna, solo en debug.
- **Estado:** ✅ cerrada: implementada el 2026-10-26 en la rama `002-ondevice-feasibility` y aprobada por el usuario el mismo día. Informe de cierre en `specs/002-ondevice-feasibility/report.md` y cifras en `benchmarks.md`. Decisiones D-023 (Gemma genérico en GPU), D-024 (consulta en CPU; GPU apta para la ingesta), D-025 (EmbeddingGemma 2 de 270M) y D-026 (G44: presupuesto de primer token ≤ 5 s con ≈ 2.000 tokens; constitución v1.1.0). Teléfono de referencia: Nothing Phone (1) (D-022).

**Objetivo.** Demostrar que Gemma 4 E2B y EmbeddingGemma 2 funcionan en el teléfono de referencia con LiteRT-LM 0.18.1, y medir:
- `initialize()` en frío de cada engine;
- embedding de consulta en CPU y GPU (p50/p95) para cada variante (270M, 440M y 740M);
- TTFT con un prompt de 2.000 tokens;
- prefill y decode en tok/s;
- RSS pico.

También se introduce `EngineCoordinator`, que garantiza que nunca haya dos engines en GPU a la vez.

**Pantallas y mockups.** Ninguna de producto; solo diagnóstico interno.

**Secciones del informe.** Resumen ejecutivo y Configuración base · Distribución de modelos (fase de desarrollo: `adb push`) · Embeddings con EmbeddingGemma 2 (Prefijos, Truncación y normalización, API, Backend) · Generación (Modelo y runtime) · Memoria y ciclo de vida · Plan del POC, fase 0 · Puntos a verificar al empezar (longitud máxima de entrada con `ModelInfo`, diferencias CPU/GPU).

**Dependencias.** F01.

**Criterios de aceptación.**
1. Los modelos se cargan desde el directorio privado tras `adb push`, con el procedimiento documentado en `quickstart.md`. El SHA-256 se verifica contra el `lfs.oid` de HF antes de `initialize()`.
2. El embedding devuelve 256D con norma L2 = 1 ± 1e-5 y usa los prefijos constantes. La longitud máxima de entrada leída del modelo queda registrada.
3. Se mide el TTFT con 2.000 tokens. El embedding de consulta en CPU queda por debajo de 150 ms p95 (criterio de salida de la fase 0 del informe). Si no se cumple, se documenta y se decide con el usuario.
4. Un test unitario con fakes demuestra que `EngineCoordinator` impide dos engines GPU simultáneos.
5. Las cifras se recogen en `specs/<feature>/benchmarks.md`, con dispositivo y versiones. DECISIONS registra:
   - fichero de Gemma genérico o `-gpu` (G35);
   - backend de embeddings;
   - variantes candidatas para F05 (G36).
6. El diagnóstico no existe en la build de release (verificado).

**Riesgos.**
- Rendimiento insuficiente en el dispositivo.
- Delegado GPU no disponible (`libOpenCL.so`; `uses-native-library` opcional).
- Presión de memoria.
- Diferencias entre la documentación y el AAR.
- No hay teléfono de referencia definido: se fija en el clarify.

---

## F03 — Modelos y primer arranque

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada y aprobada por el usuario el 2026-10-26. Informe de cierre en `specs/003-models-onboarding/report.md`.
  - **Decisiones:** D-027 (onboarding y descarga), D-028 (UIDT y WorkManager) y D-029 (español por defecto).
  - **Teléfono de referencia:** descarga real de 2,75 GB con pausa, muerte del proceso, modo avión y reinicio (+3,8 % de bytes); prueba de Gemma en GPU en ≈ 19 s; arranque en frío de 820 ms (mediana).
  - **Abierto:** recorrido manual con TalkBack (T071).

**Objetivo.** Onboarding real: Bienvenida → Preparación (3 pasos, G10) → Inicio.
- Descarga reanudable y verificada de los dos modelos (D-011).
- Comprobación de espacio y RAM.
- `ModelRepository` con `ModelState`.
- Persistencia del onboarding completado.

**Pantallas y mockups.**
- `01-bienvenida.png` y `screens/01-welcome.md`.
- `02-preparacion.png` y `screens/02-onboarding.md`.
- Propuestas G02, G09, G10, G30 y G31.

**Secciones del informe.** Distribución de modelos (descarga a `noBackupFilesDir/models/`, `Range`, `StatFs`, SHA-256; nunca cargar con el hash incorrecto) · Memoria y ciclo de vida (aviso con RAM < 7,5 GB) · Privacidad y copias de seguridad.

**Dependencias.** F01 y F02 (ficheros, tamaños y hashes decididos).

**Criterios de aceptación.**
1. El primer arranque muestra `welcome`. Con el onboarding completado, la app abre `home`.
2. La descarga va con WorkManager, en primer plano y con notificación:
   - se reanuda con `Range` tras matar la app o perder la red;
   - comprueba el espacio con `StatFs` antes de empezar;
   - el progreso son bytes reales.
3. El SHA-256 se verifica antes de marcar «Listo». Un hash incorrecto descarta el fichero y muestra Error o Incompatible. Un test garantiza que nunca aparece «Listo» sin verificación.
4. Aviso de RAM < 7,5 GB y confirmación del tamaño de descarga (G31).
5. «Continuar» solo se habilita con ambos modelos en «Listo» y el almacenamiento preparado. El usuario sabe en cada momento por qué no puede avanzar.
6. El permiso `INTERNET` lo usa solo el paquete de descarga, con una comprobación automática de que ningún otro paquete abre conexiones.
7. El onboarding se recupera tras cerrar la app en cualquier paso.
8. La UI es fiel a los PNG 01 y 02; los estados no maquetados siguen la propuesta aprobada.
9. Accesibilidad: el progreso tiene descripción verbal y los estados se entienden sin color.
10. Permiso de notificaciones (G30).

**Riesgos.**
- Descargas de 2–3 GB (datos móviles, duración).
- Cambios en el repositorio de HF, mitigados fijando el commit.
- Límites de los servicios en primer plano.
- Almacenamiento insuficiente.
- Importación por SAF como alternativa (G02), pendiente de decidir.

---

## F04 — Núcleo de datos RAG

- **Tipo:** Técnica. Sin UI; diagnóstico de la base opcional en debug.
- **Estado:** ✅ cerrada: implementada y aprobada por el usuario el 2026-10-26. Informe de cierre en `specs/004-rag-data-core/report.md`.
  - **Decisiones:** D-030 (clarify) y D-031 (extracción de nativas).
  - **Teléfono de referencia:** golden del normalizador al 100 % (367 pares); golden de embeddings con coseno mínimo 1,000000 en CPU y 0,992992 en GPU; búsqueda vectorial con 10k fragmentos con p95 de 23,1 ms; 0 violaciones del modo estricto.

**Objetivo.** Construir la capa de datos del RAG:
- `schema.sql` en `assets/`, ampliado según G27;
- `RagDatabase` sobre `BundledSQLiteDriver` + SQLite-Vector: `vector_init` en cada conexión, WAL y un solo escritor;
- validación de `rag_meta`;
- normalizador `SpanishText` v1;
- `normalizer_golden` y `embedding_golden`;
- generador Python mínimo en `offline/` con `litert-lm-api` (D-012).

**Pantallas y mockups.** Ninguna.

**Secciones del informe.** Esquema SQLite · Capa de datos en Kotlin · Búsqueda léxica en español · Motor vectorial · Test de compatibilidad offline ↔ móvil · Pipeline offline (normalizador) · Puntos a verificar (coordenadas Maven, `.so`, persistencia de `vector_init` y de la cuantización).

**Dependencias.** F02 (embedder Kotlin y variante provisional).

**Criterios de aceptación.**
1. Se verifica que `vector.so` carga en dispositivo ARM64 y en el emulador x86_64, con un test instrumentado (consulta de versión).
2. Tests instrumentados de:
   - inserción y `vector_full_scan`;
   - FTS5 con `bm25(chunks_fts, 3.0, 1.0)`;
   - triggers `ai`/`ad`/`au`;
   - `ON DELETE CASCADE`;
   - idempotencia de `INSERT OR IGNORE`.
3. Si `rag_meta` no coincide en modelo, dimensiones, prefijos o versión del normalizador, la búsqueda se bloquea (test).
4. `SpanishText` pasa los ejemplos del informe (`clientes→client`, `luces→luz`, `R-1234` intacto) y `normalizer_golden.jsonl` (≥ 300 pares) con coincidencia exacta en Kotlin (JVM e instrumentado) y en Python.
5. `embedding_golden.jsonl` (50 textos, de consulta y de documento) se genera offline. El test instrumentado exige coseno ≥ 0,99 por par.
6. Todo el acceso a la base va por el dispatcher de SQLite; StrictMode en debug sin violaciones en Main.
7. `schema_version` con la estrategia de migración documentada.

**Riesgos.**
- Carga de una extensión sin prefijo `lib` y `extractNativeLibs`.
- Entry point de `addExtension` sin confirmar.
- Diferencias numéricas entre CPU y GPU.
- Python en macOS con extensiones de SQLite (`pysqlite3`).
- Filtrado de excluidos (G28), que se resuelve en F10.

---

## F05 — Harness offline de evaluación

- **Tipo:** Técnica. Python, sin UI.
- **Estado:** ✅ cerrada: implementada y aprobada por el usuario el 2026-10-26. Informe de cierre en `specs/005-offline-eval-harness/report.md` y cifras en `offline/reports/eval-report.md`. Decisiones D-033 (revisión por el asistente), D-034 (parámetros), D-035 (respuestas correctas 78,4 % frente al 80 %, aceptado con E2B) y D-036 (entradas de embedding largas).

**Objetivo.** Fijar en escritorio los parámetros de recuperación antes de indexar en el móvil:
- ~~variante de EmbeddingGemma 2~~: resuelta en F02 (D-025: 270M; las tres variantes dan vectores de texto idénticos);
- dimensión (128, 256, 512 o 768);
- tamaño de chunk (250, 350 o 500 tokens);
- cabecera contextual sí o no;
- normalización FTS;
- bloques finales (4, 6 u 8, con o sin vecinos), dentro del presupuesto de primer token (≤ 5 s con ≈ 2.000 tokens de prompt, D-026);
- E2B frente a E4B en calidad de respuesta.

**Pantallas y mockups.** Ninguna.

**Secciones del informe.** Pipeline offline (Python) · Evaluación (conjunto de prueba, métricas y umbrales, ablaciones) · Embeddings (truncación: la menor dimensión que no pierda más de un 1 % de nDCG@10 frente a 768).

**Dependencias.** F04 (`schema.sql`, `normalizer.py`, golden).

**Criterios de aceptación.**
1. `offline/` es reproducible:
   - dependencias con versiones fijadas (`litert-lm-api==0.18.1`);
   - construye un índice de evaluación con el mismo `schema.sql`, prefijos, cabecera, truncado y normalizador que la app.
2. Conjunto de prueba sobre un **corpus público versionado** (G37, D-032):
   - ~300 preguntas;
   - ~50 sin respuesta;
   - ~40 de seguimiento;
   - al menos 100 revisadas a mano;
   - relevantes identificados por `(content_hash, ordinal)`.
3. Informe con las métricas del informe:
   - Recall@20 híbrido ≥ 0,90;
   - Recall@6 ≥ 0,80;
   - MRR híbrido ≥ el de vector y el de FTS por separado;
   - respuestas correctas ≥ 80 %;
   - negativa correcta ≥ 85 %;
   - citas correctas ≥ 90 %;
   - y todas las ablaciones.
4. Las decisiones (variante, dimensión, chunk y bloques) se registran en DECISIONS y se propagan a las constantes Kotlin.

**Riesgos.**
- No hay corpus representativo.
- Generar preguntas «con un modelo grande» puede implicar un servicio externo con documentos de desarrollo: se decide con el usuario en el clarify.
- `litert-lm-api` en macOS (alternativa: Linux o CI).
- Tiempo de embebido en CPU.

---

## F06 — Importación y parsing

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada y aprobada por el usuario el 2026-10-26 en la rama `006-import-parsing`. Decisiones D-037 (importación) y D-038 (diseño). Informe de cierre en `specs/006-import-parsing/report.md`. Queda abierto el recorrido manual con TalkBack (T072).

**Objetivo.** Importar y preparar los documentos:
- selección múltiple con SAF;
- copia a almacenamiento privado;
- SHA-256 con detección de duplicados (G22);
- validación de tipo, tamaño, cifrado y presencia de texto;
- registro del documento como `pending`;
- parsers:
  - PDF con texto (páginas y detección de PDF sin texto);
  - TXT;
  - Markdown (encabezados `#` como ruta);
- `Chunker` según el informe, con `ordinal`;
- cola de importación observable.

**Pantallas y mockups.** `05-ingesta.png` y `screens/05-ingestion.md`. Ajustes G14 y G15.

**Secciones del informe.** Parsing y chunking (Extracción, Estrategia, Chunker) · Esquema SQLite (`documents`) · Decisión previa: de dónde sale el corpus (documentos del usuario).

**Dependencias.** F04.

**Criterios de aceptación.**
1. `OpenMultipleDocuments` con los MIME de PDF, TXT y MD, sin permisos de almacenamiento amplios.
2. Copia privada y SHA-256. Un duplicado se detecta y se comunica.
3. Los errores son por archivo y no bloquean el resto: cifrado, corrupto, demasiado grande, no admitido, sin texto o sin espacio.
4. Tests de parsers con fixtures (PDF de texto multipágina, PDF escaneado, PDF cifrado, TXT y MD con encabezados): producen `RawBlock` con página y ruta de encabezados.
5. Tests del chunker:
   - no corta a mitad de frase, salvo en párrafos que superan el máximo;
   - un encabezado abre un chunk nuevo;
   - solape ≈15 %;
   - el máximo duro queda por debajo de la entrada máxima del modelo, contando prefijo y cabecera;
   - `ordinal` consecutivo.
6. La cola es observable y se recupera tras recrear la Activity y tras la muerte del proceso.
7. «Iniciar indexación» encola el trabajo y navega a `index`.
8. La UI es fiel al PNG 05 con los ajustes aprobados.

**Riesgos.**
- ~~`pdfbox-android` sin mantenimiento (G38) y peso en el APK.~~ Resuelto con D-037, que usa la API de PDF del sistema. Riesgo nuevo: los dispositivos con Android 12–14 sin la extensión S ≥ 13 no leen PDF.
- PDF complejos (tablas, columnas).
- Memoria con PDF grandes (200 páginas).

---

## F07 — Indexación

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada en la rama `007-indexing` y aprobada por el usuario el 2026-10-27. Decisiones:
  - D-039 (clarify) y D-040 (plan);
  - D-041 (alarma de reanudación);
  - D-042 (cierre: presupuesto de búsqueda durante la indexación, notificación de la tanda y pantalla compacta).

  Informe de cierre en `specs/007-indexing/report.md`. Queda abierto el recorrido manual con TalkBack (T066).

**Objetivo.** `IngestWorker` (`CoroutineWorker` en primer plano, reanudable):
- extrae, fragmenta, embebe y persiste en lotes transaccionales de 64;
- progreso real;
- pausa y reanudación con checkpoints;
- pausa térmica;
- publicación atómica: el documento pasa a `done` solo al final;
- pantalla de estado.

**Pantallas y mockups.** `08-indexacion.png` y `screens/08-indexing.md`. Ajustes G21 y G29.

**Secciones del informe.** Ingesta en el dispositivo (`IngestWorker`) · Memoria y ciclo de vida (solo el engine de embedding cargado) · Capa de datos en Kotlin (`insertChunks`, `quant_dirty`) · Embeddings (backend de ingesta) · Evaluación (ritmo en chunks/s).

**Dependencias.** F02 y F06.

**Criterios de aceptación.**
1. Un PDF de 200 páginas se importa sin intervención. Si se mata la app a mitad, al relanzar continúa sin duplicar (`INSERT OR IGNORE` por `(document_id, ordinal)`). Es el criterio de salida de la fase 3 del informe.
2. El progreso son fragmentos hechos sobre el total real, o indeterminado si no se conoce el total. Se restaura al reabrir.
3. Pausar y Reanudar son coherentes con el backend. Hay Reintentar por documento.
4. Con `THERMAL_STATUS_SEVERE` el trabajo se pausa y la UI lo explica.
5. Con almacenamiento insuficiente se detiene de forma segura y se avisa.
6. El LLM no está cargado durante la ingesta (`EngineCoordinator`).
7. Un documento solo es buscable cuando está en `done`.
8. Se mide y reporta el ritmo de embedding (chunks/s) en CPU y GPU en el teléfono de referencia.
9. La UI es fiel al PNG 08; los estados no maquetados siguen el MD.

**Riesgos.**
- Tipo y cuota del servicio en primer plano según `targetSdk` (verificar `dataSync` en Android 15+).
- Ritmo de embedding lento: puede requerir un límite por importación.
- Batería y temperatura.

---

## F08 — Biblioteca y detalle de documento

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada en la rama `008-library-detail` y aprobada por el usuario el 2026-10-28. Decisiones D-043 (clarify), D-044 (plan) y D-045 (cierre: subtítulo del interruptor según el estado, vista previa recortada a 3 líneas y decisiones de implementación); propuestas de UI B1–B8 y D1–D11 aprobadas. Informe de cierre en `specs/008-library-detail/report.md`. Queda abierto el recorrido manual con TalkBack.

**Objetivo.**
- Lista paginada con búsqueda por nombre y filtros Todos / Indexados / Pendientes (G16).
- Switch de inclusión persistido.
- Detalle con metadatos reales.
- Vista previa del fragmento, resaltado si se llega desde una cita.
- Acciones: Reindexar, Quitar del índice (G17) y Eliminar con confirmación.

**Pantallas y mockups.** `06-biblioteca.png` y `screens/06-library.md`; `07-detalle-documento.png` y `screens/07-document-detail.md`.

**Secciones del informe.** Esquema SQLite (`ON DELETE CASCADE`) · Capa de datos (`loadChunks`) · Ingesta en el dispositivo (reindexado) · Privacidad y copias de seguridad (borrado).

**Dependencias.** F07.

**Criterios de aceptación.**
1. La paginación es fluida con al menos 1.000 documentos (test con datos sintéticos) y no carga fragmentos.
2. Búsqueda y filtros se combinan, y su estado se conserva al volver del detalle y al rotar.
3. El switch está deshabilitado si el documento no está indexado. El cambio se persiste y solo afecta a recuperaciones futuras.
4. Eliminar abre un diálogo que explica las consecuencias. Borra la copia privada, los chunks, los vectores (FTS por trigger) y los metadatos, y marca INT8 como `dirty`.
5. Quitar del índice borra chunks y vectores y conserva el documento original, que sigue reindexable.
6. Reindexar mantiene el índice anterior hasta sustituirlo de forma segura.
7. Un documento inexistente muestra un aviso y vuelve a Biblioteca sin crash.
8. TalkBack anuncia el nombre, el estado y la inclusión.
9. Estados de vacío, sin resultados, carga (skeleton) y error con reintento.
10. La UI es fiel a los PNG 06 y 07.

**Riesgos.**
- El reindexado seguro necesita espacio temporal doble.
- Coste del borrado masivo en FTS.

---

## F09 — Inicio

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada en la rama `009-home` y aprobada por el usuario el 2026-10-29. Decisiones D-046 (clarify), D-047 (plan) y D-048 (cierre: resumen bajo el pliegue sin cambios, «Completar configuración» como botón propio y decisiones de implementación); propuestas de UI P1–P5 aprobadas. Informe de cierre en `specs/009-home/report.md`. Queda abierto el recorrido manual con TalkBack.

**Objetivo.** Pantalla de Inicio con:
- estado real de los modelos;
- agregados reales de la biblioteca;
- accesos rápidos funcionales;
- tarjeta de indexación en curso;
- estado vacío.

**Pantallas y mockups.** `03-home.png` y `screens/03-home.md`.

**Secciones del informe.** Ninguna directa; los agregados se obtienen a través de los repositorios.

**Dependencias.** F03, F07 y F08.

**Criterios de aceptación.**
1. El chip «IA local disponible» solo aparece con ambos modelos verificados. Si no, se muestra «Configuración pendiente» con CTA a `setup`.
2. Los contadores salen de `COUNT`/`SUM` en SQL y son reactivos, sin cargar listas completas.
3. Con la biblioteca vacía: «Tu biblioteca está vacía» y «Importar primer documento», sin estadísticas.
4. Con una indexación activa: tarjeta con el estado y acceso a `index`.
5. Se llega a «Nuevo chat» con una sola interacción.
6. Los accesos Importar, Documentos e Índice son funcionales. Idiomas queda enlazado al marcador de debug hasta F12.
7. Responsive y accesible con texto grande.
8. Si falla la carga del resumen, se mantienen los accesos y se ofrece un reintento local.

**Riesgos.** Bajos.

---

## F10 — Recuperación híbrida

- **Tipo:** Técnica. Diagnóstico de búsqueda interno, solo en debug.
- **Estado:** ✅ cerrada: implementada en la rama `010-hybrid-retrieval` y aprobada por el usuario el 2026-10-29. Decisiones D-049 (clarify), con la enmienda 1.1.1 de la constitución, D-050 (plan), D-051 (revisión del orden: se mantiene D-034) y D-052 (cierre: el total p95 de 202–211 ms pasa a F14; INT8 + precarga en F14). Informe de cierre en `specs/010-hybrid-retrieval/report.md`.

**Objetivo.** `HybridRetriever`:
- embedding de consulta en CPU;
- vector top-50 + FTS top-50;
- RRF con k = 60;
- `ContextSelector`, con los parámetros de D-034 (D-049):
  - 4 bloques y como mucho 3 por documento;
  - presupuesto de 1.719 tokens estimados;
  - sin vecinos, aunque el selector los admite;
  - orden por documento y ordinal;
  - fusión sin solape;
- regla de seguimiento;
- revisión del orden dentro del presupuesto (D-035), evaluada en el harness de escritorio;
- filtro de documentos excluidos o no indexados (G28);
- validación de `rag_meta`.

**Pantallas y mockups.** Ninguna de producto.

**Secciones del informe.** Recuperación y selección de contexto (Flujo, Preguntas de seguimiento, Selección de contexto) · Búsqueda léxica en español · Motor vectorial (latencia y criterio INT8) · Capa de datos en Kotlin.

**Dependencias.** F05 (parámetros) y F07 (datos reales).

**Criterios de aceptación.**
1. Tests unitarios de RRF, `ContextSelector` (límite por documento, presupuesto, vecinos, fusión y orden) e `isFollowUp`, con los casos del informe.
2. Los documentos excluidos o que no están en `done` nunca aparecen en los resultados (test instrumentado).
3. El diagnóstico en debug muestra, para cada pregunta, los bloques con sus puntuaciones y las latencias de embedding, vector, FTS y total.
4. Se miden las latencias p50/p95 con 10k chunks en el teléfono de referencia. El p95 de una recuperación completa es ≤ 200 ms (≤ 1 s durante una tanda de indexación). Si el p95 vectorial supera 20 ms, se anota la decisión sobre INT8 (se implementa en F14).
5. El Recall@bloques (el «Recall@6» del informe, con los 4 bloques de D-034) sobre una muestra fija de 50 preguntas del harness queda a ±5 pp del de escritorio en la misma muestra. Se mide con una prueba instrumentada en el teléfono, sobre un índice temporal (D-049).
6. La revisión del orden (D-035) queda evaluada en el escritorio, con su decisión registrada.

**Riesgos.**
- Sobre-recuperación por las exclusiones.
- Diferencias entre el chunker de Kotlin y el de Python.
- FTS con consultas muy cortas.

---

## F11 — Chat con fuentes

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada en la rama `011-chat-sources` y aprobada por el usuario el 2026-11-01.
  - Specify, clarify, plan, tasks y analyze aprobados el 2026-10-29.
  - Implementación del 2026-10-29 al 2026-10-30, y fase del teléfono del 2026-10-30 al 2026-11-01: instrumentadas 660/660 en el A063, teclado real y recorrido del chat.
  - **Fallos de la fase del teléfono, corregidos:**
    - la lectura de citas cerraba la app en Android;
    - Gemma no se precargaba al terminar una tanda;
    - un error de la prueba de medida.
  - **Primer token:** por encima de 5 s, como excepción documentada (D-057).

  Decisiones:
  - D-053 (clarify; spec y propuestas de UI C1–C15 aprobadas);
  - D-054 (plan);
  - D-055 (analyze: turno tras una negativa como el escritorio, app oculta que interrumpe, C16–C17);
  - D-056 (plantilla v4 adoptada tras la revisión en el escritorio);
  - D-057 (primer token en el teléfono como excepción documentada; el límite de 1.500 tokens se evaluó y no se adoptó).

  Informe de cierre en `specs/011-chat-sources/report.md`.

  Spec, plan, contratos y tareas en `specs/011-chat-sources/`.

**Objetivo.** Conversación con documentos:
- estados `Retrieving`, `Generating`, `Completed`, `Cancelled` y `Failed`;
- streaming;
- `PromptBuilder` con idioma configurable (español por defecto, G25);
- detección de la negativa (G26);
- marcas `[n]` convertidas en citas reales, como fichas tocables numeradas igual que la lista (G11, D-053), y «Fuentes consultadas» solo con las fuentes citadas;
- copiar y cancelar (G12);
- persistencia local de las conversaciones; la pestaña Chat abre la última (D-053);
- menú «⋯» con «Nuevo chat» y «Borrar conversación» (G13, D-053);
- ciclo de vida del engine; durante una tanda de indexación, el chat espera (D-053);
- revisión de la plantilla del prompt en el escritorio (D-035, D-053);
- estados de bloqueo y de borde: modelo ausente, biblioteca vacía, cobertura parcial durante la indexación y fuente borrada.

**Pantallas y mockups.**
- `04-chat.png` y `screens/04-chat.md`.
- Cita → `07-detalle-documento.png` con el fragmento resaltado.
- Ajustes G11, G12, G13 y G39.

**Secciones del informe.** Generación (Modelo y runtime, Plantilla de prompt, una conversación de LiteRT-LM por pregunta) · Memoria y ciclo de vida · Recuperación (seguimiento) · Resumen ejecutivo (latencia dominada por el prefill).

**Dependencias.** F08 y F10.

**Criterios de aceptación.**
1. Buscar, generar, cancelar y error son estados distintos y se anuncian con TalkBack.
2. Ninguna cita sale sin un `documentId`/`chunkId` real. Un `[n]` fuera de rango se descarta (test).
3. Sin evidencia se muestra el mensaje del MD, sin citas.
4. Cita → detalle con el fragmento resaltado → Atrás vuelve al chat conservando el scroll.
5. Primer token ≤ 5 s de extremo a extremo con los bloques que fije F05 (prompt de ≈ 2.000 tokens como máximo) en el teléfono de referencia, medido y reportado (constitución IV, D-026). Si no se cumple, se ajustan bloques o tokens.
6. Cancelar detiene la generación y conserva el texto parcial, marcado como cancelado.
7. El teclado no tapa el compositor.
8. Prompts y respuestas no aparecen en los logs de release.
9. El engine se inicializa al abrir el chat y se libera en `onTrimMemory(UI_HIDDEN)`. Nunca hay dos engines en GPU.
10. La UI es fiel al PNG 04.

**Riesgos.**
- Calidad de E2B en español.
- Formato de citas inconsistente del modelo.
- TTFT.
- Interacción con la indexación en curso.

---

## F12 — Ajustes e Idiomas

- **Tipo:** Producto.
- **Estado:** ✅ cerrada: implementada en la rama `012-settings-languages` y aprobada por el usuario el 2026-11-02. Specify y clarify aprobados el 2026-11-01, con las propuestas de UI S1–S11; plan, tasks y analyze aprobados el 2026-11-01; implementación del 2026-11-01 al 2026-11-02, con la fase del teléfono el 2026-11-02 (instrumentadas 736/736 en el A063 y recorrido acordado, con Gemma respondiendo durante el cambio de idioma). Decisiones D-058 (clarify: «Español» por defecto y «Automático» solo tras evaluarlo; ficha de modelo de solo lectura en las dos filas; medida de la consulta entre idiomas en F12) y D-059 (plan: idioma de la app sin AppCompat, evaluaciones en el escritorio antes que la app) y D-060 (analyze: mínimo de respuestas correctas en las reglas en inglés). En la implementación, D-061 («Automático» no se ofrece; el chat responde siempre en español) y D-062 (medida entre idiomas: la tarjeta «Búsqueda multilingüe» cambia de texto y la búsqueda entre idiomas pasa a F14). Spec en `specs/012-settings-languages/spec.md`; informe de cierre en `specs/012-settings-languages/report.md`.

**Objetivo.**
- Ajustes con el estado real de los modelos y su detalle (G24).
- Preferencias (Idiomas, Índice y rendimiento).
- Privacidad: indicador informativo y Borrar historial.
- Idiomas: app en ES/EN con locales por app, e idioma de respuesta Automático/Español aplicado al prompt.

**Pantallas y mockups.** `09-configuracion.png` y `screens/09-settings.md`; `10-idiomas.png` y `screens/10-languages.md`. Ajustes G23, G24 y G25.

**Secciones del informe.** Distribución de modelos (estado y hash) · Privacidad y copias de seguridad · Generación (idioma de la plantilla de prompt).

**Dependencias.** F03 y F11.

**Criterios de aceptación.**
1. Las filas de modelo muestran su estado real, y el detalle incluye fichero, tamaño, SHA-256 abreviado, dimensiones y estado.
2. Cambiar el idioma de la app (ES/EN) no pierde el estado ni dispara una reindexación.
3. El idioma de respuesta (Automático/Español) se persiste y se aplica al prompt.
4. Borrar historial pide confirmación y elimina todas las conversaciones locales.
5. «Procesamiento 100% local» es informativo, no un switch.
6. Los ajustes persisten tras reiniciar (DataStore). Si falla la persistencia, se mantienen los valores previos.
7. Funciona la navegación a Idiomas y a Índice, y el acceso Idiomas de Inicio.
8. Traducciones EN revisadas de todos los strings.
9. La UI es fiel a los PNG 09 y 10.

**Riesgos.** Los locales por app con `ComponentActivity` requieren AppCompat o `LocaleManager` (API 33).

---

## F13 — Historial de chats

- **Tipo:** Producto, **sin mockup**. Requiere una propuesta de UI aprobada (D-007, G13).
- **Estado:** ✅ cerrada: implementada en la rama `013-chat-history` y aprobada por el usuario el 2026-11-03. Specify, clarify y wireframe aprobados el 2026-11-02, con las propuestas de UI H1–H11 (`specs/013-chat-history/wireframe.md`). Decisión D-063 (orden por última actividad con grupos por fecha, papelera en cada fila, abrir otra conversación detiene la respuesta en curso, nada se borra solo). Plan hecho el 2026-11-02, con la decisión D-064 (lista sin cambio de esquema, petición devuelta al chat, borrado bajo el cerrojo de la sesión y fichas de documentos borrados sin enlace). Tareas y analyze aprobados el 2026-11-03, con la decisión D-065 (correcciones de H5 y H7, y dos arreglos de F11). Implementada el 2026-11-03, con la fase del teléfono ese mismo día (instrumentadas 775/775 en el A063, medida de 1.000 conversaciones con la primera fila en 655 ms, y recorrido acordado). Informe de cierre en `specs/013-chat-history/report.md`. Spec en `specs/013-chat-history/spec.md`.

**Objetivo.** Lista de conversaciones previas: abrir, continuar y borrar conversaciones locales.

**Pantallas y mockups.**
- Punto de entrada: el menú «⋯» de `04-chat.png` (G13).
- Pantalla de lista **propuesta** con componentes `Lisdata*` y aprobada antes de implementarse.

**Secciones del informe.** Generación (continuidad con la pregunta y la respuesta anteriores truncadas).

**Dependencias.** F11 y F12.

**Criterios de aceptación.**
1. Wireframe con componentes `Lisdata*` aprobado por el usuario antes de `/speckit-plan`.
2. Lista paginada por fecha, con el título derivado de la primera pregunta.
3. Abrir una conversación restaura sus mensajes y citas. Las citas a documentos borrados muestran «Fuente ya no disponible».
4. Borrar una conversación pide confirmación. «Borrar historial» de Ajustes vacía la lista.
5. «Nuevo chat» crea una conversación nueva.

**Riesgos.**
- Inventar UI, mitigado con la aprobación previa.
- Crecimiento del almacenamiento.

---

## F14 — Rendimiento en dispositivo y decisión INT8

- **Tipo:** Técnica. Diagnóstico y benchmark internos, solo en debug.
- **Estado:** ⬜

**Objetivo.** Completar la tabla de benchmarks del informe con corpus de 10k, 50k y 100k chunks:
- búsqueda F32 frente a INT8 (p50/p95/p99), FTS e híbrida;
- TTFT con 4, 6 y 8 bloques;
- prefill y decode;
- extremo a extremo;
- RSS, heap, tamaño del `.db`, batería y temperatura;
- sesión sostenida de 20–30 minutos;
- E2B frente a E4B;
- palancas para bajar el primer token por debajo de 5 s (D-026): decodificación especulativa y plantilla más corta;
- lo que añaden F16–F20 (D-066):
  - búsqueda acotada por ámbito (F17);
  - vectores de página en F32 frente a INT8;
  - primer token con páginas en el contexto (F20);
  - embedding de consulta con la 440M (F19).

Con esos datos se decide: INT8 sí o no, número de bloques y perfil de modelo.

**Pantallas y mockups.** Ninguna de producto.

**Secciones del informe.** Evaluación → Benchmarks en el dispositivo · Motor vectorial (criterio) · Capa de datos (`rebuildInt8`, preload, `quant_dirty`) · Plan del POC, fase 4.

**Dependencias.** F10, F11, F17 y F20 (D-066).

**Dato de partida (F04):** con 10k fragmentos sintéticos, la búsqueda vectorial exacta da un p95 de 23,1 ms en el teléfono de referencia, ya por encima de los 20 ms del criterio 2 (`specs/004-rag-data-core/report.md` §4).

**Dato de partida (F10, D-052):** con 10.000 fragmentos, la recuperación completa en el teléfono de referencia da:
- total p95 de 202–211 ms en tres tandas, frente a los 200 ms de SC-003 de F10;
- vectorial p95 de unos 52 ms, y 67–68 ms con 4.300 fragmentos ocultos;
- embedding de consulta p95 de 125,6 ms medido solo y de 144–151 ms dentro de la búsqueda.

F14 implementa INT8 + precarga (criterio 2) y vuelve a medir el total frente a 200 ms.

**Palanca de calidad (D-051):** reordenar los 20 primeros de la fusión con α = 0,5 subió el Recall@bloques de 0,889 a 0,903 en el escritorio, por debajo de la regla de adopción de F10. Queda como opción barata si se revisa el contexto.

**Dato de partida (F11, D-057):** el primer token del chat en el teléfono de referencia queda por encima de los 5 s, como excepción documentada.
- **Cifras:** p95 de 5,2 s en una medida y de 5,8 s en frío en otra; con el teléfono caliente (estado térmico «moderado»), 6,6 s.
- **Qué pesa:** el primer token es ≈ 0,27 s más 3,1 s por cada 1.000 tokens estimados del prompt.
- **Límite de 1.500 tokens:** evaluado y descartado (80,5 % de respuestas correctas, con el p95 proyectado en unos 5,3 s).

F14 evalúa las palancas y mide con el teléfono en reposo entre preguntas y en sesión sostenida. La prueba de partida es `ChatBenchmarkTest`, que escribe una línea por pregunta.

**Palanca de calidad (F12, D-062):** la búsqueda entre idiomas. Con documento y pregunta en el mismo idioma, el inglés rinde como el español (80,0 % de respuestas correctas). Con idiomas distintos baja:
- preguntas en inglés sobre documentos en español: 50,9 %, con la búsqueda acertando en el 57,9 %;
- preguntas en español sobre documentos en inglés: 64,0 %, con la búsqueda acertando en el 78,0 %.

Opciones a evaluar: normalizar la búsqueda por palabras según el idioma del documento (palabras vacías y raíces del inglés) y dar más peso a la búsqueda por significado cuando los idiomas no coinciden. Se mide otra vez con `crosslang-review`. Si mejora lo bastante, la tarjeta «Búsqueda multilingüe» puede volver a prometer la consulta entre idiomas.

**Criterios de aceptación.**
1. `benchmarks.md` con cifras reales, dispositivo y versiones, que sustituyen los presupuestos del informe.
2. Si el p95 vectorial supera 20 ms: INT8 + preload implementado, con Recall@20 de INT8 frente a F32 ≥ 0,97. Si no, la decisión de quedarse en F32 queda documentada.
3. En la sesión sostenida se mantiene el primer token ≤ 5 s de extremo a extremo (constitución IV), o hay una decisión documentada.
4. Todas las decisiones se registran en DECISIONS.

**Riesgos.**
- Generar un corpus sintético de 100k que sea realista. Una base de benchmark construida offline y cargada con `adb` solo para medir es tooling de desarrollo y no se distribuye (D-002).
- *Throttling* térmico.

---

## F15 — Pulido y release

- **Tipo:** Producto.
- **Estado:** ⬜

**Objetivo.** Cerrar `docs/disegn_app/docs/04-qa-checklist.md`:
- estados vacío y error en todas las pantallas;
- accesibilidad: TalkBack, 48 dp, contraste y `fontScale` al 200 %;
- modo oscuro revisado, con los tres modos del tema (F16);
- modo avión y test de bloqueo de red tras el onboarding;
- logs sin contenido privado;
- lecturas de disco en el hilo principal al arrancar, detectadas por StrictMode en F04 (`AppDirs`, DataStore y `DefaultModelDownloadController`);
- reglas de R8 para LiteRT-LM, SQLite-Vector y el parser de PDF;
- firma de release sin secretos en el repo;
- avisos de licencias OSS (Lucide ISC, Google Sans OFL y librerías);
- versión.

**Pantallas y mockups.** Las 10 pantallas, además del checklist QA.

**Secciones del informe.** Privacidad y copias de seguridad (test de bloqueo de red) · Distribución de modelos (evaluar Play Asset Delivery).

**Dependencias.** F01–F20.

**Criterios de aceptación.**
1. Checklist QA completo, con evidencias.
2. La build de release minificada funciona en el teléfono de referencia.
3. Un test instrumentado falla si hay tráfico de red después del onboarding.
4. La auditoría de accesibilidad no deja bloqueantes.
5. Las diferencias residuales frente a los mockups quedan documentadas.

**Riesgos.**
- R8 rompiendo JNI.
- Tamaño del APK.
- Política de distribución de los modelos en producción.

---

## F16 — Apariencia: tema e icono de Ajustes

- **Tipo:** Producto. El selector de tema no tiene mockup (G45): su UI se propone y se aprueba antes del plan (constitución VII).
- **Estado:** 🟨 en curso en la rama `016-appearance-theme`. Specify, clarify y wireframe aprobados el 2026-11-03, con las propuestas A1–A9 (`specs/016-appearance-theme/wireframe.md`) y la decisión D-067: el tema se aplica al guardar, como en Idiomas; la fila «Tema» va entre «Idiomas» e «Índice y rendimiento» con el icono `sun-moon`; y el engranaje es el Lucide `settings`. Plan hecho el 2026-11-03, con la decisión D-068: aplica el modo oscuro por app del sistema, la app guarda una copia para mostrar el valor y reconcilia al arrancar, y «Preferencias guardadas» solo lo anuncia TalkBack, lo que corrige A3. Tareas y analyze aprobados el 2026-11-03, con la decisión D-069 (A3 confirmado, emulador de API 32, reconciliación que aplica «Sistema» si la lectura falla, anuncio tras un margen y el modo del teléfono nunca leído de `Resources.getSystem()`). Implementada del 2026-11-03 al 2026-11-04, con la fase del teléfono el 2026-11-04: instrumentadas 815/815 en el A063, sin destello en el arranque y con indexación y respuesta durante el cambio. Informe de cierre en `specs/016-appearance-theme/report.md`, pendiente de la revisión del usuario. Spec en `specs/016-appearance-theme/spec.md`.

**Objetivo.**
- **Icono de Ajustes:**
  - un engranaje (Lucide `settings`) en lugar del sol, en la barra inferior, la cabecera de Inicio y el catálogo;
  - se regenera con `tools/icons/` (`icons.txt` y `lucide_to_vector.py`);
  - se corrige además el comando de `specs/001-foundations-design-system/quickstart.md`, que debe pasar la carpeta `icons` del paquete.
- **Tema de la app:** Sistema (por defecto), Claro u Oscuro.
  - Fila «Tema» en Preferencias de Ajustes, con el valor actual, que abre una pantalla como Idiomas.
  - Se aplica a toda la app, a la pantalla de arranque y a las barras del sistema.
  - Persistencia: preferiblemente `UiModeManager.setApplicationNightMode` (API 31). La guarda el sistema y no exige leer de disco al arrancar. Se verifica en el plan (constitución II).

**Pantallas y mockups.**
- `09-configuracion.png` y `screens/09-settings.md`: fila nueva en Preferencias.
- `10-idiomas.png` como patrón de la pantalla de selección.
- Propuesta G45.

**Secciones del informe.** Ninguna directa.

**Dependencias.** F12.

**Criterios de aceptación.**
1. Wireframe con componentes `Lisdata*` aprobado por el usuario antes de `/speckit-plan`.
2. El engranaje sustituye al sol en todos sus usos. `INVENTARIO.md` §2.6 y G19 quedan actualizados.
3. El tema elegido se aplica al momento y persiste tras reiniciar. «Sistema» sigue al ajuste del teléfono.
4. Cambiar el tema no pierde el estado (pestaña, chat, respuesta en curso) ni dispara una reindexación.
5. La pantalla de arranque y las barras del sistema respetan el tema elegido, sin destello del otro tema.
6. Si falla el guardado, se mantiene el valor anterior y se avisa, como en el criterio 6 de F12.
7. TalkBack anuncia la opción seleccionada. Texto al 200 % sin recortes y contraste en los dos temas.
8. Textos en ES y EN.

**Riesgos.**
- La actividad se recrea al cambiar `uiMode`, también con una respuesta en curso. F12 ya lo resolvió para el idioma.
- `setApplicationNightMode` puede comportarse distinto en Android 12 y en 13+.

---

## F17 — Carpetas y ámbito del chat

- **Tipo:** Producto, **sin mockup**. Requiere una propuesta de UI aprobada (G46, constitución VII).
- **Estado:** ⬜

**Objetivo.**
- **Carpetas de un solo nivel:** crear, renombrar y eliminar. Cada documento está en una carpeta o en «Sin carpeta», donde quedan los documentos actuales.
- **Biblioteca:**
  - carpetas con su recuento;
  - dentro de cada una, la lista actual con búsqueda y filtros;
  - mover un documento a otra carpeta.
- **Ingesta:** carpeta de destino de la tanda.
- **Chat, con ámbito por conversación:**
  - una ficha encima del compositor con el ámbito («Toda la biblioteca», «Legal», «Legal + 2 documentos»…);
  - un selector de carpetas completas y de documentos sueltos de carpetas distintas;
  - el ámbito se guarda con la conversación y se conserva al reabrirla desde Historial;
  - la disponibilidad del chat y la «cobertura parcial» se calculan dentro del ámbito;
  - «Incluir en RAG» sigue siendo la exclusión global.
- **Recuperación:**
  - `RetrievalRequest` lleva el ámbito;
  - se aplica en la búsqueda vectorial, en la FTS y en la carga de candidatos y vecinos, además del filtro `status = 'done' AND included = 1` (G28);
  - la sobre-recuperación top-(*k* + *m*) cuenta también los fragmentos fuera del ámbito.
- **Datos:**
  - `rag.db` v4: tabla `folders`, con ids que no se reutilizan y nombre único normalizado, y `documents.folder_id`;
  - `chat.db` v2: el ámbito de la conversación, con la guarda de ids reutilizados de D-065.

**Pantallas y mockups.**
- `06-biblioteca.png`, `07-detalle-documento.png`, `05-ingesta.png` y `04-chat.png` como base.
- Los elementos nuevos siguen la propuesta G46.

**Secciones del informe.** Esquema SQLite · Capa de datos en Kotlin (migraciones) · Recuperación y selección de contexto (filtros) · Motor vectorial (coste del filtro).

**Dependencias.** F08, F11 y F13.

**Criterios de aceptación.**
1. Wireframe con componentes `Lisdata*` aprobado por el usuario antes de `/speckit-plan`.
2. Migraciones:
   - `rag.db` 3 → 4 y `chat.db` 1 → 2, con pruebas instrumentadas;
   - un fallo deja el fichero intacto;
   - el harness `offline/` sigue construyendo índices con el mismo `schema.sql`.
3. Se crean, renombran y eliminan carpetas, y se rechazan los nombres vacíos o repetidos. Eliminar pide confirmación y explica qué pasa con sus documentos (se decide en el clarify).
4. Un documento fuera del ámbito nunca aparece en los resultados (prueba instrumentada). Con «Toda la biblioteca», los resultados son idénticos a los de hoy.
5. Ámbito guardado:
   - se restaura al abrir la conversación desde Historial;
   - una carpeta o un documento borrados salen del ámbito, con un aviso;
   - un ámbito sin documentos indexados se explica y ofrece cambiarlo.
6. Los recuentos por carpeta salen de consultas agregadas, y la biblioteca sigue fluida con 1.000 documentos.
7. Se mide en el teléfono la recuperación con un ámbito pequeño sobre 10k fragmentos. Si el p95 supera el de F10, queda anotado para F14.
8. TalkBack anuncia la carpeta, su recuento y el ámbito. Texto al 200 % y objetivos de 48 dp.

**Riesgos.**
- Con un ámbito pequeño, *m* se acerca al total y `vector_full_scan` recorre casi toda la tabla.
- Los ids de carpetas y documentos se guardan en `chat.db` sin clave ajena, porque `rag.db` es otro fichero.
- Complejidad del selector en pantallas pequeñas.

---

## F18 — Viabilidad del RAG visual (fase 0 visual)

- **Tipo:** Técnica. Diagnóstico interno solo en debug, y harness en el escritorio.
- **Estado:** ⬜

**Objetivo.** Demostrar en el teléfono de referencia que el RAG visual es viable y fijar en el escritorio sus parámetros, antes de construirlo. LiteRT-LM 0.18.1 ya trae la API (verificado en el AAR):
- en los embeddings, `InputData.Image` y `visionTokensPerImage`;
- en el LLM, `Content.ImageBytes`, `visionBackend`, `maxNumImages` y `ExperimentalFlags.visualTokenBudget`.

En el teléfono:
- **Gemma:**
  - `ModelInfo` de `gemma-4-E2B-it.litertlm`: si admite visión y su presupuesto visual máximo;
  - con `visionBackend` y `Content.ImageBytes`: primer token con 1, 2 y 3 páginas a 280 y 560 tokens visuales, carga del codificador de visión y RSS pico;
  - si lee tablas, cifras y texto pequeño.
- **EmbeddingGemma 2 de 440M:**
  - `embedding_golden` con coseno ≥ 0,99, y vectores de texto idénticos a los de la 270M;
  - consulta en CPU con p95 < 150 ms;
  - arranque y RSS;
  - embedding de una página en CPU y GPU, con 70 y 140 tokens por imagen.
- **`PdfRenderer`:** resolución, tiempo y peso por página (WebP o JPEG). También la decodificación y el escalado de JPG y PNG.
- Nunca dos engines en GPU: ni el embedder con visión junto al LLM.

En el escritorio (`offline/`):
- un rasterizador con versión fijada: pypdfium2, que usa el mismo motor pdfium que `PdfRenderer`;
- un conjunto de prueba visual sobre un corpus público versionado, con tablas, gráficos, PDF escaneados e imágenes:
  - unas 100 preguntas, parte revisadas a mano;
  - relevancia por (documento, página);
- ablaciones: solo texto, solo visual y ambos (RRF de tres listas);
- respuestas con imágenes de página frente a respuestas con bloques de texto.

Con esos datos se fijan:
- la resolución de página;
- los tokens por imagen del embedder;
- la dimensión de los vectores de página (256 o 512);
- el número de páginas en el contexto;
- el presupuesto visual de Gemma;
- la regla de fusión y de duplicados.

**Pantallas y mockups.** Ninguna de producto.

**Secciones del informe.** Embeddings (variante y Matryoshka) · Generación (Modelo y runtime) · Memoria y ciclo de vida · Evaluación (ablaciones) · Parsing y chunking (PDF escaneados).

**Dependencias.** F05, F10 y F11.

**Criterios de aceptación.**
1. `specs/<feature>/benchmarks.md` con cifras reales, dispositivo y versiones.
2. La 440M pasa `embedding_golden` en el teléfono y sus vectores de texto coinciden con los de la 270M, así que el índice actual sigue valiendo. Si no, se documenta y se decide con el usuario.
3. El primer token con páginas queda medido. Si supera el presupuesto (D-026, con la excepción D-057), se decide con el usuario cuántas páginas entran y con qué presupuesto visual.
4. Informe de evaluación con las ablaciones, y los parámetros registrados en DECISIONS.
5. Propuesta de enmienda de la constitución (III): la página como unidad citable, la 440M y la dimensión de los vectores de página.
6. Queda registrada la decisión de seguir o no con F19–F20.
7. El diagnóstico no existe en la build de release.

**Riesgos.**
- El primer token con imágenes en la Adreno 642L.
- La memoria con el codificador de visión cargado.
- 70 o 140 tokens por imagen pueden no bastar para páginas con mucho texto.
- Las licencias del corpus visual y la generación de preguntas sobre imágenes (D-033).
- Diferencias de rasterizado entre el escritorio y el móvil.

---

## F19 — Indexación visual

- **Tipo:** Producto, **sin mockup**. Requiere una propuesta de UI aprobada (G47, constitución VII).
- **Estado:** ⬜

**Objetivo.**
- **Cambio de modelo de embeddings (D-066):**
  - la 440M entra en el onboarding en lugar de la 270M, con los tamaños de descarga y G31 actualizados;
  - en una instalación existente se descarga la 440M y el índice actual se valida por la huella del codificador de texto (enmienda de la constitución III, verificada con `embedding_golden`), sin reindexar;
  - la 270M se borra solo después de esa validación;
  - durante la transición, el texto sigue buscable.
- **Importación por modo:**
  - cada PDF se indexa como Texto, Visual o Ambos, con el valor por defecto en Ajustes;
  - las imágenes JPG y PNG se importan siempre en Visual;
  - los PDF escaneados se admiten en Visual (hoy se rechazan por no tener texto).
- **Ingesta visual:**
  - cada página se renderiza a una imagen privada en `noBackupFilesDir`;
  - las imágenes se embeben por lotes, con checkpoint por página, progreso real por páginas y pausas térmica y por espacio;
  - el documento solo se publica al terminar, como en F07.
- **Datos:** `rag.db` v5, con:
  - la tabla de páginas y su vector, con su propio `vector_init`;
  - `documents.index_mode`;
  - el espacio de páginas en `rag_meta`.
- **Biblioteca y detalle:**
  - el modo se ve;
  - se puede reindexar en otro modo;
  - eliminar y quitar del índice borran también las imágenes de página.

**Pantallas y mockups.**
- Como base: `05-ingesta.png`, `06-biblioteca.png`, `07-detalle-documento.png`, `08-indexacion.png` y `09-configuracion.png`, con la propuesta G47.
- `02-preparacion.png`, para la descarga de la 440M.

**Secciones del informe.** Distribución de modelos · Parsing y chunking (PDF escaneados) · Ingesta en el dispositivo · Esquema SQLite · Motor vectorial · Privacidad y copias de seguridad (borrado).

**Dependencias.** F17 y F18, si F18 da el sí a F19–F20.

**Criterios de aceptación.**
1. Wireframe con componentes `Lisdata*` aprobado por el usuario antes de `/speckit-plan`.
2. Cambio de modelo:
   - una instalación nueva descarga y verifica la 440M en el onboarding;
   - una existente cambia de modelo sin reindexar el texto y sin perder la búsqueda en ningún momento (pruebas instrumentadas de la migración).
3. Un PDF de 200 páginas en Visual se importa sin intervención. Si se mata la app a mitad, al relanzar continúa sin duplicar páginas.
4. Un PDF escaneado y una imagen suelta se indexan en Visual. En Texto, el PDF escaneado sigue explicando que no tiene texto.
5. Se comprueba el espacio antes y durante la ingesta visual.
6. Eliminar, quitar del índice y reindexar dejan coherentes el archivo, los fragmentos, las páginas, sus imágenes y los vectores (prueba instrumentada).
7. El LLM no está cargado durante la ingesta, y nunca hay dos engines en GPU.
8. Se miden en el teléfono el ritmo de indexación visual (páginas/s) y el espacio por página.
9. Accesibilidad: TalkBack anuncia el modo, y el texto al 200 % no se recorta.

**Riesgos.**
- El espacio que ocupan las imágenes de página.
- El tiempo de indexación y la temperatura.
- La descarga de 388 MB para los usuarios existentes.
- La migración de un índice con datos del usuario.

---

## F20 — Chat con páginas

- **Tipo:** Producto, **sin mockup**. Requiere una propuesta de UI aprobada (G47, constitución VII).
- **Estado:** ⬜

**Objetivo.**
- **Recuperación con tres listas:**
  - vector de fragmentos, FTS y vector de páginas, fusionadas con RRF;
  - respeta el ámbito de F17 y el filtro de G28;
  - la misma consulta sirve para fragmentos y páginas, porque comparten espacio vectorial.
- **Selección de contexto mixta:**
  - bloques de texto e imágenes de página, cada imagen con su coste en tokens;
  - si entra una página, no entran también sus fragmentos.
- **Prompt multimodal (plantilla v5):** `Contents` con texto e imágenes numeradas `[n]`, y Gemma con `visionBackend`.
- **Citas a página:**
  - cada `[n]` corresponde a un documento y una página reales;
  - la ficha abre el detalle con la imagen de la página;
  - «Fuentes consultadas» muestra la página;
  - Historial restaura las citas a página. Si la página ya no existe, muestra «Fuente ya no disponible».
- **Diagnóstico de búsqueda en debug,** con las tres listas.

**Pantallas y mockups.** `04-chat.png` y `07-detalle-documento.png` como base, con la propuesta G47.

**Secciones del informe.** Recuperación y selección de contexto · Generación (Plantilla de prompt) · Memoria y ciclo de vida.

**Dependencias.** F19.

**Criterios de aceptación.**
1. Wireframe con componentes `Lisdata*` aprobado por el usuario antes de `/speckit-plan`.
2. Ninguna cita sale sin un documento y una página o un fragmento reales. Un `[n]` fuera de rango se descarta (test).
3. Se mide en el teléfono el primer token de extremo a extremo con páginas, frente a lo que fijó F18.
4. La calidad sobre el conjunto visual de F18 cumple los umbrales que fijó F18.
5. Con documentos de solo texto, las cifras del harness y el comportamiento del chat no cambian.
6. Las imágenes de página tienen descripción para TalkBack: documento y página.
7. Prompts, respuestas e imágenes no aparecen en los logs de release.
8. Nunca hay dos engines en GPU, y el codificador de visión se libera con el LLM.

**Riesgos.**
- El primer token con imágenes.
- Citas inconsistentes del modelo sobre imágenes.
- La memoria.

---

## Backlog (sin feature asignada)

| Elemento | Origen | Motivo |
|---|---|---|
| Exportar y restaurar biblioteca | Encargo · informe (Privacidad) | Sin mockup; decisión del usuario D-007 |
| OCR de PDF escaneados (ML Kit) | Encargo · informe (Parsing) | Sin mockup; el MD de ingesta lo deja fuera del MVP; D-007. Con el modo visual (F19–F20) el chat responde sobre escaneados, pero no los hace buscables por palabras (D-066) |
| Importación de HTML (Jsoup) | Encargo · informe (Parsing) | Sin mockup; D-007 |
| DOCX | Informe (Parsing: «convertir offline en el POC») | Apache POI pesa mucho en Android |
| Protección de biblioteca (biometría/PIN) | `09-settings.md` | No está en el PNG (G23) |
| Gestionar almacenamiento (desglose) | `09-settings.md` | No está en el PNG (G23) |
| Preferencia «Solo mientras carga» y sección de recursos | `08-indexing.md`, `09-settings.md` | No está en los PNG (G21) |
| Información, licencias y diagnóstico en Ajustes | `09-settings.md` | No está en el PNG. Los avisos de licencias mínimos van en F15 (G23) |
| Português (app), English (respuestas) y detección de idioma | `10-languages.md` | No está en el PNG (G25) |
| Drag & drop en la ingesta | PNG 05 | No es nativo en móvil (G14) |
| Importar modelos por SAF | `02-onboarding.md` | Se decide en el clarify de F03 (G02) |
| FTS trigram como tercera lista | Informe (Búsqueda léxica) | Solo si la evaluación lo justifica (F05) |
| Play Asset Delivery y AICore | Informe (Distribución) · ficha de Gemma 4 | Se reevalúan antes de la release de producción |
