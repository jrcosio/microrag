<!--
Sync Impact Report
- Versión: 1.0.0 → 1.1.0 (MINOR). Cambia de forma material un presupuesto del principio IV y se resuelven los TODO diferidos de la 1.0.0 con decisiones ya aprobadas.
- Principios modificados (sin cambios de título):
  - IV. Rendimiento y recursos: el presupuesto «primer token ≤ 3 s con 6 bloques» pasa a «≤ 5 s de extremo a extremo con un prompt de ≈ 2.000 tokens (6 bloques)», con la cifra medida en F02 (G44, opción (b) elegida por el usuario; DECISIONS D-026).
- Secciones modificadas:
  - Restricciones técnicas: minSdk 31 (D-017); teléfono de referencia Nothing Phone (1) (D-022); fichero genérico de Gemma 4 E2B en GPU (D-023); EmbeddingGemma 2 de 270M de solo texto (D-025).
- Secciones añadidas: ninguna.
- Secciones eliminadas: ninguna.
- Plantillas dependientes:
  - ✅ .specify/templates/plan-template.md: su «Constitution Check» lee este fichero en tiempo de ejecución; no requiere cambios.
  - ✅ .specify/templates/spec-template.md: sin referencias a presupuestos concretos; no requiere cambios.
  - ✅ .specify/templates/tasks-template.md: sin referencias a presupuestos concretos; no requiere cambios.
  - ✅ .claude/skills/speckit-*: sin referencias a presupuestos concretos.
- Documentos dependientes:
  - ✅ docs/roadmap/FEATURES.md: criterios de TTFT de F11 y F14 alineados con el nuevo presupuesto.
  - ✅ docs/roadmap/DECISIONS.md: D-026; G44 sale de «Pendientes de decisión».
  - ✅ docs/roadmap/INVENTARIO.md: G44 resuelto.
- TODO diferidos: ninguno. Los de la 1.0.0 quedan resueltos (minSdk por D-017 y teléfono de referencia por D-022).
-->

# Constitución de MicroRAG

MicroRAG es una app Android nativa de inteligencia documental RAG que se ejecuta íntegramente en el dispositivo. Su marca visual es LISDATA.

Fuentes de verdad:
- `docs/disegn_app/`: design system, especificaciones `.md` y PNG de pantallas.
- `docs/RAG_Movil/informe-rag-movil-v2.md`: informe técnico del motor RAG.
- `docs/roadmap/INVENTARIO.md` y `docs/roadmap/DECISIONS.md`: inventario y registro de decisiones.

## Core Principles

### I. 100 % on-device y privacidad (NO NEGOCIABLE)

- Durante el uso normal la app MUST NOT hacer ninguna llamada de red.
- La **única** descarga permitida es la de los modelos (Gemma 4 E2B y EmbeddingGemma 2):
  - desde Hugging Face `litert-community`, con la URL fijada a un commit concreto;
  - verificada por SHA-256 **antes** de cargarla. Un modelo cuyo hash no coincide nunca se carga.
- El acceso a red MUST quedar aislado en el componente de descarga de modelos. Ningún otro paquete lo usa.
- `android:allowBackup="false"`.
- Modelos, base de datos e índices, copias de los documentos importados y conversaciones viven en `noBackupFilesDir` o en almacenamiento privado de la app.
- Prohibido:
  - analítica y telemetría;
  - SDKs cloud;
  - WebView con contenido remoto;
  - deep links externos.
- No se registra contenido privado en logs: prompts, texto de documentos, respuestas ni nombres de archivo en builds de release.
- La importación usa exclusivamente Storage Access Framework, con permisos mínimos.

*Razón:* la propuesta de valor es «tu conocimiento, solo tuyo». Una sola fuga de red o de backup invalida el producto.

### II. Stack y arquitectura

- Kotlin y Jetpack Compose con Material 3, tematizado **solo** con los tokens de `docs/disegn_app` mediante `LisdataTheme`.
- MVVM por capas: `Screen → ViewModel → UseCase → Repository (interfaz) → implementación local`.
- Estado con coroutines y `Flow`/`StateFlow`, eventos de UI unidireccionales, Koin para DI, WorkManager para el trabajo largo y Navigation Compose.
- Un único módulo Gradle `:app` con paquetes por capa:
  - `core.designsystem` y `core.navigation`;
  - `domain`, con lógica pura y testeable en la JVM;
  - `data`;
  - `rag.runtime`;
  - `feature.*`.
- La UI y los ViewModels MUST NOT importar JNI, SDKs de inferencia (LiteRT-LM) ni APIs de SQLite. Siempre pasan por interfaces de repositorio o de dominio.
- Versiones, coordenadas Maven y nombres de librerías nativas (`.so`):
  - se verifican en Maven Central, en Google Maven o en el AAR concreto;
  - **nunca** se suponen;
  - se registran en el `plan.md` de cada feature.

*Razón:* los adaptadores nativos tienen que poder cambiar sin reescribir pantallas, y una versión supuesta rompe builds o compatibilidad.

### III. RAG según el informe técnico (NO NEGOCIABLE)

**Almacenamiento:**
- SQLite bundled (`androidx.sqlite:sqlite-bundled`) es la **fuente de verdad**.
- FTS5, INT8 y cualquier ANN futuro son **índices reconstruibles**.
- El espacio vectorial se registra en `rag_meta`: `embedding_model_sha256`, `embedding_dims`, `embedding_doc_prefix`, `embedding_query_prefix`, `normalizer_version`, `schema_version` y `quant_dirty`.
- `rag_meta` se valida al abrir la base. Si no coincide con la configuración de la app, **no se busca**: se reindexa de forma explícita.

**Embeddings:**
- Prefijos de EmbeddingGemma 2 exactamente como los documenta LiteRT-LM:
  - documento: `task: search result | text: `;
  - consulta: `task: search query | text: `.
- Se recortan los espacios antes de añadir el prefijo. Los prefijos son constantes compartidas y también se guardan en `rag_meta`.
- Truncado Matryoshka a **256D** y re-normalización **L2** en código propio.
- La cabecera contextual («documento › ruta de encabezados») va solo en el texto embebido, nunca en `content`.

**Búsqueda:**
- El normalizador español es idéntico en Kotlin y Python, garantizado por `normalizer_golden`. Aplica:
  - minúsculas;
  - eliminación de tildes;
  - stopwords;
  - stemmer ligero;
  - y deja intactos los tokens con dígitos.
- Búsqueda híbrida vector + FTS5 fusionada con RRF (k = 60), con 50 + 50 candidatos.
- Contexto de 4 a 6 bloques: ~2.200 tokens, máximo 3 por documento y expansión de vecinos.

**Respuesta:**
- El prompt lleva fuentes numeradas, citas `[n]` y una negativa explícita.
- Toda cita mostrada MUST ser trazable a un `documentId` y un `chunkId` reales. **Nunca** se fabrican citas, páginas ni nombres de archivo.

Los parámetros de este principio solo cambian con una evaluación documentada (harness offline o benchmark) y una entrada en DECISIONS.

*Razón:* la compatibilidad del espacio vectorial y la trazabilidad de las citas son lo que hace fiable el RAG.

### IV. Rendimiento y recursos

- Inferencia y acceso a base de datos SIEMPRE fuera del hilo principal, en dispatchers dedicados de un solo hilo: embeddings, LLM y SQLite. Hay una sola conexión de escritura.
- **Nunca dos engines en GPU a la vez.**
  - El LLM se libera durante la ingesta y en `onTrimMemory` con nivel `TRIM_MEMORY_UI_HIDDEN` o superior.
  - El embedding de consulta va en CPU salvo que una medición justifique otra cosa.
- Presupuestos en el teléfono de referencia:
  - primer token ≤ 5 s de extremo a extremo (de enviar la pregunta a ver el primer token) con un prompt de ≈ 2.000 tokens (6 bloques). Base medida en F02: TTFT de 4,53 s en el motor y 4,75 s de reloj con 1.986 tokens, más el embedding de consulta y la búsqueda (D-026). F05 fija el número de bloques dentro de este presupuesto y F14 evalúa palancas para bajarlo;
  - embedding de consulta < 150 ms;
  - INT8 + preload solo si el p95 de la búsqueda vectorial supera 20 ms.
- Si la RAM total es < 7,5 GB, se muestra un aviso.
- Corpus objetivo: 10k–100k fragmentos. Siempre paginación y consultas agregadas. Nunca se cargan embeddings ni listas completas de fragmentos en la UI.

*Razón:* la latencia percibida la domina el prefill del LLM, y la memoria del móvil es el límite duro.

### V. Veracidad del estado

- Un modelo no se declara «listo» sin verificar de verdad su hash y su compatibilidad.
- Sin conteos, progresos ni porcentajes ficticios. Si el total es desconocido, el indicador es indeterminado.
- Los datos de ejemplo y los fakes solo existen en previews, tests o builds de demostración etiquetadas.
- El índice de un documento nunca se publica como listo de forma parcial.
- Eliminar exige confirmación y una limpieza coherente del archivo, el texto, los vectores y los metadatos.
- Excluir un documento de las búsquedas nunca borra sus datos.

*Razón:* la confianza del usuario depende de que la UI refleje el estado real del sistema.

### VI. Calidad y pruebas

**Tests unitarios obligatorios** en dominio y datos:
- normalizador, chunker, RRF, selector de contexto y constructor de prompt;
- parsers y repositorios;
- ViewModels con fakes.

**Tests instrumentados obligatorios:**
- SQLite con SQLite-Vector, FTS5, triggers y migraciones;
- normalizador frente a `normalizer_golden`, con coincidencia exacta;
- compatibilidad de embeddings frente a `embedding_golden`, con coseno ≥ 0,99 por par. Se ejecutan en cada teléfono de prueba y en cada cambio de versión de LiteRT-LM.

**Cierre de feature:**
- Ninguna feature se cierra sin build, lint y tests en verde.
- Las features con benchmarks reportan cifras medidas (p50/p95, dispositivo y versiones), que sustituyen a los presupuestos del informe.

*Razón:* sin golden ni tests en el dispositivo, las incompatibilidades del índice fallan en silencio.

### VII. UI fiel a los mockups y accesible

**Fidelidad:**
- En UI manda el PNG de `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/`, en textos y composición.
- Los estados no maquetados siguen el `.md` de la pantalla. Si tampoco están ahí, se propone una solución coherente con el design system y se aprueba **antes** de implementarla.
- No se inventan pantallas, campos ni navegación.
- Tokens siempre, nunca hex en pantallas, y un único sistema de componentes (`Lisdata*`).
- Cada pantalla contempla vacío, carga, error y contenido.

**Accesibilidad básica obligatoria:**
- `contentDescription` cuando el icono aporta significado;
- objetivos táctiles ≥ 48 dp;
- contraste WCAG AA;
- TalkBack anuncia el estado de switches y progresos;
- escalado de fuente sin recortes;
- todos los textos en `strings.xml`.

**Cierre:** las diferencias frente al mockup se reportan al cerrar cada feature.

*Razón:* el diseño está aprobado. Las desviaciones tienen que ser explícitas y accesibles.

### VIII. Idioma, proceso y trazabilidad

**Idioma:**
- Specs, planes, tareas y documentación en **español**.
- Código, identificadores, comentarios de código y mensajes de commit en **inglés**.

**Ciclo de cada feature:**
- Sigue el ciclo SDD de Spec Kit: specify → clarify → plan → tasks → analyze → implement.
- Va en su rama `NNN-nombre`.
- Hay **parada obligatoria de revisión del usuario tras clarify y tras analyze**.
- Commits atómicos; push al remoto solo tras cada punto de control aprobado.

**Contradicciones entre fuentes:**
- Se pregunta al usuario.
- Precedencia: en UI, el PNG; en arquitectura RAG, el informe.

**Registro:**
- Toda decisión relevante va en `docs/roadmap/DECISIONS.md`, con fecha, decisión y motivo.
- El estado de cada feature se mantiene en `docs/roadmap/FEATURES.md`.

*Razón:* el registro vivo permite auditar por qué el producto es como es.

## Restricciones técnicas

- **Dispositivos y ABIs:**
  - ABIs: `arm64-v8a`; `x86_64` solo para el emulador.
  - Dispositivos de prueba: ARM64 con 8 GB o más de RAM.
  - Teléfono de referencia: Nothing Phone (1), A063. Snapdragon 778G+ (SM7325), Adreno 642L, 12 GB de RAM y Android 15 (D-022).
- **SDK:** minSdk 31 (D-017). El mínimo absoluto es 26, que exige el AAR de SQLite-Vector. targetSdk según el scaffold vigente.
- **Runtime:**
  - LiteRT-LM 0.18.x (Google Maven) en Android.
  - `litert-lm-api` 0.18.x en Python offline.
- **Modelos:**
  - LLM: Gemma 4 E2B instruction-tuned, fichero genérico `gemma-4-E2B-it.litertlm`, en GPU (D-023).
  - Embeddings: EmbeddingGemma 2 de 270M de solo texto (`embeddinggemma-2-text-270m.litertlm`) a 256D (D-025).
- **Motor vectorial:** SQLite-Vector (coseno, `normalized=1`, `vector_init` en cada conexión) sobre SQLite bundled con FTS5.
- **Formatos del MVP:** PDF con texto extraíble, TXT y Markdown. Los PDF sin texto se detectan y se informa al usuario. OCR, HTML, DOCX y exportar/restaurar están fuera del MVP (backlog).
- **Pipeline Python offline (`offline/`):** solo para evaluación, ajuste y generación de golden. **No** distribuye índices.

## Flujo de desarrollo y puertas de calidad

- **Cada `plan.md`** incluye un *Constitution Check* explícito frente a los principios I–VIII. Cualquier excepción se justifica en «Complexity Tracking» y en DECISIONS.
- **`/speckit-analyze`** se ejecuta antes de `/speckit-implement`. Las inconsistencias entre spec, plan y tasks se corrigen antes de implementar.
- **Definición de terminado por feature:**
  1. resumen de lo implementado;
  2. resultado de build, lint y tests;
  3. diferencias detectadas frente al mockup;
  4. cifras medidas, si hay benchmarks;
  5. estado actualizado en `FEATURES.md`.
- Las features **técnicas** sin UI de producto usan pantallas de diagnóstico internas, solo en debug, y nunca UI de producto inventada.

## Governance

- **Precedencia:** esta constitución prevalece sobre cualquier otra práctica o documento del proyecto. En caso de conflicto se aplica la constitución y se registra la discrepancia.
- **Enmiendas:**
  - solo mediante `/speckit-constitution`;
  - con versión semántica, fecha y nota de impacto en el Sync Impact Report;
  - con su entrada en `docs/roadmap/DECISIONS.md`.
- **Versionado:**
  - MAJOR: eliminación o redefinición incompatible de principios.
  - MINOR: principio o sección nuevos, o guía ampliada de forma material.
  - PATCH: aclaraciones y redacción.
- **Cumplimiento:** se verifica en el *Constitution Check* de `/speckit-plan` y en `/speckit-analyze`. Un incumplimiento no justificado bloquea `/speckit-implement`.

**Version**: 1.1.0 | **Ratified**: 2026-10-25 | **Last Amended**: 2026-10-26
