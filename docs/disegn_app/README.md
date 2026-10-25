# LISDATA — Especificaciones UX/UI Android

Documentación preparada para que un agente de desarrollo implemente las pantallas de LISDATA en Kotlin + Jetpack Compose.

## Producto
LISDATA es una aplicación de RAG documental 100 % local. Utiliza Gemma 4 para generar respuestas y EmbeddingGemma 2 para representar semánticamente los fragmentos. El corpus objetivo es de 10.000 a 100.000 fragmentos. La selección definitiva del runtime y motor vectorial se trata como una decisión de ingeniería independiente de la UI.

## Árbol de archivos
- `CLAUDE.md` — instrucciones para agentes.
- `docs/00-design-system.md` — marca, color, tipografía, espaciado y accesibilidad.
- `docs/01-navigation.md` — rutas, tabs y flujos.
- `docs/02-architecture.md` — capas, estados y contratos.
- `docs/03-components.md` — biblioteca de componentes Compose.
- `docs/04-qa-checklist.md` — aceptación y pruebas.
- `screens/01-welcome.md` — bienvenida.
- `screens/02-onboarding.md` — preparación inicial.
- `screens/03-home.md` — inicio y opciones.
- `screens/04-chat.md` — chat RAG con fuentes.
- `screens/05-ingestion.md` — importación de documentos.
- `screens/06-library.md` — control de documentos.
- `screens/07-document-detail.md` — ficha documental.
- `screens/08-indexing.md` — estado del procesamiento.
- `screens/09-settings.md` — ajustes.
- `screens/10-languages.md` — idiomas.

## Referencias de marca aportadas
- Imagen: `Paleta LISDATA (códigos).png`.
- Imagen: `LisdataPrincipal.png`.
- PDF: `LISDATA - Kit Iconos.pdf`.
- PDF: `LISDATA - Kit Web.pdf`.

Estos archivos deben incorporarse al repositorio como referencias de diseño si se dispone de los originales. Los PDFs muestran iconografía Rounded Lineal y Basic Gradient, además de formas Tech/Driven. No asumir que incluyen licencias de uso de terceros.

## Orden de implementación
Fase 1: tema, navegación, componentes, pantallas con fakes.
Fase 2: importación SAF, biblioteca y estados persistentes.
Fase 3: pipeline de ingesta e indexación.
Fase 4: recuperación y chat con citas reales.
Fase 5: ajustes, idiomas, pruebas y optimización.

## Importante
Las cantidades, documentos y respuestas del prototipo son datos de ejemplo, nunca resultados reales. No trasladarlos como constantes de producción. El diseño debe funcionar con biblioteca vacía desde la primera instalación.
