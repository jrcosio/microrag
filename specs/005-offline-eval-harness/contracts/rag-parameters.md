# Contrato — Parámetros RAG compartidos (`offline/config/rag_parameters.json`)

Es la fuente única de los parámetros que decide F05 (constitución III). La leen el pipeline Python y una prueba de la app.

```json
{
  "decided_in": "F05",
  "decisions": ["D-0xx"],
  "dims": 256,
  "contextual_header": true,
  "normalizer_version": 1,
  "chunk_target_chars": 1580,
  "chunk_max_chars": 2150,
  "chars_per_token": 4.5,
  "candidates": 50,
  "rrf_k": 60,
  "follow_up_rule": true,
  "max_blocks": 6,
  "max_per_document": 3,
  "budget_tokens": 1750,
  "expand_top": 2,
  "llm_model": "GEMMA4_E2B",
  "max_output_tokens": 512
}
```

Los valores del ejemplo son orientativos (con `budget_tokens` ajustado a 2.000 tokens de prompt, research R6); los definitivos salen del informe de evaluación.

## Reglas

| # | Regla |
|---|---|
| P1 | Cada valor tiene su entrada en DECISIONS con la cifra que lo justifica (SC-007) |
| P2 | `domain/rag/RagParameters.kt` declara las mismas constantes. `RagParametersTest` (JVM) lee el JSON y exige igualdad campo a campo; también comprueba que `dims == EmbeddingMath.DIMENSIONS` y que `normalizer_version == SpanishText.VERSION` |
| P3 | Si `dims` ≠ 256, en la misma entrega: se cambia `EmbeddingMath.DIMENSIONS`, se regenera `embedding_golden.jsonl`, se vuelve a ejecutar `EmbeddingGoldenTest` en el teléfono y se actualiza D-025/D-030 |
| P4 | Si `contextual_header` es `false`, `EmbeddingPrefixes.document` deja de recibir cabecera desde la ingesta (F07), y se documenta |
| P5 | `llm_model` es un `ModelId` del catálogo. Si fuera E4B, el catálogo y el onboarding (F03) cambian en una feature aparte, previa aprobación |
