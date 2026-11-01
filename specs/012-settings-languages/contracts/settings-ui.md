# Contrato: pantallas de Ajustes e Idiomas (F12)

Mandan los PNG `09-configuracion.png` y `10-idiomas.png`. Los estados sin maqueta son las propuestas S1–S11 aprobadas (spec, «Assumptions»). Rutas: `Settings` (pestaña Ajustes) y `Languages` (sin barra inferior).

## 1. Ajustes (PNG 09)

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar` | Atrás (G08: a Inicio) y «Configuración» |
| Cabecera | `Text` `headlineLarge` + `bodyLarge` | «Ajustes» y «Personaliza tu experiencia local.» |
| Inteligencia artificial | `LisdataSectionHeader` + `LisdataCardGroup` | Dos `LisdataSettingsRow`:<br>• `ic_sparkle`, «Modelo de respuestas», «Gemma 4»;<br>• `ic_layers`, «Modelo de embeddings», «EmbeddingGemma 2».<br>Las dos con `SettingsTrailing.ChipAndChevron` (S1) |
| Preferencias | ídem | • `ic_globe`, «Idiomas», subtítulo con el idioma de la app (S3), chevron;<br>• `ic_database`, «Índice y rendimiento», «Almacenamiento y procesamiento», chevron (S4) |
| Privacidad y datos | ídem | • `ic_shield_check` (tinte `success`), «Procesamiento 100% local», «Sin enviar documentos a la nube», chip «Activo» (`StatusKind.Local`), sin acción (S5);<br>• `ic_trash` (`destructive`: icono `error`, título en el color normal del texto), «Borrar historial de chat», «Eliminar conversaciones locales», chevron (S6) |
| Barra inferior | Del armazón (F01) | Ajustes seleccionada |
| Avisos | `SnackbarHost` de la pantalla | Mensajes de S6 |

- **Contenido desplazable.** Con la letra al 200 % o en horizontal, todo se alcanza desplazándose.
- **Antes del primer estado de los modelos,** las filas de modelo salen sin chip, como un estado de carga. Nunca muestran «Local» por adelantado.

### Ficha de modelo (S2)

`LisdataBottomSheet` con:
- **Cabecera:** el nombre (`titleLarge`) y el propósito: «Modelo de respuestas» o «Modelo de embeddings».
- **Filas de dato** (`LisdataFactRow`: etiqueta a la izquierda, valor a la derecha, que baja de línea si no cabe):

  | Etiqueta | Valor |
  |---|---|
  | «Fichero» | `fileName` |
  | «Tamaño» | `bytesText` |
  | «SHA-256» | 12 caracteres y «…»; TalkBack lo lee como «SHA-256, empieza por …» |
  | «Dimensiones» | «256», solo en embeddings |
  | «Procesador» | «GPU», «CPU» o «—» |
  | «Estado» | chip de S1 y, debajo, `detailText()` si lo hay |

- **Botón** `LisdataGradientButton` «Completar configuración», solo si el modelo no está listo. Abre la Preparación y cierra la ficha.

### Diálogo de borrado (S6)

`LisdataConfirmationDialog` destructivo:
- título «¿Borrar el historial de chat?»;
- texto «Se eliminarán todas tus conversaciones de este teléfono. Tus documentos y el índice no cambian.», y, si hay una respuesta en curso, «La respuesta en curso se detendrá.»;
- «Cancelar», con el foco por defecto, y «Borrar».

## 2. Idiomas (PNG 10)

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar` | Atrás y «Idiomas» |
| Cabecera | `LisdataEyebrow` + `Text` `headlineLarge` | «Configuración lingüística» y «Habla en tu idioma» |
| Hero | `LisdataHeroCard` (`decoration = false`: el PNG no tiene órbitas) | Tile con `ic_globe` (`LisdataIconTile`), «Tus documentos.» y `titleAccent` «Tu idioma.». Si el hero no admite el tile a la izquierda del título, se añade esa variante al componente |
| Idioma de la aplicación | `LisdataSectionHeader` + `LisdataCardGroup(Modifier.selectableGroup())` | `LisdataRadioRow` «Español» y «English» |
| Idioma de las respuestas | ídem | `LisdataRadioRow` «Automático» (subtítulo «Detectar idioma de la pregunta») y «Español». «Automático» solo si `automaticOffered` |
| Aviso de lectura | `LisdataInfoCard` (aviso) | Solo con `readFailed`: «No se pudieron leer tus preferencias. Se usan las de por defecto.» (S8) |
| Búsqueda multilingüe | `LisdataInfoCard` | `ic_globe`, «Búsqueda multilingüe» y el texto de S10 |
| Acción | `LisdataGradientButton` | «Guardar preferencias» con ✓. Mientras guarda: «Guardando…» y sin respuesta a toques (S8) |
| Error | `SnackbarHost` | «No se pudieron guardar las preferencias. Se mantienen las anteriores.» |

- El botón va al final del contenido desplazable, como en el PNG, no fijo.
- **Selección:** al abrir, se marcan los valores guardados. Lo marcado se guarda en `SavedStateHandle` y sobrevive a giros y recreaciones.
- **Atrás** descarta los cambios sin preguntar (S7).

## 3. Cambios en el design system

| Componente | Cambio |
|---|---|
| `SettingsTrailing` | Nuevo `ChipAndChevron(text, kind)`: chip y chevron, con 8 dp entre ellos |
| `LisdataSettingsRow` | `onClickLabel: String?`, para la acción de TalkBack («Ver detalles»). Sin `onClick`, la fila no es tocable ni enfocable como botón |
| `LisdataFactRow` (nuevo, `Rows.kt`) | Etiqueta y valor, con `contentDescription` opcional para el valor |
| Catálogo de depuración | Entradas de las variantes nuevas; las cubre `CatalogAccessibilityTest` |

## 4. Textos nuevos (`strings_settings.xml`, español e inglés)

| Clave | Español | Inglés |
|---|---|---|
| `settings_top_title` | Configuración | Configuration |
| `settings_title` | Ajustes | Settings |
| `settings_subtitle` | Personaliza tu experiencia local. | Customize your on-device experience. |
| `settings_section_ai` | Inteligencia artificial | Artificial intelligence |
| `settings_model_answers` | Modelo de respuestas | Answer model |
| `settings_model_embeddings` | Modelo de embeddings | Embedding model |
| `settings_model_local` | Local | On device |
| `settings_model_cpu` | Modo CPU, más lento | CPU mode, slower |
| `settings_section_preferences` | Preferencias | Preferences |
| `settings_languages` | Idiomas | Languages |
| `settings_index` | Índice y rendimiento | Index and performance |
| `settings_index_subtitle` | Almacenamiento y procesamiento | Storage and processing |
| `settings_section_privacy` | Privacidad y datos | Privacy and data |
| `settings_local` | Procesamiento 100% local | 100% on-device processing |
| `settings_local_subtitle` | Sin enviar documentos a la nube | No documents sent to the cloud |
| `settings_local_active` | Activo | On |
| `settings_local_a11y` | Procesamiento 100 % local: activo. Sin enviar documentos a la nube. | 100% on-device processing: on. No documents sent to the cloud. |
| `settings_clear_history` | Borrar historial de chat | Clear chat history |
| `settings_clear_history_subtitle` | Eliminar conversaciones locales | Delete local conversations |
| `settings_show_details` | Ver detalles | Show details |
| `settings_sheet_file` | Fichero | File |
| `settings_sheet_size` | Tamaño | Size |
| `settings_sheet_sha` | SHA-256 | SHA-256 |
| `settings_sheet_sha_a11y` | SHA-256, empieza por %1$s | SHA-256, starts with %1$s |
| `settings_sheet_dimensions` | Dimensiones | Dimensions |
| `settings_sheet_processor` | Procesador | Processor |
| `settings_sheet_status` | Estado | Status |
| `settings_sheet_none` | — | — |
| `settings_clear_title` | ¿Borrar el historial de chat? | Clear the chat history? |
| `settings_clear_body` | Se eliminarán todas tus conversaciones de este teléfono. Tus documentos y el índice no cambian. | All your conversations will be deleted from this phone. Your documents and the index stay as they are. |
| `settings_clear_body_answering` | La respuesta en curso se detendrá. | The answer in progress will stop. |
| `settings_clear_confirm` | Borrar | Delete |
| `settings_history_cleared` | Historial borrado | History cleared |
| `settings_no_conversations` | No hay conversaciones guardadas. | There are no saved conversations. |
| `settings_clear_failed` | No se pudo borrar el historial. | The history could not be cleared. |
| `languages_title` | Idiomas | Languages |
| `languages_eyebrow` | Configuración lingüística | Language settings |
| `languages_headline` | Habla en tu idioma | Speak your language |
| `languages_hero_title` | Tus documentos. | Your documents. |
| `languages_hero_accent` | Tu idioma. | Your language. |
| `languages_section_app` | Idioma de la aplicación | App language |
| `languages_section_answers` | Idioma de las respuestas | Answer language |
| `languages_automatic` | Automático | Automatic |
| `languages_automatic_subtitle` | Detectar idioma de la pregunta | Detect the question's language |
| `languages_answer_spanish` | Español | Spanish |
| `languages_multilingual_title` | Búsqueda multilingüe | Multilingual search |
| `languages_multilingual_body` | Consulta documentos en distintos idiomas desde el mismo chat. (S10: se confirma o cambia con la medida) | Ask about documents in different languages from the same chat. |
| `languages_save` | Guardar preferencias | Save preferences |
| `languages_saving` | Guardando… | Saving… |
| `languages_saved` | Preferencias guardadas | Preferences saved |
| `languages_save_failed` | No se pudieron guardar las preferencias. Se mantienen las anteriores. | Your preferences could not be saved. The previous ones are kept. |
| `languages_read_failed` | No se pudieron leer tus preferencias. Se usan las de por defecto. | Your preferences could not be read. The defaults are in use. |
| `language_name_es` | Español (`translatable="false"`) | — |
| `language_name_en` | English (`translatable="false"`) | — |

Se reutilizan:
- `setup_model_gemma_name` y `setup_model_embedding_name`;
- `chat_models_action` («Completar configuración»);
- `action_cancel`;
- los chips y los detalles de estado de la Preparación (`model_status_*` y `model_detail_*`).

## 5. Navegación

| Desde | Acción | Destino |
|---|---|---|
| Barra inferior | Ajustes | `Settings`, raíz de la pestaña |
| Ajustes | Atrás (flecha o gesto) | Inicio (G08) |
| Ajustes | «Idiomas» | `Languages` (`launchSingleTop`) |
| Ajustes | «Índice y rendimiento» | `Index` (`launchSingleTop`); Atrás vuelve a Ajustes |
| Ficha | «Completar configuración» | `Setup` (`launchSingleTop`) |
| Inicio | «Idiomas» | `Languages`, encima de Inicio (como hoy) |
| Inicio | icono de ajustes | Pestaña Ajustes (como hoy) |
| Idiomas | «Guardar preferencias» con éxito, o sin cambios | `popBackStack()`: Ajustes o Inicio |
| Idiomas | Atrás | `popBackStack()`, sin guardar |

## 6. Accesibilidad (U1–U10)

- **U1. Capas:** `feature/settings` no importa `data.`, `androidx.sqlite`, `androidx.work`, LiteRT, `java.io.File` ni `android.app.LocaleManager`. Nueva regla en `LayeringTest`.
- **U2. Encabezados:** los títulos de sección usan `LisdataSectionHeader`, con semántica de encabezado.
- **U3. Opciones:** cada tarjeta de opciones es un `selectableGroup`, y cada fila tiene `Role.RadioButton` y el estado «seleccionado». TalkBack dice, por ejemplo, «English, botón de opción, no seleccionado, 2 de 2».
- **U4. Filas de modelo:** un solo nodo con título, nombre y estado, y la acción «Ver detalles».
- **U5. «Procesamiento 100% local»:** un solo nodo sin acción, con `settings_local_a11y`.
- **U6. Objetivos táctiles de 48 dp**, también el asa de la ficha (ya lo cumple `LisdataBottomSheet`).
- **U7. Avisos:** región en vivo educada para «Guardando…», «Preferencias guardadas», el error al guardar, «Historial borrado», «No hay conversaciones guardadas.» y el fallo al borrar. Cada uno se anuncia una sola vez.
- **U8. Letra al 200 % y horizontal:** sin cortes de texto. Las filas crecen en alto, y la ficha se desplaza.
- **U9. Modo oscuro:** solo tokens; nada de colores fijos.
- **U10. Pruebas:**
  - ATF (`enableAccessibilityChecks`) en Ajustes, la ficha, Idiomas y el diálogo, en claro y en oscuro, al 200 % y en horizontal;
  - Robolectric para U2–U5 y U7.

## 7. Diferencias con los PNG (para el informe de cierre)

- **Filas de modelo:** las dos llevan chip y chevron (S1). En el PNG, el chip solo está en «Modelo de respuestas» y el chevron solo en «Modelo de embeddings».
- **Chip de Gemma en CPU:** «Modo CPU, más lento» en lugar de «Local» (D-027).
- **Botones de opción:** el aspecto de Material 3 (D-018, G18), aceptado desde F01.
- **Tarjeta «Búsqueda multilingüe»:** su texto puede cambiar según la medida (S10).
- **Barra de estado y franja de inicio** de iOS: no aplican (G40).
