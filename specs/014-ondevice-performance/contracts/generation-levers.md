# Contrato: palancas de generación (US4, US5, US6; R6–R10)

## G1. `GenerationConfig` (`rag/runtime/TextGenerator.kt`)

```kotlin
data class GenerationConfig(
    val maxNumTokens: Int,
    val maxOutputTokens: Int,
    val greedy: Boolean,
    val activationPrecision: ActivationPrecision? = null,     // ya existe; FP32 si se adopta (R8)
    val visionBackend: ComputeBackend? = null,
    val maxNumImages: Int = 0,
    /** F14 (R6): prefill the fixed prompt prefix once per engine and clone it per answer. */
    val prefixCache: Boolean = false,
    /** F14 (R7): multi-token prediction with the model's drafter, when the file has one. */
    val speculative: SpeculativeMode = SpeculativeMode.OFF,
)
enum class SpeculativeMode { OFF, ADAPTIVE }
```

- **Valores de la app:** `EngineAnswerEngine` los toma de `RagParameters.PREFIX_CACHE`, `SPECULATIVE_DECODING` y `ACTIVATION_PRECISION`.
- **Pruebas:** los bancos construyen la configuración que miden.

## G2. Prefijo y resto (`PromptBuilder`)

```kotlin
data class SplitPrompt(val prefix: String, val rest: String) { val whole get() = prefix + rest }
fun buildSplit(question, blocks, previous, template, language): SplitPrompt
```

- **Invariante:** `buildSplit(...).whole == build(...)`, carácter a carácter, en las goldens del prompt y en las pruebas de `PromptBuilderTest`.
- **`prefix`:** desde «Responde a la PREGUNTA…» hasta la línea en blanco tras las reglas, incluida. Depende solo de la plantilla y del idioma.
- **Hasta el motor:** `AnswerPrompt.Text` (dominio, `…/domain/chat/ChatModels.kt`) gana un `prefix` opcional (texto = prefijo + resto), que `EngineAnswerEngine` pasa como `GenerationPrompt.Split(prefix, rest)`. Sin `prefixCache`, el generador envía `whole`, como hoy.

## G3. `LiteRtTextGenerator` con `prefixCache`

- **Camino 1 (sin cambiar el prompt, R6):**
  1. En `initialize()`, o en el primer uso con un prefijo nuevo, abre una `Session` y hace el prefill de `<inicio del turno de usuario> + prefix`, con los tokens de la plantilla de Gemma 4 leídos del fichero.
  2. Por respuesta: `base.clone()`, prefill de `rest + <fin del turno de usuario> + <inicio del turno del modelo>` y decodificación en streaming.
  3. Cierra el clon al terminar o al cancelar.
  4. La base se rehace si cambia el prefijo (otra plantilla o idioma) y se libera con el motor.
- **Camino 2 (si el 1 no es posible con la API):** `Conversation` base con `systemInstruction = prefix` y `prefillPrefaceOnInit = true`, con `clone()` por respuesta. Cambia el prompt: solo se adopta con la regla de calidad de D-088.
- **`ExperimentalFlags.enableBenchmark`:** solo en las pruebas. `BenchmarkInfo` da los tokens del prefill.
- **Prueba `PrefixCacheGoldenTest`** (teléfono, greedy): las respuestas de las goldens deben salir idénticas con y sin `prefixCache`, para el camino 1. Si alguna cambia (un prefill partido en GPU puede divergir en algún token), la prueba informa las diferencias en lugar de fallar, y las respuestas que cambian se juzgan con la regla de calidad de FR-017 antes de adoptar la palanca.

## G4. Especulativa

- `EngineConfig(speculativeDecodingConfig = SpeculativeDecodingConfig(ADAPTIVE))` cuando `speculative == ADAPTIVE`. `ConversationConfig` deja `enableSpeculativeDecoding` (obsoleto) y usa `SpeculativeDecodingConfig(OFF)` por defecto.
- **Comprobación previa:** `ModelInfo` dice si el fichero tiene drafter (`hasSpeculativeDecodingSupport`). Si no lo tiene, el banco registra «no disponible».

## G5. Precisión y plantilla de cifras

- **FP32:** `ACTIVATION_PRECISION = FLOAT32` solo para el motor de texto. El de visión se mide aparte y conserva la del fichero salvo decisión.
- **`v4-cifras`:** plantilla nueva en `EXTRA_RULES`. La regla de copiar cifras pasa al final, junto a la PREGUNTA, con la redacción fijada en `offline/eval/` (paridad con la golden del prompt).
- **También con páginas (D-090):** `buildVisual` usa la plantilla de `RagParameters.PROMPT_TEMPLATE`, así que `v4-cifras` cambia también la v5-visual. Se actualizan `offline/eval/visual_prompt.py` y `VisualPromptGoldenTest` para que la golden cubra las dos plantillas. Las respuestas con páginas se miden en el teléfono, sin regla de calidad visual (D-078).

## G6. Estimador de tokens (R9)

```kotlin
fun estimatedTokens(text: String): Int  // ⌈letras/a + dígitos/b + resto/c⌉; con a = b = c = 4,554 es el de hoy
```

- **Clases:** letras (`Character.isLetter`), dígitos (`isDigit`) y resto (puntuación, espacios y símbolos), contadas por puntos de código.
- **Python:** el mismo cálculo en `offline/eval/token_estimate.py`, con una prueba de paridad sobre textos fijos (incluidos los acentos, la ñ y las cifras con separadores).
- **Alcance (D-090):** solo el límite de 2.000 tokens del prompt (`DefaultChatSession.prompt()` y su par en el harness). La estimación guardada de cada fragmento (`IngestRunner`), el presupuesto de contexto de D-034 (`ContextSelector.BUDGET_TOKENS`, 1.719) y sus pares del harness (`chunker.py`, `selector.py`) no cambian, y no hay que reindexar.

## G7. Precarga por ámbito (R10)

- **Disparador:** `ChatViewModel`, cuando cambia el ámbito de la conversación y el chat está en pantalla y listo, vuelve a llamar a `WarmUpAnswerEngineUseCase`.
- **Ámbito con páginas:** `WarmUpAnswerEngineUseCase` (`…/domain/chat/ChatUseCases.kt`) recibe el ámbito y usa el mismo predicado que hoy (modo activo, `pagesUsable` y `chatLibraryState(scope).withPages > 0`), en lugar de la biblioteca entera. Sin función nueva en `RagIndex`.
- **Coordinador:** el `EngineCoordinator` libera el motor que no se pide antes de cargar el otro (nunca dos en GPU).
- **Interruptor:** `RagParameters.PREWARM_BY_SCOPE`.

## G8. Regla de seguimiento con páginas (R10)

- **`PAGE_FOLLOW_UP`:**
  - `JOINED`: hoy;
  - `NEW_ONLY`: las páginas se buscan con la pregunta nueva;
  - `BEST_OF_BOTH`: se busca con las dos y gana la página de mayor similitud.
- **Dónde:** `HybridRetriever.search`. Con `NEW_ONLY` o `BEST_OF_BOTH`, solo cuando `FollowUpRule` ha unido la consulta **y** la respuesta puede llevar página (`request.withPages`), embebe además la pregunta sola. Con el modo apagado o sin páginas no hay embedding extra (FR-026, FR-027).
- **Pruebas JVM:** `HybridRetrieverTest`, con fakes para cada variante y casos con el modo apagado y sin páginas (un solo embedding).

## G9. Desglose de tiempos (FR-015)

- `DefaultChatSession` registra en su `log` (ya tiene `ttftMs`) los tiempos de embedding, búsqueda, construcción del prompt y hasta el primer token. Son solo números, sin texto.
- Los bancos los leen. La app de release no los escribe en logcat con contenido.
