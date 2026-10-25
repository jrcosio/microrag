# 07 — Detalle de documento

## Objetivo
Dar trazabilidad sobre un archivo y permitir acciones de mantenimiento seguras.

## Ruta
`document/{documentId}`. Entrada desde Biblioteca o desde una cita del Chat. Atrás vuelve al origen.

## Composición
- Top bar: `Detalle del documento`.
- Tarjeta hero azul claro: icono del tipo, nombre completo y chip de estado.
- Cuadrícula de metadatos: páginas si aplica, fragmentos, tipo, tamaño, fecha de importación y ubicación local.
- Switch `Incluir en respuestas`, activo solo cuando el índice está listo.
- `Vista previa del contenido`: extracto real de texto con página y, si procede, identificador de fragmento.
- Acciones: `Reindexar`, `Quitar del índice` y `Eliminar documento`.

## Interacciones
- `Reindexar`: encola trabajo de regeneración; confirmar si implica coste importante. Mantener índice anterior hasta sustituirlo de forma segura cuando sea viable.
- `Quitar del índice`: elimina vectores y fragmentos indexados según contrato, conserva original si se eligió esa política.
- `Eliminar documento`: abrir diálogo destructivo explicando exactamente si se elimina archivo privado, texto, vectores, metadatos y referencias.
- Switch de inclusión: modifica el conjunto de fuentes consultables, sin borrar datos.
- Si se abre desde cita, resaltar la página o fragmento correspondiente.

## Estados
Documento no encontrado o eliminado, indexación en curso, error, contenido sin previsualización, permiso de archivo perdido. Nunca mostrar una página ficticia cuando no existe.

## Criterios de aceptación
- [ ] Metadatos reales.
- [ ] Cita → detalle → fragmento correcto.
- [ ] Eliminación requiere confirmación.
- [ ] Acciones actualizan Biblioteca e Índice.
- [ ] Estado de documento desaparecido se resuelve sin crash.
