# MicroRAG — Registro de decisiones

Registro vivo con fecha, decisión, motivo, origen y alcance. Cada decisión nueva se añade al final con el siguiente identificador. Las decisiones no se borran: si una se sustituye, se marca como «Sustituida por D-XXX».

Los huecos aún sin resolver están en `INVENTARIO.md` §6 (G01–G47).

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
  - Sigue el ajuste del sistema, sin selector propio. *Sustituida en parte por D-067: desde F16 el usuario elige Sistema, Claro u Oscuro.*
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

## D-027 — Onboarding y descarga de modelos (clarify de F03)

- **Fecha:** 2026-10-26
- **Decisión:**
  - **Origen de los modelos (G02):** la descarga verificada es el camino principal. La confirmación ofrece además «Seleccionar archivo del dispositivo» (selector del sistema), con la misma verificación de huella y compatibilidad.
  - **Red:** una vez que el usuario confirma el tamaño (≈ 2,75 GB), la descarga usa cualquier red disponible, Wi-Fi o datos móviles. La confirmación indica la red actual y avisa si son datos móviles.
  - **Pasos 2 y 3 (G10):** una sola pantalla, la del PNG 02. El chip «PASO N DE 3» y la barra reflejan el estado real. La descarga empieza al tocar una fila de modelo. «Continuar» solo se habilita con los dos modelos «Listo» y el almacenamiento preparado.
  - **Compatibilidad:** antes de «Listo», Gemma 4 se arranca una vez en la GPU (≈ 17 s, deja la caché del motor lista). Si falla, se prueba en CPU: si arranca, queda «Listo» con el aviso «Modo CPU, más lento»; si no, queda «Incompatible». EmbeddingGemma 2 se prueba en CPU.
- **Motivo:**
  - La importación cubre los teléfonos sin Internet que pide el MD 02, y reutiliza el instalador verificado de F02.
  - Una sola pantalla es la opción que menos UI no maquetada inventa (constitución VII).
  - La prueba real cumple la constitución V («verificar de verdad la compatibilidad») y adelanta la carga en frío del primer chat (≈ 16,9 s → ≈ 5,4 s, D-023).
- **Origen:** usuario (clarify de F03).
- **Afecta:** F03, y también F11 (motor ya preparado; aviso de modo CPU) y F12 (gestión de modelos).

---

## D-028 — Descarga en segundo plano: UIDT en Android 14+ y WorkManager en 12–13 (plan de F03)

- **Fecha:** 2026-10-26
- **Decisión:**
  - En API ≥ 34, la descarga de modelos usa *user-initiated data transfer jobs* (`JobScheduler` con `setUserInitiated(true)`).
  - En API 31–33 usa WorkManager con un servicio en primer plano `dataSync`.
  - Las dos rutas ejecutan el mismo `ModelDownloadRunner`.
  - Es una desviación aprobada de la constitución II («WorkManager para trabajo largo»), justificada en el Complexity Tracking del plan de F03.
  - Se aprueba instalar una AVD API 33 arm64 para validar la ruta WorkManager.
- **Motivo:**
  - Android recomienda UIDT para descargas largas que inicia el usuario.
  - No hay librería Jetpack que lo soporte.
  - Android 15 limita `dataSync` a 6 h cada 24 h, y Android 16 somete los workers largos a las cuotas de jobs (research R3 de F03).
- **Origen:** usuario (aprobación del analyze de F03).
- **Afecta:** F03. También F07, que evaluará la misma pregunta para la indexación.

---

## D-029 — Idioma de la app: español por defecto, inglés secundario

- **Fecha:** 2026-10-26
- **Decisión:**
  - La app se muestra en **español** aunque el teléfono esté en otro idioma. El idioma del sistema por sí solo nunca la cambia.
  - El **inglés** es un idioma secundario que el usuario elige expresamente:
    - en Android 13+, desde los ajustes de idioma por app del sistema (`localeConfig` con `es` y `en`);
    - más adelante, desde el selector de la app (F12, pantalla Idiomas).
  - Otros idiomas se añadirán después, por el mismo mecanismo.
- **Cómo:** `AppLocale` aplica el idioma elegido por el usuario o, si no ha elegido ninguno, español, en el `attachBaseContext` de la aplicación (notificaciones) y de la actividad (UI).
- **Motivo:** indicación del usuario durante la implementación de F03 («el idioma principal es el español, el inglés es secundario»). En las capturas del emulador, configurado en inglés, la app salía en inglés.
- **Origen:** usuario.
- **Afecta:** todas las pantallas. F12 implementa el selector (G25) sobre esta base.

---

## D-030 — Núcleo de datos RAG (clarify de F04)

- **Fecha:** 2026-10-26
- **Decisión:**
  - **Esquema (G27):** se amplía la tabla de documentos con inclusión en búsquedas, tamaño, páginas, ruta de la copia privada y los estados pausado y cancelado. Se mantiene `source`, que vale siempre `'user'` (D-002), por compatibilidad con el esquema compartido del informe.
  - **INT8:** se aplaza a F14. F04 guarda `quant_dirty` y busca siempre en F32 exacto. La cuantización y la precarga solo se añaden si F14 mide un p95 superior a 20 ms.
  - **Compatibilidad de embeddings:** la referencia del escritorio se valida en el teléfono en CPU y en GPU, con coseno ≥ 0,99 en ambos. Si la GPU no lo cumple en un teléfono, ese teléfono indexa en CPU (D-024).
  - **Diagnóstico:** F04 no tiene pantalla de diagnóstico. Las pruebas y los registros lo cubren; el diagnóstico de búsqueda llega en F10.
- **Motivo:**
  - las pantallas de F06–F08 necesitan esos datos;
  - INT8 solo se justifica con medidas;
  - D-024 permite indexar en GPU, y la evaluación de F05 tiene que representar lo que el móvil indexa.
- **Origen:** usuario (clarify de F04).
- **Afecta:** F04, F05, F07, F10 y F14.

---

## D-031 — Extracción de las bibliotecas nativas para cargar SQLite-Vector (implementación de F04)

- **Fecha:** 2026-10-26
- **Decisión:** `packaging.jniLibs.useLegacyPackaging = true`, equivalente a `extractNativeLibs="true"`. Al instalar, las bibliotecas nativas se extraen a `nativeLibraryDir`, y el índice carga `nativeLibraryDir/vector.so` con el punto de entrada `sqlite3_vector_init`.
- **Motivo:**
  - SQLite carga las extensiones desde una ruta del sistema de ficheros;
  - por defecto, Android deja las nativas dentro del APK;
  - `vector.so` no lleva el prefijo `lib`.

  Comprobado en el emulador API 37, en la AVD API 33 y en el teléfono de referencia (API 35): `vector.so` se extrae y `vector_version()` devuelve `1.1.2` (F04 research R2, T003).
- **Coste medido:**
  - APK de release de 45,7 MB (F03: 57,4 MB), porque ahora las nativas van comprimidas;
  - descarga estimada de 24,0 MB;
  - ≈ 69,1 MB instalados (+11,7 MB frente a F03), por los 23,4 MB de nativas extraídas.
- **Alternativa descartada:** renombrar a `libvector.so` y cargarla sin extraer. No hace falta, porque la extracción funciona en todos los dispositivos probados.
- **Origen:** plan de F04, aprobado por el usuario.
- **Afecta:** F04, y F15 (tamaño y R8).

---

## D-032 — Evaluación offline (clarify de F05)

- **Fecha:** 2026-10-26
- **Decisión:**
  - **Corpus (G37):** documentos públicos en español con licencia que permita reutilizarlos y redistribuirlos: normativa, guías y manuales públicos. Los elige quien desarrolla y se versionan en el repositorio con su URL de origen, su huella y su licencia. Así la evaluación es reproducible por cualquiera y no hay datos privados.
  - **Preguntas:** las redacta Claude, el asistente de desarrollo, a partir de fragmentos muestreados por documento y tipo de contenido, con respuesta esperada, datos clave y evidencia literal, sin copiar frases. El usuario revisa al menos 100.
  - **Juicio:** reglas automáticas en todas las variantes (datos clave, frase exacta de negativa, bloque citado con la evidencia). En la configuración elegida, Claude juzga a mano todas las respuestas y el usuario revisa una muestra; las cifras finales usan el juicio manual.
  - **E4B:** se compara con E2B en F05, solo en la configuración elegida y en el escritorio. La latencia en el teléfono sigue siendo de F14.
  - **Umbrales:** si la configuración elegida no los alcanza, se prueban las palancas baratas del informe (trigramas, troceado, cabecera, candidatos, plantilla). Si aun así falta alguno, se para y se consulta al usuario antes de cerrar F05.
- **Motivo:**
  - un corpus público evita el problema de privacidad de enviar documentos a un modelo grande y hace la evaluación reproducible;
  - el juicio automático permite iterar en minutos y el manual da las cifras finales fiables;
  - E4B cuesta poco en el Mac y adelanta información para F11 y F14.
- **Origen:** usuario (clarify de F05).
- **Afecta:** F05, F06 (troceado), F10, F11 y F14.

---

## D-033 — Revisión del conjunto de prueba por el asistente (implementación de F05)

- **Fecha:** 2026-10-26
- **Decisión:** la revisión del conjunto de prueba (al menos 100 preguntas) y la muestra del juicio manual las hace Claude, el asistente de desarrollo, en lugar del usuario. Modifica D-032 en ese punto.
- **Motivo:** indicación del usuario: «no tengo tiempo de revisar así que hazlo tú».
- **Cómo se compensa la pérdida de independencia:**
  - las preguntas las redactaron agentes separados, y la revisión la hace la sesión principal, que no las había visto;
  - los criterios son explícitos: pregunta realista y no ambigua, respuesta deducible de la evidencia, y preguntas sin respuesta comprobadas con búsquedas en el corpus;
  - los veredictos quedan registrados en `offline/testset/` (campo `review` y nota).
- **Resultado:** 100 preguntas de la hoja aceptadas. Además se revisaron 12 que los agentes habían marcado como dudosas: 9 aceptadas y 3 descartadas por poco realistas.
- **Origen:** usuario.
- **Afecta:** F05.

---

## D-034 — Parámetros de recuperación y contexto (evaluación de F05)

- **Fecha:** 2026-10-26
- **Decisión:** los parámetros de la tabla, con la cifra de `offline/reports/eval-report.md` que justifica cada uno. Quedan en `offline/config/rag_parameters.json` y en `RagParameters.kt`, y una prueba JVM mantiene los dos iguales.

  | Parámetro | Valor | Cifra que lo justifica | Alternativas descartadas |
  |---|---|---|---|
  | Recuperación | Híbrida: vector + FTS5, RRF k = 60, 50 candidatos por lista | MRR híbrido 0,810 frente a 0,776 (vector) y 0,743 (FTS); Recall@20 0,984 | Solo vector, solo FTS |
  | Normalizador léxico | SpanishText v1 (palabras vacías + stemmer) | MRR híbrido 0,810 y Recall@bloques 0,862, los mejores | Sin normalizar (FTS R@20 0,882); solo palabras vacías (MRR 0,777) |
  | Cabecera contextual | Sí | nDCG@10 vectorial 0,806 frente a 0,766 sin ella | Sin cabecera |
  | Tamaño de fragmento | 350 tokens: objetivo 1.594 y máximo 2.113 caracteres, con 4,554 caracteres por token medidos | MRR 0,810 frente a 0,786 (250) y 0,797 (500) | 250 tokens; 500 tokens (recorta el 22 % de las entradas de embedding) |
  | Regla de seguimiento | Sí | Recall@20 de seguimientos 0,98 frente a 0,79 sin ella | Sin regla |
  | Dimensión | 256 (decidido por el usuario) | Híbrido igual que 512: MRR 0,810 frente a 0,811. El nDCG@10 solo vectorial pierde un 3,3 % frente a 768 (la regla del informe pide ≤ 1 %); se acepta porque la búsqueda de la app es híbrida y 512 duplicaría el coste en el móvil | 128 (nDCG 0,752), 512, 768 |
  | Contexto | 4 bloques, sin vecinos, como mucho 3 por documento, presupuesto de 1.719 tokens | Respuestas 68,9 % con la regla, a 0,6 puntos de la mejor variante, con un p95 de prompt un 16–18 % más corto (1.630 tokens) | 6 u 8 bloques, con o sin vecinos |
  | Prompt | Plantilla del informe; límite de 2.000 tokens en el p95 | Todas las variantes calibradas quedan ≤ 2.000 (constitución IV, D-026) | Plantilla v2 (no mejora y baja la negativa al 91 %) |
  | LLM | Gemma 4 E2B (D-023) | Ver D-035 | E4B (a F14) |

- **Origen:** evaluación de F05. El usuario eligió la dimensión.
- **Afecta:** F06 (troceado), F07 (indexación), F10 (recuperación) y F11 (contexto y prompt).

---

## D-035 — Umbral de respuestas correctas no alcanzado; se acepta con E2B (cierre de F05)

- **Fecha:** 2026-10-26
- **Decisión:** se acepta la configuración de D-034 con Gemma 4 E2B. En el juicio manual de las 426 respuestas da un **78,4 %** de respuestas correctas, frente al 80 % del informe; la diferencia es de 1,6 puntos. El resto de umbrales se cumplen: negativa 96,4 % y citas 94,2 %. Es la excepción que admite SC-004 de F05.
- **Palancas probadas (FR-022):**
  - más bloques y vecinos: ±1 punto;
  - plantilla v2: 0 puntos y peor negativa;
  - 512 dimensiones: +1,4 puntos;
  - Gemma 4 E4B: +6,5 puntos con la regla automática, pero 2,3 veces más lento. En el teléfono se estima por encima de los 5 s de primer token.
- **Análisis de fallos:** 36 de las 80 respuestas no correctas no tenían el fragmento relevante entre los bloques del contexto, aunque estaba entre los 20 primeros candidatos (Recall@20 0,984). El presupuesto del prompt deja unos 3 bloques de media. Las otras 44 fallan con el relevante delante: 11 negativas indebidas y 33 respuestas incorrectas o incompletas.
- **Palancas de FR-022 sin probar:** el FTS con trigramas y más candidatos. Con 50 candidatos el relevante ya está casi siempre entre los 20 primeros, así que el margen está en el orden de los primeros puestos.
- **Seguimiento:**
  - F10 revisa el orden dentro del presupuesto (trigramas como tercera lista de RRF, reordenación);
  - F11 trabaja la plantilla con respuestas reales, por las negativas indebidas y las respuestas incompletas;
  - F14 mide E4B en el teléfono (latencia y memoria) antes de proponer un cambio de modelo, que sería una feature aparte (regla P5 de F05).
- **Origen:** usuario.
- **Afecta:** F10, F11 y F14.

---

## D-036 — Entradas de embedding demasiado largas (hallazgo de F05)

- **Fecha:** 2026-10-26
- **Decisión:** si EmbeddingGemma rechaza una entrada por superar los 512 tokens, se recorta el texto embebido al 90 % de su longitud, por un espacio, y se reintenta. Solo se recorta lo que se embebe, nunca el fragmento guardado.
- **Motivo:** LiteRT-LM 0.18.1 rechaza esas entradas y su API de Android no tiene estrategia de desbordamiento. El máximo del troceado se calcula con la densidad media de tokens, pero los fragmentos con muchas cifras o con cabeceras largas del BOE la superan. En la referencia afectó a 38 de 1.913 fragmentos (2,0 %).
- **Origen:** evaluación de F05.
- **Afecta:** F07 (indexación).

---

## D-037 — Importación de documentos (clarify de F06)

- **Fecha:** 2026-10-26
- **Decisión:**
  1. **Extractor de PDF (G38):** la API de PDF del sistema. Se usa `PdfRenderer` en Android 15+ y `PdfRendererPreV` en Android 12–14 con la extensión S ≥ 13; las dos son PDFium y se actualizan con el sistema. No se añade pdfbox-android ni ninguna otra biblioteca de PDF. En un dispositivo sin esa capacidad, los PDF se rechazan con un mensaje claro, y TXT y Markdown funcionan igual.
  2. **Límites:** 100 MB por archivo y 2.000 páginas por PDF. Por encima, «Demasiado grande».
  3. **Cola de importación:** muestra todos los documentos aún no indexados, también los de sesiones anteriores, hasta que se indexan o el usuario los quita.
  4. **PDF con contraseña de apertura:** se rechaza como «Archivo cifrado». No se pide ni se guarda ninguna contraseña.
- **Motivo:**
  - pdfbox-android no publica desde el 2023-01-02 y añade 3,25 MB más BouncyCastle 1.72;
  - la API del sistema no pesa nada y la mantiene Google;
  - el teléfono de referencia tiene Android 15.

  Los límites cubren manuales y normativa largos sin indexaciones interminables. Pedir contraseñas obligaría a guardarlas o a pedirlas de nuevo al reindexar, porque F07 vuelve a extraer.
- **Alternativas descartadas:**
  - pdfbox-android, solo o como respaldo;
  - androidx.pdf 1.0.0-beta01, en beta y con un servicio en otro proceso;
  - límites de 50 MB y 1.000 páginas, o de 200 MB y 5.000;
  - una cola limitada a la sesión;
  - pedir la contraseña.
- **Origen:** usuario (clarify de F06).
- **Afecta:** F06; F07 (vuelve a extraer al indexar); F08 (la Biblioteca solo muestra lo indexado).

---

## D-038 — Diseño de la importación (plan de F06)

- **Fecha:** 2026-10-26
- **Decisión:**
  1. **Esquema v2:** `documents` gana `skipped_pages INTEGER` (páginas de PDF sin texto omitidas) y `SCHEMA_VERSION` pasa a 2, con la migración `1 → 2` (`ALTER TABLE … ADD COLUMN`). El espacio vectorial no cambia.
  2. **Intentos de importación:** un archivo que aún se está copiando, o que falló antes de registrarse, vive en memoria en el *scope* de aplicación. Sobrevive a la recreación de la Activity, pero no a la muerte del proceso. Los documentos registrados sí persisten en el índice.
  3. **Sin concesiones persistentes de URI:** se copia al volver del selector y no se guarda acceso al original (G22).
  4. **Un solo camino para PDF:** `PdfRendererPreV` en todos los dispositivos con la extensión S ≥ 13, incluido Android 15+.
  5. **Trabajo único** `ingest` con `APPEND_OR_REPLACE`. Los ids van en `inputData`, con una etiqueta por documento para derivar «En cola». Sin servicio en primer plano en F06; lo añade F07.
  6. **Extracción compartida con el escritorio:** la app porta la semántica Python de `extract.py`, comprobada con un golden. `extract.py` lee con `decode_text` (UTF-8 con BOM opcional, respaldo cp1252), lo que no cambia la extracción del corpus de F05.
- **Motivo:**
  - El recuento de páginas sin texto debe sobrevivir a un reinicio (US3-4, FR-008), y F08 podrá mostrarlo.
  - Un intento fallido no tiene copia que conservar, y su URI no se puede releer tras la muerte del proceso.
  - Un solo camino de PDF es más fácil de probar.
  - La cola debe distinguir «Listo» de «En cola» sin una tabla nueva.
  - La evaluación de F05 solo representa a la app si ambas extraen igual.
- **Alternativas descartadas:**
  - guardar las páginas omitidas en `documents.error`, o no persistirlas;
  - persistir los intentos en DataStore;
  - permisos persistentes de URI;
  - `PdfRenderer` en API 35+ con `PdfRendererPreV` por debajo;
  - `ExistingWorkPolicy.KEEP`;
  - un worker por documento.
- **Origen:** plan de F06 (research R2–R11).
- **Afecta:** F06; F07 (amplía el worker y reutiliza la extracción); F08 (`skipped_pages`); `offline/` (esquema v2 en `appconfig` y reconstrucción de índices).

---

## D-039 — Indexación (clarify de F07)

- **Fecha:** 2026-10-26
- **Decisión:**
  1. **Backend de la ingesta:** los embeddings se calculan en la GPU, como propone D-024. Si la GPU no carga el engine, se usa la CPU y se registra. El usuario no elige el backend.
  2. **Pausa:** un solo botón pausa y reanuda toda la tanda, como en el PNG 08. No hay «Cancelar» global; un documento concreto se cancela con «Quitar de la cola» de la Ingesta.
  3. **Pausas automáticas:** la indexación se pausa si el sistema informa una temperatura grave o superior (`THERMAL_STATUS_SEVERE`) y sigue sola al bajar a moderada o menos. La batería no la pausa. «Solo mientras carga» y la sección de recursos quedan en el backlog (G21).
  4. **Volumen:** sin límite de fragmentos por tanda ni aviso previo. Tras el primer lote se muestra el tiempo restante estimado con el ritmo medido en el teléfono, marcado como aproximado.
- **Motivo:**
  - la GPU es unas 2,5 veces más rápida en la ingesta (D-024) y la mezcla de vectores CPU/GPU está validada;
  - el PNG solo tiene un botón, y «Quitar de la cola» ya cancela un documento con confirmación;
  - el sistema ya limita el trabajo con el ahorro de batería, y una pausa por batería alargaría las indexaciones sin necesidad;
  - una estimación previa no sería fiable antes de medir el ritmo en el propio teléfono (constitución V).
- **Alternativas descartadas:**
  - solo CPU, o que el usuario elija el backend;
  - «Cancelar todo», o pausa por documento;
  - pausar también con batería baja, o pausar ya en temperatura moderada;
  - un aviso previo por encima de 1 h, o un límite de fragmentos por tanda.
- **Origen:** usuario (clarify de F07).
- **Afecta:** F07; F11 (el coordinador de engines decide si el chat necesita la GPU durante una indexación); F12 (no hay ajuste de backend).

---

## D-040 — Diseño de la indexación (plan de F07)

- **Fecha:** 2026-10-26
- **Decisión:**
  1. **Ejecución:**
     - el `IngestWorker` es un worker largo de WorkManager en primer plano, de tipo `dataSync` (que incluye «procesamiento de archivos locales»);
     - si el sistema no deja arrancar el primer plano, sigue como job normal en tramos y recupera el primer plano en cuanto la app está visible;
     - al abrir la app se reconcilia la tanda.
  2. **Sin planificador en proceso:** `Configuration.Builder.setGreedySchedulerEnabled(false)` (WorkManager 2.12.0, experimental). Evita el trabajo huérfano y congelado que se vio en el cierre de F06.
  3. **Dos fases por tanda:** primero se trocean todos los documentos pendientes, para conocer el total en segundos. Después, por documento, se vuelve a trocear, se embebe y se guarda en lotes de 64. Al reanudar, se comparan los guardados y, si no coinciden, se rehace el documento.
  4. **Visibilidad:** las búsquedas piden *k* + *m* candidatos (*m*, los fragmentos no buscables) y filtran `status = 'done'` e `included = 1`. Sin cambio de esquema; F10 lo hereda.
  5. **Tanda en DataStore:** ids, pausa y causa persistente (usuario, sin espacio, modelo ausente). La temperatura y el límite del sistema se derivan en vivo.
  6. **Engine retenido:** el de la ingesta no se libera al ocultarse la UI; el LLM sí, y se libera al empezar la ingesta.
  7. **Título de la cabecera y del FTS:** el nombre del documento sin extensión (`DocumentTitle`), como los títulos del escritorio.
  8. **GPU validada por dispositivo:** la ingesta usa la GPU solo si, en la primera sesión, 3 textos fijos dan coseno ≥ 0,99 frente a la CPU (D-024). Se repite al cambiar el modelo o la app. En un emulador, nunca GPU: LiteRT se bloquea al cargarla (F04).
  9. **Estados:** `paused` y `cancelled` de `documents` (D-030) no se usan; la pausa es de la tanda.
  10. **Etapas del PNG 08 (G29):** «Persistencia vectorial» es el cierre del documento. El cierre no hace `optimize` del FTS ni reconstruye INT8 (aplazado a F14).
- **Motivo:**
  - Android 15 limita `dataSync` a 6 h cada 24 (el contador se reinicia al traer la app al primer plano);
  - Android 16 somete a cuota los jobs que corren con un servicio en primer plano;
  - no se puede arrancar un servicio en primer plano desde un job en segundo plano;
  - UIDT exige red;
  - los logs de F06 muestran que el planificador en proceso deja el worker sin job.
- **Alternativas descartadas:**
  - servicio en primer plano propio, `specialUse`, `mediaProcessing` y UIDT;
  - el modo *streaming* de SQLite-Vector con `ORDER BY`, o una tabla de preparación (esquema v3);
  - guardar los fragmentos en memoria entre las fases.
- **Origen:** plan de F07 (research R1–R12).
- **Afecta:** F06 (el trabajo `ingest` y la cola), F03 (descargas en API 31–33 sin planificador en proceso), F10 (búsquedas ya filtradas), F11 (coordinador con engines retenidos) y F14 (INT8 con el mismo filtro).

---

## D-041 — Despertar la tanda tras una muerte sin primer plano (implementación de F07)

- **Fecha:** 2026-10-27
- **Decisión:**
  1. **Alarma inexacta** (`AlarmManager.set`, `ELAPSED_REALTIME_WAKEUP`, 15 min, sin permisos especiales) mientras la tanda tiene documentos sin terminar:
     - el repositorio la arma al encolar y al reconciliar, y la cancela al pausar;
     - el worker la adelanta cada minuto mientras corre y la cancela al terminar la tanda;
     - al sonar, el receptor no exportado de la notificación reconcilia y la vuelve a armar si queda trabajo.
  2. **La notificación caduca** a los 3 min (`setTimeoutAfter`, en todas). El worker la refresca cada minuto, también durante la espera térmica, así que solo desaparece si el proceso ha muerto. Android nunca hace caducar la de un servicio en primer plano (`NotificationManagerService.timeoutNotification` excluye `FLAG_FOREGROUND_SERVICE`): esa la quita con el servicio. La caducidad va también en la que se entrega a WorkManager, porque tras un reinicio su servicio arranca pero no llega al primer plano (`dataSync` no puede empezar desde `BOOT_COMPLETED`) y publica la notificación como normal (T045 en el teléfono).
- **Motivo:**
  - **Fallo observado:** si el proceso muere mientras el trabajo corre como job normal (sin primer plano), JobScheduler deja el job listo pero no lo encola, y el trabajo no vuelve hasta abrir la app. Pasó en el emulador API 37 (más de 40 min) y en el teléfono (Android 15). Además, la notificación normal se quedaba diciendo «Indexando documentos».
  - **Causa, en el código de Android 15** (`JobSchedulerService`): el job reprogramado tras la muerte solo se encola en la siguiente revisión general (`MSG_CHECK_JOB`), que disparan cambios globales (constantes, temperatura, batería, desbloqueo). En un dispositivo quieto puede tardar mucho.
  - **Por qué basta con arrancar el proceso:** WorkManager (`ForceStopRunnable`) vuelve a programar el trabajo que quedó en marcha, y un job recién programado que está listo se ejecuta en el acto (`scheduleAsPackage`). Es lo que pasa al abrir la app.
- **Alternativas descartadas:**
  - **aceptarlo y documentarlo:** el trabajo podía quedar parado indefinidamente con una notificación falsa;
  - **alarma exacta:** permite arrancar el primer plano, pero exige `SCHEDULE_EXACT_ALARM` concedido por el usuario o `USE_EXACT_ALARM`, que es solo para alarmas y calendarios;
  - **trabajo expedited:** no se agrupa, pero tiene cuota y límite de duración, y no sirve para tandas de una hora.
- **Origen:** verificación temprana T045 (research R2), en el emulador y en el teléfono.
- **Afecta:** F07 (worker, repositorio y receptor). F14 mide el coste de la alarma en batería si hace falta.

---

## D-042 — Búsqueda durante la indexación, notificación de la tanda y pantalla compacta (cierre de F07)

- **Fecha:** 2026-10-27
- **Decisión** (propuestas del informe de cierre, aceptadas por el usuario):
  1. **Búsqueda con un documento grande a medias:** se acepta y se registra que supere el presupuesto de consulta de la constitución IV (< 150 ms). Con *m* = 4.300 fragmentos ocultos, el p95 vectorial sube a ≈ 45 ms y, con el embedding de consulta (≈ 120 ms), suma ≈ 165 ms. Solo ocurre mientras se indexa un documento grande. F10 vuelve a medir con la recuperación híbrida y F14 evalúa INT8.
  2. **Notificación con las cifras de la tanda:** «N de M fragmentos» de toda la tanda, las mismas que la pantalla, en lugar de las del documento en curso. El runner las añade a su progreso (`runChunksDone`, `runChunksTotal`), contadas como en el repositorio.
  3. **Pantalla compacta, como el PNG 08:**
     - filas de etapas `LisdataStageRow(compact)` de 56 dp, con textos de 16 y 13 sp (`compactTitle` y `compactBody`);
     - héroe `LisdataHeroCard(compact)` con márgenes de 16 dp y huecos de 8;
     - anillo de 154 dp.

     Con eso, «Procesando ahora» se ve sin desplazar en el teléfono de referencia (`IndexScreenLayoutTest`, solo en el A063). Con tiempo restante y otros documentos en cola, se corta la línea de la cola.

     Las filas solas no bastaban (el héroe medía ≈ 318 dp frente a ≈ 240 en el PNG), y el usuario eligió compactar también el héroe.
- **Motivo:** T062 (búsquedas), la propuesta P11 (no fijaba qué cifras mostrar) y la comparación con el PNG 08 (T060).
- **Alternativas descartadas:**
  - búsqueda sin filtro o en *streaming* (no bajan de 150 ms, T062);
  - mantener la notificación por documento: no coincidía con la pantalla;
  - filas compactas sin tocar el héroe, o dejarlo para F15: la tarjeta seguía bajo el pliegue.
- **Origen:** cierre de F07.
- **Afecta:** F07; F09 puede reutilizar las variantes compactas; F10 y F14 (presupuesto de consulta).

---

## D-043 — Biblioteca y detalle (clarify de F08)

- **Fecha:** 2026-10-28
- **Decisión:**
  1. **Quitar del índice (G17):** el documento pasa a un estado propio, «Fuera del índice». Conserva la copia privada y los metadatos, no aparece en la cola de Ingesta ni entra en ninguna tanda, tampoco con «Iniciar indexación». Se ve en la Biblioteca, en el filtro Pendientes, y solo vuelve a indexarse con «Reindexar» desde su detalle.
  2. **Vista previa desde una cita:** muestra el fragmento citado entero y resaltado, con la última frase del fragmento anterior y la primera del siguiente sin resaltar, como en el PNG 07. El chip de página es el del fragmento citado.
  3. **Vista previa sin cita:** el texto real del primer fragmento, con su página. No hay navegación entre fragmentos.
- **Motivo:**
  - si volviera a «Pendiente», la siguiente «Iniciar indexación» desharía la acción sin que el usuario lo pidiera;
  - las citas de F11 apuntan a fragmentos enteros, no a frases, y el PNG muestra el resaltado con texto alrededor;
  - el PNG solo maqueta un extracto, y leer el documento entero no es el objetivo del detalle.
- **Alternativas descartadas:**
  - volver a «Pendiente» tras quitar del índice;
  - resaltar solo el fragmento sin contexto, o no resaltar;
  - una vista previa navegable con «Anterior» y «Siguiente».
- **Origen:** usuario (clarify de F08).
- **Afecta:** F08; F06 y F07 (la cola y las tandas ignoran los documentos fuera del índice); F11 (abre el detalle con el fragmento citado).

---

## D-044 — Diseño de la Biblioteca y el detalle (plan de F08)

- **Fecha:** 2026-10-28
- **Decisión:**
  1. **Esquema v3** (`documents.name_norm` y `documents.replaces_id` con `ON DELETE CASCADE`, más un índice por fecha de importación). Se añade con `ALTER TABLE` y la migración `from = 2`; `name_norm` se rellena en Kotlin al abrir un índice migrado.
  2. **Búsqueda por nombre:** subcadena sobre el nombre plegado (minúsculas, sin acentos, sin quitar sufijos), con los comodines escapados.
  3. **Paginación:** bloques de 50 con un límite que crece y una nueva consulta en cada cambio del índice. Sin Paging 3. La lista nunca lee fragmentos.
  4. **«Fuera del índice»** (D-043): se guarda como `cancelled`, un valor que el esquema admitía sin uso. En el dominio se llama `OUT_OF_INDEX`. La cola de Ingesta y las tandas ya lo ignoran.
  5. **Reindexar sin huecos:** un documento sombra (`replaces_id`) se indexa con la tanda de F07. Al cerrarlo, una sola transacción borra los fragmentos del original, le pasa los de la sombra, actualiza sus cifras y borra la sombra. Las sombras no aparecen en la Biblioteca ni en la cola.
  6. **Un solo camino de borrado** para «Eliminar» y «Quitar de la cola». Hace en una transacción el borrado en cascada y la marca de `quant_dirty`; después saca el documento de la tanda y borra la copia. Si eso falla, la copia la recoge la limpieza de huérfanos, que también corre al abrir la Biblioteca.
- **Motivo:** research R1–R6 de F08. La alternativa de cambiar el `CHECK` de `status` exige reconstruir `documents`. Borrar y volver a indexar dejaría el documento fuera de las respuestas durante el reindexado.
- **Alternativas descartadas:**
  - buscar en memoria;
  - Paging 3;
  - reconstruir la tabla para un estado nuevo;
  - ordinales negativos o tabla de preparación para reindexar;
  - borrar y volver a indexar.
- **Origen:** plan de F08 (research R1–R11).
- **Afecta:** F04 (esquema v3 y operaciones nuevas del índice); F06 (cola sin sombras y borrado compartido); F07 (el runner publica las sombras); `offline/` (esquema v3); F10 y F14 (las sombras cuentan como fragmentos ocultos).

---

## D-045 — Cierre de F08: interruptor, vista previa y decisiones de implementación

- **Fecha:** 2026-10-28
- **Decisión** (propuestas del informe de cierre, aceptadas por el usuario):
  1. **Subtítulo del interruptor del detalle según el estado:**
     - incluido e indexado: «Disponible para consultas RAG», como en el PNG 07;
     - excluido: «No se usa en las respuestas»;
     - sin indexar: «Se podrá incluir cuando esté indexado», el texto que TalkBack ya lee en la fila de la Biblioteca.
  2. **Vista previa sin cita recortada a 3 líneas**, con «Ver más» y «Ver menos». La cita se sigue mostrando entera. Se propusieron unas 6 líneas, pero con 6 «Eliminar» seguía bajo el pliegue en el teléfono de referencia, con sus barras de sistema. Con 3, las del extracto del PNG 07, las acciones quedan a la vista con muy poco margen si el nombre ocupa una línea (`DocumentScreenLayoutTest`, solo en el A063).
  3. **Decisiones de implementación** (informe de cierre, §6):
     - `idx_documents_replaces`, cuarto paso de la migración v3: sin él, la cascada y el estado de reindexado de cada fila recorren la tabla;
     - `IndexingRepository.add(id)`: mete en la tanda un único documento, porque `start()` metería también los pendientes que el usuario no mandó indexar;
     - `DocumentFiles`: cerrojo compartido y limpieza de huérfanos una vez por proceso, para la Ingesta y la Biblioteca;
     - chip del héroe con el estilo de éxito normal, como el PNG 07, en lugar de la variante para fondo oscuro del contrato;
     - causa de un reindexado fallido (D6): la primera frase del detalle del error, para no repetir «No se pudo reindexar: No se pudo indexar.»;
     - contexto de la cita sin el párrafo que el `Chunker` arrastra al principio del fragmento siguiente (research R7, punto 5);
     - acciones del detalle medidas: la principal ocupa el resto de la fila y «Eliminar» lo que necesita, con relleno compacto (`compact` en `LisdataGradientButton` y `LisdataOutlinedButton`); si no caben en una línea, se apilan.
- **Motivo:**
  - el subtítulo fijo no era cierto con el documento excluido o sin indexar (constitución V);
  - el primer fragmento real tiene ≈ 1.500 caracteres y dejaba las acciones a cuatro desplazamientos, cuando el PNG 07 las muestra a la vista;
  - las de implementación salieron de la implementación y de la prueba en el teléfono (T049, T053).
- **Alternativas descartadas:**
  - el subtítulo fijo del PNG;
  - la vista previa entera;
  - recortarla a 6 líneas.
- **Origen:** cierre de F08.
- **Afecta:** F08; F11 (la cita se ve entera, con su contexto); F15 (auditoría de estados y de la fuente grande).

---

## D-046 — Inicio (clarify de F09)

- **Fecha:** 2026-10-28
- **Decisión:**
  1. **Cifras de «TU BIBLIOTECA»:**
     - documentos: todos los importados, en cualquier estado, el mismo número que la Biblioteca en «Todos»;
     - fragmentos: los de los documentos indexados, incluidos o excluidos;
     - en ningún caso cuentan los documentos sombra de un reindexado (D-044).
  2. **Tarjeta de indexación:**
     - va entre el héroe y los cuatro accesos;
     - se muestra mientras haya una tanda activa (indexando, esperando o en pausa, con la causa);
     - desaparece al terminar la tanda, aunque queden documentos con error.
  3. **IA local no lista:** «Nuevo chat», Importar y los demás accesos siguen activos. El chat (F11) explicará lo que falta con un acceso a la Preparación (MD 03).
  4. **Documentos sin indexar y sin tanda:** el resumen muestra solo las cifras reales, sin aviso. El estado vacío solo aparece con 0 documentos.
- **Motivo:**
  - las cifras coinciden con lo que el usuario ve en la Biblioteca;
  - los fragmentos son los que el asistente puede usar si su documento está incluido;
  - la tarjeta se ve sin desplazar en el teléfono de referencia;
  - la importación no necesita los modelos;
  - los pendientes ya se ven en la Ingesta y en la Biblioteca, y un aviso más añadiría un elemento que el PNG 03 no tiene.
- **Alternativas descartadas:**
  - contar solo los indexados, o solo los incluidos;
  - la tarjeta al final, junto al resumen (queda bajo el pliegue);
  - mantenerla visible con los errores de la última tanda;
  - desactivar «Nuevo chat» o llevarlo a la Preparación sin modelos;
  - un aviso «N documentos sin indexar».
- **Origen:** usuario (clarify de F09).
- **Afecta:** F09; F11 (el chat sin modelos).

---

## D-047 — Diseño de Inicio (plan de F09)

- **Fecha:** 2026-10-28
- **Decisión:**
  1. **Estado de la IA local:** un caso de uso que primero llama a `refresh()` de F03 (registro de verificación, sin huellas) y después sigue `states` y `preparedBackends`. La regla «listo» del onboarding se extrae a `ModelCatalog.allReady` y la comparten los dos. Sin chip hasta la primera comprobación. Se vuelve a comprobar en cada `ON_RESUME` de Inicio.
  2. **Resumen:** operación nueva `RagIndex.librarySummary()`, una consulta sobre `documents`: `count(*)` sin sombras y `SUM(chunk_count)` de los `done`. `LibraryRepository.summary()` la recalcula con cada `documentChanges`, con el mismo patrón y el mismo `Unavailable` que la Biblioteca.
  3. **Tarjeta de indexación:** `LisdataProgressCard`, que pasa a admitir `onClick`, con las cifras y las causas de pausa de F07.
  4. **Navegación:**
     - «Nuevo chat» abre la pestaña Chat con una conversación nueva, sin restaurar la anterior (`openNewChat`);
     - Documentos y el icono de Ajustes, como sus pestañas;
     - cada acción solo navega con Inicio en `RESUMED`;
     - la Preparación abierta desde Inicio vuelve a Inicio sin duplicarlo.
  5. **Fuente grande:** con el 150 % o más, los accesos van en una columna y las cifras del resumen se apilan (el umbral de F08 A6).
  6. **Héroe:** `LisdataHeroCard` admite `decorationIcon` para el destello de las órbitas. El título va en dos líneas blancas.
- **Motivo:** research R1–R8 de F09.
  - Calcular huellas al abrir Inicio iría contra F03 SC-007.
  - Contar `chunks` costaría lo mismo que una lista.
  - Restaurar la pestaña Chat reabriría la conversación anterior, y el MD 03 pide una nueva.
- **Alternativas descartadas:**
  - `ReconcileDownloadsUseCase` en Inicio;
  - una marca «escaneado» en el repositorio de modelos;
  - `RagIndex.counts()` (cuenta sombras y documentos a medias);
  - `count(*)` sobre `chunks`;
  - `navigateToTopLevel(CHAT)` para «Nuevo chat»;
  - una tarjeta de indexación con anillo.
- **Origen:** plan de F09.
- **Afecta:** F09; F01 (dos parámetros opcionales); F03 (`allReady` y la vuelta de la Preparación); F06 y F07 (sin los accesos provisionales); F11 (la pestaña Chat abierta con una conversación nueva).

---

## D-048 — Cierre de F09: resumen bajo el pliegue, «Completar configuración» y decisiones de implementación

- **Fecha:** 2026-10-29
- **Decisión** (propuestas del informe de cierre; el usuario delegó la elección):
  1. **Resumen bajo el pliegue:** se deja como está. En el teléfono de referencia, con la barra de navegación de tres botones, la tarjeta «TU BIBLIOTECA» asoma por el borde y se ve entera al desplazar un poco.
  2. **«Completar configuración»:** sigue siendo un botón propio, en la línea siguiente al chip cuando no cabe a su lado. El chip «Configuración pendiente» sigue siendo solo un estado.
  3. **Decisiones de implementación** (informe de cierre, §6):
     - `SummaryResult` va en `LibraryRepository.kt`, junto a `LibraryResult` y `DetailResult`, y no en `LibraryModels.kt` como decía data-model §2;
     - durante la primera comprobación, una copia invisible del chip «IA local disponible», sin semántica, reserva su alto con cualquier tamaño de letra;
     - los logs de Inicio los escribe `HomeViewModel`, con `AppLogger` y un reloj inyectables;
     - la prueba del 200 % comprueba también que ninguna palabra se parta entre líneas.
- **Motivo:**
  - Para que el resumen se vea entero, el contenido tendría que subir unos 70 dp. Compactar los cinco huecos de 16 dp que hay por encima solo da 40.
  - La diferencia con el PNG viene sobre todo de la altura de los componentes de F01: los accesos miden unos 125 dp frente a unos 97 en el PNG, y el héroe unos 204 frente a 175. Encogerlos cambiaría todas las pantallas que los usan.
  - Con «Configuración pendiente» o con una tanda en curso el resumen quedaría igualmente bajo el pliegue.
  - Un botón con su texto dice lo que hace. Un chip tocable no se distingue de un chip de estado.
  - Sin la comprobación de palabras partidas, con dos columnas «Documentos» se partía sin puntos suspensivos y la prueba no lo veía.
- **Alternativas descartadas:**
  - compactar los huecos entre bloques;
  - hacer tocable el chip «Configuración pendiente» y quitar el botón.
- **Origen:** cierre de F09.
- **Afecta:** F09; F15 (auditoría de fidelidad: altura de los accesos y del héroe frente al PNG).

---

## D-049 — Recuperación híbrida (clarify de F10)

- **Fecha:** 2026-10-29
- **Decisión:**
  1. **Parámetros de contexto:** los de D-034, es decir 4 bloques, sin vecinos, como mucho 3 por documento y 1.719 tokens estimados.
     - La ficha de F10 y la constitución (III) conservaban los valores iniciales del informe: 6 bloques, ≈ 2.200 tokens y vecinos de los dos primeros.
     - Se corrige la ficha.
     - La constitución pasa a 1.1.1 (PATCH): su línea de contexto remite a la última evaluación registrada en DECISIONS.
     - El selector admite vecinos y se prueban, aunque D-034 los deje a 0.
  2. **Revisión del orden dentro del presupuesto (D-035):**
     - se evalúa en el harness de escritorio con dos variantes: trigramas como tercera lista de la fusión y una reordenación de los primeros candidatos;
     - una variante se adopta solo si sube el Recall@bloques al menos 2 puntos sobre las 370 preguntas sin pasar el presupuesto del prompt;
     - si se adopta, va a una decisión nueva y F10 la lleva al móvil; si no, se queda D-034 y se anota.
  3. **Paridad con el escritorio:** una prueba instrumentada en el teléfono de referencia, con los modelos reales y lanzada bajo demanda:
     - indexa los 21 documentos del corpus de evaluación en un índice temporal, nunca en la biblioteca del usuario;
     - usa los títulos del corpus;
     - un bloque acierta si contiene la evidencia literal de la pregunta;
     - usa una muestra fija de 50 preguntas, con la cifra de escritorio sobre esas mismas 50;
     - las preguntas cuya evidencia no se extrae en el móvil se cuentan aparte.

     El «Recall@6» de la ficha es el Recall@bloques con los bloques de D-034.
  4. **Latencia:** p95 de una recuperación completa ≤ 200 ms con 10.000 fragmentos y el motor de consulta cargado; ≤ 1 s durante una tanda de indexación.
- **Motivo:**
  - D-034 es la evaluación más reciente, y la constitución ya permitía cambiar los parámetros con una evaluación documentada. El texto contradictorio confundía.
  - Según D-035, el margen de calidad está en el orden de los primeros puestos: en 36 de las 80 respuestas fallidas, el relevante estaba entre los 20 primeros candidatos. Cualquier cambio necesita una evaluación (constitución III).
  - En el móvil cambian los títulos (nombre del fichero) y la extracción de los PDF, que mueve los ordinales. Con los títulos del corpus y la evidencia literal, la comparación es justa sin tocar la biblioteca del usuario.
  - 200 ms cubre lo ya medido: embedding ≈ 120 ms, vectorial ≈ 23 ms y léxica ≈ 27 ms en p95. Así el primer token sigue dentro de los 5 s (D-026).
- **Alternativas descartadas:**
  - los valores del informe;
  - D-034 sin enmendar la constitución;
  - dejar la revisión del orden para F14;
  - añadir los trigramas en el móvil sin evaluarlos;
  - importar el corpus con la app y probar a mano;
  - medir solo en el emulador;
  - un objetivo de 150 ms, o ninguno.
- **Origen:** usuario (clarify de F10).
- **Afecta:** F10; F11 (el contexto y su presupuesto); F14 (INT8 según la medición); constitución 1.1.1.

---

## D-050 — Diseño de la recuperación (plan de F10)

- **Fecha:** 2026-10-29
- **Decisión:**
  1. **Port exacto del harness:**
     - la regla de seguimiento, la fusión RRF (con su desempate) y el selector de contexto se portan de `offline/eval/retrieval.py` y `selector.py` a `rag/retrieval`;
     - la paridad se comprueba en la JVM con los casos de Python y con un golden de las 426 preguntas vivas (`offline/golden/retrieval_golden.jsonl`).
  2. **Candidatos fusionados:** `fused_limit = 30` pasa a `rag_parameters.json` y a `RagParameters`, sin cambiar su valor.
  3. **Léxica en paralelo:** la búsqueda léxica empieza a la vez que el embedding de la consulta; la vectorial, cuando hay vector. Una sola conexión, como hasta ahora.
  4. **Solo fragmentos buscables:** `loadChunks` y `neighbor` aceptan `searchableOnly`. La recuperación carga solo fragmentos de documentos `done` e incluidos, aunque cambien entre la búsqueda y la carga.
  5. **Motores de embeddings compartidos:**
     - el `EngineCoordinator` deja que varios bloques usen a la vez un motor de embeddings, que ya serializa sus llamadas en su único hilo;
     - el LLM sigue siendo exclusivo;
     - así, una consulta durante una tanda en CPU espera al fragmento en curso, no al final de la tanda.
  6. **Consulta demasiado larga:** si el modelo rechaza el texto embebido, se recorta primero la pregunta anterior y después la actual, con la regla de D-036. La búsqueda léxica usa el texto completo.
  7. **Resultado tipado sin excepciones:**
     - bloques;
     - sin resultados;
     - pregunta vacía;
     - índice no disponible;
     - modelos no listos;
     - fallo del embedding.

     Lleva tiempos y puntuaciones, y el registro solo lleva cifras.
  8. **Paridad en el teléfono:**
     - `offline/corpus` se empaqueta como assets del APK de pruebas;
     - la muestra fija de 50 preguntas (40 respondibles y 10 de seguimiento, con su acierto de escritorio) se genera en `offline/golden/retrieval_parity.jsonl`.
  9. **Orden de trabajo:** las pruebas en el teléfono de referencia van en la última fase (petición del usuario).
- **Motivo:**
  - El golden sobre datos reales localiza en la JVM cualquier divergencia con el escritorio, antes de tener el teléfono.
  - Compartir el motor no gasta más memoria y no gana nada duplicarlo, porque los dos usarían el mismo hilo de embeddings.
  - Solapar la léxica con el embedding ahorra su tiempo (≈ 26 ms) sin una segunda conexión.
- **Medición previa de D-035** (detalle en research R12):
  - sobre el índice de escritorio, la mejor variante (reordenar los 20 primeros por puntuación combinada) sube el Recall@bloques de 0,889 a 0,903 (+1,4 puntos);
  - los trigramas lo bajan a 0,873;
  - ninguna llega a la regla de adopción. La decisión formal se registra al implementar, con la ejecución del harness.
- **Alternativas descartadas:**
  - un segundo motor de CPU solo para consultas;
  - que la ingesta tome el motor fragmento a fragmento;
  - un dispatcher propio para la consulta (plan B si SC-007 falla);
  - una segunda conexión de solo lectura;
  - filtrar los documentos uno a uno tras cargar;
  - recortar la consulta por caracteres;
  - copiar el corpus al teléfono con adb.
- **Origen:** plan de F10 (research R1–R14).
- **Afecta:** F07 (coordinador de motores), F11 (consume la recuperación y comparte el coordinador) y F14 (INT8, y la reordenación como palanca).

---

## D-051 — Revisión del orden dentro del presupuesto (D-035, F10)

- **Fecha:** 2026-10-29
- **Decisión:** la app mantiene la recuperación de D-034. Ninguna variante del orden cumple la regla de adopción del clarify de F10 (+2 puntos de Recall@bloques sin pasar el presupuesto).
- **Cifras** (`run_eval.py order-review`, índice `ref`, 370 preguntas puntuables, configuración de D-034):

  | Variante | Recall@bloques | R@20 híbrido | MRR híbrido |
  |---|---:|---:|---:|
  | D-034 | 0,889 | 0,984 | 0,810 |
  | Trigramas como tercera lista de RRF | 0,873 | 0,976 | 0,787 |
  | Reordenación de los 20 primeros, α = 0,3 | 0,889 | 0,984 | 0,800 |
  | Reordenación de los 20 primeros, α = 0,5 | **0,903** | 0,984 | 0,825 |
  | Reordenación de los 20 primeros, α = 0,7 | 0,897 | 0,984 | 0,817 |
  | Trigramas + reordenación, α = 0,5 | 0,900 | 0,976 | 0,822 |

  La reordenación ordena los primeros de la fusión por α · similitud coseno + (1 − α) · BM25, normalizados min-max en cada lista.
- **Motivo:**
  - La mejor variante, la reordenación con α = 0,5, sube 1,4 puntos: no llega a los 2 de la regla. Con 370 preguntas son unas 5 preguntas más.
  - Los trigramas empeoran: sus coincidencias parciales desplazan a los relevantes. Además exigirían una tabla nueva en el índice (esquema v4).
- **Para F14:** la reordenación con α = 0,5 queda anotada como palanca barata si se revisa el contexto. Usa las puntuaciones que las dos búsquedas ya calculan, sin consultas nuevas.
- **Dónde está:** las dos variantes viven en el harness (`offline/eval/retrieval.py`, con pruebas). El informe `offline/reports/eval-report.md` tiene la sección «Revisión del orden (D-035, F10)».
- **Origen:** D-035 y clarify de F10 (D-049); medición previa en research R12 de F10, confirmada por la ejecución formal.
- **Afecta:** F10 (sin cambios en la app) y F14.

---

## D-052 — Latencia de la recuperación en el teléfono e INT8 (cierre de F10)

- **Fecha:** 2026-10-29
- **Decisión:**
  1. **Total por encima de 200 ms (SC-003):** se acepta en F10 y lo retoma F14. Para esa decisión el usuario vio la primera tanda; las otras dos se presentan en la revisión de cierre. `RetrievalBenchmarkTest` registra el total, pero no lo comprueba.
  2. **Embedding de consulta (constitución IV, < 150 ms):** se cumple con el coste del modelo. Medido solo, sin nada en paralelo, da p95 125,6 ms. Dentro de la búsqueda, la léxica compite con él por la CPU y lo lleva al borde del límite (una de tres tandas pasa en 1,1 ms). En la revisión de cierre, el usuario pidió que `RetrievalBenchmarkTest` compruebe en el A063 el embedding solo. La cifra dentro de la búsqueda solo se registra, y F14 la retoma junto con el total.
  3. **INT8 + precarga en F14 (FR-026):**
     - el p95 vectorial pasa de los 20 ms de la constitución IV: 51,9–52,9 ms con 10.000 fragmentos, y 66,9–68,3 ms con 4.300 ocultos;
     - F14 lo implementa con Recall@20 de INT8 frente a F32 ≥ 0,97 y vuelve a medir el total frente a 200 ms.
- **Cifras** (A063, Android 15, 10.000 fragmentos sintéticos, las 50 preguntas de la muestra en 2 vueltas, motor de consulta en CPU y cargado; p95 en ms):

  | Tanda | Embedding en la búsqueda | Embedding solo | Vectorial | Léxica | Total |
  |---|---:|---:|---:|---:|---:|
  | 1 | 143,6 | — | 51,9 | 118,8 | 202,1 |
  | 2 | 151,1 | — | — | — | 205,5 |
  | 3 | 144,4 | 125,6 | 52,9 | 109,2 | 211,0 |

  - Medianas de la tanda 3: embedding solo 122,5 ms y dentro de la búsqueda 134,2 ms. El solapamiento le cuesta unos 12 ms de mediana y 19 de p95.
  - Con 4.300 fragmentos ocultos, el total p95 da 216,1 ms (tanda 1) y 227,5 ms (tanda 3).
- **Motivo:**
  - El exceso es pequeño, entre el 1 y el 6 %. Viene de que la léxica y el embedding comparten la CPU, y la vectorial espera a la conexión única mientras la léxica no termina.
  - Este banco es pesimista para la léxica: las palabras de las preguntas aparecen en casi todos los fragmentos sintéticos. Aquí la léxica tiene un p95 de 109–119 ms; F04 midió 26,5 ms con consultas de 4 palabras.
  - Las palancas de rendimiento son de F14: INT8, precarga y la medida con corpus reales. La spec de F10 deja INT8 fuera de alcance.
  - Para F11 la recuperación cuesta unos 0,2 s, poco frente al presupuesto de 5 s del primer token (D-026).
- **Alternativas descartadas:**
  - serializar la léxica después del embedding: dejaría de competir por la CPU, pero sumaría la léxica entera al total (p95 de 109–119 ms en este banco);
  - implementar INT8 en F10, que está fuera de su alcance.
- **Origen:** T048 de F10. Respuestas del usuario en su parada: aceptar el total y pasarlo a F14, INT8 más precarga en F14, y medir el embedding solo antes de decidir. En la revisión de cierre (2026-10-29), aprobó el cierre con las tres tandas y pidió que la prueba compruebe el embedding solo.
- **Afecta:** F10 (cierre), F11 (el coste de la recuperación) y F14 (INT8 + precarga, y el total frente a 200 ms).

---

## D-053 — Chat con fuentes (clarify de F11)

- **Fecha:** 2026-10-29
- **Decisión:**
  1. **Citas en la respuesta (G11):**
     - como el PNG: el chip «N fuentes» y «FUENTES CONSULTADAS» solo con las fuentes citadas;
     - además, cada `[n]` válido del texto se muestra como una ficha pequeña tocable que abre su fragmento;
     - las fichas se numeran según el orden de la lista (1 = primera fuente listada), no con el número del bloque en el prompt;
     - las marcas fuera de rango se descartan y no se ven.
  2. **Chat durante una indexación (D-039):** mientras una tanda está en marcha, el chat espera:
     - explica que podrá preguntar cuando termine la tanda, con acceso al Estado de indexación;
     - no carga Gemma ni acepta preguntas;
     - la conversación y sus fuentes siguen visibles;
     - con la tanda en pausa, el chat funciona con los documentos ya listos y avisa de la cobertura parcial;
     - si una tanda empieza durante una respuesta, la respuesta termina antes.

     La constitución IV no cambia: el LLM se libera durante la ingesta.
  3. **Conversaciones guardadas:**
     - todas las conversaciones con mensajes se guardan en el almacenamiento privado del teléfono, sin copia de seguridad, con sus mensajes, estados y fuentes;
     - la pestaña Chat abre la última, también tras cerrar la app, y «Nuevo chat» empieza otra (D-047);
     - las anteriores quedan para el historial de F13.
  4. **Plantilla del prompt (D-035):** se evalúa en el harness de escritorio, como D-051:
     - 1 o 2 variantes sobre las mismas preguntas que F05;
     - juicio con la regla automática y revisión de las respuestas que cambian (método D-033);
     - se adopta una variante solo si sube ≥ 2 puntos las respuestas correctas, con negativas ≥ 85 % y citas ≥ 90 %, y con una decisión nueva;
     - si no, se queda la plantilla «informe».
  5. **Menú «⋯» (G13):**
     - «Nuevo chat»;
     - «Borrar conversación», con confirmación; tras borrar se abre un chat vacío.

     «Historial» se añade con F13.
- **Motivo:**
  - **Citas:** el PNG manda en la UI y el informe en la trazabilidad (constitución III). Las fichas enlazan cada afirmación con su fuente, y numerarlas como la lista evita números que no coinciden.
  - **Indexación:** es la opción más sencilla y cumple la constitución IV sin enmiendas. El coste, un chat bloqueado durante las tandas largas, se mitiga porque el usuario puede pausar la tanda.
  - **Guardado:** la ficha de F11 pide la persistencia local de los mensajes, y F13 se apoya en ella. La constitución I exige almacenamiento privado.
  - **Plantilla:** el mismo método que F10 (D-051) es reproducible y compara con las mismas preguntas.
  - **Menú:** respeta el icono del PNG sin opciones que lleven a pantallas inexistentes.
- **Alternativas descartadas:**
  - citas: solo la lista, como el PNG; o los `[n]` como texto sin tocar;
  - indexación:
    - pausar la tanda al abrir el chat;
    - pausarla solo al preguntar;
    - seguir indexando en CPU con Gemma en la GPU, que exigía enmendar la constitución IV;
  - guardado: guardar pero abrir siempre un chat nuevo; o solo en memoria hasta F13;
  - plantilla: dejarla para F14; o probar variantes en el teléfono;
  - menú: ocultar «⋯» hasta F13; o solo «Nuevo chat».
- **Propuestas de UI:** el usuario aprobó en la revisión del clarify las propuestas C1–C15 de la spec, entre ellas la negativa (G26, C1).
- **Origen:** usuario (clarify de F11).
- **Afecta:**
  - F11;
  - F07: el chat no pausa ni interrumpe la tanda;
  - F12: borrar el historial actúa sobre lo guardado;
  - F13: historial sobre lo guardado y «Historial» en «⋯»;
  - F14: si no se adopta ninguna variante, la plantilla sigue como palanca.

---

## D-054 — Diseño del chat (plan de F11)

- **Fecha:** 2026-10-29
- **Decisión:**
  1. **Port exacto del escritorio:**
     - la plantilla (`prompt.py`) y la lectura de citas y negativa (`judge.py`) se portan a `rag/answer`;
     - la paridad se comprueba en la JVM con los casos de Python y con un golden de las 426 preguntas de F05 (`offline/golden/answer_golden.jsonl`): prompt por longitud y huella, y texto completo en 10 filas.
  2. **Una sesión de chat en el ámbito de la app:**
     - una sola respuesta en curso en toda la app;
     - el texto se guarda cada segundo y al terminar;
     - la respuesta sigue al cambiar de pestaña;
     - «Nuevo chat» o borrar cancelan la respuesta en curso.
  3. **Gemma** con el backend que preparó F03, un contexto de 4.096 tokens (`llm_max_num_tokens`, parámetro compartido nuevo), 512 de salida y muestreo voraz, como el escritorio. Se precarga al abrir el chat si está disponible.
  4. **Persistencia en `chat.db`**, un fichero propio en `noBackupFilesDir/chat`:
     - tablas de conversaciones, turnos (pregunta y respuesta en bruto) y fuentes citadas;
     - abierto con la misma conexión empaquetada del índice, sin SQLite-Vector;
     - al arrancar, las respuestas a medias pasan a «interrumpidas».
  5. **App oculta:** la sesión detiene la respuesta en curso antes de que se libere Gemma. Un aviso nuevo de `EngineMemoryCallbacks` lo hace posible. D-055 fija que queda «interrumpida», con «Reintentar».
  6. **Puerta de la indexación** (D-053): con la tanda en marcha, el chat no precarga ni pregunta. Si la tanda empieza durante una respuesta, la ingesta espera a que termine, porque el coordinador no cancela el bloque en curso.
  7. **Límite del prompt:** estimación por caracteres. Si pasa de 2.000 tokens, se quitan bloques (el orden lo fija D-055). La pregunta admite como máximo 1.000 caracteres.
  8. **Citas en pantalla:**
     - fichas dentro del texto como enlaces accesibles, con el estilo del chip de cifras;
     - la fila de «Fuentes consultadas» es la vía de 48 dp a la misma fuente;
     - viñetas y negritas con un formateador propio, sin librería de Markdown.
  9. **Navegación:**
     - `Chat(newConversation = true)` para «Nuevo chat» de Inicio, y la pestaña abre la última conversación;
     - el detalle de F08 se desplaza al fragmento citado.
  10. **Revisión de la plantilla (D-035):** dos variantes fijadas de antemano:
      - `v3`: responder a todas las partes y copiar cifras y plazos tal cual;
      - `v4`: `v3` y la continuidad con la conversación previa.

      Una criba automática antes de la revisión manual de las respuestas que cambian (método D-033).
  11. **Orden de trabajo:** las pruebas en el teléfono de referencia van en la última fase. El recorrido del chat en el teléfono se acuerda antes con el usuario, porque su biblioteca tiene un documento propio.
- **Motivo:**
  - El golden sobre las respuestas reales de F05 localiza en la JVM cualquier diferencia con el escritorio, que es la referencia evaluada.
  - La respuesta no puede depender de la vida de la pantalla (FR-011), y solo cabe un LLM.
  - Un fichero propio protege las conversaciones de un índice incompatible o reconstruido, y no mueve los contadores del índice.
  - Dejar terminar una respuesta con la app oculta retendría la GPU sin que nadie la viera (constitución IV).
  - Las fichas como objetos tocables separados no cumplirían los 48 dp dentro de una línea de texto.
- **Alternativas descartadas:**
  - la orquestación en el ViewModel o en el dominio;
  - tablas en `rag.db` (esquema v4), Room, o DataStore;
  - dejar terminar la respuesta en segundo plano, o un servicio en primer plano;
  - fichas con contenido en línea (`InlineTextContent`), o una librería de Markdown;
  - más variantes de plantilla, o ajustarlas con los resultados.
- **Origen:** plan de F11 (research R1–R17).
- **Afecta:**
  - F11;
  - F07: la ingesta espera a la respuesta en curso;
  - F08: desplazamiento al fragmento citado y menú «⋯» compartido;
  - F12: `deleteAll()` para borrar el historial;
  - F13: historial sobre `chat.db`;
  - F14: primer token medido.

---

## D-055 — Ajustes del analyze de F11

- **Fecha:** 2026-10-29
- **Decisión** (respuestas del usuario):
  1. **Turno anterior tras una negativa (C12 cambiada):** entra completo, pregunta y frase de negativa, como en el escritorio. F05 midió así 13 de sus 50 seguimientos. Una pregunta sin bloques guarda la frase de negativa del prompt como respuesta, para que el turno siguiente la lleve igual. Los turnos cancelados, fallidos o interrumpidos no cuentan.
  2. **App en segundo plano:**
     - la respuesta en curso se detiene y queda «La respuesta se interrumpió», con su texto parcial y «Reintentar» (C13);
     - después se libera Gemma (constitución IV);
     - detener a mano sigue siendo «Generación cancelada», sin reintentar.
  3. **Textos aprobados además de C1–C15:**
     - el texto del bloqueo de modelos (C5), que reutiliza los de Inicio;
     - las frases de TalkBack «Tú: …» y «MicroRAG: …», y los avisos «Respuesta completa» y «Conversación borrada»;
     - **C16:** nota «No se ha podido guardar esta respuesta en el teléfono.»;
     - **C17:** «No se pueden abrir tus conversaciones guardadas.», con el chat desactivado y sin borrar nada.
- **Decisión** (ajustes técnicos del analyze, sin cambio de comportamiento aprobado):
  1. **Límite del prompt:** si pasa de 2.000 tokens estimados, se quita primero el bloque peor clasificado por la búsqueda, después el turno anterior, y nunca el último bloque. Así no hay negativas engañosas por el límite.
  2. **Reintentar** tiene la misma puerta que preguntar: solo con el chat disponible y sin otra respuesta en curso. Nunca carga Gemma durante una tanda.
  3. **Estado «Generando respuesta...»** desde que el modelo recibe el prompt, no desde el primer token.
  4. **Idioma de la respuesta:** parámetro del prompt con español por defecto, idéntico al escritorio (FR-014).
  5. **Criba de la revisión de la plantilla:** se compara con «informe» dentro de la misma regla automática. Esa regla da 76,4 % de citas frente al 94,2 % del juicio manual. La regla de adopción no cambia.
  6. **Conversación al abrir el chat:** primero el id guardado, después el de la ruta, después «Nuevo chat» y por último la última conversación.
  7. **`chat.db`:** una sola conexión, en el mismo hilo que el índice, así que en la app nunca hay dos escrituras a la vez. Queda justificado en el plan frente a «una sola conexión de escritura».
  8. **Medida del primer token:** cada pregunta de la muestra en una conversación nueva. Antes de cada uno de los 10 seguimientos se hace su pregunta anterior, sin medirla.
- **Motivo:**
  - El análisis independiente encontró 35 hallazgos, ninguno crítico.
  - El turno tras una negativa no coincidía con lo que evaluó F05.
  - El caso límite de memoria baja contradecía la regla de cancelar.
  - Reintentar podía cargar Gemma durante una tanda.
  - Había dos estados de fallo de `chat.db` sin texto aprobado.
  - Además, la criba de la plantilla pedía 90 % de citas con una regla que da 76,4 % con la plantilla actual: no habría pasado ninguna variante.
- **Alternativas descartadas:**
  - mantener C12 sin evaluar;
  - cancelar sin reintentar, o dejar que la respuesta termine en segundo plano;
  - responder con la negativa cuando ningún bloque cabe.
- **Origen:** usuario (revisión del analyze de F11) y analyze de F11.
- **Afecta:** F11 (spec, research R2–R15, contratos, modelo de datos y tareas).

---

## D-056 — Plantilla del prompt v4 (revisión de la plantilla de F11)

- **Fecha:** 2026-10-30
- **Decisión:** la app y el escritorio usan la plantilla `v4`: la del informe más tres reglas, fijadas antes de evaluar (research R4 de F11):
  - responder a todas las partes de la pregunta que estén en las fuentes;
  - copiar cifras, plazos, importes y números de artículo tal como aparecen;
  - con conversación previa, buscar la pregunta en las fuentes con ese contexto antes de responder con la negativa.

  `prompt_template` pasa a `"v4"` en `rag_parameters.json` y en `RagParameters`.
- **Cifras** (`run_eval.py template-review`: configuración de D-034, misma recuperación, 426 preguntas; las respuestas que no cambian heredan el juicio manual de F05 y las que cambian se juzgaron a mano):

  | Plantilla | Cambiadas | Correctas (manual) | Negativas | Citas | Adoptada |
  |---|---:|---:|---:|---:|---|
  | informe | — | 78,4 % (290/370) | 96,4 % (54/56) | 94,2 % | — |
  | v3 | 225 | 80,0 % (296/370) | 94,6 % | 93,1 % | no: +1,6 puntos |
  | **v4** | 220 | **81,1 % (300/370)** | **91,1 % (51/56)** | **94,3 %** | **sí** |

  Con la regla automática, la criba dio +1,6 puntos a `v3` y +3,5 a `v4`.
- **Motivo:**
  - `v4` cumple la regla de adopción de D-053: +2,7 puntos de respuestas correctas, negativas ≥ 85 % y citas ≥ 90 %.
  - Es la primera configuración que supera el 80 % de respuestas correctas del informe, que D-035 había aceptado no alcanzar.
- **Coste aceptado por el usuario:** en 3 preguntas más de las 56 sin respuesta en los documentos, `v4` se inventa una respuesta en lugar de la negativa. Un ejemplo: atribuye a la ley alemana el preaviso de la LAU española.
- **Margen:** 2 preguntas sobre el mínimo (298 de 370). Varios juicios de las respuestas cambiadas son discutibles en ambos sentidos, y quedan anotados en `offline/reports/template-review.csv`.
- **Método:** 445 respuestas cambiadas, juzgadas a mano por el asistente (método D-033) en 6 lotes agrupados por pregunta, para que cada pregunta tenga el mismo criterio en las dos variantes. Cada lote incluía como referencia el veredicto de F05 de la plantilla «informe».
- **Alternativas descartadas:**
  - mantener «informe», que priorizaba no inventar en preguntas sin respuesta;
  - `v3`, que no llega a +2 puntos.
- **Origen:** D-035 y clarify de F11 (D-053); el usuario confirmó la adopción a la vista de las cifras.
- **Afecta:**
  - F11: plantilla del chat y golden de respuestas;
  - F14: la negativa (91,1 %) se vigila al ajustar el contexto.

---

## D-057 — Primer token en el teléfono: excepción documentada (fase del teléfono de F11)

- **Fecha:** 2026-11-01
- **Decisión:**
  - Se mantienen el contexto de D-034 y el límite del prompt de 2.000 tokens estimados.
  - El p95 del primer token medido en el teléfono de referencia, por encima de los 5 s de SC-004 (constitución IV, D-026), queda como **excepción documentada**.
  - Las palancas para bajarlo pasan a F14.
  - FR-045 de F11 admite esta salida además del ajuste de bloques o tokens.
- **Cifras** (`ChatBenchmarkTest`: 50 preguntas de la muestra de F10 con Gemma cargada en GPU, Nothing Phone (1) A063, Android 15):

  | Medida | Mediana | p95 | > 5 s |
  |---|---:|---:|---:|
  | Primera (batería al 40 %, 36–38 °C) | 4.168 ms | 5.205 ms | — |
  | Segunda, cargando, 30 primeras preguntas (frío) | 4.419 ms | 5.826 ms | 12 de 30 |
  | Segunda, 20 últimas (42 °C, estado térmico «moderado») | 5.574 ms | 6.671 ms | 13 de 20 |
  | Segunda, las 50 | 4.969 ms | 6.638 ms | 25 de 50 |

  - **Respuesta en frío** (Gemma sin cargar): 15,3–16,0 s. En la app, Gemma se carga al abrir el chat.
  - **Búsqueda:** mediana de 0,24 s.
  - **Generación:** unos 9 tokens/s estimados en frío y 7,8 en caliente.
  - **Pico de memoria:** 3,2–4,8 GB.
- **Qué pesa:** en frío, el primer token ≈ 0,27 s + 3,1 s por cada 1.000 tokens estimados (r = 0,85), así que casi todo es el prefill de Gemma. Los seguimientos son los más lentos (mediana de 5,1 s), porque llevan el turno anterior.
- **Alternativa evaluada y descartada: límite del prompt de 1.500 tokens** (`run_eval.py limit-review limit=1500`, regla A6 de la app; regla fijada antes de evaluar: los umbrales del informe).
  - Cambian 65 prompts y 49 respuestas, juzgadas a mano en `offline/reports/limit-review.csv`:
    - respuestas correctas: 80,5 % (298 de 370; `v4` da 81,1 %);
    - negativas: 91,1 %;
    - citas: 93,8 %.
  - Cumple los umbrales con 2 respuestas de margen.
    - Empeoran 3: la derrama de q0052 pierde el bloque con la respuesta.
    - Mejora 1: q0368 deja de ser una negativa indebida.
  - **Proyección en el teléfono:** el p95 en frío baja de 5,8 a unos 5,3 s. Algunas de las preguntas más lentas ya tenían menos de 1.500 tokens (5,25 s con 1.368 y 5,37 s con 1.396).
  - Bajar más cuesta mucho más: con 1.400 cambia el 29 % de las preguntas y con 1.300 la mitad.
- **Motivo:** el límite pierde calidad sin asegurar los 5 s. El usuario prefiere conservar la calidad de `v4` y llevar el primer token a F14.
- **Consecuencias:**
  - `ChatBenchmarkTest` informa `sc004=met|exceeded` en lugar de fallar por el p95.
  - El plan de F11 lo recoge en «Complexity Tracking».
  - **Las cifras de la tabla son la base de F14**, que debe:
    - medir en sesiones sostenidas y con el teléfono en reposo entre preguntas, porque el calor sube el primer token más de 1 s;
    - evaluar las palancas de D-026 (decodificación especulativa, plantilla más corta) y E4B frente a E2B;
    - como propuesta nueva, evaluar reutilizar el prefijo fijo del prompt (las reglas, que no cambian) para no repetir su prefill.
- **Alternativas descartadas:**
  - límite de 1.500 (arriba);
  - 3 bloques para todas las preguntas, que cambia la calidad de D-034 en el 100 % de los casos.
- **Origen:** consulta de FR-045 en T067 de F11; el usuario eligió la excepción a la vista de las cifras. En la revisión de cierre (2026-11-01), aprobó el cierre de F11 con esta excepción.
- **Afecta:** F11 (FR-045, SC-004, cierre) y F14.

---

## D-058 — Ajustes e Idiomas (clarify de F12)

- **Fecha:** 2026-11-01
- **Decisión:**
  1. **Idioma de las respuestas (G25):**
     - «Español» por defecto: quien no toque nada sigue con el prompt de `v4`, idéntico al del escritorio;
     - «Automático» cambia solo la línea del idioma del prompt por «responde en el idioma de la pregunta», sin detector de idioma;
     - antes de ofrecerlo se evalúa en el escritorio con preguntas en español y en inglés. Regla fijada antes de evaluar: negativas ≥ 85 % y citas ≥ 90 % (umbrales del informe) y, con preguntas en español, sin bajar de las cifras de `v4`;
     - la app reconoce la negativa en español y en inglés;
     - si no cumple la regla, el trabajo se para y el usuario decide con las cifras.
  2. **Ficha de modelo (G24):**
     - las dos filas de modelo llevan chip con su estado real («Local» si está verificado y preparado) y chevron;
     - las dos abren una ficha de solo lectura con nombre, fichero, tamaño, SHA-256 abreviado, dimensiones, GPU/CPU y estado;
     - si el modelo no está listo, la ficha ofrece «Completar configuración», que abre la Preparación (D-046);
     - Ajustes no tiene acciones propias sobre los modelos: ni «Revalidar» ni nueva descarga.
  3. **Consulta entre idiomas** (pendiente «Documentos en otro idioma»):
     - se mide en F12, en el escritorio, con un conjunto pequeño: documentos públicos en inglés con preguntas en español y en inglés, y preguntas en inglés sobre los documentos en español;
     - las preguntas en inglés son las mismas de la evaluación de «Automático»;
     - la tarjeta «Búsqueda multilingüe» mantiene el texto del PNG si cada combinación cumple los umbrales del informe; si no, se propone un texto con las cifras y el usuario lo aprueba;
     - las cifras cierran el pendiente; las mejoras de la búsqueda que pidan van a F14.
  4. **Alcance (G23, en la spec):** las filas del MD que el PNG no tiene (protección de la biblioteca, almacenamiento, batería, información y licencias) quedan fuera de F12: al backlog, y las licencias a F15.
- **Motivo:**
  - **Idioma:** la constitución III exige evaluar cualquier cambio en la redacción del prompt, y ninguna plantilla se ha evaluado en otro idioma (D-056). Con el español por defecto, nada cambia para quien no elige.
  - **Ficha:** FEATURES pide el estado real y el detalle de los dos modelos. La Preparación ya resuelve descargar, importar y verificar, y repetirlo en Ajustes añadiría estados sin valor nuevo.
  - **Consulta entre idiomas:** el PNG promete algo sin medir y el MD pide mensajes honestos. Las preguntas en inglés sirven a la vez para «Automático».
- **Alternativas descartadas:**
  - idioma:
    - «Automático» por defecto, como el PNG;
    - «Automático» como idioma de la app;
    - solo «Español» en F12;
  - ficha: añadir «Revalidar» (propuesta de G24); o solo la fila de embeddings, como el PNG;
  - consulta entre idiomas: el texto del PNG sin medir, o un texto prudente sin medir, con la medida en F14.
- **Propuestas de UI:** el usuario aprobó en la revisión del clarify (2026-11-01) la spec y las propuestas S1–S11.
- **Origen:** usuario (clarify de F12). En la revisión de cierre (2026-11-02), aprobó el cierre de F12 con las diferencias frente a los PNG del informe (§6), entre ellas que el chip de estado baje bajo los textos cuando a su lado se partiría una palabra.
- **Afecta:**
  - F12;
  - F11: el prompt en español no cambia, y la lectura de la negativa admite el inglés;
  - F13: «Borrar historial» de Ajustes vacía lo guardado;
  - F14: mejoras de la búsqueda entre idiomas, si las cifras las piden;
  - F15: avisos de licencias (G23).

---

## D-059 — Diseño de Ajustes e Idiomas (plan de F12)

- **Fecha:** 2026-11-01
- **Decisión:**
  1. **Idioma de la app sin AppCompat:**
     - en Android 13+ la fuente es el `LocaleManager` del sistema: guardar llama a `setApplicationLocales` y el sistema recrea la actividad;
     - en Android 12, un fichero de una línea en `noBackupFilesDir/settings/app_language`, leído una vez al arrancar, con `recreate()`;
     - al pasar de Android 12 a 13, el valor del fichero se aplica una vez;
     - las notificaciones construyen sus textos con el idioma vigente en cada llamada.
  2. **Idioma de las respuestas** en un DataStore nuevo (`settings.preferences_pb`) con manejo de errores: un fallo al leer da español y avisa, y uno al escribir conserva el valor anterior. DataStore se queda en 1.2.1.
  3. **Guardar en Idiomas:** primero el idioma de las respuestas y después el de la app. Si falla el segundo, se repone el primero. «Preferencias guardadas» se anuncia después de la recreación, en el idioma nuevo.
  4. **Prompt:** `PromptLanguage.AUTOMATIC` con la línea «- Responde en el idioma de la PREGUNTA, de forma clara y breve.». La negativa se reconoce también como «I can't find that information in the available documents.», en la app y en el escritorio. El golden de 426 casos no cambia.
  5. **Borrar el historial a través de la sesión del chat:** detiene la respuesta en curso, borra todo y avisa a la pestaña Chat, que vuelve a un chat vacío. Una pregunta con una conversación ya borrada abre otra en lugar de perderse.
  6. **Modelos en Ajustes:** el mismo estado que la Preparación, refrescado al entrar, y una ficha de solo lectura con los datos del catálogo.
  7. **Evaluaciones en el escritorio, antes que la app:**
     - `language-review`: «Automático» con las 426 preguntas en español frente a `v4`, juzgando las respuestas que cambian;
     - `crosslang-review`: preguntas en inglés sobre los documentos en español (muestra de F10 traducida), y 3 libros de en.wikibooks con preguntas en inglés y en español;
     - los conjuntos y el corpus en inglés van en ficheros aparte, para no cambiar las huellas ni los golden;
     - «Automático» se adopta si `language-review` cumple su regla y las preguntas en inglés sobre los documentos en español cumplen los umbrales del informe.
  8. **Pruebas en el teléfono al final.** «Borrar historial» no se confirma en el teléfono del usuario sin su permiso expreso.
- **Motivo:**
  - AppCompat exigiría cambiar la actividad y el tema y añadir una dependencia;
  - DataStore no se puede leer de forma síncrona al arrancar;
  - las evaluaciones van primero porque deciden qué se construye;
  - los ficheros aparte protegen las cifras ya juzgadas.
- **Alternativas descartadas:**
  - AppCompat 1.8.0;
  - el idioma de la app en DataStore leído con `runBlocking`;
  - el selector solo en Android 13+;
  - añadir las preguntas en inglés a `questions.jsonl`;
  - reconstruir el índice `ref`.
- **Origen:** plan de F12 (`specs/012-settings-languages/plan.md` y `research.md`).
- **Afecta:** F12, F11 (sesión del chat, prompt y negativa), F03 (notificaciones de descarga) y F07 (notificaciones de indexación).

---

## D-060 — Ajustes del analyze de F12

- **Fecha:** 2026-11-01
- **Decisión:**
  1. **Mínimo de respuestas correctas** en las reglas de las preguntas en inglés: a no más de 10 puntos por debajo de su referencia.
     - Para adoptar «Automático» (FR-024): las preguntas en inglés sobre los documentos en español (ES·EN), frente a las mismas 57 preguntas en español con `v4`, según los juicios ya hechos.
     - Para el texto de la tarjeta «Búsqueda multilingüe» (FR-032): ES·EN con la misma referencia, y las preguntas en español sobre los documentos en inglés (EN·ES), frente a las mismas en inglés (EN·EN). EN·EN no tiene referencia y solo usa los umbrales del informe.
  2. **Correcciones sin decisión del usuario:**
     - si el sistema cambia el idioma de la app con Idiomas abierta y el usuario no lo había tocado, la selección sigue al idioma nuevo (P19);
     - el conjunto de preguntas en inglés sobre los documentos en español tiene 72 preguntas: las 15 sin respuesta incluyen las 2 que son anteriores de seguimientos;
     - pruebas añadidas: filas de modelo que cambian en vivo, modelo sin verificar en la ficha, anuncios de guardado, sin red y registros;
     - el recorrido del teléfono puede incluir, con permiso, una pregunta en inglés y un cambio de idioma con una respuesta en curso, sobre documentos públicos de prueba.
- **Motivo:** las reglas aprobadas solo miraban negativas y citas. Una combinación podía pasar con muchas respuestas incorrectas, y la tarjeta seguiría prometiendo la consulta entre idiomas.
- **Alternativas descartadas:** 5 puntos de margen; o dejar las reglas solo con negativas y citas.
- **Origen:** usuario (analyze de F12). El usuario aprobó el plan, las tareas y el analyze el 2026-11-01.
- **Afecta:** F12 (FR-024, FR-032, research R7 y R9, contrato `eval-language.md` L6, L13 y L14).

---

## D-061 — «Automático» no se ofrece en F12 (evaluación del idioma de las respuestas)

- **Fecha:** 2026-11-01
- **Decisión:**
  - **El chat responde siempre en español.** El prompt es el del escritorio (`v4`), sin cambios.
  - **Idiomas muestra «Español» como única opción**, marcada, en «IDIOMA DE LAS RESPUESTAS». No se guarda nada para ella.
  - **«Automático» no se ofrece en F12.** Las respuestas en otro idioma pasan al backlog, con un enfoque nuevo que haya que evaluar (plantilla en inglés, detección del idioma…).
  - **La negativa se reconoce también en inglés**, en la app y en el escritorio, por robustez y para que las dos lecturas sean iguales.
  - **La medida entre idiomas** (FR-033 de F12) sigue, con respuestas en español.
- **Cifras** (regla fijada antes de evaluar, D-058 y D-060):

  | `language-review`: las 426 preguntas en español, juicio manual | `v4` | «Automático» |
  |---|---:|---:|
  | Respuestas correctas | 81,1 % (300/370) | 81,1 % (300/370) |
  | Negativas correctas | 91,1 % (51/56) | **89,3 % (50/56)** |
  | Citas correctas | 94,3 % | **92,9 %** |
  | Negativas indebidas | 23 | 20 |

  - Cambian 196 respuestas, juzgadas a mano: gana 6 correctas y pierde 6. La negativa que falla es q0120.
  - Las citas bajan por números de fuente inexistentes ([11], [8], [4]) y por cifras mal copiadas.
  - **Preguntas en inglés sobre los documentos en español** (72, muestra de F10 traducida): **las 72 respuestas salen en español.** La línea «Responde en el idioma de la PREGUNTA», dentro de un prompt y unas fuentes en español, no cambia el idioma.
- **Motivo:**
  - con las preguntas en español queda por debajo de `v4` en dos cifras;
  - con las preguntas en inglés no hace lo que promete el PNG («Detectar idioma de la pregunta»).
- **Alternativas descartadas:**
  - probar una variante de la línea (por ejemplo, en inglés), que exigía otra ronda de unas 270 respuestas a juzgar sin garantía;
  - que «Automático» siga el idioma de la app, que cambia el PNG y también exigía evaluar;
  - ocultar la sección, o mostrar «Automático» desactivado con «próximamente».
- **Origen:** usuario, a la vista de las cifras (T010 de F12).
- **Afecta:**
  - F12: US3, FR-016, FR-017, FR-021, FR-022, FR-024, FR-027, FR-028, S11 y S12 nueva. Sobra la tienda del idioma de las respuestas;
  - el backlog: «English (respuestas) y detección de idioma» (G25) sigue ahí, ahora con estas cifras como punto de partida.

---

## D-062 — Documentos y preguntas en idiomas distintos (medida entre idiomas de F12)

- **Fecha:** 2026-11-01
- **Decisión:**
  - **La tarjeta «Búsqueda multilingüe» de Idiomas cambia de texto.** Deja el del PNG («Consulta documentos en distintos idiomas desde el mismo chat.») por:
    - español: «Consulta documentos en español o en inglés. Funciona mejor si preguntas en el idioma del documento.»;
    - inglés: "Ask about documents in Spanish or English. It works best when you ask in the document's language."
  - El título «Búsqueda multilingüe» y el icono se quedan. La tarjeta sigue siendo informativa, sin interruptor ni acción.
  - **Se cierra el pendiente «Documentos en otro idioma»** con estas cifras. Las mejoras de la búsqueda entre idiomas pasan a F14.
- **Medida** (escritorio, configuración elegida con `v4` y respuestas siempre en español, D-061; juicio manual con el método D-033; regla fijada antes de medir, D-058 y D-060):

  | Combinación | Documentos | Preguntas | Búsqueda | Correctas | Referencia | Negativas | Citas | Negativas indebidas |
  |---|---|---|---:|---:|---:|---:|---:|---:|
  | ES·EN | español (`ref`) | 72 en inglés (muestra de F10 traducida) | 57,9 % | **50,9 %** (29/57) | 82,5 % | 100 % (15/15) | 96,1 % | 16 |
  | EN·EN | inglés (`en`) | 65 en inglés | 90,0 % | 80,0 % (40/50) | — | 100 % (15/15) | 95,5 % | 3 |
  | EN·ES | inglés (`en`) | las mismas 65 en español | 78,0 % | **64,0 %** (32/50) | 80,0 % | 86,7 % (13/15) | 90,8 % | 11 |

  - **Búsqueda:** preguntas con un fragmento que lleva la evidencia en el contexto.
  - **Referencia:** ES·EN, sus mismas preguntas en español con `v4`; EN·ES, EN·EN.
  - **Corpus en inglés:** 3 libros de en.wikibooks (Git, Basic Book Design y Knots, CC BY-SA 4.0, revisiones fijas) en `offline/corpus/en/`. El índice `en` tiene 184 fragmentos, con el troceado de la app.
  - **Fallos:** de las respuestas que no son correctas, son de búsqueda 21 de 28 en ES·EN, 4 de 10 en EN·EN y 11 de 18 en EN·ES. El resto son de generación.
  - **Idioma de las respuestas:** 2 de las 65 respuestas de EN·EN salen en inglés, o casi, aunque el prompt pide español.
- **Lectura:**
  - con documento y pregunta en el mismo idioma, el inglés rinde como el español: 80,0 % frente al 81,1 % de `v4`;
  - con idiomas distintos, ES·EN queda 31,6 puntos por debajo de su referencia y EN·ES 16,0. No se cumple la regla del texto del PNG (no más de 10 puntos);
  - la causa principal es la búsqueda: la parte por palabras usa palabras vacías y raíces del español, y la búsqueda por significado sola no basta para cruzar idiomas.
- **Motivo del texto:** dice lo que funciona (documentos en español y en inglés) y el consejo que más ayuda, sin prometer la consulta entre idiomas.
- **Alternativas descartadas:**
  - solo el consejo, sin nombrar los idiomas;
  - el consejo con «Las respuestas llegan en español», que Idiomas ya muestra en «IDIOMA DE LAS RESPUESTAS».
- **Origen:** usuario, a la vista de las cifras (T016 de F12, S10).
- **Afecta:**
  - F12: FR-032 y S10; claves `languages_multilingual_body` del contrato `settings-ui.md`;
  - F14: palanca de calidad nueva, la búsqueda entre idiomas;
  - informe de evaluación: sección «Idiomas (F12)».

---

## D-063 — Historial de chats (clarify de F13)

- **Fecha:** 2026-11-02
- **Decisión:**
  1. **Orden y grupos de la lista:**
     - por última actividad, que es la fecha de la última pregunta: arriba la más reciente, y una conversación continuada sube;
     - grupos «Hoy», «Ayer», «Últimos 7 días» (de 2 a 6 días antes) y, después, uno por mes y año;
     - carga por tandas al desplazarse, como la Biblioteca.
  2. **Borrar desde la lista:**
     - un botón de papelera visible en cada fila, de 48 dp, con la confirmación de F11 (C15);
     - tocar el resto de la fila abre la conversación;
     - se borra de una en una, sin deslizar, pulsación larga ni modo de selección; borrar todo sigue en Ajustes (F12).
  3. **Respuesta en curso:** abrir otra conversación desde el Historial la detiene como «Nuevo chat» (D-054). Queda «Generación cancelada», con su texto parcial, en su conversación. Abrir la misma conversación no detiene nada.
  4. **Almacenamiento:**
     - nada se borra solo: no hay límite, borrado automático ni aviso de tamaño (FR-026 de F11);
     - se miden en el teléfono de referencia el tamaño de 1.000 conversaciones de 5 preguntas y el tiempo de apertura del Historial, con datos de prueba aparte, y las cifras van al informe de cierre;
     - la estimación previa es de unos 5–10 MB.
- **Motivo:**
  - **Orden:** es lo habitual en las apps de chat, y lo reciente se encuentra antes.
  - **Borrar:** la papelera se ve y funciona con TalkBack. Deslizar y mantener pulsado no se ven y no existen en el design system.
  - **Respuesta en curso:** es coherente con «Nuevo chat», mantiene una sola respuesta en toda la app y no añade estados al chat.
  - **Almacenamiento:** borrar solo podría eliminar algo que el usuario quería. Con las cifras se puede decidir más adelante si hace falta un límite.
- **Alternativas descartadas:**
  - orden: por última actividad sin grupos; o por fecha de creación con grupos;
  - borrar: solo desde dentro de la conversación («⋯» → «Borrar conversación»); o un modo de selección para borrar varias;
  - respuesta en curso: dejarla terminar con el campo de pregunta en espera; o preguntar antes de detenerla;
  - almacenamiento: un límite con borrado automático; o un aviso a partir de un tamaño.
- **Propuestas de UI:** el usuario aprobó en la revisión del clarify (2026-11-02) la spec, el wireframe (`specs/013-chat-history/wireframe.md`) y las propuestas H1–H11, antes de `/speckit-plan` (criterio 1 de F13).
- **Origen:** usuario (clarify de F13). En la revisión de cierre (2026-11-03), aprobó el cierre de F13 con las diferencias frente al wireframe del informe (§6).
- **Afecta:**
  - F13;
  - F11:
    - «Historial» en el menú «⋯»;
    - el chat abre una conversación concreta;
    - las fichas `[n]` de documentos borrados dejan de abrir nada;
  - F12: «Borrar historial de chat» vacía también la lista.

---

## D-064 — Diseño del Historial de chats (plan de F13)

- **Fecha:** 2026-11-02
- **Decisión:**
  1. **Lista sin cambio de esquema:**
     - una consulta sobre `chat.db` v1 da el título (la primera pregunta, hasta 300 caracteres), la última actividad y el número de preguntas, solo de las conversaciones con preguntas;
     - orden `updated_at DESC, id DESC`;
     - tandas de 50 con un límite creciente, como la Biblioteca.
  2. **La lista se vuelve a leer solo cuando cambia:** un contador propio sube con cada pregunta nueva y con cada borrado, y no con los guardados de cada segundo de una respuesta.
  3. **Fechas y grupos con `java.time`:**
     - por días de calendario en la zona del teléfono, con «hoy» recalculado al volver a la pantalla;
     - formatos en el idioma de la app, y frases largas para TalkBack.
  4. **Abrir desde Historial:**
     - Historial devuelve la petición (`open:<id>` o `new`) a la entrada del chat, como el detalle devuelve su aviso a la Biblioteca;
     - el chat la atiende una vez;
     - la sesión detiene la respuesta en curso solo si es de otra conversación.
  5. **Borrar una conversación:**
     - la sesión borra bajo su cerrojo, como «borrar todo», y avisa con el id borrado;
     - el chat vuelve a un chat vacío si era la suya;
     - borrar deja de lanzar excepciones: un fallo se avisa en Historial y en el chat, y no quita nada.
  6. **Fichas `[n]` de documentos borrados:**
     - en gris y sin enlace, con la misma disponibilidad que las filas de fuentes;
     - TalkBack lee su número sin acción, y la frase «Fuente ya no disponible» la da su fila de fuentes.

     Es una corrección a H7, que se aprueba en la parada del analyze.
  7. **Medida** con un fichero de prueba aparte: 1.000 conversaciones de 5 preguntas, en el emulador y en el teléfono, con su tamaño y su tiempo de apertura.
  8. **Fase del teléfono al final**, sin capturar ni volcar Historial mientras liste la conversación del usuario.
- **Motivo:**
  - un título guardado repetiría un dato que ya está en `chat.db` y obligaría a migrar ficheros del usuario;
  - releer la lista con cada guardado haría una consulta por segundo durante cada respuesta;
  - navegar de nuevo al chat crearía otra instancia de la pestaña, y la ruta no se refresca (R13 de F11);
  - el borrado fuera del cerrojo dejaba empezar una pregunta entre la parada y el borrado, y una excepción de borrado sin capturar cerraba la app;
  - Compose no permite poner a una parte del texto una frase propia sin quitar los enlaces de las demás.
- **Alternativas descartadas:**
  - una columna de título con migración a v2; Paging 3; paginación por cursor;
  - releer la lista con cada escritura;
  - `navigate(Chat(id))` sustituyendo la entrada del chat; una petición guardada en la sesión;
  - una `contentDescription` para toda la respuesta, o `VerbatimTtsAnnotation`, para las fichas inertes.
- **Origen:** plan de F13 (`specs/013-chat-history/plan.md` y `research.md`).
- **Afecta:**
  - F13;
  - F11: la sesión, el ViewModel y la pantalla del chat, y la tarjeta de respuesta;
  - F08: `LoadMoreNearTheEnd` pasa al design system.

---

## D-065 — Ajustes del analyze de F13

- **Fecha:** 2026-11-03
- **Decisión** (respuestas del usuario):
  1. **Fichas `[n]` de documentos borrados y TalkBack** (corrección de H7): TalkBack lee su número, sin acción. La frase «Fuente N: documento, Fuente ya no disponible» la dice su fila de «Fuentes consultadas». Por lo demás, la ficha sigue como se aprobó: en gris y sin abrir nada.
  2. **Error de Historial sin «Reintentar»** (corrección de H5). Con las conversaciones guardadas ilegibles, Historial muestra solo «No se pueden abrir tus conversaciones guardadas.», como el chat. La app no vuelve a abrir el fichero hasta reiniciarse, así que el botón no podría arreglar nada.
- **Decisión** (ajustes técnicos del analyze, sin cambio de comportamiento aprobado):
  1. **Una respuesta parada termina antes de empezar otra** (arreglo de F11):
     - preguntar o reintentar justo después de «Nuevo chat», de detener o de abrir otra conversación espera a que la anterior acabe de guardarse;
     - la respuesta nueva ya no se ve «interrumpida» mientras se genera.
  2. **Identificadores reutilizados** (arreglo de F11):
     - SQLite puede dar el id de un documento borrado al siguiente que se importa;
     - un documento creado después de una respuesta nunca cuenta como su fuente, así que la cita vieja sigue diciendo «Fuente ya no disponible» y no abre otro documento.
  3. **Conversación borrada con la app cerrada por el sistema:** si el chat se restaura con una conversación que ya no existe, vuelve a un chat vacío.
  4. **Otros:**
     - zona horaria leída al volver a la pantalla;
     - la ruta de depuración de Historial abre antes el chat;
     - la página y el total se leen juntos;
     - la pestaña Chat nunca abre una conversación sin preguntas;
     - la frase de TalkBack de cada fila no pone un punto tras «?»;
     - la medida de SC-001 cuenta hasta la primera fila dibujada;
     - un escenario de US5 que no se podía alcanzar se reformula;
     - las fichas inertes usan un par de colores ya comprobado.
- **Motivo:** el análisis independiente encontró 29 hallazgos: ninguno crítico, 2 altos (el id reutilizado y el escenario de US5), 7 medios y 20 bajos.
- **Alternativas descartadas:**
  - ocultar las fichas inertes a TalkBack;
  - que «Reintentar» vuelva a abrir el fichero, o dejarlo sin efecto;
  - una columna nueva en `rag.db` para no reutilizar ids.
- **Origen:** usuario (revisión del analyze de F13) y analyze de F13. El usuario aprobó el plan, las tareas y el analyze el 2026-11-03.
- **Afecta:**
  - F13: spec (H5, H7, FR-007, FR-011, FR-019, FR-020, US5, SC-006), wireframe, research, contratos y tareas;
  - F11: la sesión del chat (Q14) y la resolución de fuentes (Q15).

---

## D-066 — Features F16–F20 antes de F14: apariencia, carpetas y RAG visual

- **Fecha:** 2026-11-03
- **Decisión** (respuestas del usuario):
  1. **Cinco features nuevas** se ejecutan antes de F14 y F15:
     - F16, Apariencia: tema e icono de Ajustes;
     - F17, Carpetas y ámbito del chat;
     - F18, Viabilidad del RAG visual;
     - F19, Indexación visual;
     - F20, Chat con páginas.
  2. **Numeración:** F14 y F15 conservan su número, porque D-052, D-057, D-062 y los informes de cierre las citan así. Las ramas nuevas se crean con `--number` (016…020), y F14 se lanzará con `--number 14`.
  3. **Orden de ejecución:** F16 → F17 → F18 → F19 → F20 → F14 → F15.
  4. **RAG visual:**
     - el modo se elige por documento al importar: Texto, Visual o Ambos;
     - el valor por defecto va en Ajustes, y el modo se cambia reindexando;
     - entran los PDF, también los escaneados, y las imágenes sueltas (JPG y PNG), que siempre van en Visual;
     - el índice de texto y el visual conviven.
  5. **Modelo de embeddings:**
     - EmbeddingGemma 2 de 440M (texto y visión) **sustituye a la 270M** y se descarga en el onboarding;
     - las instalaciones existentes migran sin reindexar el texto, porque las dos variantes dan los mismos vectores de texto (D-025, R15 de F02);
     - está sujeto a lo que mida F18.
  6. **Carpetas:**
     - un solo nivel, y cada documento está en una carpeta o en «Sin carpeta», donde quedan los actuales;
     - el ámbito del chat se elige **por conversación** (carpetas completas y documentos sueltos de carpetas distintas) y se conserva al reabrirla desde Historial;
     - «Incluir en RAG» sigue siendo la exclusión global.
  7. **Tema:**
     - Sistema (por defecto), Claro y Oscuro, en una pantalla propia como Idiomas;
     - el icono de Ajustes pasa a ser un engranaje.
- **Motivo:**
  - **Por qué estas features van antes:** cambian la ingesta, el esquema, la recuperación y el prompt, así que F14 debe medir el sistema final.
  - **Por qué el orden:** lo pequeño va primero. F17 deja hecho el filtro de ámbito que reutiliza el RAG visual, y la viabilidad visual va justo antes de construirlo.
  - **LiteRT-LM 0.18.1 ya admite imágenes** (verificado en el AAR):
    - `InputData.Image` y `visionTokensPerImage` en los embeddings;
    - `Content.ImageBytes`, `visionBackend`, `maxNumImages` y `ExperimentalFlags.visualTokenBudget` en el LLM.
  - **Riesgo principal:** el primer token con imágenes. Ya supera los 5 s sin ellas (D-057), y cada página añade cientos de tokens visuales. Por eso F18 es una feature de viabilidad, con un sí o un no a F19–F20.
- **Alternativas descartadas:**
  - renumerar F14 y F15 detrás de las nuevas;
  - un interruptor global del modo visual, o indexar siempre los dos modos;
  - descargar la 440M solo al activar el modo visual y mantener la 270M;
  - la selección global de carpetas en Biblioteca;
  - varias carpetas por documento, o carpetas anidadas;
  - una hoja inferior para el tema;
  - el RAG visual solo para PDF;
  - empezar por la viabilidad visual.
- **Origen:** usuario (encargo y respuestas del 2026-11-03, antes de F14).
- **Afecta:**
  - FEATURES (F16–F20, F14, F15 y backlog) e INVENTARIO (G45–G47);
  - se sustituyen formalmente en el clarify de cada feature:
    - G19 (sol de Ajustes), en F16: es una excepción del usuario a la precedencia del PNG (D-004);
    - D-016, en la parte de «sin selector propio», en F16;
    - D-025 (270M), en F18–F19;
    - D-007 (formatos del MVP y OCR al backlog), en F19;
  - la constitución:
    - III: `embedding_model_sha256`, 256D y citas trazables a un `chunkId`;
    - Restricciones técnicas: «270M de solo texto» y los formatos del MVP;
    - la enmienda se propone en F18 y se aplica en F19.

---

## D-067 — Tema e icono de Ajustes (clarify de F16)

- **Fecha:** 2026-11-03
- **Decisión:**
  1. **Tema de la app:**
     - tres valores: «Sistema» (por defecto, también al actualizar), «Claro» y «Oscuro»;
     - una fila «Tema» en Preferencias de Ajustes, entre «Idiomas» e «Índice y rendimiento», con el icono Lucide `sun-moon` y el tema guardado como subtítulo;
     - abre una pantalla Tema como Idiomas, sin tarjeta hero, con las tres opciones de radio («Sistema» con «Sigue el modo oscuro del teléfono»).
  2. **Cuándo se aplica:** al tocar «Guardar preferencias», como en Idiomas.
     - Marcar una opción no cambia nada.
     - Guardar aplica el tema, vuelve a Ajustes y anuncia «Preferencias guardadas».
     - Atrás sin guardar no cambia nada.
  3. **Alcance:** toda la app, la pantalla de arranque y las barras del sistema, con los tokens de siempre (la paleta oscura derivada de D-016 no cambia).
  4. **Fallos:** si no se puede guardar, se mantiene el tema anterior y se avisa; si no se puede leer, se usa «Sistema» y la pantalla Tema lo dice. Son los textos de Idiomas.
  5. **Icono de Ajustes:** el Lucide `settings` (engranaje) en la barra inferior, la cabecera de Inicio y el catálogo, en lugar del sol del PNG. La etiqueta «Ajustes» no cambia.
- **Motivo:**
  - **El tema:** lo pide el usuario (D-066); hoy la app sigue siempre al teléfono.
  - **Guardar como Idiomas:** el mismo patrón en las dos pantallas de preferencias; la app se recrea una sola vez y Atrás nunca deja un tema a medias.
  - **El engranaje:** es la convención de los teléfonos, y el sol se confundiría con el tema.
- **Alternativas descartadas:**
  - aplicar el tema al marcar la opción, sin botón;
  - una hoja inferior en Ajustes (D-066);
  - una tarjeta hero en la pantalla Tema;
  - los iconos `palette` o `contrast` para la fila.
- **Propuestas de UI:** el wireframe y las propuestas A1–A9 (`specs/016-appearance-theme/wireframe.md` y la spec) quedan aprobados por el usuario el 2026-11-03, antes de `/speckit-plan` (criterio 1 de F16).
- **Origen:** usuario (clarify y revisión del wireframe de F16).
- **Afecta:**
  - F16;
  - D-016: sustituida en parte, solo en «sigue el ajuste del sistema, sin selector propio»;
  - G19 (sol de Ajustes) y FR-006 de F01: el icono pasa a ser el engranaje, como excepción del usuario a la precedencia del PNG (D-004);
  - G45: resuelto con esta propuesta;
  - F12: la sección Preferencias de Ajustes tiene una fila más;
  - F15: la revisión del modo oscuro cubre los tres valores.

---

## D-068 — Cómo se aplica y se guarda el tema (plan de F16)

- **Fecha:** 2026-11-03
- **Decisión:**
  1. **Quién aplica el tema:** el modo oscuro por app del sistema, `UiModeManager.setApplicationNightMode`:
     - «Sistema» → `MODE_NIGHT_AUTO`, que no impone nada;
     - «Claro» → `NO`;
     - «Oscuro» → `YES`.

     `LisdataTheme`, `MainActivity` y los temas XML no cambian. La pantalla de arranque, las barras y Compose leen la configuración de la app.
  2. **Qué se muestra:** una copia propia del valor en `noBackupFilesDir/datastore/appearance.preferences_pb`, solo para la fila y la opción marcada. Nunca se lee en el hilo principal.
  3. **Orden al guardar:**
     - primero la copia y después el sistema;
     - si la copia falla, no se aplica nada;
     - si el sistema lo rechaza, se restaura la copia anterior.
  4. **Reconciliación al arrancar,** en segundo plano: se aplica la copia solo si la configuración de la app no la refleja.
  5. **«Preferencias guardadas»:** solo lo anuncia TalkBack, como en Idiomas (S9 de F12), cuando la app ya se ve en el tema nuevo. **Corrige A3** y §3 del wireframe, que decían «aparece el aviso inferior»: en Idiomas no hay aviso visible.
  6. **Iconos:** `ic_settings` se regenera desde Lucide `settings` y entra `ic_sun_moon`, con el conversor de `tools/icons` y `lucide-static` 1.61.0. Se corrige el comando de regeneración de `specs/001-…/quickstart.md`.
  7. **Proceso:** el usuario pidió el 2026-11-03 hacer el ciclo SDD de F16 de forma automática, salvo que haga falta preguntarle. La parada tras analyze se hace solo si el analyze deja algo que decidir.
- **Motivo:**
  - Solo el modo por app del sistema llega a la pantalla de arranque, que el sistema dibuja antes que la app (FR-010). Además persiste tras reiniciar y se borra con los datos de la app.
  - El sistema no deja leer ese valor (`javap` de las plataformas 33 y 37.1), y sin la copia «Sistema» y «Oscuro» serían indistinguibles con el teléfono en oscuro (constitución V).
  - Escribir primero la intención y reconciliar al arrancar cierra la ventana de un proceso muerto entre los dos pasos.
- **Alternativas descartadas:**
  - AppCompat (D-059), que no persiste ni llega a la pantalla de arranque;
  - un DataStore leído antes del primer fotograma con `LisdataTheme(darkTheme)` y `SystemBarStyle` a mano, con destello en el arranque y lectura de disco en el hilo principal;
  - aplicar el tema en cada arranque;
  - un aviso visible solo para el tema.
- **Origen:** plan de F16 (`specs/016-appearance-theme/plan.md` y `research.md`), y el usuario (ciclo automático).
- **Afecta:**
  - F16: spec (A3), wireframe (§3), contratos y tareas;
  - F12: `SettingsNotices` y el anuncio del shell esperan también al modo nocturno;
  - F01: el comando de regenerar iconos;
  - F15: el riesgo residual de Android 12, sin dispositivo de prueba.

---

## D-069 — Ajustes del analyze de F16

- **Fecha:** 2026-11-03
- **Decisión** (respuestas del usuario):
  1. **«Preferencias guardadas»:** al guardar el tema, solo lo anuncia TalkBack, como en Idiomas. Confirma la corrección de A3 de D-068.
  2. **Android 12:** se crea el emulador `MicroRAG_API32` (API 32), donde se verifica el modo por app y se graban los arranques en frío. Sustituye el riesgo residual de D-068.
  3. **Parada del analyze:** hecha. El plan, las tareas y el analyze quedan aprobados con sus correcciones, y la implementación sigue en automático hasta la fase del teléfono, que se consulta antes.
- **Decisión** (ajustes técnicos del analyze, sin cambio de comportamiento aprobado):
  1. **El modo del teléfono no se lee de `Resources.getSystem()`** (H1): esa configuración ya incluye el modo por app (`ResourcesManager`, SDK 36.1). Si hace falta, se usa `UiModeManager.getNightMode()`.
  2. **El anuncio** espera un margen de 1 s en la composición, en lugar de comparar el modo nocturno. Si la actividad se recrea, sale en la nueva.
  3. **Reconciliación:**
     - aplica la copia, o «Sistema» si la lectura falló, como pide FR-016 (H2);
     - **variante A:** aplicar siempre, si reaplicar el mismo valor no recrea;
     - **variante B:** aplicar solo cuando no coincide;
     - la prueba instrumentada de la puerta T015 decide entre las dos.
  4. **Fichero corrupto:** el almacén lo sustituye por uno vacío y marca la lectura fallida, de modo que el siguiente guardado funciona (M2).
  5. **Otros:**
     - el criterio del icono pasa a «ningún icono del Lucide `sun`», para no chocar con `sun-moon`;
     - se miden los tiempos de SC-001 y SC-004;
     - `SaveFailure` se comparte con Idiomas;
     - las pruebas instrumentadas esperan cada cambio con un plazo máximo y dejan siempre «Sistema»;
     - el arranque en el teléfono no se graba;
     - G19 y G45 se cierran en F16;
     - se corrigen referencias desfasadas.
- **Motivo:** la revisión independiente encontró 19 hallazgos:
  - 1 crítico: la parada obligatoria tras el analyze;
  - 2 altos (H1 y H2);
  - 7 medios y 9 bajos.
- **Alternativas descartadas:**
  - un aviso visible al guardar el tema;
  - aceptar Android 12 sin verificar;
  - comparar con `Resources.getSystem()`;
  - `MODE_NIGHT_CUSTOM` como alternativa a `AUTO`, porque también acaba en «sin imponer».
- **Origen:** usuario (revisión del analyze de F16) y analyze de F16.
- **Afecta:**
  - F16: spec (FR-001, FR-016, SC-007), wireframe, research (R1, R2, R3, R4, R6, R9, R12), data-model, contratos, quickstart y tareas;
  - F12: `SaveFailure` de `LanguagesScreen.kt` pasa a ser compartido.

---

## D-070 — Carpetas y ámbito del chat (clarify de F17)

- **Fecha:** 2026-11-04
- **Decisión:**
  1. **Biblioteca:** una lista de carpetas con su recuento, en orden alfabético, y «Sin carpeta» al final si tiene documentos.
     - Al entrar en una carpeta, la lista de hoy: búsqueda, filtros, «Incluir en RAG» y estados.
     - Desde fuera, la búsqueda encuentra documentos de todas las carpetas.
     - Crear y renombrar, con nombres únicos sin distinguir mayúsculas ni tildes, de 1 a 40 caracteres; «Sin carpeta» está reservado.
  2. **Eliminar una carpeta:** se elige en el diálogo.
     - «Mover sus documentos a «Sin carpeta»», la opción por defecto;
     - o «Eliminar también sus documentos», con un segundo aviso y la limpieza de F08 de cada documento.
  3. **Mover e importar:** «Mover a carpeta» en el «⋯» del detalle, y «Carpeta de destino» en la Ingesta, que por defecto es la carpeta desde la que se abrió. Mover no reindexa.
  4. **Ámbito del chat:**
     - una ficha «Buscar en: …» encima del compositor;
     - un selector con «Toda la biblioteca», carpetas enteras y documentos sueltos;
     - los documentos con «Incluir en RAG» desactivado se ven en gris y no se pueden marcar;
     - se guarda con cada conversación, y «Nuevo chat» empieza con el **último ámbito usado** (la primera vez, «Toda la biblioteca»);
     - lo que deja de existir sale del ámbito con un aviso;
     - un ámbito sin documentos listos no deja preguntar y ofrece cambiarlo.
  5. **Recuperación:** solo filtra por el ámbito, y con «Toda la biblioteca» da exactamente lo de hoy.
- **Motivo:**
  - la lista de carpetas es la metáfora que pidió el usuario;
  - elegir al eliminar deja hacer las dos cosas sin borrar nada por defecto;
  - el último ámbito usado es lo más cómodo para quien trabaja sobre una carpeta, y la ficha siempre lo muestra;
  - ver los excluidos explica por qué no se buscan.
- **Alternativas descartadas:**
  - fichas de filtro sobre la lista actual;
  - mover siempre a «Sin carpeta», o borrar siempre los documentos;
  - «Nuevo chat» siempre con «Toda la biblioteca»;
  - ocultar los excluidos en el selector.
- **Propuestas de UI:** el wireframe y las propuestas K1–K14 (`specs/017-folders-chat-scope/wireframe.md` y la spec), con K4 y K11 cambiadas en el clarify, quedan aprobados por el usuario el 2026-11-04, antes de `/speckit-plan` (criterio 1 de F17).
- **Proceso:** como en F16, el usuario pidió el 2026-11-04 seguir el ciclo SDD en automático salvo que haga falta preguntarle.
- **Origen:** usuario (clarify y revisión del wireframe de F17).
- **Afecta:**
  - F17;
  - G46: resuelto;
  - F08: la Biblioteca y el detalle;
  - F06: la Ingesta;
  - F10: la petición de búsqueda;
  - F11 y F13: el chat, sus estados y las conversaciones guardadas;
  - F14: la búsqueda acotada se mide de nuevo.

---

## D-071 — Cómo se guardan las carpetas y cómo se busca con un ámbito (plan de F17)

- **Fecha:** 2026-11-04
- **Decisión:**
  1. **`rag.db` v4:**
     - tabla `folders`, con `AUTOINCREMENT` para que un id no se reutilice y con el nombre plegado único;
     - `documents.folder_id`, con `ON DELETE SET NULL`, y un índice `(folder_id, created_at_ms, id)`;
     - migración 3 → 4 en una transacción, con todos los documentos en «Sin carpeta».
  2. **`chat.db` v2:** `conversation_scope` y `last_scope`, una fila por carpeta o documento, con el `created_at_ms` del documento como guarda de D-065. Sin filas es «Toda la biblioteca».
  3. **Búsqueda:**
     - con «Toda la biblioteca», el SQL de hoy, sin cambios;
     - con un ámbito, el modo en streaming de `vector_full_scan` (sin `k`) y FTS, con `JOIN`/`WHERE` y `ORDER BY … LIMIT k`;
     - la carga de candidatos y los vecinos aplican el mismo filtro;
     - una prueba instrumentada verifica el modo en streaming de 1.1.2 antes de construir encima.
  4. **Eliminar una carpeta borrando sus documentos:** usa `LibraryRepository.delete` documento a documento (F08), y la carpeta solo se borra cuando queda vacía.
  5. **Corrección de la spec:** al eliminar documentos, una respuesta en curso no se detiene. Sigue, y sus fuentes de esos documentos quedan como «Fuente ya no disponible», como hoy al eliminar un documento.
  6. **Pantallas y design system:**
     - la raíz de la Biblioteca con las carpetas, y la lista de F08 con un filtro de carpeta, en una ruta nueva `LibraryFolder`;
     - la hoja de carpetas común en `feature/folders`, y el selector del ámbito con su propio ViewModel;
     - cuatro piezas nuevas del design system: campo de texto, casilla de tres estados, fila desplegable y ficha del ámbito.
- **Motivo:**
  - el modo en streaming da un resultado exacto con un ámbito pequeño sin pedir casi todos los fragmentos como candidatos, y con «Toda la biblioteca» nada cambia;
  - los ids que no se reutilizan y la guarda por fecha evitan que un ámbito guardado apunte a otra carpeta u otro documento;
  - reutilizar la lista y el borrado de F08 conserva sus estados y su limpieza.
- **Alternativas descartadas:**
  - top-(k + m) también con ámbito;
  - una tabla de vectores por carpeta;
  - filtrar después de un top-k fijo;
  - el ámbito como JSON en `conversations`;
  - el último ámbito en DataStore.
- **Origen:** plan de F17 (`specs/017-folders-chat-scope/plan.md` y `research.md`).
- **Afecta:**
  - F17;
  - F04 y F10: esquema, migración, búsqueda y petición de búsqueda;
  - F08: Biblioteca y detalle;
  - F06: Ingesta;
  - F11 y F13: `chat.db`, la sesión y los estados del chat;
  - F14: la búsqueda acotada se mide de nuevo y entra en la decisión de INT8;
  - el harness de escritorio: versión 4.

---

## D-072 — Ajustes del analyze de F17

- **Fecha:** 2026-11-04
- **Decisión** (respuestas del usuario):
  1. **«General»:**
     - el grupo de los documentos sin carpeta se llama «General» («Sin carpeta» en D-066 y D-070), porque al usuario le parece un nombre con más estilo;
     - es un nombre reservado;
     - en el ámbito del chat se marca entero y se comporta como una carpeta: incluye también lo que llegue después sin carpeta.
  2. **Desmarcar un documento de una carpeta marcada entera:** la carpeta se convierte en documentos sueltos, con los demás de ese momento marcados. Lo que llegue después ya no entra.
  3. **Parada del analyze:** hecha. El plan, las tareas y el analyze quedan aprobados con sus correcciones. La implementación sigue en automático hasta la fase del teléfono, que se consulta antes. El botón de la hoja de carpetas en la Ingesta dice «Elegir».
- **Decisión** (ajustes técnicos del analyze):
  1. **Búsqueda acotada:**
     - el modo en streaming va dentro de una CTE `MATERIALIZED`, como exige F07, y la puerta comprueba con `EXPLAIN QUERY PLAN` que el escaneo se hace una vez;
     - no contradice D-040, que lo descartó para la búsqueda de toda la biblioteca, donde ordenaba todo el índice; con «Toda la biblioteca» no se usa;
     - se mide frente a top-(*k* + *m*) con el ámbito contado en m, y se queda la variante más rápida, porque las dos son exactas.
  2. **Chat:**
     - `retry` usa el ámbito de su conversación, y la puerta de la sesión y la cobertura parcial se calculan dentro del ámbito;
     - precedencia de la disponibilidad: primero los estados globales, en este orden: almacenamiento, modelos, índice, biblioteca vacía y espera a la indexación; después, nada listo en la biblioteca o en el ámbito;
     - el ámbito ajustado se reescribe, para que el aviso salga una vez;
     - «Borrar historial» borra también el último ámbito, y cambiar el ámbito no mueve la conversación en Historial.
  3. **Datos:**
     - `chat.db` guarda también la fecha de creación de las carpetas del ámbito;
     - el destino de una importación se guarda en el intento al elegir los archivos; si la carpeta se borra entretanto, va a «General» con aviso;
     - el borrado de una carpeta con sus documentos corre en el ámbito de la app y recuenta lo que queda;
     - las consultas con `folders` usan alias.
  4. **Otros:**
     - con la biblioteca vacía se ven las carpetas;
     - «ñ» y «n» cuentan como el mismo carácter en los nombres;
     - los diálogos de carpeta van con la hoja en `feature/folders`;
     - la medida de la Biblioteca va en `LibraryBenchmarkTest`, y la de la búsqueda (que necesita modelos) en el teléfono;
     - un recurso `schema_v3.sql` para la prueba de migración, y referencias corregidas.
- **Motivo:** la revisión independiente encontró 21 hallazgos:
  - 1 crítico: la parada tras el analyze;
  - 3 altos: el streaming sin `MATERIALIZED` frente a D-040, «Sin carpeta» en el ámbito y el reintento sin ámbito;
  - 8 medios y 9 bajos.
- **Alternativas descartadas:**
  - «Sin carpeta» solo con sus documentos actuales;
  - la carpeta menos los desmarcados;
  - el streaming sin CTE;
  - top-(*k* + *m*) sin medirlo frente al streaming.
- **Origen:** usuario (revisión del analyze de F17) y analyze de F17.
- **Afecta:**
  - F17: spec (FR-001, FR-015, FR-016, FR-016b, FR-020c y casos límite), wireframe, research (R3, R7, R8, R15), data-model, contratos y tareas;
  - D-066 y D-070, en el nombre del grupo sin carpeta.

---

## D-073 — Regla de viabilidad del RAG visual (clarify de F18)

- **Fecha:** 2026-11-05
- **Decisión:**
  1. **Regla de sí o no a F19–F20**, fijada antes de evaluar (FR-025 de F18). Se sigue si se cumplen las tres condiciones:
     - **calidad visual:** en las preguntas visuales (tabla, gráfico, escaneado e imagen suelta), «Ambos» da al menos **15 puntos más** de respuestas correctas que «Texto»;
     - **sin pérdida en texto:** en los controles de texto no pierde más de **2 puntos**;
     - **primer token:** p95 **≤ 8 s** de extremo a extremo en el teléfono de referencia, con al menos 1 página. Entran las páginas que quepan. Si no cabe ni una, el usuario decide si se sigue con una excepción o se para.

     Además, la 440M tiene que pasar el golden y dar los mismos vectores de texto que la 270M (SC-002), o decide el usuario.
  2. **Revisión del conjunto de prueba:** la hace el asistente, como en F05 (D-033). Las preguntas las redactan agentes separados a partir de las imágenes de página, y la sesión principal las revisa mirando cada página.
  3. **Imágenes sueltas de prueba:** imágenes públicas con licencia, más fotos simuladas generadas con semilla a partir de páginas del corpus (giro, perspectiva, sombra, ruido y JPEG).
- **Hallazgo previo** (sonda de `ModelInfo` en el escritorio, 2026-11-05):
  - el fichero de Gemma de la app admite como mucho **280 tokens por imagen** (70, 140 o 280), no los 560 que suponía el roadmap;
  - la visión de Gemma y la de la 440M solo corren en CPU.

  F18 mide hasta 280 y evalúa partir la página en trozos como alternativa. Otro fichero de Gemma sería un cambio de modelo, fuera de F18.
- **Motivo:**
  - F19–F20 cambian el modelo, el esquema, la ingesta y el prompt. Solo compensan con una mejora clara de las respuestas visuales, sin empeorar las de texto;
  - 8 s es una espera notable, pero solo en las respuestas que llevan páginas;
  - la revisión por el asistente no le quita tiempo al usuario, y su independencia se compensa como en D-033;
  - las fotos simuladas tienen contenido y relevancia conocidos y son reproducibles, y hay pocas fotos de documentos con licencia clara.
- **Alternativas descartadas:**
  - +10 o +25 puntos, o decidir sin umbral al ver las cifras;
  - un primer token de 10 s, el de hoy (≈ 6,6 s, D-057) o sin límite previo;
  - que el usuario revise una muestra o todas las preguntas;
  - solo imágenes públicas, o fotos hechas por el usuario.
- **Proceso:** como en F16 y F17, el usuario pidió el 2026-11-05 seguir el ciclo SDD en automático salvo que haga falta preguntarle. La fase del teléfono y el cierre se le preguntan antes.
- **Origen:** usuario (clarify y revisión de la spec de F18).
- **Afecta:**
  - F18: FR-019, FR-021, FR-025, SC-004 y SC-008;
  - F19 y F20: solo siguen si se cumple la regla;
  - el roadmap de F18, en los 560 tokens por imagen.

---

## D-074 — Cómo se mide la viabilidad del RAG visual (plan de F18)

- **Fecha:** 2026-11-05
- **Decisión:**
  1. **Runtime:** LiteRT-LM 0.18.1, sin cambiar de versión. Su API de visión está entera en Android y en Python. En Python, el presupuesto visual de Gemma se fija por `ctypes` con `litert_lm_conversation_optional_args_set_visual_token_budget`; en el escritorio da 101, 164 y 304 tokens de prefill con 70, 140 y 280 (research R1).
  2. **Ficheros:**
     - el de Gemma de la app admite como mucho **280 tokens por imagen**, y su visión, como la de la 440M, solo corre en CPU;
     - en lugar de 560 tokens se evalúan páginas partidas en 2 trozos (R2, R11).
  3. **Teléfono:** un diagnóstico solo de debug, «Viabilidad visual», con nueve bloques y una prueba instrumentada que los lanza (R3–R6, R14).
     - Los adaptadores de visión y de `PdfRenderer` viven en `src/debug/java`, en los paquetes `rag/runtime/litert` y `data/ingest`.
     - `LayeringTest` se amplía a esos paquetes de debug y comprueba que ni `main` ni `release` nombran el diagnóstico.
  4. **La 440M:** golden de la 270M (≥ 0,99) y vectores idénticos a los de la 270M (≥ 0,9999) en el teléfono, en una prueba instrumentada aparte (R5).
  5. **Escritorio:**
     - un corpus visual de 18 documentos (unas 314 páginas) y 15 imágenes públicas, más unas 10 fotos simuladas, de INE, AEAT, AEMET, BOE (con la Gazeta, reconstruida como solo imagen) y Wikimedia Commons (R8);
     - `pypdfium2` 5.14.0 y `pillow` 12.3.0 (R7);
     - unas 100 preguntas con relevancia por página (R9);
     - recuperación por página con RRF de tres listas (R10);
     - respuestas de texto, de páginas y de mezcla, con la regla de duplicados y la plantilla v5-visual, que solo se evalúa (R11).
  6. **Regla congelada:** `offline/config/visual_rules.json`, con la huella del conjunto de prueba, se hace commit antes de evaluar. Incluye la regla de D-073 y las reglas para elegir cada parámetro (R12).
  7. **Paridad:** 20 imágenes con los mismos bytes en el teléfono y en el escritorio, con coseno ≥ 0,99, y las páginas rasterizadas en cada lado (R13).
- **Motivo:**
  - reutiliza lo que ya funcionó: el diagnóstico y el protocolo de F02, y el harness y el juez de F05;
  - lo comprobado en el escritorio (API, presupuestos y tiempos) quita los riesgos de diseño antes de construir;
  - las fuentes elegidas permiten guardar copias en git.
- **Alternativas descartadas:**
  - subir LiteRT-LM a 0.19.0 (F14);
  - adaptadores de visión en `main` sin usar;
  - regenerar el golden con la 440M;
  - el Banco de España (todos los derechos reservados) y las fuentes con licencia no comercial;
  - una regla elegida a la vista de las cifras.
- **Hallazgos del plan:**
  - **Permisos de red:** la spec decía que la build de publicación sigue «sin permisos de red», pero desde F03 la app pide `INTERNET` para descargar los modelos. SC-009 y la historia 7 dicen ahora que los permisos y el uso de la red son los de F17;
  - **Escaneados con OCR:** los escaneados públicos llevan una capa de texto OCR.
- **Origen:** plan de F18, en el ciclo automático pedido por el usuario (D-073).
- **Afecta:**
  - F18: spec (SC-009, historia 7, supuestos del corpus), research, data-model, contratos y quickstart;
  - `LayeringTest`;
  - `offline/requirements.txt`.

---

## D-075 — Ajustes del analyze de F18

- **Fecha:** 2026-11-05
- **Decisión** (respuestas del usuario en la revisión del analyze):
  1. **Pérdida en texto:** se mide en las 323 preguntas respondibles del conjunto de F05, con la configuración elegida. Sus 6 PDF ganan páginas.
     - Solo se juzgan a mano las respuestas que cambian; las demás conservan su veredicto, como en la revisión de plantilla de F11.
     - −2 puntos son unas 6 preguntas, frente a menos de 1 con los 20 controles del conjunto visual.
  2. **Juez:** las cifras que deciden salen de una revisión manual del asistente (D-033) de «Texto» y de la configuración elegida, unas 200 respuestas, como D-035 en F05. El juez automático recorre la malla y elige los parámetros.
  3. **Primer token, en dos etapas:**
     - **A:** la matriz completa con 3 repeticiones, para descartar. Son 25 combinaciones: páginas solas y mezcla con el contexto de D-034, 1–3 páginas × 70/140/280, trozos a 280 y la base;
     - **B:** las candidatas, como mucho 3, con 20 preguntas distintas en frío y en caliente. Su p95 decide.
  4. **Arreglos aprobados:**
     - **«Ambos»** es la configuración que elige la regla de parámetros;
     - **fórmula de extremo a extremo** compuesta, con sus constantes de F17 en el A063: 206,5 ms de recuperación y 53,0 ms de cota para la lista de páginas. Se marca como compuesta;
     - **la regla congelada** también lleva los umbrales de coseno (0,99 y 0,9999). Las ablaciones se niegan a correr si el fichero difiere de `HEAD`, y cada ejecución guarda su hash git;
     - **la decisión en tres pasos:** recuperación, candidatas y regla completa;
     - **la propuesta de enmienda** incluye también IV: un presupuesto de primer token propio para las respuestas con páginas;
     - **la prueba de variantes** comprueba las huellas contra `ModelCatalog` y escribe un JSON;
     - **un engine por escenario:** cada escenario usa una sola configuración de engine;
     - **Gemma recibe las partes en el orden de v5-visual** en los dos lados;
     - **el conjunto del dispositivo** gana `decode/` y 20 preguntas en `contexts.json`;
     - **el texto generado** va solo al JSON;
     - **el corpus** va en `offline/corpus_visual/`, fuera de los assets de las pruebas;
     - **las pruebas del ViewModel**, antes de la pantalla;
     - y correcciones menores en rutas, recuentos y textos.
- **Motivo:** la revisión independiente encontró 29 hallazgos:
  - 1 crítico: la parada tras el analyze, que se ha hecho;
  - 5 altos: la etapa B, la muestra del p95, el significado de «Ambos» y el juez, los 2 puntos sobre 20 preguntas, y el principio IV;
  - 11 medios y 12 bajos.
- **Alternativas descartadas:**
  - 40 controles visuales, o 20 sin ninguna pérdida;
  - decidir con el juez automático;
  - toda la matriz con 20 preguntas (más de 1 h de teléfono y con calor).
- **Origen:** usuario (revisión del analyze de F18) y analyze de F18.
- **Afecta:**
  - F18: spec (FR-005, FR-025, FR-029, SC-004, SC-008 y US5), research (R3, R4, R5, R8, R11, R12, R13 y R15), data-model, contratos, quickstart y tareas (60);
  - D-073, en cómo se miden sus tres condiciones.

---

## D-076 — Las páginas de la respuesta salen de la lista de páginas (implement de F18)

- **Fecha:** 2026-11-05
- **Decisión** (respuesta del usuario tras las ablaciones de recuperación):
  - **las páginas** que recibe Gemma en «Páginas» y en «Mezcla» salen de la búsqueda por páginas («Visual»), no de la RRF de las tres listas;
  - **los bloques de texto** siguen saliendo de la búsqueda de fragmentos de D-034, con la regla de duplicados;
  - **la resolución, los tokens y la dimensión** se eligen con el Recall@5 visual de «Visual», con los mismos márgenes (0,02 y 0,03);
  - **los umbrales del sí o no no cambian** (D-073, D-075). «Ambos» sigue siendo la configuración que eligen las reglas, ahora con la fusión en el contexto y no en la lista.
  - La RRF de tres listas sigue en el informe, como hallazgo.
- **Motivo:** la RRF premia las páginas que aparecen en varias listas, y una página sin texto solo está en la de páginas. Con 1.024 px, 140 tokens y 256 dimensiones, este es el Recall@5 de cada fusión:

  | | Escaneados | Imágenes | Visuales | Todas |
  |---|---|---|---|---|
  | «Texto» | 0,00 | 0,25 | 0,42 | 0,55 |
  | «Visual» | 0,66 | 0,82 | 0,73 | 0,76 |
  | «Ambos» con la RRF de tres listas | 0,00 | 0,25 | 0,45 | 0,56 |

  Con la RRF de tres listas, casi ningún escaneado ni imagen llegaría a Gemma, y el sí o no mediría la fusión, no la lectura de las páginas.
- **Desviación:** el cambio se hizo después de ver las ablaciones de recuperación, con la regla ya congelada (FR-025). Se volvió a hacer commit de la regla antes de las ablaciones de respuesta, y el informe lo explica.
- **Alternativas descartadas:**
  - mantener la RRF de tres listas, la registrada antes de mirar los datos;
  - una RRF en dos niveles, con un voto por tipo de lista: los escaneados suben a unos 0,27, pero las imágenes siguen en 0,25.
- **Origen:** usuario (pregunta de la sesión tras T028).
- **Afecta:**
  - F18: spec (FR-022, FR-025 y US5), research (R10, R11 y R12), plan, `offline/config/visual_rules.json` (`selection.retrieval.mode` y `answer_pages`) y tareas (T028 y T032);
  - F20: la recuperación con tres listas de su ficha se revisa con lo que diga F18.

---

## D-077 — Resultado de F18: «no» al RAG visual, que pasa al backlog

- **Fecha:** 2026-11-05
- **Decisión** (respuesta del usuario tras aplicar la regla de D-073):
  1. **«No» a F19–F20.** Pasan al backlog, con las condiciones para volver a intentarlo de la sección 4. No se redacta la enmienda de la constitución (FR-029 solo la pide con el «sí»).
  2. **La 270M se queda** (D-025 sigue). La 440M cumple el golden y la identidad, pero sin páginas no aporta nada y pesa 230 MB más.
  3. **Sin revisión decisiva ni pérdida en texto en F05.** Ninguna configuración cumple el primer token, así que no hay configuración elegida que revisar, y esas cifras no cambiarían el resultado. Es una desviación de D-075 que aprobó el usuario.
  4. **Parámetros que quedan medidos**, para un intento futuro:
     - página a 768 px, 140 tokens por imagen y vectores de 256 dimensiones (elegidos con la regla congelada, D-076);
     - JPEG a calidad 85 hacia los modelos, porque LiteRT-LM 0.18.1 no decodifica WebP;
     - las páginas de la respuesta salen de la lista de páginas (D-076);
     - Gemma con páginas necesita una ventana de 8.192 tokens;
     - la regla de duplicados (si entra la página, salen sus fragmentos) empeora el texto.
- **Motivo:** la regla da «no» y pide decidir con el usuario, porque ninguna configuración con páginas cumple el primer token.

  | Condición (D-073) | Cifra | Cumple |
  |---|---|---|
  | p95 compuesto ≤ 8 s con al menos 1 página | la mejor candidata de la etapa B (`mezcla-p1-b70-t0`, 20 preguntas) da 9,8 s. Con 1 página a 280 son 14,5 s en la etapa A | no |
  | +15 puntos en las visuales | sin revisión decisiva. Con el juez automático (comprobado a mano en 90 respuestas, 88 coincidencias), las tres candidatas dan 18,9 %, 14,7 % y 13,7 %, frente al 18,9 % de «Texto». La mejor variante de todas, 3 páginas en mitades, da 27,4 % (+8,5), pero tarda 70 s en el teléfono | no |
  | −2 puntos como mucho en F05 | sin medir. En el conjunto visual, todas las mezclas bajan los controles de texto del 85,7 % a un 11–57 % | — |
  | 440M: golden ≥ 0,99 e identidad ≥ 0,9999 | golden en CPU 1,00000 y en GPU 0,99299; identidad 1,00000; consulta con p95 de 121,1 ms | sí |

  El cuello de botella es el codificador de visión de Gemma, que el fichero solo declara en CPU: 1 página a 70 tokens añade unos 2 s al primer token, a 140 unos 4 s y a 280 unos 11 s. Cifras en `specs/018-visual-rag-feasibility/benchmarks.md` y `offline/reports/visual-eval-report.md`.
- **Condiciones para retomar F19–F20:**
  - un codificador de visión en GPU o NPU (otro LiteRT-LM u otro fichero de Gemma), o un teléfono de referencia más rápido, que deje 1 página a 280 tokens por debajo de 8 s;
  - una mezcla que no quite el texto de la página que entra como imagen;
  - una página entera leída a 280 tokens no basta para la letra pequeña: hay que partirla en trozos, y eso multiplica el primer token.
- **Hallazgos para otras features:**
  - **Cifras deformadas con fp16 (F14, y afecta al chat de hoy).** Con las activaciones fp16 por defecto, Gemma deforma cifras en contextos llenos de números: «2020» sale como «20000» y «36,4» como «366,4». En el Mac pasaba en muchas respuestas, y con FP32 desaparece. En el teléfono salió en 2 de 60 respuestas de la etapa B («septiembre de 20206»). En el corpus de F05, de leyes y guías, no se ve: con FP32 cambian 239 de 426 respuestas, pero solo de redacción, y la regla da lo mismo (72,4 % frente a 72,2 %). FP32 en el teléfono cuesta casi 3 s de primer token (D-023: 7,36 s frente a 4,53 s).
  - **Estimación de tokens (F14).** En texto lleno de cifras, los 4,554 caracteres por token de D-034 se quedan cortos: los bloques de D-034 del corpus visual gastan unas 2,2 veces lo estimado (3.471 tokens reales frente a 1.542 estimados).
  - **RRF de tres listas.** Premia las páginas que aparecen en varias listas, así que entierra las que no tienen texto: los escaneados se quedaron con un Recall@5 de 0 (D-076).
  - **Lo que sí funciona en el teléfono:**
    - `PdfRenderer` convierte una página en 28–67 ms en JPEG, también un PDF con contraseña de propietario;
    - la decodificación de imágenes funciona con transparencia, con giro EXIF y con CMYK;
    - la paridad de vectores con el escritorio da un coseno medio de 0,99931 (1 de 40 por debajo de 0,99).
- **Alternativas descartadas:**
  - una excepción de primer token y la revisión decisiva de la candidata más rápida: según el juez, pierde 4 puntos en las visuales;
  - descartar F19–F20 del todo, sin dejarlas en el backlog.
- **Origen:** usuario (pregunta de T058, tras aplicar la regla) y mediciones de F18.
- **Afecta:**
  - F18: cierre, `benchmarks.md`, `report.md` e informe de evaluación;
  - F19 y F20: pasan al backlog, y G47 queda aplazado;
  - F14: pierde lo que venía de F19–F20 y gana los hallazgos de fp16 y de la estimación de tokens;
  - F15: depende de F01–F18;
  - D-025 y D-066.

---

## D-078 — El RAG visual se construye como modo opcional (cierre de F18)

- **Fecha:** 2026-11-05
- **Decisión** (del usuario, al revisar el resultado de F18):
  1. **F19–F20 vuelven al roadmap.** Se implementan como se definieron (D-066), pero como **modo «Multimodal» opcional** en Ajustes, **desactivado por defecto**. Desactivado, la app se comporta exactamente como hoy.
  2. **Sin regla de sí o no ni ablaciones comparativas.** El usuario conoce los límites que midió F18 y decide activarlo aceptando respuestas más lentas. Las cifras del teléfono se miden e informan, pero no bloquean.
  3. **La 440M se descarga al activar el modo** (369 MB) y pasa a embeber también el texto. Su identidad con la 270M es de 1,00000, así que no hay que reindexar. La 270M se borra después de validar. Quien no activa el modo no descarga nada.
  4. **Sin regla de duplicados:** entran los bloques de texto de D-034 más la página como imagen.
  5. **El usuario elige el detalle** («Rápido / Equilibrado / Detallado»), con 1 página a 70, 140 o 280 tokens. En el A063 son unos 10, 11,5 y 14,5 s de primer token.
  6. **Parámetros de F18:**
     - página a 768 px, en JPEG a calidad 85;
     - la 440M a 140 tokens por imagen, con vectores de página de 256D;
     - las páginas salen de su propia lista (D-076);
     - Gemma con la visión en CPU y una ventana de 8.192 tokens en este modo.
- **Motivo:**
  - la regla de D-073 dio «no» con cifras medidas (D-077): con páginas, el primer token pasa de 8 s en el A063, y lo que cabe en ese tiempo no mejora a «Texto»;
  - aun así, el modo aporta donde el texto no llega. En F18, con las imágenes sueltas acertaba hasta un 50–55 %, frente al 36 % de «Texto», y es la única forma de responder sobre escaneados sin texto;
  - como opción que se activa a sabiendas, el coste solo lo paga quien lo elige;
  - quitar el texto de la página hundía los controles de texto del 86 % al 43 % (D-077), y por eso sale la regla de duplicados.
- **Alternativas descartadas:**
  - dejar F19–F20 en el backlog (D-077);
  - la 440M para todos desde el onboarding (D-066), que son 230 MB más para quien no usa el modo;
  - las dos variantes de embeddings a la vez, que embeben cada consulta dos veces;
  - un detalle fijo.
- **Origen:** usuario, en la revisión de cierre de F18.
- **Afecta:**
  - **D-077:** sustituye su punto 1. Siguen valiendo las cifras, los hallazgos y la falta de enmienda dentro de F18;
  - **D-025:** con el modo activo, la 440M sustituye a la 270M;
  - **D-066 y D-076;**
  - **la constitución:** III (página citable, 440M y 256D para las páginas), IV (primer token propio de las respuestas con páginas, medido y avisado) y Restricciones. Se enmienda al empezar F19, antes de su plan;
  - **FEATURES:**
    - F18 cerrada;
    - F19 y F20 de nuevo en el roadmap, sin el criterio de calidad de F18;
    - F14, que mide también el camino multimodal;
    - F15, que vuelve a depender de F01–F20;
  - **INVENTARIO:** G47 vuelve a estar abierto.

---

## D-079 — Modo multimodal: decisiones del clarify de F19 y enmienda 1.2.0

- **Fecha:** 2026-11-05
- **Decisión** (respuestas del usuario en el clarify de F19):
  1. **Desactivar el modo conserva todo:**
     - las páginas y sus vectores se quedan, sin usarse;
     - la 440M sigue embebiendo el texto, y la 270M no vuelve;
     - reactivarlo es inmediato, sin descargar ni reindexar.
  2. **Activar el modo no reindexa nada:** los PDF ya indexados en Texto se pasan a Visual o Ambos desde su detalle, cuando el usuario quiera.
  3. **El modo por defecto al importar es Ambos,** texto más páginas.
  4. **El interruptor va en Ajustes → «Inteligencia artificial»,** junto a los modelos. Debajo van el modo por defecto y, en F20, el detalle de las páginas.
  5. **Wireframe G47 aprobado** (`specs/019-visual-indexing/wireframe.md`):
     - aviso y progreso al activar;
     - la pantalla «Modo al importar PDF», como Tema;
     - la fila «Modo de los PDF» en la Ingesta;
     - el modo y las páginas en la Biblioteca y en el detalle;
     - todo con piezas `Lisdata*` que ya existen.
  6. **Enmienda de la constitución 1.1.1 → 1.2.0** (MINOR):
     - **III:** el espacio de páginas en `rag_meta`; la 440M en el modo multimodal, que sustituye a la 270M sin reindexar tras validar en el dispositivo una identidad ≥ 0,9999; los vectores de página a 256D con L2; y las citas a una página real;
     - **IV:** el primer token propio de las respuestas con páginas, medido y avisado; la visión en CPU; y la indexación visual medida e informada;
     - **Restricciones:** la 440M y los escaneados, JPG y PNG con el modo activo.
- **Motivo:**
  - quien desactiva el modo puede volver a activarlo sin repetir una descarga ni una indexación de minutos;
  - una indexación de unos 3 s por página no debe empezar sin pedirla;
  - Ambos es lo más completo, y no pierde la respuesta por texto;
  - el interruptor cambia el modelo de embeddings, así que va con los modelos.
- **Alternativas descartadas:**
  - al desactivar, preguntar si borrar las páginas, o volver a la 270M;
  - ofrecer reindexar en bloque, o hacerlo automático;
  - Texto o Visual por defecto;
  - el interruptor en «Preferencias» o en una pantalla propia.
- **Origen:** usuario (clarify de F19).
- **Afecta:**
  - F19: spec (FR-001, FR-007, FR-008b y FR-022), wireframe y plan;
  - F20: el detalle de las páginas va bajo el interruptor;
  - la constitución, D-025 y D-078.

---

## D-080 — Cómo se construye el modo multimodal (plan de F19)

- **Fecha:** 2026-11-05
- **Decisión** (plan de F19, `specs/019-visual-indexing/research.md`):
  1. **Activación** como máquina de estados persistida, `OFF → DOWNLOADING → VALIDATING → SWITCHING → ON`, en `multimodal.preferences_pb`. La lleva un controlador con ámbito de aplicación, que la retoma al arrancar (R1).
  2. **El embedder de texto activo lo dice el índice** (`rag_meta.embedding_model_sha256`). El comprobador acepta la 270M y la 440M. La indexación, la consulta, Ajustes y la lista de modelos obligatorios lo leen del índice (R2, R5).
  3. **Validación de identidad:** 20 textos fijos, embebidos como documento y como consulta con los dos modelos en CPU. Pasa con un coseno mínimo ≥ 0,9999 sobre el vector final de 256D (R3).
  4. **Cambio:**
     - pausa la ingesta;
     - actualiza la huella en una transacción;
     - libera los engines y borra la 270M con `ModelFileRepository.delete`, que es nuevo;
     - reanuda.

     Un corte se reconcilia al arrancar (R4).
  5. **La descarga de la 440M** usa el camino de F03, con las listas de modelos generalizadas (R6).
  6. **El embedder de imagen y el conversor** de F18 pasan de debug a `rag/runtime/litert` y `data/ingest`, y el diagnóstico los reutiliza (R7).
  7. **`rag.db` v5:**
     - `documents.index_mode`;
     - la tabla `pages`, con un vector de 256D y su `vector_init`;
     - las claves `page_*` de `rag_meta`, que no impiden abrir el índice: sin ellas no se usan las páginas (R8).
  8. **Una fase de páginas en el mismo run de ingesta:**
     - una página por transacción;
     - el fichero en `noBackupFilesDir/pages/`, escrito de forma atómica;
     - las pausas de F07, con 250 KB por página más el margen;
     - el documento solo queda indexado con todo completo (R9).
  9. **JPG y PNG** solo con el modo activo, siempre en Visual. Un PDF sin texto en Ambos no es un error (R9, R10).
  10. **Reindexar en otro modo** usa la copia en sombra con sus páginas. Quitar del índice y eliminar borran los ficheros, y al arrancar se barren los huérfanos (R11).
- **Motivo:**
  - reutiliza lo que ya funciona: la descarga de F03, la ingesta de F07, la sombra de F08 y los adaptadores de F18;
  - deja una sola fuente para el embedder activo;
  - con el modo apagado, no cambia nada.
- **Alternativas descartadas:**
  - un `WorkManager` para toda la activación;
  - un ajuste aparte con el modelo activo;
  - las páginas en la tabla de fragmentos;
  - un trabajo de ingesta aparte para las páginas.
- **Origen:** plan de F19.
- **Afecta:** F19 (tareas e implementación) y F20, que busca en `pages`.

---

## D-081 — Ajustes del analyze de F19

- **Fecha:** 2026-11-05
- **Decisión:**
  1. **Anexo del wireframe (§8),** aprobado por el usuario (C1):
     - **cola:** una estimación de páginas, tiempo y espacio, y la pausa por espacio con lo que falta;
     - **Indexación:** la etapa «Imágenes de página», con el anillo en páginas;
     - **notificación:** «Indexando páginas · 12 / 40»;
     - **pausas:** «cambiando el modelo de búsqueda» y «Esperando al modo multimodal»;
     - **error:** «Imagen no válida».
  2. **Correcciones técnicas,** recogidas en research, contratos y tareas (T058–T062):
     - **la etapa de páginas llega al programador, a Indexación y a la notificación (H1).** Sin ello, el worker fallaría al publicar el progreso;
     - **`EngineKey` con tipo (H2):** el embedder de imagen y el de texto de la 440M en CPU son engines distintos;
     - **el embedder activo, antes de abrir el índice (H3),** sale de un espejo en el almacén del modo, y `rag_meta` manda al abrir;
     - **reindexar con modo sin sombra (H4),** para un documento en error o fuera del índice, y `publishReindex` copia el modo;
     - **en el teléfono, la batería y la validación antes de activar el modo (H5),** y después el golden con la 440M contra los mismos vectores de referencia;
     - **fallos (M1):** los de descarga se quedan en `DOWNLOADING` con Reintentar y Cancelar; los de validación vuelven a `OFF` y borran la 440M;
     - **con el modo apagado (M5),** los documentos con páginas esperan, y se reanudan al reactivarlo;
     - **el tamaño de la descarga** sale del catálogo, 388 MB (M7);
     - **los ficheros de página** comparten un cerrojo con el barrido de huérfanos, y la limpieza cubre las sombras (M6);
     - correcciones menores de nombres, rutas, cifras y pruebas (M2–M4, M8 y L1–L9).
- **Motivo:** la revisión independiente encontró 23 hallazgos: 1 CRITICAL, 5 HIGH, 8 MEDIUM y 9 LOW.
- **Origen:** usuario (aprobación del anexo) y analyze de F19.
- **Afecta:** F19: spec (FR-002, FR-007, FR-015 y FR-018), wireframe, research (R2, R4, R7, R9, R11, R14–R16), contratos y tareas (62).

---

## D-082 — Resultado de F19: el modo multimodal, medido en el teléfono

- **Fecha:** 2026-11-05
- **Decisión:** el modo «Multimodal» queda implementado como opción desactivada por defecto. Con el modo apagado, las cifras de texto no cambian: `ablations` y `order-review` dan lo mismo que en F18. En el A063, con permiso del usuario, se activó de verdad y se deja **activado**, como pidió. Cifras, todas informativas, sin regla de sí o no (D-078):
  1. **Activación, de «Activar» a `ON`, en 45 s:**
     - descarga y huella de la 440M, 32,6 s;
     - preparación, 1,3 s;
     - identidad de los vectores de texto, 11,0 s con 20 textos, mínimo 1,000000;
     - cambio y borrado de la 270M, 0,07 s.
  2. **Búsqueda igual con la 440M:**
     - golden CPU 1,000000 y GPU 0,992993, iguales a los de la 270M;
     - `RetrievalParityTest` 0,816 frente a 0,837, igual que desde F10;
     - consulta p95 de 136,6 ms con 10.000 fragmentos.
  3. **Indexación por páginas:**
     - 14,2 páginas/min (4,2 s por página) y 111 KB por página en 200 páginas generadas, con la reanudación a mitad sin duplicar;
     - un JPG de 4,7 MB en 4,6 s y un escaneado de 2 páginas en 7,7 s.
  4. **Decisiones de implementación** (informe, §7):
     - `ModelRequirements` separa lo que responde (`core`), lo que se descarga y prepara (`files`) y las filas (`shown`);
     - los ficheros de página se leen antes de cada borrado y se escriben con su fila bajo un mismo cerrojo;
     - la pausa nueva «Esperando al modo multimodal» sale cuando solo quedan documentos con páginas;
     - el espacio de todas las páginas que faltan se comprueba antes de empezar;
     - icono nuevo `ic_image` para la etapa de páginas.
  5. **Cierre aprobado** por el usuario el 2026-11-05, con dos arreglos del informe §6:
     - con el modo activo, la línea de formatos de la Ingesta nombra JPG y PNG;
     - en un documento solo Visual incluido, el subtítulo de «Incluir en respuestas» dice «Se usará cuando el chat admita páginas», no «Disponible para consultas RAG».
- **Motivo:** cerrar F19 con las cifras reales del teléfono de referencia, como pide la constitución 1.2.0 (IV: la indexación visual se mide y se informa).
- **Origen:** implement de F19, fase del teléfono y revisión de cierre, aprobadas por el usuario.
- **Afecta:** F19 (cierre); F20, que parte de un índice ya en la 440M en el teléfono; F14, que mide cargar el motor de imagen una vez por run (hoy una vez por documento, unos 0,9 s) y los vectores de página en F32 frente a INT8.

---

## D-083 — Chat con páginas: decisiones del clarify de F20

- **Fecha:** 2026-11-05
- **Decisión** (respuestas del usuario en el clarify de F20):
  1. **La página entra siempre** que haya páginas en el ámbito: la mejor de la lista de páginas, sin umbral de parecido. Para respuestas rápidas sobre texto, se acota el ámbito a carpetas sin páginas.
  2. **El detalle por defecto es Equilibrado:** 1 página a 140 tokens, los mismos con que se indexan las páginas.
  3. **La página citada se ve en un visor a pantalla completa:** en el detalle, la página a lo ancho sustituye al fragmento resaltado; al tocarla se abre el visor, con pellizco y doble toque para ampliar.
  4. **Wireframe G47 aprobado** (`specs/020-visual-chat/wireframe.md`):
     - «Detalle de las páginas» en Ajustes, como «Modo al importar PDF»;
     - el estado «Leyendo 1 página · unos 11,5 s…» en el chat;
     - la miniatura de la página en «Fuentes consultadas», con un hueco nuevo en `LisdataSourceCitation`;
     - la página en el detalle y el visor, la única pieza nueva;
     - las notas de F19 que cambian y el diagnóstico de búsqueda en debug.
  5. **Sin enmienda de la constitución:** la 1.2.0 ya admite las citas a página (III) y el primer token propio de las respuestas con páginas (IV).
- **Motivo:**
  - un umbral sin calibrar puede dejar fuera los escaneados que sí responden, y D-078 no prevé pruebas comparativas para fijarlo;
  - Equilibrado lee con el mismo detalle con que se buscó la página;
  - una página de 768 px no se lee en el móvil sin ampliarla.
- **Alternativas descartadas:**
  - un umbral de parecido;
  - un control «Con páginas» en el compositor;
  - Rápido o Detallado por defecto;
  - la página sin zoom, o con zoom dentro de su tarjeta.
- **Origen:** usuario (clarify de F20).
- **Afecta:** F20: spec (FR-004, FR-012, FR-016 y FR-006b), wireframe y plan; G47.

---

## D-084 — Chat con páginas: decisiones del plan de F20

- **Fecha:** 2026-11-05
- **Decisión** (plan de F20, `specs/020-visual-chat/research.md`):
  1. **Cuándo hay páginas:** solo con el modo en `ON`, el espacio de páginas del índice compatible y alguna página en el ámbito. Se decide al empezar cada respuesta (R1).
  2. **Búsqueda de páginas:** `RagIndex.pageSearch`, un barrido completo de `pages` con el mismo vector de la consulta, filtrado por ámbito y por buscables. Devuelve 5 candidatas (`PAGE_CANDIDATES`), de las que entra 1. No pasa por la RRF (R2, D-076).
  3. **Plantilla v5-visual:** port exacto de `visual_prompt.py` a `PromptBuilder.buildVisual`:
     - los bloques de D-034 sin tocar y la página después, con el número `[k + 1]`;
     - el límite de 2.000 tokens solo sobre el texto;
     - una golden del escritorio (`visual_prompt_golden.jsonl`) vigila la paridad (R4).
  4. **Gemma con el modo activo:** siempre con la visión en CPU, una ventana de 8.192 tokens y 1 imagen, haya página o no. Lleva su propia clave (`EngineKind.VISION_GENERATOR`) y el generador de F18 pasa de debug a `LiteRtTextGenerator` (R5, R6).
  5. **La imagen:** el JPEG guardado por F19. Si falta, la respuesta sigue sin la página (R7).
  6. **Fuentes de página:**
     - `kind` en las fuentes, con `chunk_id = 0` para las páginas;
     - `chat.db` v3, con `sources.kind` y `turns.page_tokens`;
     - `PAGE_GONE` en el Historial (R8–R10).
  7. **Detalle de las páginas:** `PageDetail` (70, 140 y 280 tokens), con las esperas de F18 hasta que F20 las mida, y la clave `page_detail` en el almacén del modo (R12).
  8. **UI:**
     - el hueco `leading` en `LisdataSourceCitation` para la miniatura;
     - `PageImageRepository` y un cargador con caché;
     - la ruta `PageViewer` con `ZoomState`, de 1× a 4×, con acciones de TalkBack (R14, R15).
  9. **Medición:** `VisualChatBenchmarkTest` en el teléfono, con 20 preguntas por detalle y 20 sin páginas, sin umbral (R19).
- **Motivo:**
  - una sola configuración de Gemma por modo evita recargarlo entre respuestas;
  - un campo de tipo en las fuentes es el cambio más pequeño, sin reconstruir tablas;
  - la golden evita que el port se separe del escritorio.
- **Alternativas descartadas:**
  - cargar Gemma con visión solo cuando entra una página;
  - un tipo sellado de fuente;
  - `chunk_id` nulo, que obliga a reconstruir `sources`;
  - una librería de imágenes o de zoom.
- **Origen:** plan de F20, en el ciclo automático que pidió el usuario.
- **Afecta:** F20: plan, research, data-model, contratos y tareas; F14, que mide el camino con visión.

---

## Pendientes de decisión

Se presentan al usuario junto con el inventario. Detalle en `INVENTARIO.md` §6.

- **Huecos G del inventario:** queda abierto G47 (UI del modo visual, F19–F20, añadido con D-066), que vuelve con D-078. G45 (selector de tema) se resolvió en D-067 y G46 (carpetas y ámbito) en D-070. Su UI se propone y se aprueba en el clarify de su feature. Los anteriores se resolvieron en sus features; el estado de cada uno está en `INVENTARIO.md` §6.
- **Documentos en otro idioma** — Cerrado en D-062 (medida entre idiomas de F12). Las mejoras de la búsqueda entre idiomas están en la ficha de F14.
