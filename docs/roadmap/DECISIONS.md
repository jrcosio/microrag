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
- **Propuestas de UI:** S1–S11 de la spec, que se revisan en la parada del clarify.
- **Origen:** usuario (clarify de F12).
- **Afecta:**
  - F12;
  - F11: el prompt en español no cambia, y la lectura de la negativa admite el inglés;
  - F13: «Borrar historial» de Ajustes vacía lo guardado;
  - F14: mejoras de la búsqueda entre idiomas, si las cifras las piden;
  - F15: avisos de licencias (G23).

---

## Pendientes de decisión

Se presentan al usuario junto con el inventario. Detalle en `INVENTARIO.md` §6.

- **Huecos G del inventario:** ninguno queda abierto. Los últimos, G23, G24 y G25, quedan resueltos en D-058. Los demás se resolvieron en sus features; el estado de cada uno está en `INVENTARIO.md` §6.
- **Documentos en otro idioma** — Sin medir.
  - **Lo que hay:** toda la evaluación usa documentos y preguntas en español (F05, la paridad de F10 y la medida del primer token de F11).
  - **Generación:** Gemma 4 E2B lee el contexto en inglés y responde en español.
  - **Búsqueda:** la mitad por palabras está pensada para el español (palabras vacías y raíces del español). Con un documento en inglés y la pregunta en español, solo ayuda la búsqueda por significado (EmbeddingGemma 2, multilingüe). Se esperan más negativas o respuestas incompletas.
  - **Pendiente:**
    - medirlo en el escritorio con documentos en inglés y preguntas en español y en inglés;
    - decidir con cifras, por ejemplo una normalización según el idioma del documento.

    Encaja con F12 (selector de idioma) o con F14.
  - **Origen:** pregunta del usuario en la fase del teléfono de F11 (2026-10-31), que pidió dejarlo pendiente.
  - **Estado:** se mide en F12 (D-058, FR-033 de su spec) y se cierra con esas cifras.
