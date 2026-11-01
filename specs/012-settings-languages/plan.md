# Implementation Plan: Ajustes e Idiomas (F12)

**Branch**: `012-settings-languages` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-settings-languages/spec.md`

## Summary

> **Cambio en la implementación (D-061):** la evaluación descartó «Automático». No hay tienda del idioma de las respuestas, el prompt no cambia, e Idiomas solo guarda el idioma de la app. Las tareas afectadas están marcadas en `tasks.md`.

F12 sustituye los marcadores de Ajustes e Idiomas por las pantallas de los PNG 09 y 10.

- **Ajustes** (`feature/settings`):
  - los dos modelos con el estado real de la Preparación y una ficha de solo lectura;
  - Idiomas e «Índice y rendimiento»;
  - el indicador «Procesamiento 100% local»;
  - «Borrar historial de chat», que pasa por la sesión del chat: detiene la respuesta en curso, borra todo y el chat queda vacío.
- **Idioma de la app** (`core/locale`):
  - sin AppCompat;
  - en Android 13+, el `LocaleManager` del sistema;
  - en Android 12, un fichero propio en `noBackupFilesDir`, leído al arrancar, con `recreate()`;
  - las notificaciones construyen sus textos con el idioma vigente.
- **Idioma de las respuestas:** siempre español, con el prompt idéntico al del escritorio. Idiomas lo muestra como única opción. La negativa se reconoce también en inglés. «Automático» se evaluó y no se ofrece (D-061).
- **Escritorio**, antes que la app:
  - `language-review` evalúa «Automático» con las 426 preguntas en español frente a `v4`;
  - `crosslang-review` mide las preguntas en inglés sobre los documentos en español, y los documentos en inglés (3 libros de en.wikibooks) con preguntas en inglés y en español;
  - deciden si se ofrece «Automático» y el texto de la tarjeta «Búsqueda multilingüe», y cierran el pendiente «Documentos en otro idioma».
- **Pruebas en el teléfono, en la última fase** (petición del usuario).

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva. Versiones verificadas en Google Maven el 2026-11-01 (research R16):
- DataStore se queda en 1.2.1; la 1.3.0 no aporta nada que F12 necesite;
- AppCompat 1.8.0 no se añade;
- sin cambios: Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, navigation 2.10.2, Koin 4.2.2 y LiteRT-LM 0.18.1;
- en el escritorio, `litert-lm-api` 0.18.1.

**Storage**:
- solo en Android 12, `noBackupFilesDir/settings/app_language`;
- `chat.db` sin cambio de esquema (v1), con dos consultas nuevas;
- en el escritorio: corpus `offline/corpus/en/`, índice `build/index/en.db`, conjuntos `testset/crosslang/*.jsonl` e informes `reports/{language,crosslang}-review.csv`.

**Testing**:
- **JVM:**
  - prompt y negativa (`PromptBuilderTest`, `AnswerReadingTest`; `AnswerGoldenTest` sin cambios);
  - sesión y ViewModel del chat;
  - tienda, controlador (Robolectric con `sdk = 32` y `sdk = 36`), `AppLocale` y casos de uso;
  - ViewModels y pantallas de Ajustes e Idiomas;
  - `NavigationTest`, `StringsParityTest` (nueva), `LayeringTest` y `AppModulesTest`.
- **Python:** prompt, juicio, huellas, corpus, conjuntos y comandos nuevos.
- **Instrumentadas en el emulador:**
  - `SqliteChatRepositoryTest` ampliada;
  - accesibilidad (ATF) de Ajustes e Idiomas;
  - `SystemBackTest`;
  - las de F01–F11.
- **Teléfono de referencia:** batería completa y recorrido, en la última fase.

**Target Platform**: Android, minSdk 31 y targetSdk 37.
- Emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37).
- Android 12 en Robolectric.
- Teléfono de referencia A063 (API 35), al final.

**Project Type**: mobile-app, módulo único `:app` (D-009). Herramienta de escritorio en `offline/`.

**Performance Goals**:
- borrar el historial detiene una respuesta en curso en menos de 1 s (SC-006);
- cambiar el idioma de la app redibuja en el acto, sin reindexar ni recargar datos;
- las preferencias se leen fuera del hilo principal. La única excepción es el fichero de Android 12, de unos bytes, que se lee una vez antes de StrictMode.

**Constraints**:
- **Sin red** (I), salvo al construir el corpus del escritorio.
- **Preferencias** en `noBackupFilesDir` (I).
- **Capas:** `feature/settings` no importa `data`, SQLite, WorkManager, LiteRT, `File` ni `LocaleManager` (II, U1).
- **Prompt:** en español no cambia. «Automático» solo con su evaluación (III).
- **Modelos:** «Local» solo con el modelo verificado y preparado (V).
- **UI:** el PNG manda y S1–S11 están aprobadas (VII).

**Scale/Scope**:
- 2 pantallas, 1 ficha y 1 diálogo;
- 1 tienda, 1 controlador de idioma y 7 casos de uso;
- 2 miembros nuevos en la sesión del chat y 2 consultas en `chat.db`;
- 3 cambios en el design system;
- unos 55 textos nuevos en español e inglés, más la revisión del inglés de toda la app;
- en el escritorio: 2 comandos, 1 corpus de 3 libros, 3 conjuntos de unas 205 preguntas y unas 425 respuestas a juzgar a mano.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F12 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos (`NetworkConfinementTest`). El idioma de las respuestas y el fichero de Android 12 viven en `noBackupFilesDir`, con `allowBackup=false`. En Android 13+, el idioma de la app lo guarda el sistema como ajuste por app: no es un dato de la app. Registros solo con el tipo de fallo (P18). El recorrido en el teléfono se acuerda antes, y no se borra el historial del usuario (R15) | ✅ |
| II. Stack y arquitectura | `SettingsScreen → SettingsViewModel → casos de uso → LanguageSettingsStore/AppLanguageController/ChatSession/ObserveModelSetupUseCase`. DataStore solo en `data/settings`; `LocaleManager` solo en `core/locale`. Regla nueva de `LayeringTest` para `feature/settings`. Sin dependencias nuevas; versiones verificadas y registradas (R16) | ✅ |
| III. RAG según el informe | Con «Español», el prompt es idéntico al del escritorio (golden de F11 sin cambios). «Automático» cambia una línea del prompt y solo se ofrece si su evaluación documentada cumple la regla fijada antes de evaluar (R7). La negativa sigue siendo explícita, y se reconoce en las dos lenguas en la app y en el escritorio (R6) | ✅ |
| IV. Rendimiento | DataStore y `chat.db` fuera del hilo principal. El fichero de Android 12 se lee una vez en `attachBaseContext`, antes de StrictMode: unos bytes, sin base de datos. Ningún motor nuevo; borrar el historial espera a la respuesta en curso y no toca la GPU | ✅ |
| V. Veracidad del estado | Las filas de modelo usan el estado real de la Preparación. «Local» solo con el modelo verificado y preparado, y nunca antes de la primera lectura. Borrar exige confirmación (S6). La tarjeta multilingüe solo dice lo que se ha medido (R9) | ✅ |
| VI. Calidad y pruebas | Pruebas del constructor del prompt con paridad con Python. ViewModels con fakes. Ruta de Android 12 en Robolectric. ATF en el emulador. Juicio manual documentado (D-033) | ✅ |
| VII. UI fiel y accesible | PNG 09 y 10, con S1–S11 aprobadas. Componentes `Lisdata*` y tokens. U1–U10: TalkBack, 48 dp, letra al 200 %, horizontal y modo oscuro. Textos en español e inglés con paridad comprobada. Diferencias con los PNG en el informe de cierre | ✅ |
| VIII. Proceso | Documentación en español y código en inglés. D-058 (clarify) y D-059 (plan). Parada tras analyze. Paradas internas si «Automático» no pasa o si cambia el texto de la tarjeta. Pruebas en el teléfono al final | ✅ |

**Re-check post-diseño:** sin violaciones.
- La lectura síncrona del fichero de Android 12 no es acceso a base de datos (IV). Ocurre una vez por proceso y antes de que exista la UI.
- El idioma por app en Android 13+ lo guarda el sistema, y la constitución I solo exige que los datos de la app queden fuera de las copias.

## Project Structure

### Documentation (this feature)

```text
specs/012-settings-languages/
├── spec.md
├── plan.md                        # este fichero
├── research.md                    # R1–R16
├── data-model.md                  # idiomas, modelos, chat, prompt, pantallas, escritorio
├── quickstart.md
├── contracts/
│   ├── settings-api.md            # casos de uso, reglas P1–P18, inyección y cambios
│   ├── settings-ui.md             # pantallas, ficha, diálogo, textos, navegación, U1–U10
│   └── eval-language.md           # language-review, crosslang-review (L1–L16)
├── checklists/requirements.md
└── tasks.md                       # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── core/locale/
│   ├── AppLocale.kt                            # init, localized, fichero de Android 12 (R2)
│   └── PlatformAppLanguageController.kt        # P1–P4
├── core/di/CoreModule.kt                       # controlador y SettingsNotices
├── core/navigation/{MicroRagNavHost, MicroRagAppShell}.kt   # rutas sin marcador; aviso de P7
├── core/designsystem/component/Rows.kt         # ChipAndChevron, onClickLabel, LisdataFactRow
├── MicroRagApplication.kt                      # AppLocale.init y migración (P4)
├── domain/settings/
│   ├── SettingsModels.kt                       # data-model §1–§2
│   ├── AppLanguageController.kt, SettingsNotices.kt
│   └── SettingsUseCases.kt                     # idiomas y modelos
├── domain/chat/{ChatSession, ChatRepository, ChatUseCases}.kt   # deleteAll, historyCleared, recuento
├── data/chat/{DefaultChatSession, SqliteChatRepository}.kt      # R10, P16
├── data/indexing/IndexingNotifications.kt, data/download/DownloadNotifications.kt   # P5
├── data/di/DataModule.kt, domain/di/DomainModule.kt, feature/di/FeatureModule.kt
├── rag/answer/{PromptBuilder, AnswerReading}.kt                 # REFUSAL_EN
├── feature/settings/
│   ├── SettingsRoute.kt, SettingsScreen.kt, SettingsViewModel.kt, SettingsUiState.kt
│   ├── ModelSheet.kt
│   ├── LanguagesRoute.kt, LanguagesScreen.kt, LanguagesViewModel.kt, LanguagesUiState.kt
│   └── SettingsPreviewData.kt
└── feature/chat/ChatViewModel.kt               # P11
app/src/main/res/values{,-en}/strings_settings.xml   # y las correcciones del inglés (R13)
app/src/debug/java/com/lisdatasolutions/microrag/debug/catalog/CatalogEntries.kt   # variantes nuevas

app/src/test/java/com/lisdatasolutions/microrag/
├── core/locale/{AppLocaleTest, PlatformAppLanguageControllerTest}.kt
├── domain/settings/{SaveLanguageSettingsUseCaseTest, ObserveModelOverviewUseCaseTest}.kt
├── feature/settings/{SettingsViewModelTest, LanguagesViewModelTest, SettingsScreenTest, LanguagesScreenTest}.kt
├── resources/StringsParityTest.kt
├── testing/SettingsFakes.kt
└── (ampliadas) PromptBuilderTest, AnswerReadingTest, DefaultChatSessionTest, ChatViewModelTest,
    NavigationTest, LayeringTest, AppModulesTest
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/chat/SqliteChatRepositoryTest.kt       # ampliada
├── feature/settings/{SettingsScreenAccessibilityTest, LanguagesScreenAccessibilityTest}.kt
└── SystemBackTest.kt                            # sin el marcador

offline/
├── eval/{prompt, judge, config, runs, corpus, testset, pipeline, report}.py   # L2–L16
├── run_eval.py                                  # language-review, crosslang-review, corpus=en, set=crosslang
├── corpus/en/{manifest.json, SOURCES.md, docs/}
├── testset/crosslang/{es-docs.en, en-docs.en, en-docs.es}.jsonl
├── reports/{language-review.csv, crosslang-review.csv, eval-report.md}
├── README.md
└── tests/                                       # pruebas nuevas y ampliadas (L16)
```

**Structure Decision**: módulo único (D-009).
- **`domain/settings`:** el contrato de las preferencias y de la vista de modelos.
- **`data/settings`:** DataStore.
- **`core/locale`:** el idioma de la app, que se necesita antes que Koin.
- **`feature/settings`:** las dos pantallas.

## Diseño de ejecución

1. **Abrir Ajustes.**
   - El ViewModel refresca el estado de los modelos (`RefreshLocalAiUseCase`) y observa `ObserveModelOverviewUseCase`.
   - Lee el idioma de la app para el subtítulo de «Idiomas».
   - Tocar un modelo abre su ficha, que se guarda en `SavedStateHandle`.
2. **Borrar el historial:**
   1. recuento: si es 0, aviso;
   2. diálogo, con la frase extra si hay una respuesta en curso;
   3. `deleteAll()` en la sesión: detiene, espera, borra, `current = null` y avisa a la pestaña Chat (`historyCleared`);
   4. aviso «Historial borrado».
3. **Idiomas:**
   - la pantalla marca lo guardado;
   - «Guardar preferencias» llama a `SaveLanguageSettingsUseCase` (P6);
   - si va bien: vuelta al origen, recreación (la del sistema en Android 13+, o `recreate()` en Android 12) y anuncio «Preferencias guardadas» en el idioma nuevo (P7);
   - si falla: aviso y valores anteriores.
4. **Chat:**
   - cada respuesta lee el idioma de las respuestas al empezar (P16);
   - una negativa en español o en inglés se muestra con C1;
   - si el historial se borra, la pestaña vuelve a un chat vacío.
5. **Notificaciones:** cada vez que se construyen usan el idioma vigente.

## Orden de implementación

Las pruebas en el teléfono van al final, como en F10 y F11 (petición del usuario).

1. **Escritorio** (decide qué se construye en la app):
   1. idioma en el prompt, negativa en inglés y `answer_language`, con sus pruebas;
   2. `language-review` y su juicio;
   3. traducción de `es-docs.en` y ES·EN;
   4. **decisión sobre «Automático»**: si no cumple, se para;
   5. corpus e índice `en`, preguntas `en-docs.*`, EN·EN y EN·ES, y su juicio;
   6. informe, decisión y cierre del pendiente: se para si cambia el texto de la tarjeta.
2. **Base:**
   - `PromptLanguage.AUTOMATIC` y `REFUSAL_EN` en Kotlin, con la paridad;
   - dominio de ajustes;
   - DataStore;
   - `AppLocale` y el controlador, con Robolectric en `sdk = 32` y 36;
   - notificaciones localizadas.
3. **Chat:**
   - `deleteAll`, `historyCleared`, recuperación del id borrado e idioma de las respuestas en la sesión;
   - consultas nuevas en `chat.db` con sus instrumentadas;
   - `ChatViewModel`.
4. **Design system:** `ChipAndChevron`, `onClickLabel`, `LisdataFactRow` y el catálogo.
5. **Ajustes** (US1, US4, US5): ruta, ViewModel, pantalla, ficha, diálogo, avisos y navegación.
6. **Idiomas** (US2, US3, US6, US8): ruta, ViewModel, pantalla, guardado, recreación y anuncio.
7. **Textos:** `strings_settings.xml` y la revisión del inglés de toda la app, en un commit propio, con `StringsParityTest`.
8. **Emulador:**
   - instrumentadas con ATF;
   - recorrido en API 33 y API 37, con el cambio de idioma, el ajuste del sistema, el borrado y la accesibilidad.
9. **Última fase, en el teléfono de referencia:**
   - batería instrumentada;
   - recorrido acordado (sin confirmar el borrado del historial salvo permiso);
   - capturas frente a los PNG;
   - red;
   - cierre.

## Riesgos y verificaciones tempranas

1. **«Automático» no cumple la regla** (R7). Lo más probable es una caída pequeña en español, por el ruido de una línea distinta, o negativas en inglés con otra redacción. Se para y decide el usuario con las cifras. Hasta entonces, la app no ofrece la opción (`AUTOMATIC_OFFERED`).
2. **Juicio manual largo:** unas 425 respuestas. Se hace por lotes, como en D-056, con los informes de avance que pidió el usuario en F11.
3. **Recreación en Android 13+ en mitad del guardado** (R4). El evento `Close` sobrevive en el ViewModel. Se prueba en el emulador API 33 y con Robolectric.
4. **Android 12 sin dispositivo:** no hay imagen de API 31–32 instalada. La ruta se prueba en Robolectric. Si el usuario lo pide, se descarga una imagen de API 32, con su permiso.
5. **Notificaciones en el idioma anterior** (P5). Una prueba Robolectric construye la notificación tras cambiar el idioma.
6. **Borrar el historial en el teléfono del usuario** (R15). Nunca sin su permiso expreso.
7. **Textos más largos en inglés** con la letra al 200 %. Lo cubren las pruebas ATF y Robolectric en horizontal.
8. **Huellas del escritorio** (L1). Una prueba comprueba que `informe` y `v4` conservan su huella antes de generar nada.

## Complexity Tracking

Sin violaciones de la constitución.
