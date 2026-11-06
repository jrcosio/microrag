# Contrato: pantallas del chat con páginas (F20)

**Base:** el [wireframe aprobado](../wireframe.md), §1–§8.

**Piezas:**
- **una que cambia:** `LisdataSourceCitation` gana `leading`;
- **dos nuevas:** el visor, `PageViewerScreen`, y la miniatura, `LisdataPageThumbnail` (analyze C4);
- **el resto,** las que ya existen.

## 1. Ajustes (`feature/settings`)

| Elemento | Regla |
|---|---|
| Fila «Detalle de las páginas» | **U1:** solo en `ON`, debajo de «Modo al importar PDF». Su subtítulo es el detalle guardado con su espera: «Equilibrado · unos 8,5 s». Abre el destino `PageDetailSetting` |
| `PageDetailRoute` (destino `PageDetailSetting`) | **U2:** como «Modo al importar PDF»: la introducción, tres `LisdataRadioRow` con su descripción y su espera, la nota y «Guardar». Si guardar falla, `PreferencesSaveFailure`. Con el modo apagado, la ruta vuelve atrás |

## 2. Chat (`feature/chat`)

| Elemento | Regla |
|---|---|
| Estado de la respuesta | **U3:** `TurnStatus.READING_PAGE` muestra «Leyendo 1 página · unos %s s…» con la espera del detalle de la respuesta (R13). TalkBack anuncia «Leyendo 1 página» (`ChatAnnouncement.READING_PAGE`) en lugar de «Generando respuesta» |
| Tarjeta de una fuente de página | **U4:** `LisdataSourceCitation` con `leading = LisdataPageThumbnail` (40 × 56 dp, esquinas redondeadas y borde fino), el nombre y «Página p · Imagen de la página». Una imagen suelta dice «Imagen» (wireframe §10). Mientras carga, o si falla, sale el icono de documento |
| Fuente no disponible | **U5:** `DOCUMENT_GONE` es como hoy: el icono en gris, «Fuente ya no disponible», sin chevron y sin poder pulsarse. `PAGE_GONE` se puede pulsar (U9) |
| TalkBack de la tarjeta | **U6:** «Fuente n: Nombre, página p, imagen de la página», o «Fuente n: Nombre, imagen» para una imagen suelta, con la acción de abrir |
| Marcas `[n]` | **U7:** la de una página abre lo mismo que su tarjeta: `Document(documentId, page = p)` |
| Copiar respuesta | **U8:** la línea de una página es `chat_copy_source` («[n] Nombre, página p»). Una imagen suelta usa `chat_copy_source_no_page` |

## 3. Detalle del documento (`feature/document`)

| Elemento | Regla |
|---|---|
| Vista previa de una página | **U9:** con `page`, la tarjeta de «Vista previa del contenido» muestra `LisdataCountChip` «Página p» («Imagen» para una imagen suelta), la imagen a lo ancho con su proporción y la pista «Toca para ampliar». Mientras carga, un hueco de 200 dp con `LisdataProgressRing` indeterminado (wireframe §10). Toda la tarjeta se pulsa y abre `PageViewer`. Se desplaza a la vista una vez por apertura. Con `PAGE_GONE`, sale el aviso «La página citada ya no está en este documento.» y la vista previa de siempre |
| TalkBack | **U10:** «Página p de Nombre», o «Imagen Nombre», con la acción «Ampliar» |
| Notas de F19 | **U11:** wireframe §7, R16 |

## 4. Visor (`PageViewerScreen`)

| Elemento | Regla |
|---|---|
| Barra superior | **U12:** «✕» (48 dp, `contentDescription` «Cerrar») y el título «Nombre · Página p», o solo «Nombre» para una imagen suelta. «Atrás» también cierra |
| Imagen | **U13:**<br>• mientras carga, `LisdataProgressRing` indeterminado centrado;<br>• ajustada a la pantalla;<br>• pellizco hasta 4×, doble toque entre 1× y 2×, y arrastre con la imagen ampliada, sin salirse de sus bordes (`ZoomState`);<br>• al girar, se conserva la página y el zoom vuelve a 1× |
| Accesibilidad | **U14:**<br>• la imagen se lee como «Página p de Nombre»;<br>• tiene las `customActions` «Ampliar», «Reducir» y «Restablecer», con pasos de 1× entre 1× y 4×;<br>• los colores salen del tema, claro u oscuro |
| Página que falta | **U15:** si la imagen no se puede leer, muestra «La página citada ya no está en este documento.» y «Cerrar» |

## 5. Diagnóstico (solo debug)

**U16:** `RetrievalDiagnosticScreen`, wireframe §8 y R17.

## 6. Accesibilidad e idiomas

- **U17** (ATF, `VisualChatAccessibilityTest`): `PageDetailRoute`, la tarjeta con miniatura, la vista previa de página y el visor:
  - pasan ATF en claro y en oscuro;
  - tienen objetivos de 48 dp;
  - caben a lo ancho con la letra al 200 %.
- **U18:** todos los textos nuevos, en `values/strings_visual_chat.xml` y `values-en/strings_visual_chat.xml`, con paridad (`StringsParityTest`). Los números de las esperas se formatean con la configuración regional.
