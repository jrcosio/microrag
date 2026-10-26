# Contrato — UI de Importar documentos (`ingest`)

- **Textos:** `res/values/strings_ingest.xml` (es, por defecto) y `res/values-en/strings_ingest.xml` (en), según D-029. Los textos visibles en el PNG 05 se copian literalmente. El resto son propuestas (§Propuestas) que se aprueban en la parada tras analyze, antes de implementarlas (constitución VII).
- **Componentes:** solo `Lisdata*` y tokens. Nada de hex en la pantalla.

## Estructura (PNG 05, de arriba abajo)

| # | Elemento | Componente | es | en |
|---|---|---|---|---|
| 1 | Top bar con Atrás | `LisdataTopBar(onBack)` | «Importar documentos» | «Import documents» |
| 2 | Eyebrow | `LisdataEyebrow` | «INGESTA LOCAL» | «LOCAL INGESTION» |
| 3 | Título (H1 de pantalla) | `material.headlineLarge` (≈ 30 sp, INVENTARIO) | «Amplía tu biblioteca» | «Grow your library» |
| 4 | Subtítulo | `material.bodyLarge`, `textSecondary` | «Selecciona archivos de tu dispositivo.» | «Select files from your device.» |
| 5 | Tarjeta de selección | `LisdataFilePickerCard` | Título «Selecciona archivos» (G14, en lugar de «Arrastra o selecciona archivos»). Subtítulo «Todo se procesa en tu móvil». Botón «Seleccionar archivos» | «Select files» / «Everything is processed on your phone» / «Select files» |
| 6 | Formatos | `material.bodySmall`, `textSecondary`, centrado | «Formatos: PDF · TXT · Markdown» | «Formats: PDF · TXT · Markdown» |
| 7 | Cabecera de la cola | `LisdataSectionHeader` y `LisdataCountChip` | «COLA DE IMPORTACIÓN» y «%d archivo» / «%d archivos» (plurales) | «IMPORT QUEUE» / «%d file(s)» |
| 8 | Filas | `LisdataQueueRow` con `onClick` (chevron) | §Filas | §Filas |
| 9 | CTA fija abajo | `LisdataGradientButton` con `ic_chevron_right`, 52 dp | «Iniciar indexación» | «Start indexing» |

- **Desplazamiento:** el contenido (2–8) se desplaza; la CTA queda fija sobre el borde inferior, con el margen de 20 dp del PNG.
- **Estado de la CTA:**
  - **habilitada** solo con `readyCount > 0` e índice disponible;
  - **deshabilitada** en otro caso, con `disabledContainer`, como en F03.

  Al pulsarla:
  1. `StartIndexingUseCase`;
  2. `navigate(Index)`.

  Mientras se encola, la CTA se deshabilita para no encolar dos veces.

## Filas

La fila sigue el PNG:
- tile `ic_file_text`;
- el nombre, en 2 líneas como máximo, con elipsis;
- debajo, «`tamaño` · `estado`»;
- el chevron.

El tamaño se formatea con `Formatter.formatShortFileSize`, en base 10 y con la configuración regional (por ejemplo «2,4 MB» y «48 KB»).

| Estado | Línea de metadatos (es) | en | Extra |
|---|---|---|---|
| `Importing` con tamaño | «%1$s · Importando» | «%1$s · Importing» | `LisdataLinearProgress` determinada (bytes copiados / total) |
| `Importing` sin tamaño | «Importando» | «Importing» | Progreso indeterminado |
| `Ready` | «%1$s · Listo para procesar» (PNG) | «%1$s · Ready to process» | — |
| `Queued` | «%1$s · En cola» | «%1$s · Queued» | — |
| `Extracting` (PDF) | «%1$s · Extrayendo · página %2$d de %3$d» | «%1$s · Extracting · page %2$d of %3$d» | Progreso determinado (páginas) |
| `Extracting` (TXT/MD) | «%1$s · Extrayendo» | «%1$s · Extracting» | Progreso indeterminado |
| `Chunking` | «%1$s · Fragmentando» | «%1$s · Chunking» | Progreso indeterminado |
| `Prepared` | «%1$s · Preparado para indexar · %2$d fragmentos» y, si `skippedPages > 0`, « · %3$d páginas sin texto» (plurales) | «%1$s · Ready to index · %2$d chunks» / « · %3$d pages without text» | — (texto plano, como el PNG) |
| `ImportFailed` / `Failed` | «%1$s» (o vacío si no hay tamaño) | igual | `LisdataStatusChip(StatusKind.Error, motivo corto)` |

### Motivos (chip corto y frase de la hoja)

| `IngestError` | Chip es | Hoja es | Chip en | Hoja en |
|---|---|---|---|---|
| `UNSUPPORTED_FORMAT` | Formato no admitido | Solo se admiten PDF con texto, TXT y Markdown. | Unsupported format | Only PDF with text, TXT and Markdown are supported. |
| `DAMAGED` | Archivo dañado | El archivo está dañado o no es un %1$s válido. | Damaged file | The file is damaged or is not a valid %1$s. |
| `ENCRYPTED` | Archivo cifrado | El PDF está protegido con contraseña. Quita la protección e impórtalo de nuevo. | Encrypted file | The PDF is password protected. Remove the protection and import it again. |
| `TOO_LARGE` | Demasiado grande | El máximo es 100 MB por archivo y 2.000 páginas por PDF. | Too large | The limit is 100 MB per file and 2,000 pages per PDF. |
| `NO_SPACE` | Sin espacio | No hay espacio suficiente en el teléfono para copiarlo. | No space | There is not enough space on the phone to copy it. |
| `ACCESS_LOST` | Acceso perdido | La app ya no puede leer el archivo original. Vuelve a seleccionarlo. | Access lost | The app can no longer read the original file. Select it again. |
| `READ_FAILED` | Error de lectura | No se pudo leer el archivo. | Read error | The file could not be read. |
| `DUPLICATE` | Ya está en tu biblioteca | Ya hay un documento con el mismo contenido. | Already in your library | A document with the same content already exists. |
| `PDF_UNAVAILABLE` | PDF no disponible | Este dispositivo no puede leer PDF. Actualiza el sistema e inténtalo de nuevo. | PDF unavailable | This device cannot read PDFs. Update the system and try again. |
| `NO_TEXT` | Sin texto | Sin texto extraíble (¿documento escaneado?). | No text | No extractable text (scanned document?). |
| `EXTRACTION_FAILED` | Error al extraer | No se pudo extraer el texto. | Extraction error | The text could not be extracted. |
| `INDEX_UNAVAILABLE` | Biblioteca no disponible | La biblioteca local no está disponible ahora. | Library unavailable | The local library is not available right now. |

## Hoja de detalle de la fila (G15)

Se abre con el chevron o con un toque en la fila. Es un `ModalBottomSheet` de M3, tematizado (D-018), con:
1. el nombre (título) y «`tamaño` · `formato`»;
2. el estado:
   - con su frase de la tabla anterior;
   - con el número de fragmentos, si está preparado, y las páginas sin texto si las hay;
   - con su progreso, si está en curso;
3. las acciones, como `LisdataGradientButton` y `LisdataOutlinedButton` de 48 dp o más:
   - **Reintentar**, solo si `canRetry`;
   - **Quitar de la cola**, siempre. En `Importing`, el texto es «Cancelar importación».

Quitar un **documento** borra su copia privada y su registro. Por la constitución V («eliminar exige confirmación») pide confirmación con `LisdataConfirmationDialog(destructive = true)`:
- título «¿Quitar de la cola?»;
- texto «Se borrará la copia de «%1$s» que guarda la app. El archivo original de tu teléfono no cambia.»;
- botones «Quitar» y «Cancelar».

Quitar un **intento** fallido o cancelar una importación en curso no pide confirmación, porque no hay nada guardado.

| Acción | es | en |
|---|---|---|
| Reintentar | «Reintentar» | «Retry» |
| Quitar | «Quitar de la cola» | «Remove from queue» |
| Cancelar | «Cancelar importación» | «Cancel import» |
| Confirmación: título | «¿Quitar de la cola?» | «Remove from queue?» |
| Confirmación: texto | «Se borrará la copia de «%1$s» que guarda la app. El archivo original de tu teléfono no cambia.» | «The app's copy of “%1$s” will be deleted. The original file on your phone is not changed.» |
| Confirmación: botones | «Quitar» / «Cancelar» | «Remove» / «Cancel» |

## Propuestas para estados sin mockup (aprobar en la parada tras analyze)

- **P1 — Cola vacía:** bajo la cabecera de la cola, sin el chip de recuento, va `LisdataEmptyState` compacto con `ic_folder`. Título: «Aún no hay archivos en la cola». Texto: «Los archivos que selecciones aparecerán aquí hasta que se indexen.». La CTA queda deshabilitada.
- **P2 — Filas en curso y con error:** como en §Filas.
  - En curso, la misma fila del PNG con una barra fina de progreso debajo de la línea de metadatos.
  - Con error, la línea de metadatos solo lleva el tamaño, y debajo va `LisdataStatusChip(StatusKind.Error)` con los tokens `error` y `errorContainer`.
- **P3 — Hoja de detalle (G15):** como en §Hoja.
- **P4 — Dispositivo sin soporte de PDF:** la línea de formatos pasa a «Formatos: TXT · Markdown · PDF requiere actualizar el sistema». El selector sigue ofreciendo PDF, y cada PDF elegido termina en `PDF_UNAVAILABLE` con su explicación (spec FR-016 y caso borde).
- **P5 — Biblioteca no disponible** (índice `Incompatible` o `Unreadable`, F04):
  - en lugar de la cola, `LisdataErrorState` con el título «La biblioteca no está disponible» y el texto «No se puede importar mientras la biblioteca local no esté disponible.»;
  - «Seleccionar archivos» y la CTA, deshabilitados.

  La reindexación explícita es de F07/F12.
- **P6 — Acceso provisional desde Inicio** (FR-025): la pantalla provisional de Inicio (`PendingDestination("home")`) gana un `LisdataOutlinedButton` con el texto «Importar documentos (provisional)», que navega a `Ingest`. Se retira cuando F09 entregue Inicio.

## Accesibilidad (FR-024)

- **U1:** cada fila fusiona su semántica y se anuncia como «`nombre`, `tamaño`, `estado`». En curso añade el progreso: «página 12 de 200» o «35 %». Su acción se anuncia como «Ver detalle».
- **U2:** los objetivos táctiles miden 48 dp o más: filas, botones, chevron y acciones de la hoja.
- **U3:** contraste AA con los roles de texto de D-019. El chip de error usa los roles de `LisdataStatusChip(Error)`, ya validados en `ContrastTest`.
- **U4:** con la fuente al 200 % no hay recortes: el nombre usa 2 líneas con elipsis, los metadatos pueden partirse y la CTA crece en alto.
- **U5:** los cambios de estado de la cola se anuncian sin robar el foco: «%1$s listo para procesar» y «%1$s: %2$s» (error), con `liveRegion = Polite` en un resumen invisible.
- **U6:** claro y oscuro con los tokens de F01 (modo oscuro derivado, propuesta de F01).

## Navegación

- **Entrada:** P6 por ahora. En F08 y F09, «+» de Biblioteca, el tile Importar de Inicio y las CTA de vacío.
- **Salida:**
  - Atrás → la pantalla anterior;
  - «Iniciar indexación» → `Index` (placeholder de F07). Atrás desde `Index` vuelve a Importar documentos.
- **Selector:** se lanza con `rememberLauncherForActivityResult(OpenMultipleDocuments())`. Cancelarlo no cambia nada (US1-4).
