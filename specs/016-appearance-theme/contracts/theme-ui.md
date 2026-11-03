# Contrato: fila «Tema», pantalla Tema y engranaje (F16)

Manda el wireframe aprobado ([wireframe.md](../wireframe.md), propuestas A1–A9, D-067), con la corrección de A3 de D-068: «Preferencias guardadas» solo lo anuncia TalkBack, como en Idiomas. Rutas: `Settings` (pestaña Ajustes, F12) y `Theme` (sin barra inferior).

## 1. Engranaje (A6)

| Dónde | Hoy | F16 |
|---|---|---|
| `TopLevelDestination.SETTINGS.icon` (barra inferior) | `LisdataIcons.Settings` = `ic_settings` (`sun`) | Mismo nombre, `ic_settings` regenerado desde `settings` |
| Cabecera de Inicio (`HomeScreen`, `home_settings`) | ídem | ídem |
| Catálogo (`CatalogEntries`, rejilla `LisdataIcons.all`) y `BarsPreviews` | ídem | ídem, más `SunMoon` en la rejilla |

- No cambia ningún uso: solo el recurso. Las etiquetas y descripciones siguen igual (FR-002).

## 2. Fila «Tema» en Ajustes (A1)

| Zona | Componente | Contenido |
|---|---|---|
| Preferencias, entre «Idiomas» e «Índice y rendimiento» | `LisdataSettingsRow(icon = LisdataIcons.SunMoon, title = settings_theme, subtitle = …, trailing = Chevron, onClick = onTheme)` con un `LisdataDivider` a cada lado | Título «Tema»; subtítulo `theme_system`, `theme_light` o `theme_dark` según `SettingsUiState.theme` |

- **TalkBack:** la fila agrupada de `LisdataSettingsRow` lee «Tema, Sistema», con su acción de clic.
- **Al volver de Tema**, o de los ajustes del sistema, `onResume` relee el tema.

## 3. Pantalla Tema (A2–A5)

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar(title = theme_title, onBack)` | «Tema» |
| Cabecera | `LisdataEyebrow(theme_eyebrow)` + `Text(theme_headline, headlineLarge)` con `heading()` | «APARIENCIA», «Elige cómo se ve la app» |
| Opciones | `LisdataSectionHeader(theme_section)` + `LisdataCardGroup(Modifier.selectableGroup())` con tres `LisdataRadioRow` y `LisdataDivider` | «Sistema» (`subtitle = theme_system_subtitle`), «Claro», «Oscuro»; `selected = state.selected == …` |
| Lectura fallida | `LisdataInfoCard(icon = Warning, title = languages_read_failed)` | Solo con `saved.readFailed` (A5) |
| Botón | `LisdataGradientButton(text = languages_save / languages_saving, trailingIcon = Check, enabled = !saving && selected != null)` con región en vivo educada | «Guardar preferencias» / «Guardando…» |
| Fallo al guardar | `SnackbarHost` de la pantalla | `languages_save_failed`, una vez (A4) |

- **Contenido** en una columna desplazable (A8). Sin tarjeta hero (A2).

**Comportamiento** (`ThemeViewModel`, como `LanguagesViewModel`):
- **U1. Al abrir y en `ON_START`:** lee los ajustes. Si el usuario no ha tocado nada (`theme_touched`), la opción marcada sigue al guardado.
- **U2. Marcar:** `theme_touched = true` y `theme_selected = nombre`. No se aplica nada (clarify).
- **U3. Guardar:** un segundo toque mientras `saving` no hace nada.
  - `Unchanged` y `Saved` → `ThemeEvent.Close`: la ruta llama a `onBack()`;
  - `Failed` → `saveFailed = true`, la pantalla sigue abierta y la opción marcada se conserva.

  Nunca se llama a `recreate()`: el sistema recrea la actividad (R5).
- **U4. Atrás:** cierra sin guardar y sin preguntar (como S7 de F12).
- **U5. Aviso:** «Preferencias guardadas» lo anuncia el shell (theme-api P6), no la pantalla.

## 4. Textos nuevos (`strings_settings.xml`, ES y EN)

| Clave | Español | Inglés |
|---|---|---|
| `settings_theme` | Tema | Theme |
| `theme_title` | Tema | Theme |
| `theme_eyebrow` | Apariencia | Appearance |
| `theme_headline` | Elige cómo se ve la app | Choose how the app looks |
| `theme_section` | Tema de la aplicación | App theme |
| `theme_system` | Sistema | System |
| `theme_system_subtitle` | Sigue el modo oscuro del teléfono | Follows your phone's dark mode |
| `theme_light` | Claro | Light |
| `theme_dark` | Oscuro | Dark |

- **Se reutilizan:** `languages_save`, `languages_saving`, `languages_saved`, `languages_save_failed` y `languages_read_failed` (FR-018).
- **Paridad:** `StringsParityTest` comprueba que las dos lenguas tienen las mismas claves.

## 5. Navegación

- `Destinations.kt`: `@Serializable data object Theme`.
- `MicroRagNavHost.kt`:
  - `composable<Theme> { ThemeRoute(onBack = back) }`;
  - `SettingsRoute(onTheme = { navController.navigate(Theme) { launchSingleTop = true } })`.
- La barra inferior no aparece en `Theme`, que no es una raíz de pestaña. `NavigationTest` lo comprueba.

## 6. Accesibilidad (A8, FR-019)

- **U6. Objetivos de 48 dp** en la fila, en cada opción y en el botón. La comprobación ATF los verifica.
- **U7. TalkBack:**
  - cada opción tiene el rol `RadioButton` y su estado (`LisdataRadioRow`);
  - el grupo es `selectableGroup`;
  - el titular es un encabezado;
  - «Guardando…» y el aviso de fallo se anuncian una vez.
- **U8. Letra y orientación:** con la letra al 200 % y en horizontal, nada se corta y todo se alcanza desplazando. En claro y en oscuro, los pares de texto son los de siempre (`ContrastTest`).
- **U9. Capas:** `feature/settings` no importa `data`, `File`, `LocaleManager` ni `UiModeManager` (`LayeringTest`).
