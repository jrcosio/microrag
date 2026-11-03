# Implementation Plan: Apariencia: tema e icono de Ajustes (F16)

**Branch**: `016-appearance-theme` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-appearance-theme/spec.md`

## Summary

F16 cambia el icono de Ajustes y añade el tema de la app (D-066, D-067).

- **Engranaje:** `ic_settings` se regenera desde el Lucide `settings` y entra `ic_sun_moon` para la fila nueva. El conversor de `tools/icons` ya lo produce sin tocar los otros 30 iconos (research R7).
- **Tema:** «Sistema» (por defecto), «Claro» u «Oscuro», en una fila de Preferencias que abre la pantalla Tema, copiada de Idiomas. Se aplica al guardar.
  - **Quién aplica:** el **modo oscuro por app del sistema** (`UiModeManager.setApplicationNightMode`, API 31). Lo guarda el sistema y lo aplica antes de la primera ventana, así que la pantalla de arranque, las barras y Compose lo siguen sin cambios en `LisdataTheme` ni en `MainActivity` (R1, R4).
  - **Qué se muestra:** una **copia propia** en un DataStore, porque el sistema no deja leer el valor (R2).
  - **Al guardar,** primero la copia y después el sistema. **Al arrancar,** una reconciliación en segundo plano aplica la copia si no se refleja (R3).
- **Aviso:** «Preferencias guardadas» lo anuncia TalkBack cuando la app ya se ve en el tema nuevo, como en Idiomas. Esto corrige A3 (R6, D-068).
- **Pruebas en el teléfono, en la última fase.**

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 como bytecode target y JBR 21 (sin cambios).

**Primary Dependencies**: ninguna nueva (research R10).
- Se mantienen DataStore 1.2.1, activity-compose 1.13.0, Compose BOM 2026.09.00, lifecycle 2.11.0, navigation 2.10.2, Koin 4.2.2 y Robolectric 4.17.
- `UiModeManager.setApplicationNightMode(int)` está verificado en `android.jar` de las plataformas 33 y 37.1.
- Para los iconos, `lucide-static` 1.61.0, solo en el escritorio.

**Storage**:
- el modo por app lo guarda el sistema;
- la copia, en `noBackupFilesDir/datastore/appearance.preferences_pb` (clave `theme`);
- sin cambios en `rag.db` ni en `chat.db`.

**Testing**:
- **JVM:**
  - casos de uso con fakes;
  - almacén sobre un DataStore temporal;
  - ViewModels y pantallas de Tema y Ajustes con Robolectric;
  - `NavigationTest`, `StringsParityTest`, `LayeringTest`, `AppModulesTest` y `DesignSystemConformanceTest`.
- **Instrumentadas en el emulador:**
  - `PlatformAppThemeControllerTest` (R1);
  - ATF de Tema y Ajustes;
  - la batería de F01–F13.
- **Teléfono A063:** batería y recorrido, en la última fase.

**Target Platform**: Android, minSdk 31 y targetSdk 37.
- Emuladores `MicroRAG_API33` y `Pixel_10` (API 37).
- Teléfono A063 (API 35), al final.
- Android 12 sin dispositivo (riesgo residual, R4).

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- el tema se ve en menos de 1 s tras guardar (SC-001);
- sin destello del otro tema en el arranque en frío (SC-002);
- la copia nunca se lee en el hilo principal (FR-017).

**Constraints**:
- **Sin red** (I).
- **Copia** en `noBackupFilesDir` (I).
- **Capas:** `feature/settings` no importa `data`, `File`, `LocaleManager` ni `UiModeManager` (II).
- **UI:** el wireframe aprobado, con los tokens de siempre y sin colores nuevos (VII).

**Scale/Scope**:
- 1 pantalla nueva y 1 fila;
- 2 iconos (1 regenerado y 1 nuevo);
- 1 almacén, 1 controlador y 3 casos de uso;
- 1 cambio en `SettingsNotices` y en el anuncio del shell;
- 9 textos nuevos en español e inglés.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F16 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos (`setApplicationNightMode` no pide permiso). La copia vive en `noBackupFilesDir`, con `allowBackup=false`. El modo por app lo guarda el sistema como ajuste por app, como el idioma de F12. Los logs llevan solo el tipo de fallo (P7). El recorrido en el teléfono no toca los datos del usuario (R11) | ✅ |
| II. Stack y arquitectura | `ThemeScreen → ThemeViewModel → casos de uso → AppThemeStore / AppThemeController`. DataStore solo en `data/settings` y `UiModeManager` solo en `core/theme`. `LayeringTest` amplía la regla de `feature/settings`. API de plataforma verificada en el SDK (R1); sin dependencias nuevas (R10) | ✅ |
| III. RAG según el informe | No toca el RAG, el prompt ni el índice. Cambiar el tema no reindexa (FR-013) | ✅ |
| IV. Rendimiento | La copia se lee en los ViewModels y en la reconciliación, fuera del hilo principal. El primer fotograma no espera a nada propio: lo resuelve el sistema (FR-017). Sin motores nuevos | ✅ |
| V. Veracidad del estado | La fila muestra la copia, y la reconciliación garantiza que la copia es lo que se ve (R3). Un fallo al guardar no cambia nada y se dice. Un fallo al leer se dice | ✅ |
| VI. Calidad y pruebas | Casos de uso, almacén y ViewModels con fakes y pruebas JVM. Controlador de plataforma con prueba instrumentada. ATF en el emulador. Conformidad del icono | ✅ |
| VII. UI fiel y accesible | La fila y la pantalla siguen el wireframe aprobado (A1–A9, D-067), con la corrección de A3 de D-068, que se alinea con Idiomas. Componentes `Lisdata*` y tokens. U6–U8. Textos ES/EN con paridad. Las diferencias frente al PNG 09 (la fila nueva y el engranaje) van al informe de cierre | ✅ |
| VIII. Proceso | Documentación en español y código en inglés. D-067 (clarify) y D-068 (plan). El usuario pidió el ciclo automático salvo preguntas (2026-11-03), y la parada tras analyze se mantiene si el analyze deja algo para decidir. Pruebas en el teléfono al final | ✅ |

**Re-check post-diseño:** sin violaciones.
- El modo por app del sistema no es un dato de la app, igual que el idioma por app de Android 13+ (F12).
- La reconciliación solo aplica cuando hay discrepancia (R3).

## Project Structure

### Documentation (this feature)

```text
specs/016-appearance-theme/
├── spec.md
├── wireframe.md                  # aprobado (D-067), con la corrección de A3 (D-068)
├── plan.md                       # este fichero
├── research.md                   # R1–R11
├── data-model.md                 # preferencia, dónde vive, estados, pantallas y aviso
├── quickstart.md
├── contracts/
│   ├── theme-api.md              # interfaces, casos de uso, reglas P1–P7, inyección y cambios
│   └── theme-ui.md               # engranaje, fila, pantalla, textos, navegación, U1–U9
├── checklists/requirements.md
└── tasks.md                      # /speckit-tasks
```

### Source Code (repository root)

```text
tools/icons/icons.txt                                   # ic_settings = settings; ic_sun_moon = sun-moon
app/src/main/res/drawable/ic_settings.xml               # regenerado
app/src/main/res/drawable/ic_sun_moon.xml               # nuevo
app/src/main/res/values{,-en}/strings_settings.xml      # 9 textos nuevos
app/src/main/java/com/lisdatasolutions/microrag/
├── core/designsystem/icon/LisdataIcons.kt              # SunMoon
├── core/theme/PlatformAppThemeController.kt            # nuevo (UiModeManager)
├── core/di/CoreModule.kt
├── core/navigation/{Destinations,MicroRagNavHost,MicroRagAppShell}.kt
├── data/settings/DataStoreAppThemeStore.kt             # nuevo
├── data/di/DataModule.kt
├── domain/settings/{ThemeModels,ThemeUseCases,AppThemeStore,AppThemeController,SettingsNotices}.kt
├── domain/di/DomainModule.kt
├── feature/settings/{ThemeRoute,ThemeScreen,ThemeViewModel,ThemeUiState}.kt   # nuevos
├── feature/settings/{SettingsScreen,SettingsRoute,SettingsViewModel,SettingsUiState,SettingsPreviewData}.kt
├── feature/di/FeatureModule.kt
└── MicroRagApplication.kt                              # reconciliación al arrancar
app/src/test/java/com/lisdatasolutions/microrag/        # pruebas JVM nuevas y ampliadas
app/src/androidTest/java/com/lisdatasolutions/microrag/ # PlatformAppThemeControllerTest, ThemeScreenAccessibilityTest
docs/roadmap/INVENTARIO.md                              # §2.6 iconos y P03
specs/001-foundations-design-system/quickstart.md       # comando de regenerar iconos corregido
```

**Structure Decision**: módulo único `:app` con los paquetes por capa de D-009, siguiendo la forma de Idiomas (F12): plataforma en `core/`, almacén en `data/settings`, contratos y casos de uso en `domain/settings`, y pantalla en `feature/settings`.

## Complexity Tracking

Sin violaciones que justificar.
