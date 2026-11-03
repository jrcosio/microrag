# Research: Apariencia: tema e icono de Ajustes (F16)

**Fecha:** 2026-11-03
**Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)

Cada punto sigue el formato **Decisión / Motivo / Alternativas**. Lo verificado se marca con la fuente; lo que se verifica durante la implementación tiene su tarea.

## R1 — Cómo se aplica y se guarda el tema: el modo oscuro por app del sistema

- **Decisión:** el tema se aplica con `UiModeManager.setApplicationNightMode(int)`:
  - «Sistema» → `MODE_NIGHT_AUTO`;
  - «Claro» → `MODE_NIGHT_NO`;
  - «Oscuro» → `MODE_NIGHT_YES`.

  El sistema lo guarda y lo aplica a la configuración de la app antes de crear sus ventanas.
- **Verificado:**
  - **Firma:** `public void setApplicationNightMode(int)` está en `android.jar` de las plataformas 33 y 37.1 (`javap`), y existe desde la API 31, el `minSdk` de la app.
  - **Documentación** (`sources/android-36.1/android/app/UiModeManager.java`): «Sets and persist the night mode for this application… will result in a configuration change (and potentially an Activity lifecycle event)… The mode is persisted for this application until it is either modified by the application, the user clears the data for the application, or this application is uninstalled.».
  - **«Sistema» equivale a no imponer nada:** `MODE_NIGHT_AUTO` vale `0`, que es `Configuration.UI_MODE_NIGHT_UNDEFINED >> 4`. La app queda en el modo del teléfono.
  - **Pendiente de comprobar en el emulador:** una prueba instrumentada lo verifica antes de construir nada encima (T005).
- **Motivo:**
  - Es la única forma de que la **pantalla de arranque** salga en el tema elegido (FR-010). La dibuja el sistema antes de que exista el proceso de la app, con los recursos `values-night` según la configuración de la app.
  - No hay que leer nada al arrancar (FR-017).
  - Persiste tras reiniciar (FR-008) y se borra con los datos de la app, como pide el caso límite de la spec.
  - Es el mismo modelo que el idioma en Android 13+ (`LocaleManager`, F12 R2): un ajuste por app que guarda el sistema, sin AppCompat (D-059).
- **Alternativas descartadas:**
  - `AppCompatDelegate.setDefaultNightMode`: añade AppCompat (D-059), no persiste y no llega a la pantalla de arranque.
  - Un DataStore propio con `LisdataTheme(darkTheme = …)` y `SystemBarStyle` a mano: la pantalla de arranque y la primera pantalla saldrían un instante en el modo del teléfono (FR-010), y habría que leer de disco antes del primer fotograma (FR-017).
  - Imponer la configuración en `attachBaseContext` de la actividad: tampoco llega a la pantalla de arranque.

## R2 — Leer el tema guardado: una copia propia

- **Decisión:** la app guarda además su propia copia del tema en un DataStore de preferencias, `noBackupFilesDir/datastore/appearance.preferences_pb`, con la clave `theme` (`SYSTEM`, `LIGHT` o `DARK`). La copia solo sirve para **mostrar** el valor (la fila «Tema» y la opción marcada) y para reconciliar (R3). Quien aplica el tema es siempre el sistema.
- **Verificado:** `UiModeManager` no tiene ningún método para leer el modo por app (`javap` de las plataformas 33 y 37.1). `getNightMode()` devuelve el del teléfono.
- **Motivo:**
  - Sin la copia no se puede distinguir «Sistema» de «Oscuro» cuando el teléfono está en oscuro, y la fila mentiría (constitución V).
  - La copia se lee solo fuera del hilo principal: en los ViewModels y en la reconciliación, nunca para pintar el primer fotograma.
  - DataStore ya está en el proyecto (1.2.1), con el patrón de `DataStoreOnboardingStore` (`create(noBackupDir, scope)`).
- **Fallos** (FR-015, FR-016):
  - Una lectura que lanza `IOException` (también `CorruptionException`) da `ThemeSettings(SYSTEM, readFailed = true)`.
  - Una escritura que lanza `IOException` devuelve `false`.
  - Los dos se registran solo con el tipo de fallo, nunca con el valor (FR-020).
- **Alternativas descartadas:**
  - un fichero de una línea, como el idioma en Android 12: DataStore ya trae escritura atómica y es el patrón de los demás almacenes;
  - deducirlo de `Resources.getSystem()` frente a la configuración de la app: no distingue «Sistema» de un valor igual al del teléfono.

## R3 — Orden al guardar y reconciliación al arrancar

- **Decisión al guardar**, en `SaveThemeSettingsUseCase`:
  1. si el valor es el guardado → `Unchanged`, y no se escribe ni se aplica nada;
  2. se escribe la copia; si falla → `Failed`, sin aplicar nada;
  3. se aplica en el sistema; si lanza una excepción → se intenta restaurar la copia anterior → `Failed`;
  4. → `Saved`, que deja «Preferencias guardadas» pendiente de anunciar (R6).
- **Decisión al arrancar:** `ReconcileThemeUseCase` corre una vez en el `ApplicationScope`, fuera del hilo principal, después de Koin. Lee la copia y solo aplica si la configuración de la app no la refleja:
  - con `LIGHT` o `DARK`, si el modo nocturno de la app no es el esperado;
  - con `SYSTEM`, si el de la app no coincide con el del teléfono.
- **Motivo:**
  - La intención del usuario se escribe primero. Si el proceso muere entre la escritura y la aplicación, la reconciliación la aplica en el siguiente arranque, y la fila nunca dice un tema que no se ve (constitución V).
  - Aplicar solo cuando no coincide evita recrear la actividad en cada arranque.
  - Con los datos borrados se pierden la copia y el valor del sistema a la vez (R1), así que no hay nada que reconciliar.
- **Alternativas descartadas:**
  - aplicar primero y escribir después: si el proceso muere entre medias, la reconciliación desharía el cambio del usuario;
  - aplicar en cada arranque: recrea la actividad sin necesidad;
  - no reconciliar: deja una ventana, pequeña pero real, en la que la fila mentiría.

## R4 — Pantalla de arranque, barras del sistema y Compose

- **Decisión:** `LisdataTheme`, `MainActivity` y los temas XML **no cambian**.
- **Verificado en el código:**
  - `LisdataTheme(darkTheme = isSystemInDarkTheme())` lee `LocalConfiguration`, que refleja la configuración de la app con el modo por app ya aplicado.
  - `enableEdgeToEdge()` sin argumentos usa `SystemBarStyle.auto`, que decide el color de los iconos con los recursos de la actividad: los mismos.
  - `values-night/themes.xml` y `values-night/colors.xml` dan la pantalla de arranque oscura con el modo nocturno de la app.
  - `AppLocale.wrap` copia la configuración entera al crear el contexto de la actividad, también `uiMode`. Al recrearse por el cambio de tema, la copia sale ya con el nuevo modo.
- **Se verifica en el emulador y en el teléfono:**
  - grabación de pantalla de 10 arranques en frío por combinación (SC-002, T030);
  - legibilidad de los iconos de las barras en claro y en oscuro (FR-011).
- **Riesgo residual:** en Android 12 (API 31–32) no hay emulador ni teléfono. Se apoya en la documentación de la API y en Robolectric para el resto del código. Queda anotado en el informe.

## R5 — Recreación y estado

- **Decisión:** sin código nuevo para conservar el estado.
- **Verificado:**
  - la actividad no declara `configChanges`, así que el sistema la recrea al cambiar `uiMode`, como ya hace hoy con el modo del teléfono;
  - los ViewModels y su `SavedStateHandle` sobreviven: el borrador del chat está en el `SavedStateHandle` (`ChatViewModel`, clave `QUESTION`), y la conversación y el desplazamiento ya sobreviven al giro (F11, F13);
  - la sesión del chat vive en el ámbito de la app, y una respuesta en curso sigue durante la recreación (F12, cambio de idioma con Gemma respondiendo);
  - la indexación corre en WorkManager, en primer plano, fuera de la actividad.
- **Cierre de la pantalla Tema:** como Idiomas en Android 13+:
  - el ViewModel envía `ThemeEvent.Close` por un `Channel` con búfer;
  - la ruta llama a `onBack()`;
  - el sistema recrea la actividad con Ajustes arriba;
  - si la recreación llega antes de consumir el evento, lo consume la ruta recreada.

  Nunca hace falta `recreate()`: el sistema recrea en todas las versiones.

## R6 — «Preferencias guardadas»: solo TalkBack, cuando ya se ve el tema nuevo

- **Decisión:**
  - Como en Idiomas (S9 de F12), «Preferencias guardadas» lo **anuncia TalkBack** una vez, con la región en vivo del shell (`SettingsSavedAnnouncement`). No hay aviso visible.
  - `SettingsNotices.announceSaved(expectDark: Boolean? = null)` guarda el modo esperado.
  - El anuncio espera a que `LocalConfiguration` tenga ese modo nocturno, igual que ya espera al idioma nuevo. Así no se dice en la actividad que está a punto de destruirse.
- **Corrección de A3 y del wireframe:** la propuesta aprobada decía «aparece el aviso inferior “Preferencias guardadas”». En Idiomas ese aviso no se ve: solo se anuncia. Como el usuario eligió «como Idiomas» (clarify), A3 y §3 del wireframe se corrigen a «TalkBack anuncia». Queda en D-068.
- **Alternativas descartadas:**
  - un aviso visible solo para el tema: rompería el patrón elegido;
  - anunciar sin esperar: TalkBack lo cortaría al recrearse la actividad.

## R7 — Iconos: Lucide `settings` y `sun-moon`

- **Decisión:**
  - `ic_settings = settings`, que sustituye al `sun`;
  - nuevo `ic_sun_moon = sun-moon`, como `LisdataIcons.SunMoon`;
  - los dos se regeneran con `tools/icons/lucide_to_vector.py` desde `lucide-static` 1.61.0, la versión fijada;
  - se corrige el comando de `specs/001-foundations-design-system/quickstart.md`, que pasaba `icon-nodes.json` cuando el script espera la carpeta `icons`.
- **Verificado** (2026-11-03, `npm pack lucide-static@1.61.0` en el scratchpad):
  - `package/icons/settings.svg` (engranaje: un trazo y un círculo de radio 3) y `package/icons/sun-moon.svg` existen;
  - el conversor, ejecutado sobre la carpeta `icons` con el mapeo nuevo, genera 32 recursos;
  - comparados con el repo, solo cambia `ic_settings.xml` y aparece `ic_sun_moon.xml`, de modo que los otros 30 salen idénticos byte a byte;
  - el arco de `settings` se conserva como comandos `a` en `pathData`, que VectorDrawable admite.
- **Además:**
  - el comentario de `icons.txt` pasa de G19 a D-067;
  - `INVENTARIO.md` §2.6 (fila de `ic_settings` y fila nueva de `ic_sun_moon`) y la ficha P03 («`ic_settings` (sol)»).
- **Prueba nueva** en `DesignSystemConformanceTest`: `ic_settings.xml` declara `icon 'settings'` y ningún recurso sale de `icon 'sun'` (SC-007).

## R8 — Estructura del código

- **Decisión:** copiar la forma de Idiomas.
  - `domain/settings`:
    - `ThemePreference`, `ThemeSettings` y `ThemeSaveResult`;
    - las interfaces `AppThemeStore` y `AppThemeController`;
    - `ObserveThemeSettingsUseCase`, `SaveThemeSettingsUseCase` y `ReconcileThemeUseCase`.
  - `data/settings/DataStoreAppThemeStore` (DataStore, R2).
  - `core/theme/PlatformAppThemeController` (`UiModeManager`, R1). Va en `core`, como `PlatformAppLanguageController`, porque habla con la plataforma y no con datos de la app.
  - `feature/settings`:
    - `ThemeRoute`, `ThemeScreen`, `ThemeViewModel`, `ThemeUiState` y `ThemeEvent`;
    - la fila de `SettingsScreen`;
    - el tema en `SettingsUiState` y `SettingsViewModel`, releído en `onResume` como el idioma.
  - Navegación: `data object Theme` en `Destinations.kt` y su `composable<Theme>` en `MicroRagNavHost.kt`; Ajustes recibe `onTheme`.
  - `LayeringTest`: `feature/settings` tampoco puede importar `android.app.UiModeManager`.
- **Motivo:** el mismo patrón que F12, probado en el teléfono, con las mismas reglas de capas (U1 de F12).

## R9 — Pruebas

- **JVM** (Robolectric donde hay Compose):
  - casos de uso con fakes del almacén y del controlador: guardar sin cambios, fallo de escritura, fallo al aplicar con la copia restaurada, y reconciliación (con y sin discrepancia en los tres valores);
  - el almacén sobre un DataStore temporal: valor por defecto, sobrevive a reabrir y fallo de lectura;
  - `ThemeViewModel`: selección en el `SavedStateHandle`, doble toque, cierre y fallo;
  - `ThemeScreen`: estructura, estados y TalkBack;
  - `SettingsScreen` y `SettingsViewModel`: la fila nueva, su subtítulo y su navegación;
  - `NavigationTest`, `StringsParityTest`, `LayeringTest`, `AppModulesTest` y `DesignSystemConformanceTest`.
- **Instrumentadas en el emulador** (`MicroRAG_API33` y `Pixel_10`):
  - `PlatformAppThemeControllerTest`: `NO` y `YES` cambian el modo nocturno de la configuración de la app, y `AUTO` lo devuelve al del teléfono (R1), restaurando `AUTO` al terminar;
  - `ThemeScreenAccessibilityTest` (ATF) en claro y en oscuro, al 200 % y en horizontal;
  - `SettingsScreenAccessibilityTest` con la fila nueva;
  - la batería de F01–F13.
- **Teléfono de referencia (A063, API 35), en la última fase:**
  - arranques en frío grabados (SC-002);
  - reinicio (SC-003);
  - «Sistema» con el modo del teléfono (SC-004);
  - una respuesta y una indexación durante el cambio (SC-005);
  - el recorrido del wireframe (SC-010).

## R10 — Versiones

- **Decisión:** ninguna dependencia nueva.
- **Versiones del catálogo, sin cambios:**
  - DataStore 1.2.1;
  - activity-compose 1.13.0 (`enableEdgeToEdge`);
  - Compose BOM 2026.09.00;
  - lifecycle 2.11.0;
  - navigation 2.10.2;
  - Robolectric 4.17.
- **Plataforma:** `UiModeManager.setApplicationNightMode` está en el SDK (compileSdk 37.1).
- **Herramienta de iconos:** `lucide-static` 1.61.0, solo en el escritorio y sin red en la app.

## R11 — Fase del teléfono

- **Decisión:** las reglas del teléfono de F11–F13 siguen vigentes:
  - los volcados van a `/data/local/tmp`;
  - la conversación y el documento del usuario no se abren, no se vuelcan y no se capturan;
  - los ajustes del teléfono se anotan al empezar y se restauran al terminar, comprobando el giro con `dumpsys window`;
  - la pantalla se mantiene despierta durante la batería.
- **Además en F16:**
  - se anota el modo oscuro del teléfono;
  - el tema de la app vuelve a «Sistema» al terminar, salvo que el usuario diga otra cosa;
  - el arranque en frío abre Inicio, que muestra el documento del usuario. Por eso las grabaciones de SC-002 se hacen en el emulador, con datos de prueba. En el teléfono solo se muestrea el color de un píxel del fondo, en una esquina sin texto, en los primeros fotogramas. No se guarda ninguna imagen.
