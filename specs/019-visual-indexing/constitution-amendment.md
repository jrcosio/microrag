# Propuesta de enmienda de la constitución: 1.1.1 → 1.2.0 (F19, D-078)

**Estado:** aprobada por el usuario el 2026-11-05 y aplicada en `.specify/memory/constitution.md` (v1.2.0, D-079).

**Tipo:** MINOR. Amplía la guía de III, IV y las Restricciones con el modo multimodal opcional; no quita ni redefine principios. Lo que hoy rige sigue rigiendo con el modo apagado.

## III. RAG según el informe técnico

**Almacenamiento:** se añade:
- El espacio de páginas también se registra en `rag_meta`:
  - el modelo y su huella;
  - la dimensión de los vectores de página;
  - los tokens por imagen;
  - la resolución y el formato de las imágenes.

  Si no coincide con la configuración de la app, **no se buscan páginas**, pero el texto sigue funcionando.

**Embeddings:** se añade:
- **Modo multimodal (opcional, D-078):**
  - el embedder del texto y de las imágenes es EmbeddingGemma 2 de 440M;
  - un índice creado con la 270M pasa a la 440M **sin reindexar**, solo si el dispositivo comprueba antes que la 440M reproduce sus vectores de texto sobre una muestra fija, con un coseno mínimo ≥ 0,9999;
  - entonces `embedding_model_sha256` se actualiza en la misma transacción en que se da por válido;
  - si la comprobación falla, el índice se queda con la 270M.
- Los vectores de página se truncan a **256D** y se re-normalizan con **L2**, igual que los de texto.

**Respuesta:** «Toda cita mostrada MUST ser trazable a un `documentId` y un `chunkId` reales» pasa a:
- Toda cita mostrada MUST ser trazable a un `documentId` real y a un `chunkId` real o a una página real de ese documento (número de página e imagen indexada).
- **Nunca** se fabrican citas, páginas ni nombres de archivo.

## IV. Rendimiento y recursos

Se añade a los presupuestos:
- **Respuestas con páginas** (modo multimodal):
  - tienen un primer token propio, que se mide en el teléfono de referencia con cada nivel de detalle;
  - se avisa al usuario antes de activar el modo y al elegir el detalle;
  - no están sujetas a los 5 s del texto. En el A063, F18 midió unos 10, 11,5 y 14,5 s con 1 página a 70, 140 y 280 tokens;
  - el codificador de visión corre en CPU, y la regla de «nunca dos engines en GPU» no cambia.
- **Indexación visual:** su ritmo, en páginas por minuto, y el espacio por página se miden e informan, pero no bloquean (D-078).

## Restricciones técnicas

- **Modelos → Embeddings:** EmbeddingGemma 2 de 270M de solo texto a 256D (D-025), por defecto. Con el modo multimodal, EmbeddingGemma 2 de 440M (`embeddinggemma-2-text-vision-440m.litertlm`), a 256D para texto y páginas y a 140 tokens por imagen (D-078).
- **Formatos del MVP:** con el modo multimodal se añaden los PDF escaneados, que se indexan como páginas, y las imágenes JPG y PNG. Con el modo apagado, los PDF sin texto se siguen detectando y avisando como hoy. El OCR sigue fuera del MVP.

## Sync Impact Report previsto

- **Versión:** 1.1.1 → 1.2.0.
- **Plantillas:** sin cambios. El *Constitution Check* lee el fichero en tiempo de ejecución.
- **Documentos:**
  - DECISIONS registra la enmienda en una entrada nueva, que cita D-078;
  - FEATURES ya describe F19–F20 con el modo opcional.
