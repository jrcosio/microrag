# Contrato: preferencia de tema (F16)

Capas (constitución II): `feature/settings` → casos de uso (`domain/settings`) → `AppThemeStore` (`data/settings`) y `AppThemeController` (`core/theme`). Tipos en data-model §1–§5.

## 1. Interfaces nuevas (`domain/settings`)

```kotlin
/** The copy that says which theme was picked (research R2). Never read on the main thread. */
interface AppThemeStore {
    /** SYSTEM with readFailed = true when the copy cannot be read; SYSTEM without a copy. */
    suspend fun read(): ThemeSettings
    /** False when it could not be written; nothing changes then. */
    suspend fun write(preference: ThemePreference): Boolean
}

/** The system's per-app night mode (research R1). */
interface AppThemeController {
    /** False when the system refused it; the theme did not change then. */
    fun apply(preference: ThemePreference): Boolean
    /** Whether the app's configuration already shows [preference] (R3: reconcile only on a mismatch). */
    fun isShowing(preference: ThemePreference): Boolean
    /** The night mode [preference] gives now: the phone's for SYSTEM (R6: what the announcement waits for). */
    fun isDark(preference: ThemePreference): Boolean
}
```

## 2. Casos de uso nuevos

| Caso de uso | Qué hace |
|---|---|
| `ObserveThemeSettingsUseCase` | `Flow<ThemeSettings>` con una lectura de `AppThemeStore.read()` (P2) |
| `SaveThemeSettingsUseCase` | Guarda con el orden de P3 y devuelve `ThemeSaveResult` |
| `ReconcileThemeUseCase` | Una vez al arrancar, aplica la copia si la configuración no la refleja (P5) |

Se modifica `SettingsNotices`: `announceSaved(expectDark: Boolean? = null)` y `val expectedDark: StateFlow<Boolean?>`. La llamada de Idiomas no cambia.

## 3. Reglas

**Lectura**
- **P1. Por defecto, `SYSTEM`:**
  - sin copia o con una clave desconocida, `SYSTEM`;
  - con `IOException` al leer (también `CorruptionException`), `ThemeSettings(SYSTEM, readFailed = true)`;
  - el almacén nunca lanza.
- **P2. Sin caché:** cada `read()` lee el DataStore. Ajustes relee en `onResume` y Tema en `ON_START`, como el idioma (S3 y P19 de F12).

**Guardado**
- **P3. Orden** (R3):
  1. si `preference == store.read().preference` y la lectura no falló → `Unchanged`. Con la lectura fallida se guarda siempre, para que un guardado arregle el fallo (FR-016);
  2. si `store.write(preference)` es `false` → `Failed`, y no se aplica;
  3. si `controller.apply(preference)` es `false` → `store.write(anterior)`, al mejor esfuerzo → `Failed`;
  4. `notices.announceSaved(expectDark = controller.isDark(preference))` → `Saved`.
- **P4. Plataforma** (`PlatformAppThemeController`):
  - `apply()` llama a `UiModeManager.setApplicationNightMode` con `AUTO`, `NO` o `YES` (data-model §1). Captura `RuntimeException` (`IllegalArgumentException` o la de un `RemoteException` del servidor), la registra solo por su tipo y devuelve `false`;
  - `isShowing()` compara el modo nocturno de `context.resources.configuration` con el de la preferencia. Para `SYSTEM`, lo compara con el de `Resources.getSystem().configuration`;
  - `isDark(SYSTEM)` es el modo nocturno del teléfono (`Resources.getSystem()`).

**Arranque**
- **P5. Reconciliación:** `MicroRagApplication.onCreate`, después de Koin, lanza en el `ApplicationScope` (dispatcher `default`) `ReconcileThemeUseCase`:
  - lee la copia; si `readFailed`, no hace nada;
  - si `!controller.isShowing(copy)`, llama a `controller.apply(copy)`;
  - nunca bloquea el hilo principal ni el primer fotograma (FR-017).

**Aviso**
- **P6. «Preferencias guardadas»** (R6): `SettingsSavedAnnouncement` (shell) lo anuncia una vez, con la región en vivo, cuando:
  - hay un aviso pendiente;
  - el idioma mostrado es el resuelto (F12, sin cambios);
  - si `expectedDark != null`, el modo nocturno de `LocalConfiguration` coincide.

  Después consume el aviso y el modo esperado.

**Privacidad**
- **P7.** Los logs llevan solo el tipo de fallo (`theme not stored: IOException`, `theme not applied: …`), nunca la preferencia (FR-020). Sin red.

## 4. Inyección (Koin)

| Módulo | Alta |
|---|---|
| `coreModule` | `single<AppThemeController> { PlatformAppThemeController(androidContext(), get()) }` |
| `dataModule` | `single<AppThemeStore> { DataStoreAppThemeStore(DataStoreAppThemeStore.create(androidContext().noBackupFilesDir, get<ApplicationScope>().scope), get()) }` |
| `domainModule` | `factoryOf(::ObserveThemeSettingsUseCase)`, `factoryOf(::SaveThemeSettingsUseCase)`, `factoryOf(::ReconcileThemeUseCase)` |
| `featureModule` | `viewModelOf(::ThemeViewModel)`; `SettingsViewModel` recibe `ObserveThemeSettingsUseCase` |

`AppModulesTest` comprueba el grafo.

## 5. Cambios en código existente

| Fichero | Cambio |
|---|---|
| `domain/settings/SettingsNotices.kt` | `announceSaved(expectDark)`, `expectedDark` (P6) |
| `core/navigation/MicroRagAppShell.kt` | `SettingsSavedAnnouncement` espera también al modo nocturno (P6) |
| `MicroRagApplication.kt` | Lanza la reconciliación (P5) |
| `feature/settings/SettingsViewModel.kt` y `SettingsUiState.kt` | `theme`, leído al crear y en `onResume` |
| `architecture/LayeringTest` (test) | `feature/settings` tampoco importa `android.app.UiModeManager` |
