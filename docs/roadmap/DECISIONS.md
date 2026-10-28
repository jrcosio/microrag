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

## Pendientes de decisión

Se presentan al usuario junto con el inventario. Detalle en `INVENTARIO.md` §6.

- **G08** — Flecha Atrás en las pestañas, que vuelve a Inicio. Queda aplicada en la spec de F01 como supuesto por la precedencia del PNG; se revisa en el checkpoint de F01.
- **G10** — Contenido de los pasos 2 y 3 de Preparación.
- **G11–G17, G21, G22, G24–G26, G29–G31, G39** — Propuestas para estados y acciones no maquetados.
