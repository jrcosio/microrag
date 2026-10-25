# Contrato del design system — F01

- **Paquete raíz:** `com.lisdatasolutions.microrag.core.designsystem`.
- **Componentes:** todos son de presentación. Reciben datos ya formateados (cadenas desde recursos) y callbacks, y no acceden a repositorios ni a ViewModels.
- **Firmas:** son orientativas. Los parámetros obligatorios están fijados; los opcionales pueden ampliarse sin romper a quien los llama.
- **Fuera del paquete** no se usan `Color(0x…)`, tamaños `.sp` ni textos literales. Lo verifica `DesignSystemConformanceTest`.

## Tema y acceso a tokens

```text
@Composable fun LisdataTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)

object LisdataTheme {
  val colors: LisdataColors          // §1 data-model (incluye roles no-M3)
  val gradients: LisdataGradients    // §2
  val typography: LisdataTypography  // §3 (M3 Typography + navLabel, progressValue)
  val spacing: LisdataSpacing        // §4
  val shapes: LisdataShapes          // §4
}
```

`LisdataTheme` también configura el `MaterialTheme` de M3, sin color dinámico (R14). Los controles estándar heredan los tokens (D-018).

## Iconos y marca

```text
object LisdataIcons { val Home: Int /* R.drawable.ic_home */ … }    // 29 entradas, ver INVENTARIO §2.6
@Composable fun LisdataIcon(icon: Int, contentDescription: String?, tint: Color = LocalContentColor.current, size: Dp = 24.dp)
@Composable fun LisdataLogo(modifier: Modifier = Modifier, height: Dp = 32.dp)  // asset oficial, cd = R.string.cd_logo
```

`contentDescription = null` solo se usa en iconos decorativos (FR-015).

## Componentes

Cada componente lleva previews (`*Previews.kt`) con las anotaciones `@LisdataPreviews`: claro, oscuro, `fontScale = 2f` y texto largo. Los componentes con estado de error tienen además una preview de error.

| Componente | Parámetros clave | Notas de contrato |
|---|---|---|
| `LisdataTopBar` | `title`, `onBack: (() -> Unit)?`, `actions`, `subtitle: (@Composable)?` | Respeta los insets de la barra de estado. Atrás con `cd` «Volver» |
| `LisdataBottomBar` | `selected: TopLevelDestination`, `onSelect` | M3 `NavigationBar` tematizada. Icono y etiqueta siempre visibles |
| `LisdataGradientButton` | `text`, `onClick`, `enabled`, `loading`, `trailingIcon?` | 52 dp. Si está deshabilitado usa `disabledContainer`. `loading` muestra un indicador y bloquea los clics |
| `LisdataDarkButton` | `text`, `onClick`, `trailingIcon?` | `navy900` |
| `LisdataOutlinedButton` | `text`, `onClick`, `destructive: Boolean`, `leadingIcon?` | Borde de 1 dp. Si es destructivo usa `error` |
| `LisdataCard` | `onClick?`, `content` | `surface`, borde `outline` de 1 dp, radio 16 dp |
| `LisdataHeroCard` | `eyebrow?`, `title`, `content`, `decoration: Boolean` | `gradients.hero`. La decoración (órbitas) es solo visual y no se anuncia |
| `LisdataInfoCard` | `icon`, `title`, `body?` | `surfaceInfoAlt`, texto `textSecondaryOnContainer` |
| `LisdataIconTile` | `icon`, `style: IconTileStyle`, `size` | — |
| `LisdataStatusChip` | `text`, `kind: StatusKind` | Punto o icono más texto. Área táctil de 48 dp si es clicable |
| `LisdataFilterChip` | `text`, `selected`, `onClick` | M3 `FilterChip` tematizado (D-018) |
| `LisdataCountChip` | `text` | «3 archivos», «2 fuentes», «Página 12» |
| `LisdataEyebrow` / `LisdataSectionHeader` | `text`, `trailing?` | `labelSmall` en mayúsculas |
| `LisdataStepIndicator` | `step`, `total` | «PASO N DE M» y barra `stepProgress`. Anuncia «Paso N de M» |
| `LisdataModelStatusRow` | `icon`, `name`, `purpose`, `status: StatusKind` + `statusText`, `progress: ProgressValue?`, `onClick?` | El progreso se anuncia en palabras |
| `LisdataQuickActionTile` | `icon`, `title`, `subtitle`, `onClick` | Toda la tarjeta es el botón |
| `LisdataStatCard` | `label`, `value`, `emphasis: Boolean`, `secondaryValue?` | — |
| `LisdataDocumentRow` | `name`, `meta`, `status?`, `included`, `includeEnabled`, `onToggle`, `onClick` | Semántica agrupada. El switch tiene etiqueta y se deshabilita si `includeEnabled = false` |
| `LisdataQueueRow` | `name`, `meta`, `status`, `progress: ProgressValue?`, `onClick` | — |
| `LisdataSourceCitation` | `citation: CitationUi`, `onOpen` | Si `available = false`, muestra «Fuente ya no disponible» y no es clicable |
| `LisdataChatBubble` | `text` | Usuario, alineada a la derecha |
| `LisdataAnswerCard` | `assistantName`, `body`, `state: AnswerState`, `sourcesCount?`, `onCopy`, `onSources?`, `onRetry?` | Estados de streaming, completada, cancelada y error. `assistantName` sale del recurso «MicroRAG» (D-014) |
| `LisdataChatComposer` | `value`, `onValueChange`, `onSend`, `generating`, `onStop`, `placeholder` | Mientras se genera, enviar pasa a detener (G12). Se ancla al IME |
| `LisdataProgressRing` | `progress: ProgressValue`, `label` | Indeterminado si no hay total |
| `LisdataProgressCard` | `stage`, `done`, `total: Int?`, `progress: ProgressValue`, `action?` | Nunca inventa un porcentaje |
| `LisdataStageRow` | `title`, `status: StageStatus`, `statusText` | Icono más texto |
| `LisdataSettingsRow` | `icon`, `title`, `subtitle?`, `trailing` (chip o chevron), `destructive`, `onClick?` | — |
| `LisdataSwitchRow` | `title`, `subtitle?`, `checked`, `enabled`, `onCheckedChange` | La fila completa es el control (`toggleable`) |
| `LisdataRadioRow` | `title`, `subtitle?`, `selected`, `onSelect` | `selectable` con `Role.RadioButton` |
| `LisdataSearchField` | `query`, `onQueryChange`, `placeholder`, `onClear` | 48 dp, radio 24 dp |
| `LisdataFilePickerCard` | `title`, `subtitle`, `buttonText`, `onPick` | Borde discontinuo. No promete arrastrar y soltar (G14) |
| `LisdataEmptyState` | `icon`, `title`, `body`, `actionText?`, `onAction?` | — |
| `LisdataErrorState` | `message`, `onRetry` | Texto `R.string.action_retry` |
| `LisdataSkeletonRow` | `lines: Int` | Se anuncia como «Cargando» |
| `LisdataConfirmationDialog` | `title`, `body`, `confirmText`, `onConfirm`, `onDismiss`, `destructive` | M3 `AlertDialog`. El foco inicial va a Cancelar |
| `LisdataModelMissingBlock` | `title`, `body`, `actionText`, `onAction` | Basado en `LisdataEmptyState` |

## Recursos de cadena compartidos (es / en)

| Clave | es | en |
|---|---|---|
| `app_name` | MicroRAG | MicroRAG |
| `assistant_name` | MicroRAG | MicroRAG |
| `nav_home` / `nav_chat` / `nav_library` / `nav_settings` | Inicio / Chat / Biblioteca / Ajustes | Home / Chat / Library / Settings |
| `action_back` | Volver | Back |
| `action_retry` | Reintentar | Retry |
| `action_cancel` | Cancelar | Cancel |
| `action_copy_answer` | Copiar respuesta | Copy answer |
| `action_send` / `action_stop` | Enviar / Detener | Send / Stop |
| `cd_logo` | Logotipo de LISDATA | LISDATA logo |
| `state_loading` | Cargando | Loading |
| `source_unavailable` | Fuente ya no disponible | Source no longer available |
| `pending_destination_title` | Pendiente · %1$s | Pending · %1$s |
| `pending_destination_body` | Destino «%1$s». Argumentos: %2$s | Destination “%1$s”. Arguments: %2$s |
| `progress_percent` | %1$d %% completado | %1$d%% complete |
| `step_of` | Paso %1$d de %2$d | Step %1$d of %2$d |

Las demás cadenas de componentes se añaden siguiendo el mismo patrón. Los textos de las pantallas de producto llegan con cada feature.
