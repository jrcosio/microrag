# 04 — Chat / Conversación con documentos

## Objetivo
Permitir preguntas en lenguaje natural sobre documentos locales, mostrando siempre la procedencia verificable de la información recuperada.

## Ruta
`chat/{conversationId?}`. Tab Chat. Desde una cita → `document/{documentId}` conservando la conversación.

## Layout
- Top bar: `Consulta de documentos`, subtítulo `Gemma 4 · RAG local`, indicador `Local`.
- Zona central scrollable con mensajes de usuario y asistente.
- Mensajes del usuario: burbuja azul muy clara alineada a la derecha.
- Mensajes de LISDATA: tarjeta blanca alineada a la izquierda, con cuerpo legible.
- Debajo de cada respuesta fundamentada: sección `Fuentes consultadas`, con filas que muestran archivo y página cuando exista.
- Compositor fijo sobre teclado y barra inferior: campo de texto, enviar, cancelar generación si procede.

## Comportamiento
1. Usuario escribe pregunta y pulsa enviar.
2. Estado `Buscando en documentos...`.
3. Recuperar fragmentos locales según documentos incluidos.
4. Estado `Generando respuesta...`; mostrar streaming si el runtime lo permite.
5. Adjuntar solo citas realmente utilizadas y trazables a `documentId`, `chunkId` y página opcional.
6. Al pulsar una cita, abrir ficha y ubicación del fragmento.
7. Permitir cancelar generación y conservar lo que el contrato de producto determine.

## Casos especiales
- Biblioteca vacía: ilustración simple, explicación y CTA `Importar documentos`.
- Sin evidencia suficiente: respuesta explícita `No encuentro información suficiente en tus documentos`; no inventar citas.
- Modelo ausente: CTA a Ajustes/Preparación.
- Error de inferencia: mensaje con `Reintentar`.
- Consulta durante indexación: usar únicamente fragmentos listos; indicarlo si afecta a cobertura.
- Respuesta larga: scroll y posibilidad de copiar texto.
- Si una fuente fue borrada: mostrar `Fuente ya no disponible`, sin abrir contenido inexistente.

## Privacidad
Historial local; sin llamadas externas. No guardar prompts en logs de depuración.

## Criterios de aceptación
- [ ] Pregunta, recuperación, generación y cancelación tienen estados distintos.
- [ ] Citas abren documentos reales.
- [ ] Ninguna cita se genera de forma ficticia.
- [ ] Teclado no oculta compositor.
- [ ] El usuario puede volver al chat desde la fuente.
