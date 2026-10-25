# Data model — F01 Fundaciones y design system

F01 no persiste datos de dominio. Su «modelo de datos» son los **tokens de diseño**, los **destinos de navegación**, los **modelos de UI** de los componentes y los **servicios base**. Todo vive en memoria y no se guarda nada.

Los valores salen de `docs/roadmap/INVENTARIO.md` §2 y de las decisiones D-014 a D-019.

## 1. Tokens de color (`LisdataColors`)

Cada token tiene un valor claro y otro oscuro. La columna «Origen» indica de dónde viene el valor:
- **PNG**: medido en los mockups.
- **Doc**: `00-design-system.md`.
- **D-0xx**: decisión registrada.
- **Derivado**: propuesta aprobada en D-016 o D-019.

| Token | Claro | Oscuro | Origen | Rol |
|---|---|---|---|---|
| `brandCyan` | `#02B0E2` | `#02B0E2` | Doc/PNG | Acento gráfico sin texto (anillo de progreso, puntos) |
| `brandBlue` | `#0070DD` | `#02B0E2` | Doc/PNG; oscuro D-016 | Acción primaria, iconos activos, switch encendido, enlaces |
| `onBrand` | `#FFFFFF` | `#052A39` | Derivado | Texto e icono sobre `brandBlue` sólido |
| `navy900` | `#052A39` | `#052A39` | Doc | Botón oscuro («Pausar»), base de los degradados |
| `navy700` | `#0D3F51` | `#0D3F51` | Doc | Reserva |
| `blue800` | `#004994` | `#A8ECFD` | Doc/PNG; oscuro derivado | Texto acentuado en chips informativos, pestaña seleccionada, resaltado de cita |
| `ice200` | `#A8ECFD` | `#A8ECFD` | Doc/PNG | Acento sobre fondos oscuros |
| `ice100` | `#C9F6FF` | `#C9F6FF` | Doc | Reserva |
| `background` | `#F7FBFD` | `#03161F` | PNG; D-016 | Fondo de pantalla |
| `surface` | `#FFFFFF` | `#072634` | PNG; D-016 | Tarjetas, barra inferior, campos |
| `outline` | `#DDEBF0` | `#1E4A5C` | PNG; D-016 | Borde de 1 dp y pista de la barra de pasos |
| `divider` | `#EEF5F7` | `#123848` | PNG; derivado | Separadores internos |
| `surfaceInfo` | `#EAF8FD` | `#0D3F51` | PNG; D-016 | Chips informativos, filtros no seleccionados, chip «Página N» |
| `surfaceInfoAlt` | `#E7F5FC` | `#0B3546` | PNG; derivado | Tile de icono, burbuja de usuario, tarjeta info |
| `citationHighlight` | `#DDF4FF` | `#0E4A66` | PNG; derivado | Fondo de la frase citada |
| `neutralTile` | `#EFF4F6` | `#123848` | PNG; derivado | Etapa pendiente |
| `disabledContainer` | `#B6CBD4` | `#2A4B59` | PNG; derivado | Botón deshabilitado (exento de contraste en WCAG) |
| `onDisabled` | `#FFFFFF` | `#9FBCC8` | PNG; derivado | Texto de botón deshabilitado |
| `textPrimary` | `#052A39` | `#E6F4F9` | PNG; D-016 | Títulos y cuerpo |
| `textSecondary` | `#54798A` | `#9FBCC8` | D-015; D-016 | Texto secundario sobre `background` o `surface` |
| `textSecondaryOnContainer` | `#4D7183` | `#9FBCC8` | **D-019** | Texto secundario sobre contenedores tintados (`surfaceInfo*`, `neutralTile`, contenedores semánticos) |
| `success` | `#087A65` | `#4FD1B4` | D-015; D-016 | Texto de éxito |
| `successGraphic` | `#129D83` | `#4FD1B4` | PNG | Punto o icono de éxito (no texto, umbral 3:1) |
| `successContainer` | `#E4F8F3` | `#0E3B36` | PNG; D-016 | Fondo de chip de éxito |
| `warning` | `#8A5A12` | `#F2B759` | D-015; D-016 | Texto de aviso |
| `warningContainer` | `#FFF4DF` | `#3D2E12` | PNG; D-016 | Fondo de chip de aviso |
| `error` | `#C23F4D` | `#FF8A95` | D-015; D-016 | Texto e icono de error y acciones destructivas |
| `errorContainer` | `#FEF2F3` | `#4A1C22` | **D-019**; D-016 | Fondo de chip o tarjeta de error |
| `onDarkText` | `#FFFFFF` | `#FFFFFF` | PNG | Texto sobre hero y bienvenida |
| `onDarkAccent` | `#A8ECFD` | `#A8ECFD` | PNG | Eyebrow y acento sobre fondo oscuro |
| `onDarkTile` | `#294B5D` | `#294B5D` | PNG | Tile sobre hero |
| `onDarkChip` | `#164A5B` | `#164A5B` | PNG | Chip sobre fondo oscuro |
| `onDarkTrack` | `#355E7B` | `#355E7B` | PNG | Pista del anillo |

**Regla de validación.** Todo par texto/fondo de la tabla §1.1 MUST cumplir FR-008:
- ≥ 4,5:1 en texto normal;
- ≥ 3:1 en texto grande (≥ 18 sp, o ≥ 14 sp en negrita);
- ≥ 3:1 en gráficos sin texto.

El test `ContrastTest` es la fuente ejecutable de esta regla.

### 1.1 Pares texto/fondo verificados

Valores precalculados; el test los recalcula.

| Par (claro) | Ratio | Par (oscuro) | Ratio |
|---|---|---|---|
| `textPrimary` / `background` | 14,4 | `textPrimary` / `background` | 16,4 |
| `textSecondary` / `background` | 4,50 | `textSecondary` / `background` | 9,2 |
| `textSecondary` / `surface` | 4,69 | `textSecondary` / `surface` | 7,9 |
| `textSecondaryOnContainer` / `surfaceInfoAlt` | 4,71 | `textSecondaryOnContainer` / `surfaceInfo` | 5,7 |
| `textSecondaryOnContainer` / `neutralTile` | 4,73 | — | — |
| `blue800` / `surfaceInfo` | 8,1 | `blue800`(oscuro) / `surfaceInfo` | 8,7 |
| `blue800` / `citationHighlight` | 7,8 | `textPrimary` / `citationHighlight` | 8,5 |
| `brandBlue` / `surface` | 4,81 | `brandBlue`(oscuro) / `surface` | 6,2 |
| `onBrand` / `brandBlue` | 4,81 | `onBrand`(oscuro) / `brandBlue`(oscuro) | 5,95 |
| `success` / `successContainer` | 4,78 | `success` / `successContainer` | 6,6 |
| `warning` / `warningContainer` | 5,42 | `warning` / `warningContainer` | 7,3 |
| `error` / `errorContainer` | 4,67 | `error` / `errorContainer` | 6,3 |
| `error` / `surface` | 5,11 | `error` / `surface` | 7,0 |
| `onDarkText` / `navy900` | 15,0 | (igual) | — |
| `onDarkAccent` / hero (extremo claro `#0155A4`) | 5,7 | (igual) | — |
| `onDarkAccent` / `onDarkChip` | 7,4 | (igual) | — |
| `onDarkText` / `gradientCta` (todo el tramo) | ≥ 3,23 | (igual) | — |

## 2. Degradados (`LisdataGradients`)

| Token | Paradas | Dirección | Uso | Origen |
|---|---|---|---|---|
| `cta` | `#0099D4 → #0072DD` | Horizontal | Botones primarios con texto y botón enviar | PNG `#02AEE2 → #0072DD`, con la parada cian oscurecida por **D-019** para garantizar texto blanco ≥ 3:1 en todo el tramo |
| `brandAccent` | `#02AEE2 → #0072DD` | Diagonal | Tiles o avatares sin texto (`ic_sparkle`) | PNG (sin cambios) |
| `hero` | `#042B3C → #0155A4` | Diagonal ↘ | Hero de Inicio, Detalle, Indexación e Idiomas | PNG |
| `welcome` | `#052E45 → #023C6E → #01468A` | Vertical | Fondo de Bienvenida | PNG |
| `stepProgress` | `#02ACE1 → #0072DD` | Horizontal | Relleno de `LisdataStepIndicator` | PNG |

## 3. Tipografía (`LisdataTypography`)

Familia: `GoogleSans`, la fuente variable `res/font/google_sans_variable.ttf`, con pesos 400, 500, 600 y 700 (research R4).

| Estilo | Talla / interlineado | Peso | Extra | Uso |
|---|---|---|---|---|
| `displayMedium` | 34 / 40 sp | 700 | — | H1 de Bienvenida |
| `headlineLarge` | 30 / 36 sp | 700 | — | H1 de pantalla |
| `headlineMedium` | 26 / 32 sp | 700 | — | Título de hero y cifras estadísticas |
| `titleLarge` | 22 / 28 sp | 700 | — | Título de la barra superior |
| `titleMedium` | 18 / 24 sp | 600 | — | Título de tarjeta o fila |
| `bodyLarge` | 17 / 26 sp | 400 | — | Cuerpo de respuesta y burbuja (600) |
| `bodyMedium` | 15 / 22 sp | 400 | — | Descripciones y subtítulos |
| `labelLarge` | 17 / 22 sp | 700 | — | Botones (texto grande según ATF) |
| `labelMedium` | 13 / 18 sp | 600 | — | Chips y filtros |
| `labelSmall` | 12 / 16 sp | 700 | MAYÚSCULAS, tracking 0,6 sp | Eyebrow y cabecera de sección |
| `navLabel` | 13 / 16 sp | 500 (600 si está seleccionada) | — | Barra inferior |
| `progressValue` | 40 / 44 sp | 700 | — | Porcentaje del anillo |

**Regla:** ningún estilo fija una altura de contenedor; todos escalan con el `fontScale` del sistema.

## 4. Espaciado, formas y medidas (`LisdataSpacing`, `LisdataShapes`)

| Token | Valor |
|---|---|
| `space1` … `space8` | 4, 8, 12, 16, 20, 24, 32, 40 dp |
| `screenPaddingHorizontal` | 20 dp |
| `gridGap`, `listGap` | 12 dp |
| `cardPadding` | 16 dp |
| `minTouchTarget` | 48 dp |
| `buttonHeight`, `buttonHeightSecondary` | 52 dp, 48 dp |
| `searchFieldHeight` | 48 dp |
| `iconSize`, `iconSizeSmall` | 24 dp, 20 dp |
| `iconTileSize`, `iconTileSizeSmall` | 44 dp, 40 dp |
| `avatarSize` | 32 dp |
| `borderWidth` | 1 dp |
| `shapeCard`, `shapeHero`, `shapeBubble` | 16 dp |
| `shapeButton`, `shapeIconTile` | 12 dp |
| `shapeSearch` | 24 dp |
| `shapeChip` | píldora (50 %) |

En el `Shapes` de M3 se mapean `small = 12 dp`, `medium = 16 dp`, `large = 16 dp` y `extraLarge = 24 dp`.

## 5. Iconos (`LisdataIcons`)

Hay 29 vector drawables `ic_*`, generados desde Lucide 1.61.0 (research R5). El mapeo completo está en `INVENTARIO.md` §2.6 y en `tools/icons/icons.txt`.

Formato de cada icono:
- viewport de 24×24;
- trazo de 2;
- extremos y uniones redondeados;
- tintado por token.

## 6. Destinos de navegación

| Destino (tipo Kotlin) | Ruta lógica | Argumentos | Barra inferior | Pestaña | Contenido en F01 | Feature que lo sustituye |
|---|---|---|---|---|---|---|
| `Welcome` | `welcome` | — | No | — | Marcador | F03 |
| `Setup` | `setup` | — | No | — | Marcador | F03 |
| `Home` | `home` | — | Sí | Inicio | Marcador (**destino inicial**) | F09 |
| `Chat` | `chat/{conversationId?}` | `conversationId: Long?` | Sí | Chat | Marcador | F11 |
| `Ingest` | `ingest` | — | No | — | Marcador | F06 |
| `Library` | `library` | — | Sí | Biblioteca | Marcador | F08 |
| `Document` | `document/{documentId}` | `documentId: Long`, `chunkId: Long?` | No | — | Marcador | F08 / F11 |
| `Index` | `index` | — | No | — | Marcador | F07 |
| `Settings` | `settings` | — | Sí | Ajustes | Marcador | F12 |
| `Languages` | `languages` | — | No | — | Marcador | F12 |

**`TopLevelDestination`**: `Home`, `Chat`, `Library` y `Settings`. Cada uno tiene icono (`ic_home`, `ic_chat`, `ic_folder`, `ic_settings`), etiqueta (`R.string.nav_*`) y destino raíz.

**Reglas de navegación** (ver [contracts/navigation.md](contracts/navigation.md)):
- **Barra inferior visible** = el destino actual es raíz de una pestaña **y** el teclado no está visible.
- **Navegar a una pestaña** = `popUpTo<Home>(saveState)` + `launchSingleTop` + `restoreState`.
- **Reseleccionar la pestaña activa** = volver a su raíz.
- **Atrás**:
  - en una raíz de pestaña distinta de Inicio, vuelve a `Home`;
  - en `Home`, termina la Activity.

## 7. Modelos de UI de los componentes

Son tipos de presentación de `core.designsystem`, no de dominio. Los mapeos desde el dominio llegarán con cada feature.

| Tipo | Valores / campos | Uso |
|---|---|---|
| `StatusKind` | `Local`, `Ready`, `Processing`, `Queued`, `Paused`, `Warning`, `Error`, `Info` | `LisdataStatusChip`, `LisdataModelStatusRow` y `LisdataDocumentRow`. Cada valor define el contenedor, el texto y un icono o punto; nunca solo el color (FR-013) |
| `ProgressValue` | `Determinate(fraction: Float ∈ [0,1])` \| `Indeterminate` | `LisdataProgressRing`, `LisdataStepIndicator`, `LisdataProgressCard` y `LisdataQueueRow`. Nunca se inventa un porcentaje (constitución V) |
| `StageStatus` | `Done`, `InProgress`, `Pending`, `Failed` | `LisdataStageRow` |
| `IconTileStyle` | `Light`, `OnDark`, `Success`, `Neutral`, `Accent` (degradado) | `LisdataIconTile` |
| `CitationUi` | `documentName: String`, `page: Int?`, `fragment: Int?`, `available: Boolean` | `LisdataSourceCitation` (si `available = false`, muestra «Fuente ya no disponible» y no navega) |
| `ChatRole` / `AnswerState` | `User`, `Assistant` / `Streaming`, `Completed`, `Cancelled`, `Failed` | `LisdataChatBubble` y `LisdataAnswerCard` |

## 8. Servicios base

| Tipo | Contenido | Ciclo de vida |
|---|---|---|
| `AppDispatchers` | `main`, `io`, `default`, `database`, `llm`, `embeddings`. Los tres últimos son de un solo hilo y con nombre | `single` en Koin; vive lo que el proceso |
| `AppLogger` | `d`/`i`/`w`/`e` con mensaje en lambda; solo emite si `BuildConfig.DEBUG` | `single` |
| `DebugRouting` | `initialRoute(intent): Any?`. Interpreta el extra en `src/debug` y devuelve `null` en `src/release` | Objeto por variante |
| `CatalogEntry` (solo debug) | `id`, `title`, `section`, `content: @Composable () -> Unit` | Lista estática del catálogo |
