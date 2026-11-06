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
- **El generador** recibe `GenerationPrompt.Split(prefix, rest)`. Sin `prefixCache`, envía `whole` como hoy.

## G3. `LiteRtTextGenerator` con `prefixCache`

- **Camino 1 (sin cambiar el prompt, R6):**
  1. En `initialize()`, o en el primer uso con un prefijo nuevo, abre una `Session` y hace el prefill de `<inicio del turno de usuario> + prefix`, con los tokens de la plantilla de Gemma 4 leídos del fichero.
  2. Por respuesta: `base.clone()`, prefill de `rest + <fin del turno de usuario> + <inicio del turno del modelo>` y decodificación en streaming.
  3. Cierra el clon al terminar o al cancelar.
  4. La base se rehace si cambia el prefijo (otra plantilla o idioma) y se libera con el motor.
- **Camino 2 (si el 1 no es posible con la API):** `Conversation` base con `systemInstruction = prefix` y `prefillPrefaceOnInit = true`, con `clone()` por respuesta. Cambia el prompt: solo se adopta con la regla de calidad de D-088.
- **`ExperimentalFlags.enableBenchmark`:** solo en las pruebas. `BenchmarkInfo` da los tokens del prefill.
- **Prueba `PrefixCacheGoldenTest`** (teléfono, greedy): las respuestas de las goldens salen idénticas con y sin `prefixCache`, para el camino 1.

## G4. Especulativa

- `EngineConfig(speculativeDecodingConfig = SpeculativeDecodingConfig(ADAPTIVE))` cuando `speculative == ADAPTIVE`. `ConversationConfig` deja `enableSpeculativeDecoding` (obsoleto) y usa `SpeculativeDecodingConfig(OFF)` por defecto.
- **Comprobación previa:** `ModelInfo` dice si el fichero tiene drafter (`hasSpeculativeDecodingSupport`). Si no lo tiene, el banco registra «no disponible».

## G5. Precisión y plantilla de cifras

- **FP32:** `ACTIVATION_PRECISION = FLOAT32` solo para el motor de texto. El de visión se mide aparte y conserva la del fichero salvo decisión.
- **`v4-cifras`:** plantilla nueva en `EXTRA_RULES`. La regla de copiar cifras pasa al final, junto a la PREGUNTA, con la redacción fijada en `offline/eval/` (paridad con la golden del prompt).

## G6. Estimador de tokens (R9)

```kotlin
fun estimatedTokens(text: String): Int  // ⌈letras/a + dígitos/b + resto/c⌉; con a = b = c = 4,554 es el de hoy
```

- **Clases:** letras (`Character.isLetter`), dígitos (`isDigit`) y resto (puntuación, espacios y símbolos), contadas por puntos de código.
- **Python:** el mismo cálculo en `offline/eval/token_estimate.py`, con una prueba de paridad sobre textos fijos (incluidos los acentos, la ñ y las cifras con separadores).

## G7. Precarga por ámbito (R10)

- **Disparador:** `ChatViewModel`, cuando cambia el ámbito de la conversación y el chat está en pantalla y listo. Pide `warmUp(visual = modo activo && ámbito con páginas)`.
- **Ámbito con páginas:** se consulta con una función nueva de `RagIndex` que cuenta las páginas buscables del ámbito (`COUNT` sobre `pages` con el filtro de ámbito).
- **Coordinador:** el `EngineCoordinator` libera el motor que no se pide antes de cargar el otro (nunca dos en GPU).
- **Interruptor:** `RagParameters.PREWARM_BY_SCOPE`.

## G8. Regla de seguimiento con páginas (R10)

- **`PAGE_FOLLOW_UP`:**
  - `JOINED`: hoy;
  - `NEW_ONLY`: las páginas se buscan con la pregunta nueva;
  - `BEST_OF_BOTH`: se busca con las dos y gana la página de mayor similitud.
- **Dónde:** `HybridRetriever.search`. Con `NEW_ONLY` o `BEST_OF_BOTH`, y solo cuando `FollowUpRule` ha unido la consulta, embebe además la pregunta sola.
- **Pruebas JVM:** `HybridRetrieverTest`, con fakes para cada variante.

## G9. Desglose de tiempos (FR-015)

- `DefaultChatSession` registra en su `log` (ya tiene `ttftMs`) los tiempos de embedding, búsqueda, construcción del prompt y hasta el primer token. Son solo números, sin texto.
- Los bancos los leen. La app de release no los escribe en logcat con contenido.
