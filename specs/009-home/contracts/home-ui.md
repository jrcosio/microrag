# Contrato: pantalla Inicio (F09)

## Inicio (`home`, PNG 03)

De arriba abajo, en una columna desplazable con el margen de pantalla (20 dp) y la barra inferior con Inicio seleccionado.

| # | Elemento | Componente | Español |
|---|---|---|---|
| 1 | Cabecera | Fila propia: `LisdataLogo` a la izquierda (G20) e `IconButton` 48 dp con `ic_settings` a la derecha | Etiqueta del icono: «Ajustes» |
| 2 | Estado de la IA | `LisdataStatusChip(Ready)`. Sin mockup: `LisdataStatusChip(Warning)` y `TextButton` (P1) | «IA local disponible» |
| 3 | Saludo | `Text` en `headlineLarge` | «¿Qué quieres explorar hoy?» |
| 4 | Héroe | `LisdataHeroCard` con eyebrow, título en dos líneas blancas, órbitas, `decorationIcon = ic_sparkle` y `LisdataGradientButton(trailingIcon = ic_arrow_right)` | «PREGUNTA A TU BIBLIOTECA», «Una respuesta.» / «Todas tus fuentes.», «Nuevo chat» |
| 5 | Tarjeta de indexación | Sin mockup: `LisdataProgressCard` tocable (P5), solo con tanda activa (D-046) | — |
| 6 | Accesos | 2×2 de `LisdataQuickActionTile`, filas a la misma altura | «Importar / Añadir archivos» (`ic_upload`), «Documentos / Ver biblioteca» (`ic_folder`), «Índice local / Estado del RAG» (`ic_database`), «Idiomas / Preferencias» (`ic_globe`) |
| 7 | Resumen | `HomeSummaryCard`: `LisdataCard` con `LisdataEyebrow` y dos cifras (R6) | «TU BIBLIOTECA», «24» «documentos», «12.400» «fragmentos» |

**Formatos:**
- cifras con el separador de miles del idioma (`grouped()`, como la Biblioteca);
- etiquetas con plural («1 documento», «24 documentos»);
- «12.400» en `brandBlue` y «24» en `textPrimary`, como el PNG.

**Diferencias con el PNG (anotadas):**
- «documentos» va debajo de su cifra, como «fragmentos», y no al lado (R6);
- las órbitas quedan dentro del héroe (el PNG las deja salir por arriba), como en el resto de héroes de F01.

## Propuestas para los estados sin mockup (se aprueban en la parada tras analyze)

- **P1 — IA no lista:** chip `Warning` «Configuración pendiente» y, a su derecha (o debajo si no cabe), `TextButton` «Completar configuración» → `Setup`. El resto de Inicio no cambia (D-046).
- **P2 — Comprobando:** sin chip, con su alto reservado.
- **P3 — Biblioteca vacía (0 documentos):** en lugar de las cifras, `LisdataEmptyState` dentro de la tarjeta del resumen, con:
  - `ic_folder`;
  - «Tu biblioteca está vacía»;
  - «Importa tus PDF, TXT o Markdown para empezar a preguntar.»;
  - `LisdataGradientButton` «Importar primer documento» → `Ingest`.
- **P4 — Resumen cargando o con error:**
  - **cargando:** la tarjeta con el eyebrow y `LisdataSkeletonRow` en lugar de las cifras;
  - **error:** `LisdataErrorState` en la tarjeta:
    - índice no disponible: «La biblioteca no está disponible», el texto de F08;
    - fallo de carga: «No se pudo cargar el resumen»;
    - en los dos casos, «Reintentar».
- **P5 — Tarjeta de indexación** (R3):
  - título: «Indexando documentos», o la causa de la pausa con los textos de F07 («En pausa», «Pausa por temperatura», «Esperando al sistema», «Sin espacio», «Falta el modelo de embeddings»);
  - barra de progreso real (determinada con total, indeterminada sin él);
  - detalle: «320 de 860 fragmentos», o «Preparando los documentos»;
  - toda la tarjeta abre `Index`, con la etiqueta «Ver estado de la indexación».

## Accesibilidad (FR-021, FR-022)

- **A1:** el chip anuncia «IA local disponible» o «Configuración pendiente»; el botón, «Completar configuración».
- **A2:** el resumen se lee como una sola frase: «24 documentos y 12.400 fragmentos» (`home_summary_description`). El eyebrow se lee antes como encabezado.
- **A3:** cada acceso es un botón con título y descripción («Importar, Añadir archivos»). El icono no se anuncia.
- **A4:** la tarjeta de indexación se lee como «Indexando documentos, 320 de 860 fragmentos» y su acción, «Ver estado de la indexación».
- **A5:** las órbitas y el destello del héroe no se anuncian. El logotipo se anuncia una vez («Logotipo de LISDATA», `cd_logo`, como en la Bienvenida).
- **A6:** objetivos de 48 dp (icono de Ajustes, botón de texto, accesos y tarjeta).
- **A7:** con la fuente al 150 % o más, los accesos pasan a una columna y las dos cifras del resumen se apilan. Con el 200 %, nada se corta ni se solapa. Contraste AA en claro y en oscuro.
- **A8:** en horizontal, todo se alcanza desplazando la pantalla, y la posición se conserva al girar.

## Navegación (R4)

| Desde Inicio | Destino | Atrás |
|---|---|---|
| «Nuevo chat» | `Chat(null)` como pestaña, conversación nueva | Inicio |
| Importar / «Importar primer documento» | `Ingest` | Inicio |
| Documentos | `Library` (pestaña) | Inicio |
| Índice local / tarjeta de indexación | `Index` | Inicio |
| Idiomas | `Languages` | Inicio |
| Icono de Ajustes | `Settings` (pestaña) | Inicio |
| «Completar configuración» | `Setup` | Inicio; al completar, vuelve a Inicio sin duplicarla |
| Atrás en Inicio | sale de la app (F03 FR-003) | — |
