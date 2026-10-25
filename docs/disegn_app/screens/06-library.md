# 06 — Biblioteca / Control de documentos

## Objetivo
Permitir encontrar documentos, conocer su estado y controlar qué fuentes participan en el RAG.

## Ruta
`library`. Tab Biblioteca seleccionada. Importar → `ingest`. Abrir fila → `document/{documentId}`.

## Layout
- Cabecera: eyebrow `BIBLIOTECA LOCAL`, título `Documentos`, icono de importación.
- Descripción: `Busca, incluye o excluye fuentes de las consultas.`
- Buscador por nombre.
- Filtros: `Todos`, `Indexados`, `Pendientes`; ampliar con `Errores` si es necesario.
- Lista vertical de filas con icono del tipo, nombre truncado, fragmentos o estado, switch de inclusión y botón de detalle.
- Mensaje inferior que explica que excluir no equivale a borrar.

## Comportamiento
- Búsqueda y filtros combinables, con estado preservado al volver de detalle.
- Switch de inclusión: persistir cambio; afecta a futuras recuperaciones, no a mensajes históricos.
- No permitir activar un documento aún no indexado.
- Pulsar nombre o chevron abre detalle.
- Botón de importación abre SAF a través de pantalla de ingesta.
- En corpus grandes, paginar documentos y usar consultas SQL filtradas; no cargar todos los fragmentos.

## Estados
- Vacío: `Aún no tienes documentos` y CTA `Importar documentos`.
- Sin resultados: `No hay coincidencias` y `Limpiar filtros`.
- Cargando: skeleton de filas.
- Error: mensaje y `Reintentar`.
- Documento en error: etiqueta `Error de indexación` y acceso a detalle.
- Documento excluido: estado visible y accesible.

## Criterios de aceptación
- [ ] Filtrado y búsqueda funcionales.
- [ ] Switch refleja el estado persistido.
- [ ] Excluir no borra contenido.
- [ ] Lista fluida con biblioteca grande.
- [ ] TalkBack anuncia nombre, estado y si está incluido.
