# Contrato: idiomas, modelos e historial (F12)

Capas (constitución II): `feature/settings` → casos de uso (`domain/settings`, `domain/chat`, `domain/onboarding`) → `LanguageSettingsStore`, `AppLanguageController`, `ChatSession` y `ModelFileRepository`. Tipos en data-model §1–§4.

## 1. Casos de uso nuevos

| Caso de uso | Paquete | Qué hace |
|---|---|---|
| `ObserveLanguageSettingsUseCase` | `domain/settings` | `Flow<LanguageSettings>`: el idioma de la app de `AppLanguageController.current()` y el de las respuestas del store, con `readFailed` |
| `SaveLanguageSettingsUseCase` | `domain/settings` | Guarda con el orden de P6 y devuelve `SaveResult` |
| `ObserveAnswerLanguageUseCase` | `domain/settings` | `Flow<AnswerLanguage>` para la sesión del chat (P12) |
| `ObserveModelOverviewUseCase` | `domain/settings` | `Flow<List<ModelOverview>>` (P11) |
| `CountConversationsUseCase` | `domain/chat` | `repository.conversationCount()` |
| `ClearChatHistoryUseCase` | `domain/chat` | `session.deleteAll()`; devuelve `true` o `false` y nunca lanza |
| `ObserveHistoryClearedUseCase` | `domain/chat` | `session.historyCleared` |

Se reutilizan:
- `RefreshLocalAiUseCase` (F03), al entrar en Ajustes;
- `ObserveModelSetupUseCase` (F03), dentro de `ObserveModelOverviewUseCase`.

## 2. Reglas

**Idioma de la app**
- **P1. Por defecto, español** (D-029):
  - en Android 13+, un `LocaleManager.applicationLocales` vacío se lee como `SPANISH`;
  - en Android 12, un fichero ausente o ilegible se lee igual;
  - el idioma del sistema nunca cuenta.
- **P2. Android 13+:**
  - `current()` lee `LocaleManager` en cada llamada, así que un cambio hecho en los ajustes del sistema se ve al volver;
  - `apply()` llama a `setApplicationLocales` con `es` o `en` explícitos, devuelve `true`, y el sistema recrea las actividades;
  - `recreatesItself = true`.
- **P3. Android 12:**
  - `AppLocale` lee el fichero una vez, en `MicroRagApplication.attachBaseContext`, antes de StrictMode;
  - `apply()` escribe `noBackupFilesDir/settings/app_language` en el hilo `io`, con un fichero temporal y `renameTo`, y actualiza la memoria. Si falla, devuelve `false` y deja la memoria como estaba;
  - `recreatesItself = false`: la pantalla llama a `recreate()`.
- **P4. Paso a Android 13:** al arrancar en API 33+, si `LocaleManager` está vacío y existe el fichero, se aplica su valor y se borra el fichero. Una sola vez.
- **P5. Textos fuera de la actividad:** las notificaciones de indexación y de descarga construyen sus textos con `AppLocale.localized(context)` en cada llamada. Nunca usan el contexto de la `Application` envuelto al arrancar.

**Idioma de las respuestas**
- **P6. Guardado:**
  1. si el idioma de las respuestas ha cambiado, se guarda primero; si falla, `Failed`;
  2. si el idioma de la app ha cambiado, se aplica después; si falla, se repone el idioma de las respuestas anterior y se devuelve `Failed`;
  3. sin cambios, `Unchanged` y no se escribe nada;
  4. si todo va bien, `Saved(appLanguageChanged)`, y `SettingsNotices.saved` pasa a `true`.
- **P7. Aviso:** `SettingsNotices.saved` lo consume el armazón de la app, una sola vez, después de la recreación si la hay. Lo anuncia con el texto «Preferencias guardadas» de los recursos ya recreados.
- **P8. Lectura del store** (FR-028):
  - un `IOException` emite `(SPANISH, readFailed = true)`;
  - un fichero dañado se sustituye por uno vacío;
  - un valor desconocido se lee como `SPANISH`.

  Escritura: un `IOException` devuelve `false` y el valor anterior sigue en el fichero.

**Historial**
- **P9. `ChatSession.deleteAll()`** se hace bajo el `Mutex` de la sesión:
  1. detiene la respuesta en curso (`CANCELLED`) y espera a su trabajo;
  2. `repository.deleteAll()`;
  3. `current = null` y `historyCleared` + 1.

  Una `ChatStorageException` se propaga sin tocar el contador.
- **P10. `ask(id, …)`** con un `id` que ya no existe crea una conversación nueva y responde en ella. Nunca la deja como «no guardada» por ese motivo.
- **P11. `ChatViewModel`:** cuando `historyCleared` cambia después de crearse, vuelve a un chat vacío (`setConversation(null)`) y cierra el diálogo de borrar. El valor inicial se ignora.
- **P12. Ajustes:**
  - al tocar «Borrar historial de chat», si `conversationCount() == 0` muestra el aviso «No hay conversaciones guardadas.»;
  - si no, muestra el diálogo con `stopsAnswer = session.active.value?.state in {RETRIEVING, GENERATING}`;
  - al confirmar, `ClearChatHistoryUseCase` muestra el aviso de éxito o de fallo.

**Modelos**
- **P13. Estado:** `ObserveModelOverviewUseCase` usa exactamente el estado de la Preparación (`ObserveModelSetupUseCase`). «Local» solo aparece con `Ready` (constitución V).
- **P14. Chip:**
  - `Ready(GPU)` → «Local»;
  - `Ready(CPU)` → «Local» en el modelo de embeddings y «Modo CPU, más lento» en `GEMMA4_E2B`;
  - el resto → `chipText()` y `statusKind()` de la Preparación.
- **P15. Ficha:**
  - solo lee: no verifica, no descarga y no borra;
  - «Completar configuración» navega a `Setup` y solo aparece si no está `Ready`.

**Prompt**
- **P16. Idioma en el prompt:** `DefaultChatSession` lee `answerLanguage().first()` al empezar cada respuesta, también al reintentar. Lo pasa como `PromptLanguage` a `PromptBuilder.build`. Con `SPANISH`, el prompt es idéntico al del escritorio y al del golden (SC-004).
- **P17. Negativa:** `AnswerReading.isRefusal(answer)` es `norm(answer) == norm(REFUSAL) || norm(answer) == norm(REFUSAL_EN)`. El escritorio hace lo mismo en `judge.is_refusal`.

**Privacidad**
- **P18. Registros:** las tiendas y el controlador solo registran el tipo de fallo (`IOException`, corrupción), nunca el valor. Nada en release, salvo los avisos ya existentes del `AppLogger`.

## 3. Inyección (Koin)

- `DataModule`:
  - `single<LanguageSettingsStore> { DataStoreLanguageSettingsStore.create(noBackupDir, applicationScope) }`;
  - `DefaultChatSession` recibe `answerLanguage = { get<ObserveAnswerLanguageUseCase>()() }`.
- `CoreModule`, o donde vive hoy el `ApplicationScope`:
  - `single<AppLanguageController> { PlatformAppLanguageController(androidContext(), dispatchers) }`;
  - `single { SettingsNotices() }`.
- `DomainModule`: los casos de uso de §1.
- `FeatureModule`: `viewModelOf(::SettingsViewModel)` y `viewModelOf(::LanguagesViewModel)`.
- `AppModulesTest` comprueba el grafo.

## 4. Cambios en código existente

| Fichero | Cambio |
|---|---|
| `core/locale/AppLocale.kt` | `init(base)` (fichero en API 31–32), `localized(context)`, `resolve()` con el valor en memoria por debajo de API 33 |
| `MicroRagApplication.kt` | `AppLocale.init(base)` antes de `wrap`; la migración de P4 en `onCreate` |
| `data/indexing/IndexingNotifications.kt`, `data/download/DownloadNotifications.kt` | Textos con `AppLocale.localized(context)` (P5) |
| `rag/answer/PromptBuilder.kt` | `PromptLanguage.AUTOMATIC` y `REFUSAL_EN` |
| `rag/answer/AnswerReading.kt` | `isRefusal` con las dos frases (P17) |
| `domain/chat/ChatSession.kt`, `ChatRepository.kt` | Data-model §3 |
| `data/chat/DefaultChatSession.kt` | `deleteAll`, `historyCleared`, idioma de las respuestas (P16) y P10 |
| `data/chat/SqliteChatRepository.kt` | `conversationCount` y `conversationExists` |
| `feature/chat/ChatViewModel.kt` | P11 |
| `core/navigation/MicroRagNavHost.kt` | Las rutas `Settings` y `Languages` sin marcador (settings-ui §5) |
| `core/navigation/MicroRagAppShell.kt` | Anuncio de P7 |
