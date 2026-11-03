# Data model: Apariencia: tema e icono de Ajustes (F16)

**Fecha:** 2026-11-03 · **Spec:** [spec.md](spec.md) · **Research:** [research.md](research.md)

F16 no toca `rag.db` ni `chat.db`. Solo añade una preferencia, guardada en dos sitios (R1, R2), y el estado de dos pantallas.

## 1. Preferencia de tema

```kotlin
/** domain/settings: what the user picked (D-067). SYSTEM unless a save says otherwise. */
enum class ThemePreference { SYSTEM, LIGHT, DARK;
    companion object { fun fromName(name: String?): ThemePreference /* SYSTEM for anything else */ }
}

/** What Tema and the «Tema» row show; [readFailed] when the stored copy could not be read (FR-016). */
data class ThemeSettings(val preference: ThemePreference, val readFailed: Boolean = false)

/** Outcome of «Guardar preferencias» in Tema (R3). */
sealed interface ThemeSaveResult { data object Saved; data object Unchanged; data object Failed }
```

| Valor | Lo que ve el usuario | Modo por app del sistema (R1) | Copia propia (R2) |
|---|---|---|---|
| `SYSTEM` | «Sistema»: sigue el modo oscuro del teléfono | `MODE_NIGHT_AUTO`, sin imponer nada | `theme = "SYSTEM"` o sin clave |
| `LIGHT` | «Claro» | `MODE_NIGHT_NO` | `theme = "LIGHT"` |
| `DARK` | «Oscuro» | `MODE_NIGHT_YES` | `theme = "DARK"` |

**Reglas:**
- **Valor por defecto:** sin copia, con una clave desconocida o con la lectura fallida, se usa `SYSTEM`. Si la lectura falló, `readFailed = true`.
- **Quién aplica y quién informa:** el sistema aplica el tema y la copia solo informa. Nunca se aplica el tema leyendo la copia en el hilo principal.
- **Valores de la copia:** solo se escriben el nombre del enum. Nunca se registra el valor en los logs, solo el tipo de fallo (FR-020).

## 2. Dónde vive

| Dato | Lugar | Copia de seguridad | Se borra |
|---|---|---|---|
| Modo por app | Lo guarda el sistema para el paquete | No es un dato de la app | Al cambiarlo, al borrar los datos de la app o al desinstalarla (R1) |
| Copia | `noBackupFilesDir/datastore/appearance.preferences_pb`, clave `theme` | No (`noBackupFilesDir`, `allowBackup=false`) | Con los datos de la app |

## 3. Estados de la preferencia

```
             guardar (copia ✓, sistema ✓)
 SYSTEM ◀─────────────────────────────────▶ LIGHT ◀──────▶ DARK
   ▲  (y cualquier par entre los tres)
   │
   └─ sin copia · clave desconocida · lectura fallida (readFailed) · datos borrados
```

**Guardar** (R3):

| Paso | Si falla |
|---|---|
| 1. Mismo valor que el guardado | → `Unchanged`: no se escribe ni se aplica nada |
| 2. Escribir la copia | → `Failed`: no se aplica nada y el tema sigue siendo el anterior |
| 3. Aplicar en el sistema | → se restaura la copia anterior (si se puede) → `Failed` |
| 4. Anunciar | `SettingsNotices.announceSaved(expectDark)` → `Saved` |

**Reconciliar al arrancar** (R3): con la copia en `c`, `appDark` (modo nocturno de la configuración de la app) y `phoneDark` (el del teléfono), se aplica `c` solo si:
- `c = LIGHT` y `appDark`;
- `c = DARK` y no `appDark`;
- `c = SYSTEM` y `appDark ≠ phoneDark`.

Con la lectura fallida no se hace nada.

## 4. Estado de las pantallas

```kotlin
/** feature/settings: Tema (contracts/theme-ui.md). Mirrors LanguagesUiState. */
data class ThemeUiState(
    val saved: ThemeSettings? = null,        // null while loading
    val selected: ThemePreference? = null,   // what the user marked, kept in the SavedStateHandle
    val saving: Boolean = false,
    val saveFailed: Boolean = false,         // FR-015: the snackbar, once
)

sealed interface ThemeEvent { data object Close : ThemeEvent }   // the system recreates; never recreate() (R5)

/** SettingsUiState gains the «Tema» row's subtitle. */
data class SettingsUiState(/* … F12 fields … */ val theme: ThemePreference = ThemePreference.SYSTEM)
```

**Claves del `SavedStateHandle`:**
- `theme_selected`: el nombre de la preferencia marcada;
- `theme_touched`: true en cuanto el usuario marca una opción. Mientras no lo esté, una relectura actualiza la opción marcada, como en Idiomas (P19 de F12).

## 5. Aviso pendiente

`SettingsNotices` (vive lo que el proceso) pasa a guardar, además de que hay un aviso pendiente, el modo nocturno esperado:

| Llamada | Quién | Cuándo se anuncia |
|---|---|---|
| `announceSaved()` | Idiomas (F12, sin cambios) | Cuando la pantalla ya muestra el idioma nuevo |
| `announceSaved(expectDark)` | Tema | Cuando, además, `LocalConfiguration` tiene ese modo nocturno (R6) |
