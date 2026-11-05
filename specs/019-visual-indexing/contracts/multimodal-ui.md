# Contrato: pantallas del modo multimodal (F19)

**Base:** el [wireframe aprobado](../wireframe.md), §1–§6. Todo se construye con piezas `Lisdata*` que ya existen; no hay ninguna nueva.

## 1. Ajustes (`feature/settings`)

| Elemento | Regla |
|---|---|
| Filas de modelos | **U1:** Gemma y el embedder de texto activo (`RagIndex.textEmbedder`): la 270M o la 440M |
| `LisdataSwitchRow` «Modo multimodal» | **U2:** en «Inteligencia artificial», bajo los modelos.<br>• Encendido en `ON` y durante la activación; en esta, no se puede tocar.<br>• El subtítulo cambia con la etapa (wireframe §2): «Descargando el modelo · 45 %», «Descarga en pausa», «Comprobando el modelo…», «Cambiando el modelo de búsqueda…» o el error.<br>• Debajo, los botones de la etapa: Pausar o Reanudar, Cancelar, Reintentar |
| Diálogo de activar | **U3:** `LisdataConfirmationDialog`, no destructivo, con los cuatro puntos del wireframe |
| Diálogo de desactivar | **U4:** el texto del wireframe §3 |
| Fila «Modo al importar PDF» | **U5:** solo en `ON`. Abre `ImportModeRoute`, y su subtítulo es el modo guardado |
| `ImportModeRoute` | **U6:** como Tema (F16): tres `LisdataRadioRow` y «Guardar». Si guardar falla, `PreferencesSaveFailure` |
| Aviso | **U7:** al llegar a `ON`, «Modo multimodal activado», una sola vez |

## 2. Ingesta (`feature/ingest`)

| Elemento | Regla |
|---|---|
| Fila «Modo de los PDF» | **U8:** solo en `ON`, bajo «Carpeta de destino». Abre una hoja con las tres `LisdataRadioRow`. El modo de la selección empieza siendo el de Ajustes y se guarda en `SavedStateHandle` |
| Selector de archivos | **U9:** con `ON`, los tipos MIME admitidos incluyen `image/jpeg` e `image/png` |
| Fila de la cola | **U10:**<br>• un `LisdataStatusChip` con el modo;<br>• la estimación de páginas, tiempo y espacio en cuanto se conoce el número de páginas (wireframe §8);<br>• `IMAGE_UNREADABLE` dice «Imagen no válida» y «No se ha podido leer la imagen»;<br>• en `PAGES`, «Indexando páginas · 12 / 40» con progreso y el tiempo restante;<br>• `IMAGE_NEEDS_MULTIMODAL` dice «Imagen no admitida» y «Activa el modo multimodal en Ajustes para importar imágenes» |

## 2b. Indexación y notificación (`feature/indexing`, `data/indexing`)

| Elemento | Regla |
|---|---|
| Etapa «Imágenes de página» | **U15:** tras «Generación de embeddings», solo si el documento en curso tiene páginas. Durante ella, el anillo y el recuento son del documento en curso, en páginas (wireframe §8) |
| Pausas nuevas | **U16:** «En pausa: cambiando el modelo de búsqueda» y «Esperando al modo multimodal», con el bloque de pausa de hoy |
| Notificación | **U17:** «Indexando páginas · 12 / 40», con su barra, en la etapa de páginas |

## 3. Biblioteca y detalle (`feature/library`, `feature/document`)

| Elemento | Regla |
|---|---|
| Subtítulo de la fila | **U11:** tipo · modo · páginas (solo en Visual y Ambos) · fragmentos (solo en Texto y Ambos) |
| Datos del detalle | **U12:**<br>• `LisdataFactRow` «Modo de indexación» y «Páginas»;<br>• si es solo Visual, la nota «Solo páginas. Todavía no se usa en el chat»;<br>• con el modo apagado y páginas guardadas, «Sus páginas necesitan el modo multimodal» |
| «Reindexar documento» | **U13:** con `ON`, abre primero la hoja de modo; sin `ON`, se reindexa en `TEXT` |
| «Reindexar como páginas» | **U14:** solo con `ON` y un error `NO_TEXT`. Reindexa en `VISUAL` |

## 4. Accesibilidad y textos

- **TalkBack:**
  - el interruptor anuncia su estado y su etapa;
  - las filas de la Biblioteca leen el modo y las páginas;
  - las hojas son radio groups.
- **Con la letra al 200 %,** nada se corta: lo comprueban las pruebas con `fontScale = 2f`, como en F16 y F17.
- **Textos** en `res/values/strings_multimodal.xml` y en `values-en`, con plurales para páginas y fragmentos. `StringsParityTest` los vigila.
- **Etiquetas de prueba:** `multimodal_switch`, `multimodal_status`, `import_mode_row`, `import_mode_sheet`, `ingest_mode_row` y `document_index_mode`.
