# Contrato — UI de Estado de indexación (`index`) y cambios en la Ingesta

- **Textos:** `res/values/strings_indexing.xml` (es, por defecto) y `res/values-en/strings_indexing.xml` (en), según D-029. Los textos visibles en el PNG 08 se copian literalmente. El resto son propuestas (§Propuestas) que se aprueban en la parada tras analyze, antes de implementarlas (constitución VII).
- **Componentes:** solo `Lisdata*` y tokens. Nada de hex en la pantalla.
- **Componente ampliado:** `LisdataHeroCard` hace opcional el título, porque el hero del PNG 08 no lo tiene. Se ajustan su preview y el catálogo.

## Estructura (PNG 08, de arriba abajo)

| # | Elemento | Componente | es | en |
|---|---|---|---|---|
| 1 | Top bar con Atrás | `LisdataTopBar(onBack)` | «Estado de indexación» | «Indexing status» |
| 2 | Hero oscuro | `LisdataHeroCard(compact)` con eyebrow: márgenes de 16 dp y huecos de 8, como el PNG (D-042) | «PROCESAMIENTO LOCAL» | «LOCAL PROCESSING» |
| 3 | Anillo | `LisdataProgressRing(ProgressValue.Determinate(%))` de 154 dp, etiqueta «COMPLETADO» (D-042) | «68%» y «COMPLETADO» | «COMPLETE» |
| 4 | Fragmentos | Texto bajo el anillo, `onDarkText` | «8.432 / 12.400 fragmentos» (formato local) | «8,432 / 12,400 chunks» |
| 5 | Cabecera | `LisdataSectionHeader` | «ETAPAS DEL PROCESO» | «PROCESS STAGES» |
| 6 | Etapas | `LisdataStageRow(compact)` × 4: 16 y 13 sp, 56 dp (data-model §3, G29, D-042) | «Extracción de texto», «Fragmentación», «Generación de embeddings», «Persistencia vectorial»; estados «Completado», «En curso», «Pendiente» | «Text extraction», «Chunking», «Embedding generation», «Vector persistence»; «Done», «In progress», «Pending» |
| 7 | Documento actual | `LisdataInfoCard(ic_file_text)` | «Procesando ahora» y el nombre | «Processing now» |
| 8 | Acción fija abajo | `LisdataDarkButton` con `ic_pause`, 52 dp | «Pausar indexación» | «Pause indexing» |

**Iconos de las etapas**, como el PNG:
- completada: `ic_check` sobre el contenedor de éxito;
- en curso: `ic_cpu` sobre el contenedor de marca;
- pendiente: `ic_database` sobre el tile neutro.

**Desplazamiento:** 2–7 se desplazan. La acción queda fija abajo, con el margen de 20 dp (tras la corrección de márgenes de F06, una sola vez la barra del sistema).

## Propuestas para estados sin mockup (aprobar en la parada tras analyze)

- **P1 — Sin tareas** («Todo actualizado», FR-032):
  - en lugar del hero y las etapas, `LisdataEmptyState` con `ic_check_circle`;
  - título «Todo actualizado» y texto «No hay documentos pendientes de indexar.»;
  - sin acción. Si hay documentos con error, se añade debajo la sección de P6.
- **P2 — Total desconocido** (fase 1 de la tanda): anillo indeterminado con la etiqueta «Procesando...» y, debajo, «Preparando documentos». No se muestra «N / M».
- **P3 — En pausa por el usuario:**
  - el anillo queda en su porcentaje;
  - bajo los fragmentos va `LisdataStatusChip(StatusKind.Paused)` «En pausa»;
  - la acción pasa a `LisdataGradientButton` «Reanudar» con `ic_play`;
  - la etapa en curso muestra «En pausa».
- **P4 — Pausa automática** (temperatura o límite del sistema):
  - como P3, pero con `LisdataStatusChip(StatusKind.Warning)`: «Pausa por temperatura» o «Esperando al sistema»;
  - bajo las etapas va `LisdataInfoCard(ic_warning)` con la explicación:
    - temperatura: «El teléfono está muy caliente. La indexación seguirá sola cuando se enfríe.»;
    - límite del sistema: «Android ha limitado el trabajo en segundo plano. Seguirá en cuanto lo permita o al abrir la app.»;
  - la acción sigue siendo «Pausar indexación», para que el usuario pueda convertirla en pausa manual.
- **P5 — Sin espacio:**
  - `LisdataStatusChip(StatusKind.Error)` «Sin espacio»;
  - `LisdataInfoCard(ic_warning)` con «No queda espacio para seguir indexando. Libera espacio en el teléfono y pulsa Reanudar.»;
  - acción «Reanudar».
- **P6 — Documentos con error** (en curso, en pausa o sin tareas):
  - sección «DOCUMENTOS CON ERROR» con `LisdataQueueRow` por documento: nombre, chip de error con el motivo (textos de la Ingesta) y la acción «Reintentar» si se puede reintentar;
  - si el documento que falla es el único de la tanda, la acción principal es «Reintentar».
- **P7 — Modelo ausente:**
  - `LisdataEmptyState` con `ic_cpu`, título «Falta el modelo de embeddings» y texto «Hace falta para indexar. Puedes volver a descargarlo desde la preparación de modelos.»;
  - acción `LisdataGradientButton` «Preparar modelos», que navega a `Setup` (F03).
- **P8 — Biblioteca no disponible:** como P5 de la Ingesta: `LisdataErrorState` «La biblioteca no está disponible». Sin acción de indexar.
- **P9 — Tiempo restante:**
  - bajo «N / M fragmentos», en el hero, «≈ 12 min restantes» con `onDarkAccent`;
  - solo con ritmo estable y nunca en pausa (R8);
  - si quedan otros documentos además del actual, la tarjeta «Procesando ahora» añade la línea «y %d documentos más en cola».
- **P10 — Acceso provisional desde Inicio:** el placeholder de Inicio gana un segundo `LisdataOutlinedButton`, «Estado de indexación (provisional)», bajo el de la Ingesta. Se retira con F09.
- **P12 — Carga:** mientras se calcula el primer estado (normalmente menos de un segundo), la top bar y, en el centro, un `LisdataProgressRing` indeterminado pequeño, sin texto visible, anunciado como «Cargando».
- **P11 — Notificación** (R3):
  - título «Indexando documentos»;
  - texto «%1$s de %2$s fragmentos» de toda la tanda, las mismas cifras que la pantalla (D-042), o «Preparando documentos»;
  - en pausa automática, la causa;
  - acción «Pausar». Al tocarla, abre esta pantalla.

## Cambios en la Ingesta (F06)

| Estado del documento | Fila (metadatos) | Barra | Hoja de detalle |
|---|---|---|---|
| `embedding` en curso | «Indexando · %1$s de %2$s fragmentos» | Determinada | Igual, sin acciones salvo «Quitar de la cola» |
| De una tanda en pausa | «En pausa · %1$s de %2$s fragmentos», o «En pausa» si no hay total | No | «Quitar de la cola» |
| `error` `embedding_failed` | Chip «No se pudo indexar» | — | «Falló el cálculo de los vectores. Puedes reintentarlo.» y «Reintentar» |
| `error` `incomplete` | Chip «Indexación incompleta» | — | «Faltan fragmentos por guardar. Puedes reintentarlo.» y «Reintentar» |
| `done` | Sale de la cola | — | — |

**Otros cambios:**
- **«Preparado para indexar · N fragmentos»** (`parsed`) se mantiene. Ahora es transitorio: la fase 2 sigue sin intervención.
- **La CTA «Iniciar indexación»** también se habilita con documentos `parsed` o `embedding` sin trabajo activo, y con una tanda en pausa: en ese caso, reanuda.

## Accesibilidad (FR-038)

- **U1:** el anillo se anuncia como «%1$d %% completado, %2$s de %3$s fragmentos»; en P2, «Procesando».
- **U2:** cada etapa fusiona su semántica: «Generación de embeddings, en curso».
- **U3:** los cambios importantes se anuncian sin mover el foco (`liveRegion = Polite` en un resumen invisible):
  - inicio y fin de un documento;
  - pausa y su causa;
  - error;
  - «Todo actualizado».

  El progreso de cada lote no se anuncia, para no saturar TalkBack.
- **U4:** objetivos táctiles de 48 dp o más.
- **U5:** contraste AA con los roles de D-019, en claro y en oscuro. El hero oscuro usa los roles `onDark*` de F01.
- **U6:** con la fuente al 200 %:
  - el anillo mantiene su tamaño y el porcentaje cabe;
  - los textos largos se parten;
  - nada se solapa con la acción fija.

## Navegación

- **Entrada:**
  - «Iniciar indexación» de la Ingesta (ya navega a `Index`);
  - la notificación (`ACTION_OPEN_INDEXING`): `MainActivity` en `singleTop`, atendida también en `onNewIntent` y solo con el onboarding completado (research R3);
  - P10.
- **Salida:**
  - Atrás → la pantalla anterior; si se abrió desde la notificación, Inicio;
  - P7 → `Setup`.
- **Sin barra inferior**, como dice el `.md`.
