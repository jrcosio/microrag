# Wireframe: Chat con páginas, modo «Multimodal» opcional (F20)

**Estado:** aprobado por el usuario el 2026-11-05, antes de `/speckit-plan` (criterio 1 de F20, constitución VII, G47).
**Fecha:** 2026-11-05
**Spec:** [spec.md](spec.md) (FR-001 a FR-024).

Las páginas en el chat, el detalle de las páginas y el visor no tienen PNG. Todo se compone con piezas del design system `Lisdata*` y sus tokens:
- **Sin cambios:** `LisdataSettingsRow`, `LisdataRadioRow`, `LisdataCardGroup`, `LisdataGradientButton`, `LisdataAnswerCard`, `LisdataCountChip`, `LisdataCard` y `LisdataTopBar`.
- **Una pieza que cambia:** `LisdataSourceCitation` gana un hueco opcional para una miniatura en lugar del icono de documento. Sin miniatura, se ve como hoy.
- **Una pieza nueva:** el visor de página a pantalla completa (§6), que hoy no existe. Usa los tokens, una barra superior con «Cerrar» y nada más.
- **La miniatura de §4** va como pieza del design system, `LisdataPageThumbnail` (analyze C4).

Los textos van entre «» en español; la versión inglesa está al final. Los dibujos son de un teléfono en vertical de unos 390 dp de ancho, sin escala.

## 1. Ajustes, sección «Inteligencia artificial» (FR-011)

Con el modo activo, debajo de «Modo al importar PDF» (F19). Con el modo apagado no se ve.

```
│ ┌─────────────────────────────────┐ │
│ │ Modo multimodal            [■■] │ │ ← como en F19
│ │ Lee también páginas e imágenes. │ │
│ ├─────────────────────────────────┤ │
│ │ Modo al importar PDF            │ │ ← como en F19
│ │ Ambos (texto y páginas)       › │ │
│ ├─────────────────────────────────┤ │
│ │ Detalle de las páginas          │ │ ← LisdataSettingsRow + chevron, nuevo
│ │ Equilibrado · unos 11,5 s     › │ │   abre la pantalla §2
│ └─────────────────────────────────┘ │
```

## 2. «Detalle de las páginas» (FR-011, FR-012)

Una pantalla propia, como «Modo al importar PDF»: `LisdataRadioRow` y «Guardar». Equilibrado viene por defecto.

```
┌─────────────────────────────────────┐
│ ‹  Detalle de las páginas           │
├─────────────────────────────────────┤
│ Cuánto detalle lee Gemma en la      │
│ página que entra en cada respuesta. │ ← texto de introducción
│ ┌─────────────────────────────────┐ │
│ │ ( ) Rápido                      │ │
│ │     Menos detalle.              │ │
│ │     Unos 10 s por respuesta.    │ │
│ ├─────────────────────────────────┤ │
│ │ (●) Equilibrado                 │ │ ← por defecto
│ │     El detalle con que se       │ │
│ │     indexan las páginas.        │ │
│ │     Unos 11,5 s por respuesta.  │ │
│ ├─────────────────────────────────┤ │
│ │ ( ) Detallado                   │ │
│ │     Para letra pequeña y        │ │
│ │     tablas densas.              │ │
│ │     Unos 14,5 s por respuesta.  │ │
│ └─────────────────────────────────┘ │
│ Tiempos medidos en el teléfono de   │
│ referencia. Las respuestas sin      │
│ páginas no cambian.                 │ ← nota
│ [         Guardar          ]        │ ← LisdataGradientButton
└─────────────────────────────────────┘
```

- Las esperas son las que mida F20 en el teléfono. Si difieren de las de F18, se cambian los textos.
- Si guardar falla: el aviso de siempre de las preferencias, y se mantiene el valor anterior.

## 3. Chat: esperando una respuesta con página (FR-013)

La línea de estado de la tarjeta de respuesta (`LisdataAnswerCard`, la de hoy) tiene un paso más cuando entra una página:

```
│ ┌─────────────────────────────────┐ │
│ │ ✦ LISDATA                       │ │
│ │ Buscando en documentos…         │ │ ← como hoy
│ └─────────────────────────────────┘ │
                 ↓
│ ┌─────────────────────────────────┐ │
│ │ ✦ LISDATA                       │ │
│ │ Leyendo 1 página · unos 11,5 s… │ │ ← nuevo; con la espera del detalle
│ └─────────────────────────────────┘ │
                 ↓
│   el texto llega como hoy           │
```

- Sin página, el estado es «Generando respuesta…», como hoy.
- TalkBack anuncia el cambio de estado, como hoy.
- «Cancelar» funciona igual.

## 4. «Fuentes consultadas» con páginas (FR-014, FR-015, FR-020)

Las fuentes de texto se ven como hoy. Una página usa la misma tarjeta, con su miniatura en lugar del icono de documento:

```
│ FUENTES CONSULTADAS                 │
│ ┌─────────────────────────────────┐ │
│ │ ▤  Manual de procesos.pdf     › │ │ ← fuente de texto, como hoy
│ │    Página 12 · Fragmento 86     │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ ┌──┐ informe-ine.pdf          › │ │ ← fuente de página
│ │ │▒▒│ Página 4 · Imagen de la    │ │   miniatura de 40 × 56 dp, esquinas
│ │ └──┘ página                     │ │   redondeadas, borde fino
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ ┌──┐ cartel.jpg               › │ │ ← imagen suelta: sin número de página
│ │ │▒▒│ Imagen                     │ │
│ │ └──┘                            │ │
│ └─────────────────────────────────┘ │
```

- **Orden:** el de las citas, mezclando fuentes de texto y de página, como hoy (D-053). El contador «N fuentes» las cuenta todas.
- **Las marcas `[n]` del texto** de una página se pulsan igual que las de texto y llevan al mismo sitio que su tarjeta.
- **Documento borrado:** la miniatura se sustituye por el icono en gris y dice «Fuente ya no disponible», sin chevron y sin poder pulsarse, como hoy.
- **TalkBack:** «Fuente 2: informe-ine.pdf, página 4, imagen de la página. Toca dos veces para abrir».
- **«Copiar respuesta»:** la línea de una página es «[2] informe-ine.pdf, página 4».

## 5. Detalle del documento, abierto desde una cita a página (FR-016, FR-018)

En «Vista previa del contenido», la página citada sustituye al fragmento resaltado:

```
│ VISTA PREVIA DEL CONTENIDO          │
│ ┌─────────────────────────────────┐ │
│ │ [Página 4]                      │ │ ← LisdataCountChip, como hoy
│ │ ┌─────────────────────────────┐ │ │
│ │ │                             │ │ │
│ │ │   imagen de la página a lo  │ │ │ ← a lo ancho de la tarjeta,
│ │ │   ancho, con su proporción  │ │ │   con su proporción
│ │ │                             │ │ │
│ │ └─────────────────────────────┘ │ │
│ │ ⤢ Toca para ampliar             │ │ ← pista, texto secundario
│ └─────────────────────────────────┘ │
```

- **Toda la tarjeta** es un objetivo táctil que abre el visor de §6.
- **TalkBack:** «Página 4 de informe-ine.pdf. Toca dos veces para ampliar».
- **Al abrirse,** la tarjeta se desplaza a la vista una vez, como el fragmento citado de hoy.
- **El documento existe pero la página no** (reindexado en Texto): se abre el detalle normal con el aviso «La página citada ya no está en este documento.», como pasa hoy con un fragmento.

## 6. Visor de página a pantalla completa (FR-016)

La pieza nueva. Ocupa toda la pantalla sobre el detalle:

```
┌─────────────────────────────────────┐
│ ✕  informe-ine.pdf · Página 4       │ ← barra superior: «Cerrar» (48 dp) y título
├─────────────────────────────────────┤
│                                     │
│                                     │
│     imagen de la página, ajustada   │ ← pellizco para ampliar (hasta 4×),
│     a la pantalla                   │   doble toque para 2× o volver a 1×,
│                                     │   arrastrar para moverse ampliada
│                                     │
│                                     │
└─────────────────────────────────────┘
```

- **Fondo:** el `background` del tema, claro u oscuro según el tema.
- **«Atrás»** del sistema y «✕» cierran el visor y vuelven al detalle.
- **TalkBack:** la imagen se anuncia como «Página 4 de informe-ine.pdf», con las acciones «Ampliar», «Reducir» y «Restablecer», porque el pellizco no está al alcance de TalkBack.
- **Al girar** el teléfono, se conserva la página, pero no el zoom.

## 7. Detalle del documento: notas de F19 que cambian

Con F20, un documento solo con páginas ya se usa en el chat cuando el modo está activo:

| Caso | F19 | F20 |
|---|---|---|
| Modo activo, solo páginas: nota bajo los datos | «Solo páginas. Todavía no se usa en el chat» | Sin nota: el modo «Visual» y las páginas ya se ven en los datos |
| Modo activo, solo páginas: subtítulo de «Incluir en respuestas» | «Se usará cuando el chat admita páginas» | «Disponible para consultas RAG», como un documento de texto |
| Modo apagado, con páginas: nota | «Sus páginas necesitan el modo multimodal» | Igual |
| Modo apagado, solo páginas: subtítulo de «Incluir en respuestas» | «Se usará cuando el chat admita páginas» | «Se usará al activar el modo multimodal» |

## 8. Diagnóstico de búsqueda (solo en debug, FR-022)

No es UI de producto. El diagnóstico de hoy gana, por este orden:
1. **Vectores:** puesto, fragmento y distancia.
2. **Palabras:** puesto y fragmento.
3. **Fusionada:** la tabla de hoy.
4. **Páginas:** puesto, documento, página y distancia. Solo con el modo activo.
5. **Contexto:** los bloques elegidos y la página que entra, con su detalle.

Muestra identificadores y cifras, nunca textos ni imágenes, como hoy.

## 9. Textos en inglés

| Español | English |
|---|---|
| Detalle de las páginas | Page detail |
| Cuánto detalle lee Gemma en la página que entra en cada respuesta. | How much detail Gemma reads in the page that goes into each answer. |
| Rápido / Equilibrado / Detallado | Fast / Balanced / Detailed |
| Menos detalle. | Less detail. |
| El detalle con que se indexan las páginas. | The detail pages are indexed with. |
| Para letra pequeña y tablas densas. | For small print and dense tables. |
| Unos 10 s por respuesta. | About 10 s per answer. |
| Equilibrado · unos 11,5 s | Balanced · about 11.5 s |
| Tiempos medidos en el teléfono de referencia. Las respuestas sin páginas no cambian. | Times measured on the reference phone. Answers without pages don't change. |
| Leyendo 1 página · unos 11,5 s… | Reading 1 page · about 11.5 s… |
| Imagen de la página | Page image |
| Imagen | Image |
| Fuente 2: informe-ine.pdf, página 4, imagen de la página | Source 2: informe-ine.pdf, page 4, page image |
| Toca para ampliar | Tap to zoom |
| Página 4 de informe-ine.pdf | Page 4 of informe-ine.pdf |
| Cerrar | Close |
| Ampliar / Reducir / Restablecer | Zoom in / Zoom out / Reset |
| La página citada ya no está en este documento. | The cited page is no longer in this document. |
| Se usará al activar el modo multimodal | It will be used once multimodal mode is on |

## 10. Anexo del analyze: imágenes sueltas, carga y anuncio

Aprobado por el usuario el 2026-11-05, en la revisión del analyze. Completa §3–§6 con lo que el wireframe no definía (analyze U1, U3 y C3).

**Imagen suelta (JPG o PNG):** no tiene número de página, así que nunca se dice «Página 1».

| Sitio | PDF (como en §4–§6) | Imagen suelta |
|---|---|---|
| Tarjeta, segunda línea | «Página 4 · Imagen de la página» | «Imagen» |
| Tarjeta, TalkBack | «Fuente 2: informe-ine.pdf, página 4, imagen de la página» | «Fuente 2: cartel.jpg, imagen» |
| Vista previa, chip | «Página 4» | «Imagen» |
| Vista previa y visor, TalkBack | «Página 4 de informe-ine.pdf» | «Imagen cartel.jpg» |
| Visor, título | «informe-ine.pdf · Página 4» | «cartel.jpg» |
| Copiar respuesta | «[2] informe-ine.pdf, página 4» | «[2] cartel.jpg» |

**Cargando la imagen:**

```
│ ┌─────────────────────────────────┐ │
│ │ [Página 4]                      │ │
│ │ ┌─────────────────────────────┐ │ │
│ │ │                             │ │ │ ← hueco de 200 dp de alto,
│ │ │            ◌                │ │ │   con LisdataProgressRing indeterminado
│ │ │                             │ │ │
│ │ └─────────────────────────────┘ │ │
│ └─────────────────────────────────┘ │
```

- En el visor: el mismo anillo, centrado, sobre el fondo del tema.
- En la miniatura: el icono de documento de hoy, hasta que llega la imagen (§4).
- Si la imagen no se puede leer: el aviso de §5 en la vista previa, y el de U15 en el visor.

**Anuncio de TalkBack:** cuando la respuesta empieza a leer la página, TalkBack anuncia «Leyendo 1 página», sin la espera, en lugar de «Generando respuesta».

| Español | English |
|---|---|
| Fuente 2: cartel.jpg, imagen | Source 2: cartel.jpg, image |
| Imagen cartel.jpg | Image cartel.jpg |
| Leyendo 1 página | Reading 1 page |
