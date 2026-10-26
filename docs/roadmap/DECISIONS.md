# MicroRAG — Registro de decisiones

Registro vivo con fecha, decisión, motivo, origen y alcance. Cada decisión nueva se añade al final con el siguiente identificador. Las decisiones no se borran: si una se sustituye, se marca como «Sustituida por D-XXX».

Los huecos aún sin resolver están en `INVENTARIO.md` §6 (G01–G44).

---

## D-001 — Rutas reales de las fuentes de verdad

- **Fecha:** 2026-10-25
- **Decisión:** las fuentes son:
  - `docs/disegn_app/`, para el design system, los `.md` de pantallas y los PNG en `screens/LISDATA-10-PANTALLAS-PNG/`;
  - `docs/RAG_Movil/informe-rag-movil-v2.md`, para el informe RAG;
  - `.specify/` y `.claude/skills/speckit-*`, para Spec Kit.
- **Motivo:** las rutas del encargo (`docs/design_app`, `docs/docs`, `docs/screen`, `.claude/commands/`) no existen. Spec Kit 0.16.5 instala los comandos como skills.
- **Origen:** descubrimiento.
- **Afecta:** todas las specs y planes, que enlazarán estas rutas.

## D-002 — La ingesta en el dispositivo es el camino principal; Python offline solo evalúa

- **Fecha:** 2026-10-25
- **Decisión:**
  - MicroRAG indexa en el móvil los documentos que importa el usuario: parsing, chunking, embeddings con EmbeddingGemma 2 y un worker reanudable.
  - El pipeline Python offline (`offline/`) se usa solo para evaluación y ajuste (conjunto de prueba, métricas, ablaciones) y para generar los ficheros golden de compatibilidad.
  - **No** distribuye un índice.
  - `documents.source` vale siempre `'user'`; la columna se mantiene por compatibilidad con el esquema del informe.
- **Motivo:** es lo que pide el encargo. El informe recomienda un índice offline para corpus fijos, y aquí el corpus lo aporta el usuario.
- **Origen:** usuario (encargo).
- **Afecta:** F04, F05, F06, F07 y F10.

## D-003 — Tipografía Google Sans

- **Fecha:** 2026-10-25
- **Decisión:**
  - Se usa Google Sans (`docs/disegn_app/Tipografias/`, licencia OFL) en toda la app, con los pesos Regular, Medium, SemiBold y Bold.
  - La escala se mapea a `Typography` de M3 según `INVENTARIO.md` §2.4.
- **Motivo:** los ficheros de fuente vienen con el design system. `00-design-system.md` proponía Roboto solo «hasta confirmar la fuente oficial».
- **Origen:** descubrimiento (precedencia del material de marca aportado).
- **Afecta:** F01.

## D-004 — En UI mandan los textos y la composición de los PNG

- **Fecha:** 2026-10-25
- **Decisión:**
  - Cuando el PNG y el `.md` de una pantalla difieren en textos o composición, se implementa el PNG.
  - Los estados que no están maquetados (vacío, error, carga…) usan los textos y reglas del `.md`.
  - Si un estado tampoco está en el `.md`, se propone una solución coherente con el design system antes de implementarlo.
- **Motivo:** es la precedencia acordada («en UI manda el mockup»). Las diferencias detectadas están en `INVENTARIO.md` §3.
- **Origen:** usuario (precedencias del encargo).
- **Afecta:** todas las features de producto.

## D-005 — Orden del roadmap

- **Fecha:** 2026-10-25
- **Decisión:** se reordena la lista inicial del encargo. Ver `FEATURES.md`.
  - **Viabilidad on-device (F02) antes que la gestión de modelos (F03).** Es el riesgo técnico mayor: si Gemma 4 E2B no cumple en el teléfono de referencia, cambia todo lo demás. Se puede probar con `adb push` y deja tamaños, hashes y backends reales para F03.
  - **Harness de evaluación offline adelantado (F05).** Fija la dimensión, el chunking y la variante de embedding antes de indexar en el móvil, porque cambiarlos después obliga a reindexar. Los benchmarks en el dispositivo y la decisión de INT8 se quedan al final (F14).
  - **Inicio como feature propia (F09).** Agrega el estado de modelos, biblioteca e indexación, así que depende de esas features.
  - **Biblioteca y detalle (F08) antes que el chat (F11).** Las citas del chat abren el detalle del documento.
- **Motivo:** dependencias técnicas y reducción de riesgo.
- **Origen:** propuesta, aprobada por el usuario el 2026-10-25 (ver D-013).
- **Afecta:** el roadmap completo.

## D-006 — minSdk de al menos 26

- **Fecha:** 2026-10-25
- **Decisión:**
  - El minSdk sube del 24 del scaffold a **26 como mínimo**.
  - **Propuesta:** 31 (Android 12), que se confirma en el clarify de F01.
- **Motivo:**
  - El AAR de `ai.sqlite:vector` 1.1.2 declara minSdk 26; LiteRT-LM declara 24.
  - Los dispositivos objetivo (ARM64 con 8 GB o más de RAM) y los tipos de servicio en primer plano hacen razonable 31.
- **Origen:** verificación de artefactos.
- **Afecta:** F01.

## D-007 — Alcance de las funciones sin mockup

- **Fecha:** 2026-10-25
- **Decisión:**
  - Entra en el roadmap solo el **historial de chats** (F13). Su UI se propone con el design system y se aprueba antes de implementarla.
  - **Exportar/restaurar, OCR de PDF escaneados e importación de HTML** van al backlog.
  - Formatos del MVP: PDF con texto extraíble, TXT y Markdown. Esto coincide con «Formatos: PDF · TXT · Markdown» del PNG de ingesta.
  - Los PDF sin texto se detectan y se informa de ello.
- **Motivo:** no hay pantallas para esas funciones. El usuario eligió el alcance.
- **Origen:** usuario.
- **Afecta:** F06, F12 y F13, y el backlog.

## D-008 — Iconos Lucide

- **Fecha:** 2026-10-25
- **Decisión:**
  - Se usa el set lineal open source **Lucide** (licencia ISC), convertido a vector drawables `ic_*` tintables por token. Lista y nombres en `INVENTARIO.md` §2.6.
  - No se mezclan estilos.
  - El catálogo de Magnific no se usa.
- **Motivo:**
  - El PDF de Magnific es un catálogo de estilo, sin SVG ni licencia, y le faltan la mayoría de los iconos de los PNG.
  - Lucide coincide con el trazo de los mockups.
- **Origen:** usuario.
- **Afecta:** F01. Hay que incluir la licencia ISC en los avisos de licencias (F15).

## D-009 — Un único módulo Gradle

- **Fecha:** 2026-10-25
- **Decisión:**
  - Un solo módulo `:app` con paquetes por capa:
    - `core.designsystem`, `core.navigation`;
    - `domain` (modelos, casos de uso, interfaces de repositorio y lógica pura: normalizador, chunker, RRF, selector de contexto, constructor de prompt);
    - `data` (SQLite, WorkManager, parsers, descarga);
    - `rag.runtime` (adaptadores de LiteRT-LM);
    - `feature.*` (pantallas y ViewModels).
  - La regla «la UI no importa código nativo ni SDKs de inferencia» se mantiene por convención y revisión.
- **Motivo:** `02-architecture.md` recomienda evitar la modularización prematura, y el usuario elige un solo módulo.
- **Origen:** usuario.
- **Afecta:** F01 y todas las demás.

## D-010 — Repositorio y flujo git

- **Fecha:** 2026-10-25
- **Decisión:**
  - Remoto `origin = https://github.com/jrcosio/microrag.git`, rama principal `main`.
  - Cada feature se desarrolla en su rama Spec Kit `NNN-nombre`, con commits atómicos en inglés.
  - Se hace push **tras cada punto de control aprobado** por el usuario.
  - `docs/disegn_app/iconos/*.pdf` (catálogo de Magnific) se excluye por `.gitignore`.
  - Los modelos (`*.litertlm`) y los artefactos de `offline/` nunca se versionan.
- **Motivo:** lo pide el usuario. El PDF de Magnific puede no ser redistribuible.
- **Origen:** usuario.
- **Afecta:** el proceso.

## D-011 — Origen de los modelos

- **Fecha:** 2026-10-25
- **Decisión:**
  - Los modelos se descargan de Hugging Face `litert-community`, que es público, sin gate y Apache-2.0:
    - Gemma 4 E2B: `gemma-4-E2B-it-litert-lm`;
    - EmbeddingGemma 2: la variante que se elija.
  - Las URL quedan fijadas a un commit concreto y el SHA-256 publicado se verifica (`lfs.oid`) antes de cargar nada.
  - El fichero exacto (genérico o `-gpu`; 270M, 440M o 740M) se fija en F02, F03 y F05 con medidas reales.
  - En desarrollo se usa `adb push`.
  - Play Asset Delivery se reevaluará antes de la release.
- **Motivo:**
  - El informe y el encargo permiten descargar modelos con verificación SHA-256.
  - `02-onboarding.md` pedía evitar descargas (G02).
  - La verificación confirma que los repositorios son públicos y publican el hash.
- **Origen:** verificación + encargo. La importación por SAF como alternativa se decide en el clarify de F03.
- **Afecta:** F02, F03 y F05.

## D-012 — Paquete Python de LiteRT-LM

- **Fecha:** 2026-10-25
- **Decisión:** el harness offline usa **`litert-lm-api==0.18.1`** (`from litert_lm import EmbeddingEngine`).
- **Motivo:** el informe cita `litert-lm`, pero ese paquete de PyPI es solo la CLI.
- **Origen:** verificación.
- **Afecta:** F04 (generador de golden) y F05.

## D-013 — Aprobación de inventario, constitución y roadmap

- **Fecha:** 2026-10-25
- **Decisión:**
  - Se aprueban `INVENTARIO.md`, la constitución v1.0.0 y `FEATURES.md` (F01–F15 y backlog).
  - Arranca el ciclo SDD con F01.
  - Los huecos aún abiertos (🟡/❓) no quedan aprobados con esto: se resuelven en el `/speckit-clarify` de la feature a la que afectan.
- **Motivo:** primer punto de control del encargo.
- **Origen:** usuario.
- **Afecta:** el proceso. Se hace el push inicial a `origin/main`.

## D-014 — Nombre visible: MicroRAG

- **Fecha:** 2026-10-25
- **Decisión:**
  - La app se llama «MicroRAG» en el lanzador y en todos los textos de interfaz que nombran el producto o el asistente. Por ejemplo, la etiqueta del asistente en el chat pasa de «LISDATA» a «MicroRAG», y la CTA de Preparación es «Entrar en MicroRAG».
  - El logo de LISDATA se mantiene como marca de la empresa en las cabeceras donde aparece en los mockups (Bienvenida e Inicio).
- **Motivo:** decisión del usuario sobre G32.
- **Origen:** usuario (clarify de F01).
- **Afecta:** F01 (`app_name` y strings) y los textos de F03, F09 y F11.

## D-015 — Contraste: oscurecer solo los colores de texto

- **Fecha:** 2026-10-25
- **Decisión:**
  - Se mantienen los rellenos, chips y degradados de los PNG.
  - Los colores de texto que no cumplen WCAG AA se oscurecen:
    - secundario `#658797` → `#54798A`;
    - aviso `#A46C19` → `#8A5A12`;
    - éxito `#129D83` → `#087A65`;
    - error `#DB5865` → `#C23F4D`.
  - El degradado de las CTA solo se ajusta si el tramo bajo la etiqueta no alcanza 3:1.
- **Motivo:** el principio VII de la constitución exige WCAG AA (G05, G06, G42). La diferencia visual es mínima.
- **Origen:** usuario (clarify de F01).
- **Afecta:** F01 (tokens) y todas las pantallas.

## D-016 — Modo oscuro derivado

- **Fecha:** 2026-10-25
- **Decisión:**
  - Tema oscuro derivado de los tokens navy de la marca (`INVENTARIO.md` §2.2).
  - Sigue el ajuste del sistema, sin selector propio.
  - Se valida con las previews oscuras de cada componente.
- **Motivo:** no hay mockups oscuros. El design system pide derivar roles y el checklist QA exige comprobar el modo oscuro (G07).
- **Origen:** usuario (clarify de F01).
- **Afecta:** F01 y F15 (auditoría).

## D-017 — minSdk 31 (Android 12)

- **Fecha:** 2026-10-25
- **Decisión:** minSdk = 31. Concreta D-006, que fijaba el mínimo en 26.
- **Motivo:**
  - Los dispositivos objetivo (ARM64 con 8 GB o más) usan Android 12+.
  - Simplifica los servicios en primer plano, el control térmico y los insets (G33).
- **Origen:** usuario (clarify de F01).
- **Afecta:** F01 y todas las demás.

## D-018 — Controles estándar de Material 3 tematizados

- **Fecha:** 2026-10-25
- **Decisión:**
  - El interruptor, los radios, los chips de filtro, la barra inferior, el campo de texto y los diálogos son los de Material 3, tematizados con los tokens de LISDATA.
  - No se hacen réplicas a medida.
  - Las diferencias de tamaño frente al PNG se documentan en cada entrega.
- **Motivo:** se obtienen accesibilidad y comportamiento estándar con menos código propio (G18).
- **Origen:** usuario (clarify de F01).
- **Afecta:** F01 y todas las features de producto.

## D-019 — Ajustes derivados de contraste (aplicación de D-015)

- **Fecha:** 2026-10-25
- **Decisión:** durante el plan de F01 se calcularon todos los pares texto/fondo y aparecieron tres casos que D-015 no cubría:
  - **Degradado de las CTA con texto:** la parada cian pasa de `#02AEE2` a **`#0099D4`**. Así el texto blanco tiene ≥ 3,23:1 en todo el tramo, también en botones estrechos con etiquetas anchas como «Seleccionar archivos». El degradado original se mantiene en elementos sin texto (avatar, tile sparkle).
  - **Nuevo token `textSecondaryOnContainer` = `#4D7183`:** se usa para el texto secundario sobre contenedores tintados (`surfaceInfo*`, `neutralTile`, contenedores semánticos), donde `#54798A` se queda en ≈4,2–4,3:1.
  - **`errorContainer` = `#FEF2F3`:** cubre el contenedor de error, que no aparece en los mockups. Da 4,67:1 con `#C23F4D`.
- **Motivo:** cumplir FR-008 y la constitución VII. D-015 ya autorizaba ajustar el degradado. La diferencia visual es mínima.
- **Origen:** plan de F01 (`specs/001-foundations-design-system/research.md` y `data-model.md`). Se revisa en la parada tras analyze.
- **Afecta:** F01 (tokens).

## D-020 — Gradle 9.8.2 y compileSdk 37.1

- **Fecha:** 2026-10-25
- **Decisión:**
  - El wrapper de Gradle pasa de 9.5.0 a **9.8.2**, la última estable. Se verifica su SHA-256 en `distributionSha256Sum`.
  - **compileSdk** pasa de 36.1 a **37.1**. targetSdk se queda en **36** y minSdk en **31**.
- **Motivo:**
  - AGP 9.4.1 exige Gradle 9.6.0 o superior.
  - Las librerías estables de AndroidX elegidas (Compose BOM 2026.09.00, core 1.19.1, lifecycle 2.11.0, navigation 2.10.2) exigen compileSdk 37 o superior.
  - Subir compileSdk no cambia el comportamiento en ejecución. Subir targetSdk sí lo haría, así que queda para cuando se evalúe.
- **Origen:** implementación de F01 (T004). Verificado con `assembleDebug`: AGP 9.4.1 resuelve KGP 2.4.21.
- **Afecta:** F01 (build) y todas las demás.

## D-021 — AGP 9.3.3 por compatibilidad con Android Studio

- **Fecha:** 2026-10-25
- **Decisión:**
  - AGP pasa de 9.4.1 a **9.3.3**, la versión con la que Android Studio generó el scaffold.
  - Se mantienen Kotlin 2.4.21 (AGP resuelve KGP 2.4.21), Gradle 9.8.2 y compileSdk 37.1. Todo queda verificado con build, lint y tests en verde.
  - El aviso de lint `AndroidGradlePluginVersion` se desactiva con justificación.
  - `rootProject.name` vuelve a «MIcroRAG», igual que la carpeta y la configuración `.idea`. El cambio de nombre a «MicroRAG» del plan rompía la sincronización de Android Studio («Can't find module entity for MIcroRAG.app»). El nombre visible de la app sigue siendo «MicroRAG» (D-014).
- **Motivo:** el Android Studio del equipo admite como máximo AGP 9.3.x («The project is using an incompatible version (AGP 9.4.1)… Latest supported version is AGP 9.3.0»).
- **Origen:** usuario, durante la implementación de F01.
- **Afecta:** el build. Sustituye la versión de AGP de research R1 y el renombrado de proyecto del plan de F01. D-020 sigue vigente: Gradle 9.8.2 funciona con AGP 9.3.3.

## D-022 — Alcance de la fase 0 (F02) y teléfono de referencia

- **Fecha:** 2026-10-25
- **Decisión:**
  - **Teléfono de referencia:** Nothing Phone (1), A063. Snapdragon 778G+ (SM7325), Adreno 642L con OpenCL, 12 GB de RAM y Android 15 (API 35).
  - **Modelos:** se prueban los cinco ficheros (≈ 5,7 GB): Gemma 4 E2B genérico y GPU; EmbeddingGemma 2 de 740M, 440M y 270M.
  - **Compatibilidad CPU↔GPU:** se mide la de los embeddings, con coseno mínimo ≥ 0,99 sobre ≈ 20 textos.
  - **Gemma 4 E2B:** se mide en GPU; en CPU solo como plan B si la GPU falla para un fichero.
- **Motivo:** cerrar G35 y G36 con datos reales y saber pronto si consultas (CPU) e ingesta (GPU) pueden usar backends distintos.
- **Origen:** usuario (clarify de F02).
- **Afecta:** F02, F05, F07 y F10.

## D-023 — Fichero de Gemma 4 E2B: el genérico, en GPU (G35)

- **Fecha:** 2026-10-26
- **Decisión:** se usa `gemma-4-E2B-it.litertlm` (genérico, 2,59 GB) en GPU con la precisión de activaciones por defecto. El fichero `gemma-4-E2B-it-gpu.litertlm` se descarta en el teléfono de referencia.
- **Motivo:** cifras de `specs/002-ondevice-feasibility/benchmarks.md` (Nothing Phone (1), LiteRT-LM 0.18.1, prompt de 1.986 tokens, 128 tokens de salida, mediana de 3 ejecuciones).

  | Fichero y activaciones | TTFT | Prefill | Decode | Init frío / caché | RSS pico | Salida |
  |---|---:|---:|---:|---:|---:|---|
  | genérico, por defecto | **4,53 s** | 450 tok/s | 8,8 tok/s | 16,9 s / 5,4 s | 2.478 MB | coherente, con citas |
  | genérico, FP32 | 7,36 s | 274 tok/s | 8,9 tok/s | 16,8 s / 5,5 s | 2.735 MB | coherente |
  | `-gpu`, por defecto (fp16) | 4,78 s | 425 tok/s | 9,9 tok/s | 12,7 s / 12,5 s | 2.147 MB | **incoherente** |
  | `-gpu`, FP32 | 13,48 s | 149 tok/s | 9,1 tok/s | 12,3 s / 11,4 s | 2.330 MB | coherente |

  En la Adreno 642L, el fichero `-gpu` solo genera texto válido con activaciones FP32, y entonces es casi 3 veces más lento que el genérico (research R14). Además no aprovecha la caché de kernels: tarda lo mismo en frío que con caché.
- **Origen:** medición de F02 (FR-018).
- **Afecta:** F03 (qué fichero se descarga, su tamaño y su huella) y F11. Se revisa si cambia el teléfono de referencia: en GPU más recientes el fichero `-gpu` podría funcionar en fp16.

## D-024 — Backend de embeddings: consulta en CPU; la GPU es apta para la ingesta

- **Fecha:** 2026-10-26
- **Decisión:**
  - **Consulta:** embedding en **CPU**.
  - **Ingesta (F07):** puede usar la **GPU**. Se pueden mezclar vectores de CPU y de GPU en el mismo índice.
- **Motivo:**
  - **Consulta:** en CPU, p95 de 120,3 ms con la 270M, por debajo del objetivo de 150 ms (SC-004). La GPU no la acelera (p95 entre 124 y 141 ms) y competiría con el LLM.
  - **Ingesta:** un fragmento de ≈ 350 tokens tarda unos 880 ms (p50) en CPU y unos 320–355 ms en GPU, unas 2,5 veces más rápido.
  - **Mezcla:** el coseno CPU↔GPU es como mínimo 0,99321 y de media 0,99744 sobre 20 textos. Supera el umbral de 0,99 (FR-009b), aunque con poco margen.
- **Origen:** medición de F02 (FR-018).
- **Afecta:** F07 y F10. F04 vuelve a comprobar la compatibilidad con `embedding_golden` (constitución VI). Si el margen sobre 0,99 se pierde en otro teléfono, la ingesta pasa a CPU.

## D-025 — Variante de EmbeddingGemma 2: la 270M de solo texto (G36)

- **Fecha:** 2026-10-26
- **Decisión:**
  - Se usa `embeddinggemma-2-text-270m.litertlm` (157 MB).
  - F05 deja de comparar variantes y se centra en la dimensión, el tamaño de fragmento, la cabecera y los bloques.
- **Motivo:**
  - Las tres variantes producen **exactamente los mismos vectores de texto**: coseno 1,0 en los 20 textos de prueba (research R15). Comparten el codificador de texto; las mayores solo añaden visión y audio.
  - Las latencias medidas son equivalentes (consulta en CPU, p95 entre 120 y 125 ms en las tres).
  - Para un RAG de solo texto, la 270M tiene la misma calidad con un tercio de la descarga.
- **Origen:** medición de F02 (FR-018). Cambia el plan de G36 («elegir por nDCG en F05»), porque con vectores idénticos el nDCG es idéntico.
- **Afecta:** F03 (descarga de 157 MB en lugar de 388 o 485), F04 (huella del modelo en `rag_meta`) y F05 (las ablaciones de variante desaparecen).

---

## D-026 — Presupuesto de primer token: ≤ 5 s con ≈ 2.000 tokens (G44)

- **Fecha:** 2026-10-26
- **Decisión:**
  - Se enmienda la constitución a la **v1.1.0** (principio IV). El presupuesto «primer token ≤ 3 s con 6 bloques» pasa a **≤ 5 s de extremo a extremo** (de enviar la pregunta a ver el primer token) con un prompt de ≈ 2.000 tokens (6 bloques) en el teléfono de referencia.
  - No se recorta el contexto ahora. F05 fija el número de bloques con datos de calidad, dentro de este presupuesto.
  - Las palancas para bajar el TTFT (decodificación especulativa, plantilla más corta, E4B frente a E2B) se evalúan en F14 como mejora.
- **Motivo:**
  - El objetivo de 3 s del informe era un presupuesto previo a la medición. La constitución VI dice que las cifras medidas sustituyen a los presupuestos.
  - **Medido en F02 (D-023):** TTFT de 4,53 s en el motor y 4,75 s de reloj con un prompt de 1.986 tokens y 6 fuentes.
  - **De extremo a extremo** hay que sumar el embedding de consulta (p95 de 120 ms, D-024) y la búsqueda (≤ 20 ms p95, constitución IV): ≈ 4,9 s.
  - Con un prefill de 450 tok/s, cada bloque de ≈ 350 tokens cuesta ≈ 0,8 s. Si F05 elige menos bloques, el TTFT baja en proporción.
  - **Opciones descartadas:**
    - (a) reducir el contexto a ≈ 1.200 tokens: sacrifica calidad de recuperación sin datos de F05;
    - (c) como única vía (mantener 3 s hasta F14): dejaría F10 y F11 sin un presupuesto alcanzable. Se conserva como mejora posterior.
- **Origen:** usuario, al cerrar F02 (opción (b) de G44). En el mismo punto de control aprueba F02 y confirma D-023, D-024 y D-025.
- **Afecta:**
  - la constitución (IV, y Restricciones técnicas alineadas con D-017, D-022, D-023 y D-025);
  - F05 (número de bloques), F11 (criterio de TTFT) y F14 (sesión sostenida y palancas).

---

## Pendientes de decisión

Se presentan al usuario junto con el inventario. Detalle en `INVENTARIO.md` §6.

- **G08** — Flecha Atrás en las pestañas, que vuelve a Inicio. Queda aplicada en la spec de F01 como supuesto por la precedencia del PNG; se revisa en el checkpoint de F01.
- **G10** — Contenido de los pasos 2 y 3 de Preparación.
- **G11–G17, G21, G22, G24–G26, G29–G31, G39** — Propuestas para estados y acciones no maquetados.
