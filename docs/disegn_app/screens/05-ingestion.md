# 05 — Ingesta / Importar documentos

## Objetivo
Añadir archivos locales a LISDATA y explicar de forma transparente cómo pasan a estar disponibles para consultas.

## Ruta
`ingest`. Desde Home o Biblioteca. Pantalla secundaria sin barra inferior.

## Composición
- Top bar: `Importar documentos` con Atrás.
- Eyebrow: `INGESTA LOCAL`.
- H1: `Amplía tu biblioteca`.
- Zona de importación con borde discontinuo, icono de subida y CTA `Seleccionar archivos`.
- Formatos MVP: PDF con texto extraíble, TXT y Markdown; otros formatos solo si están implementados.
- Sección `Cola de importación`: filas con nombre, tamaño, estado y acción contextual.
- Chips de etapas: `Extraer → Fragmentar → Embeddings → Indexar`.
- CTA `Ver estado de indexación`.

## Implementación Android
Usar Activity Result API y Storage Access Framework (`OpenMultipleDocuments` o equivalente). Solicitar acceso solo a archivos elegidos. Copiar o persistir permisos URI según estrategia definida; no asumir acceso perpetuo. Validar tamaño, tipo, integridad y duplicados mediante hash.

Pipeline local: lectura → extracción de texto → detección de páginas cuando aplique → fragmentación → embeddings → persistencia e índice. Los PDFs escaneados sin OCR deben detectarse y comunicar que no contienen texto extraíble; OCR queda fuera del MVP salvo implementación explícita.

## Estados
`Seleccionado`, `En cola`, `Extrayendo`, `Fragmentando`, `Generando embeddings`, `Indexando`, `Listo`, `Error`, `Cancelado`. Mostrar progreso real si existe total fiable; de otro modo, indeterminado.

## Errores
Archivo cifrado, corrupto, demasiado grande, formato no admitido, sin texto, almacenamiento insuficiente, acceso URI revocado. Ofrecer `Reintentar`, `Quitar de la cola` o `Ver detalle`.

## Criterios de aceptación
- [ ] Selección múltiple de archivos locales.
- [ ] Ningún archivo se sube a la nube.
- [ ] Cola observable y recuperable tras recreación de Activity.
- [ ] Errores por archivo, sin bloquear el resto.
- [ ] CTA de seguimiento abre `index`.
