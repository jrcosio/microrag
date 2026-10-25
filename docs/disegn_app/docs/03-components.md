# Catálogo de componentes Compose — LISDATA

## Regla general
Separar componentes presentacionales de componentes conectados a estado. Evitar duplicar tarjetas, chips, filas de documentos y diálogos en distintas pantallas.

## Componentes propuestos
### `LisdataTopBar`
Props: título, icono Atrás opcional, acción secundaria opcional. Debe respetar status bar y navegación accesible.

### `LisdataBottomBar`
Destinos Inicio, Chat, Biblioteca y Ajustes; indicador de selección; icono + etiqueta; sin mostrarla en flujos de primer uso ni detalles.

### `LisdataStatusChip`
Variantes: `local`, `ready`, `processing`, `queued`, `paused`, `error`. Debe combinar texto e icono, no solo color.

### `LisdataDocumentRow`
Props: nombre, tipo, tamaño/fragmentos, estado, incluido en búsqueda, callbacks abrir y alternar inclusión. La inclusión debe deshabilitarse cuando el documento aún no está indexado.

### `LisdataSourceCitation`
Props: nombre del documento, página opcional, extracto opcional, callback abrir fuente. No crear cita si no existe `documentId` válido.

### `LisdataProgressCard`
Props: etapa, completados, total opcional, porcentaje nullable, estado y acción. Si el total es desconocido, mostrar indicador indeterminado, nunca un porcentaje inventado.

### `LisdataEmptyState`
Icono, título, explicación, CTA opcional. Reutilizable para biblioteca vacía, chat sin documentos, cero resultados y cola vacía.

### `LisdataFilePickerCard`
Zona de importación que dispara el launcher SAF. En Android no prometer drag-and-drop si no se implementa.

### `LisdataModelStatusRow`
Modelo, propósito, versión/artefacto disponible, estado real, acción de importar/validar.

### `LisdataConfirmationDialog`
Título, descripción de consecuencias, botón cancelar y botón destructivo; accesible y con foco seguro.

## Estados compartidos
Crear componentes de loading/skeleton, error con reintento, snackbar, botón ocupado, etiqueta «Procesamiento local» y pantalla de modelo ausente.

## Previews requeridas
Para cada componente: tema claro, tema oscuro, textos largos y al menos un estado de error. Añadir previews de ancho compacto y tamaño de fuente aumentado.

## Accesibilidad
Usar `contentDescription` solo cuando el icono aporte significado no expresado por texto. Agrupar semántica de filas; etiquetas explícitas para switches; objetivos táctiles de 48 dp.

## Diseño
Usar tokens de `docs/00-design-system.md`. No hardcodear hex en pantallas ni introducir un segundo sistema de componentes.
