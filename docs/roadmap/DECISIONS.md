# MicroRAG — Registro de decisiones

Registro vivo con fecha, decisión, motivo, origen y alcance. Cada decisión nueva se añade al final con el siguiente identificador. Las decisiones no se borran: si una se sustituye, se marca como «Sustituida por D-XXX».

Los huecos aún sin resolver están en `INVENTARIO.md` §6 (G01–G42).

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

---

## Pendientes de decisión

Se presentan al usuario junto con el inventario. Detalle en `INVENTARIO.md` §6.

- **G08** — Flecha Atrás en las pestañas, que vuelve a Inicio. Queda aplicada en la spec de F01 como supuesto por la precedencia del PNG; se revisa en el checkpoint de F01.
- **G10** — Contenido de los pasos 2 y 3 de Preparación.
- **G11–G17, G21, G22, G24–G26, G29–G31, G39** — Propuestas para estados y acciones no maquetados.
