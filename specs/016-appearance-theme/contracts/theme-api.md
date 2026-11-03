# Contrato: preferencia de tema (F16)

Capas (constitución II): `feature/settings` → casos de uso (`domain/settings`) → `AppThemeStore` (`data/settings`) y `AppThemeController` (`core/theme`). Tipos en data-model §1–§5. Corregido en el analyze (D-069): H1, H2, M2 y L7.

## 1. Interfaces nuevas (`domain/settings`)

```kotlin
/** The copy that says which theme was picked (research R2). Never read on the main thread. */
interface AppThemeStore {
    /** SYSTEM with readFailed = true when the copy cannot be read (or was corrupt and got replaced); SYSTEM without a copy. */
    suspend fun read(): ThemeSettings
    /** False when it could not be written; nothing changes then. */
    suspend fun write(preference: ThemePreference): Boolean
}

/** The system's per-app night mode (research R1). */
interface AppThemeController {
    /** False when the system refused it; the theme did not change then. */
    fun apply(preference: ThemePreference): Boolean
    /** Variant B of P5 only (research R3), at process start: whether the app's configuration already shows [preference]. */
    fun isShowing(preference: ThemePreference): Boolean
}
```

`isShowing` solo existe si la puerta T015 elige la variante B. Con la variante A no se escribe.

## 2. Casos de uso nuevos

| Caso de uso | Qué hace |
|---|---|
| `ObserveThemeSettingsUseCase` | `Flow<ThemeSettings>` con una lectura de `AppThemeStore.read()` (P2) |
| `SaveThemeSettingsUseCase` | Guarda con el orden de P3 y devuelve `ThemeSaveResult` |
| `ReconcileThemeUseCase` | Una vez al arrancar, aplica la copia o `SYSTEM` (P5) |

Se modifica `SettingsNotices`: `announceSaved(settle: Boolean = false)` y `val settlePending: StateFlow<Boolean>`. La llamada de Idiomas no cambia.

## 3. Reglas

**Lectura**
- **P1. Por defecto, `SYSTEM`:**
  - sin copia o con una clave desconocida, `SYSTEM`;
  - con `IOException` al leer, `ThemeSettings(SYSTEM, readFailed = true)`;
  - con el fichero corrupto, `ReplaceFileCorruptionHandler` lo sustituye por uno vacío y marca en memoria la lectura fallida. Las lecturas de esa sesión dan `readFailed = true`, y la escritura siguiente funciona (M2);
  - el almacén nunca lanza.
- **P2. Sin caché del valor:** cada `read()` lee el DataStore. Ajustes relee en `onResume` y Tema en `ON_START`, como el idioma (S3 y P19 de F12).

**Guardado**
- **P3. Orden** (R3):
  1. con `prev = store.read()`, si `!prev.readFailed` y `preference == prev.preference` → `Unchanged`;
  2. si `store.write(preference)` es `false` → `Failed`, y no se aplica;
  3. si `controller.apply(preference)` es `false` → `store.write(if (prev.readFailed) SYSTEM else prev.preference)`, al mejor esfuerzo → `Failed`;
  4. `notices.announceSaved(settle = true)` → `Saved`.
- **P4. Plataforma** (`PlatformAppThemeController`):
  - `apply()` llama a `UiModeManager.setApplicationNightMode` con `AUTO`, `NO` o `YES` (data-model §1). Captura `RuntimeException` (`IllegalArgumentException` o la de un `RemoteException` del servidor), la registra solo por su tipo y devuelve `false`;
  - `isShowing()` (variante B) compara el modo nocturno de `context.resources.configuration` al arrancar con el de la preferencia. Para `SYSTEM`, lo compara con `UiModeManager.getNightMode()` cuando este es `YES` o `NO`; con `AUTO` o `CUSTOM` devuelve `true`, porque no se puede saber (R12).

    **Nunca usa `Resources.getSystem()`**, que refleja el modo por app (R12, H1).

**Arranque**
- **P5. Reconciliación:** `MicroRagApplication.onCreate`, después de Koin, lanza en el `ApplicationScope` (dispatcher `default`) una corrutina que resuelve y ejecuta `ReconcileThemeUseCase` dentro de ella (L9). El caso de uso:
  - lee la copia;
  - `target = if (readFailed) SYSTEM else copy` (FR-016, H2);
  - **variante A:** `controller.apply(target)`;
  - **variante B:** `if (!controller.isShowing(target)) controller.apply(target)`.

  Nunca bloquea el hilo principal ni el primer fotograma (FR-017).

**Aviso**
- **P6. «Preferencias guardadas»** (R6): `SettingsSavedAnnouncement` (shell) lo anuncia una vez, con la región en vivo, cuando:
  - hay un aviso pendiente;
  - el idioma mostrado es el resuelto (F12, sin cambios);
  - con `settlePending`, han pasado `SETTLE_MS = 1000` desde que esta composición vio el aviso. Si la actividad se recrea, la nueva composición empieza su propia espera.

  Después consume el aviso y el margen.

**Privacidad**
- **P7.** Los logs llevan solo el tipo de fallo (`theme not stored: IOException`, `theme not applied: …`), nunca la preferencia (FR-020). Sin red.

## 4. Inyección (Koin)

| Módulo | Alta |
|---|---|
| `coreModule` | `single<AppThemeController> { PlatformAppThemeController(androidContext(), get()) }` |
| `dataModule` | `single<AppThemeStore> { DataStoreAppThemeStore(DataStoreAppThemeStore.create(androidContext().noBackupFilesDir, get<ApplicationScope>().scope), get()) }` |
| `domainModule` | `factoryOf(::ObserveThemeSettingsUseCase)`, `factoryOf(::SaveThemeSettingsUseCase)`, `factoryOf(::ReconcileThemeUseCase)` |
| `featureModule` | `viewModelOf(::ThemeViewModel)`; `SettingsViewModel` recibe `ObserveThemeSettingsUseCase` |

`AppModulesTest` comprueba el grafo. `NavigationTest.settingsViewModels()` construye `SettingsViewModel` y `ThemeViewModel` con fakes (L3).

## 5. Cambios en código existente

| Fichero | Cambio |
|---|---|
| `domain/settings/SettingsNotices.kt` | `announceSaved(settle)`, `settlePending` (P6) |
| `core/navigation/MicroRagAppShell.kt` | `SettingsSavedAnnouncement` espera también el margen (P6) |
| `MicroRagApplication.kt` | Lanza la reconciliación (P5) |
| `feature/settings/SettingsViewModel.kt` y `SettingsUiState.kt` | `theme`, leído al crear y en `onResume` |
| `feature/settings/LanguagesScreen.kt` | `SaveFailure` pasa a un composable interno compartido con Tema (L2) |
| `architecture/LayeringTest` (test) | `feature/settings` tampoco importa `android.app.UiModeManager` |
