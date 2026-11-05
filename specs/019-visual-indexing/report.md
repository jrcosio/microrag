# Informe de cierre — F19 Indexación visual (modo «Multimodal» opcional)

> **Estado:** borrador (T054). Falta la fase del teléfono (T055–T056), que espera el permiso del usuario.

**Rama:** `019-visual-indexing`, creada desde `af2b836`.
**Fecha:** 2026-11-05.
**Dispositivos:**
- emuladores `Pixel_10` (API 37) y `MicroRAG_API33` (API 33);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1), sin dependencias nuevas;
- esquema del índice `rag.db` v4 → **v5**; `chat.db` sin cambios;
- constitución 1.2.0 (D-078, aplicada en el clarify);
- lint avisa de las dos versiones más nuevas de siempre, LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0. No se actualizan en F19.

## 1. Qué se ha implementado

**Datos** (research R2, R8):
- **`rag.db` v5:**
  - `documents.index_mode` (`text`, `visual` o `both`, `text` por defecto);
  - tabla `pages`, con una imagen y un vector de 256D por página y `UNIQUE (document_id, page)`;
  - las claves `page_*` de `rag_meta`, que se escriben con la primera página y no deciden si el índice se abre.

  La migración 4 → 5 deja todos los documentos en Texto. Si falla, el fichero v4 queda intacto.
- **El embedder de texto lo dice el índice:** `rag_meta.embedding_model_sha256` acepta la 270M o la 440M, y `RagIndex.textEmbedder` es la única fuente para quien embebe texto. Antes de abrir el índice, `ModelRequirements` usa el espejo `text_embedder` del almacén del modo.
- **Almacén del modo:** `multimodal.preferences_pb` en `noBackupFilesDir/datastore/`, con la etapa, el error, el modo por defecto (Ambos) y el espejo.

**Activación** (research R1, R3, R4):
- `DefaultMultimodalController`, iniciado en `Application.onCreate`, sigue la etapa guardada `OFF → DOWNLOADING → VALIDATING → SWITCHING → ON`:
  - **DOWNLOADING:** la descarga de F03 de `[EG2_440M]` y la preparación de su backend. Un fallo de descarga o la falta de espacio se quedan aquí, con Reintentar y Cancelar;
  - **VALIDATING:** la identidad de los vectores de texto de la 270M y de la 440M con 20 textos, como documento y como consulta, en CPU y uno detrás de otro. Por debajo de 0,9999, vuelve a `OFF` y borra la 440M;
  - **SWITCHING:** pausa la ingesta con `MODEL_SWITCH`, cambia `rag_meta` en una transacción, actualiza el espejo, libera y borra la 270M y reanuda la ingesta.

  Cada paso se puede repetir tras cerrar la app. Reactivar es inmediato si el índice ya está en la 440M, y desactivar no borra nada (D-079).
- **`ModelRequirements`** sustituye a la lista fija de F03: la descarga, la limpieza de parciales, la preparación, el chip de Inicio, Preparación y las filas de Ajustes siguen al embedder del índice, más la 440M mientras se activa el modo.
- **`EngineKey` con tipo:** el motor de imagen y el de texto de la 440M son entradas distintas, así que una consulta durante las páginas recibe un motor de texto.

**Indexación por páginas** (research R9–R11, R14):
- Tras el texto, cada página que falta se convierte a 768 px en JPEG 85, se embebe con el motor de imagen de la 440M en CPU a 140 tokens, se trunca a 256D, se normaliza y se guarda con su imagen en `noBackupFilesDir/pages/`. El fichero y la fila se escriben con un mismo cerrojo.
- **Checkpoint por página;** las pausas por calor, por espacio (comprobado antes de empezar para todas las páginas que faltan, y antes de cada una) y por modelo son las de F07. Los motores de texto y de imagen nunca coinciden en la ingesta.
- **Ambos** sin texto sigue con sus páginas; **Visual** no extrae texto; una imagen ilegible da `IMAGE_UNREADABLE`.
- **Con el modo apagado,** los documentos con páginas esperan y los de texto del mismo run siguen.
- **Reindexar** en otro modo da a la sombra ese modo y borra las imágenes del original al publicar. Eliminar, quitar del índice y descartar una reindexación borran las imágenes después de sus filas, y un barrido al arrancar quita las que ninguna fila nombra, nunca con el índice no disponible.

**Pantallas** (wireframe §1–§8):
- **Ajustes:** el interruptor bajo los modelos, con la etapa como subtítulo, la barra real de la descarga, los botones de cada etapa, los dos diálogos y el aviso «Modo multimodal activado». La fila del embedder pasa a la 440M tras el cambio. Con el modo activo, «Modo al importar PDF», con su pantalla como la de Tema.
- **Ingesta:** «Modo de los PDF» con su hoja, el selector con JPG y PNG, y en la cola el modo, la estimación de páginas, tiempo y espacio, «Indexando páginas · 12 / 40» con el tiempo restante y el aviso de espacio para las páginas.
- **Indexación y notificación:** la etapa «Imágenes de página», con un icono nuevo, el anillo en páginas y las pausas «cambiando el modelo de búsqueda» y «Esperando al modo multimodal».
- **Biblioteca y detalle:** tipo · modo · páginas · fragmentos; el modo y las páginas en el detalle, las notas «Solo páginas…» y «Sus páginas necesitan el modo multimodal», la hoja de modo en «Reindexar» y «Reindexar como páginas» para un PDF sin texto.
- **Chat:** los documentos solo con páginas no cuentan como buscables hasta F20.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.283 de 1.283 (119 más que al cerrar F18) |
| Lint (`lintDebug`) | 0 errores. Los 2 avisos de versiones de siempre |
| Escritorio (Python) | 285 pruebas OK, con `schema_version == 5` |
| Regresión del texto (`run_eval.py ablations` y `order-review`) | Mismas cifras que en F18, sin contar los segundos: `ablations.json` y `order_review.json` idénticos |
| `Pixel_10` (API 37), instrumentadas | 998 de 998 (53 más que en F18), en 21,9 min, sin `@RequiresNetwork`. Las que necesitan modelos (`IdentityValidatorDeviceTest`, `VisualIngestBenchmarkTest` y el golden) se saltan: el emulador no los tiene |
| `MicroRAG_API33` (API 33), instrumentadas | 998, en 22,9 min: solo los 4 fallos previos de Atrás desde la raíz |
| Repetición con los APK finales en `Pixel_10` | 686 de 686 en 18,5 min: los paquetes de pantallas, modo multimodal, runtime y recuperación |
| Release | Ver `checklists/release-verification.md`: los mismos 9 permisos y sin `feasibility` |

## 3. Validación por criterio

Pendiente de la fase del teléfono.

## 4. Cifras medidas

### Recorrido en el emulador (T054)

En `MicroRAG_API32` (2 GB de RAM y 4,9 GB libres), con la 270M copiada a la carpeta de modelos de la app y verificada por Preparación. Gemma no cabe en la memoria de ningún emulador, así que el chat no se prueba aquí. En API 32 el sistema no extrae texto de los PDF (F06), así que los PDF en Ambos y el escaneado quedan para el teléfono. Pasos:

1. **Modo apagado:** Ajustes muestra el interruptor apagado bajo Gemma y EmbeddingGemma 2, con «Lee también páginas e imágenes. Respuestas más lentas.».
2. **Activar:** el aviso con «Descarga un modelo de 388 MB» y los otros tres puntos. Con «Activar»:
   - descarga real de la 440M con la descarga de F03, en 39 s. «Pausar» deja «Descarga en pausa» con «Reanudar» y «Cancelar», y «Reanudar» sigue desde el 75 %;
   - verificación, preparación en CPU y «Comprobando el modelo…»: la identidad da **1,000000** con 20 textos, un modelo cada vez, en unos 9 s;
   - `rag_meta` pasa a la 440M y se borra la 270M; la etapa queda en `ON` con «Modo al importar PDF: Ambos (texto y páginas)»;
   - la ficha del embedder es «EmbeddingGemma 2 440M», 388 MB y 256 dimensiones.
3. **Importar un PNG:** el selector ofrece imágenes. La fila de la cola dice «1 página · …» con el chip «Visual». En Indexación salen «Imágenes de página» y el anillo en páginas. La imagen queda indexada en 9 s (carga del motor de imagen 3,3 s, una página unos 5,7 s en la CPU del emulador). En la Biblioteca, «Imagen · Visual · 1 página»; en el detalle, el modo, las páginas y «Solo páginas. Todavía no se usa en el chat».
4. **Quitar del índice** borra su imagen de `pages/`; **Reindexar** la vuelve a indexar en Visual sin preguntar el modo, porque es una imagen.
5. **Desactivar:** el diálogo del wireframe §3. Se conservan la 440M y la imagen; la Ingesta ya no muestra el modo y el detalle dice «Sus páginas necesitan el modo multimodal». **Reactivar** es inmediato, sin descarga, con «Modo multimodal activado».
6. **Al 200 % y en oscuro:** Ajustes con el interruptor y «Modo al importar PDF» y la Ingesta sin cortes.
7. **Limpieza:** el índice, la copia, la imagen, la 440M y el almacén del modo de la prueba borrados, y la letra y el modo oscuro del emulador restaurados.

**Corregido tras el recorrido:** TalkBack no leía el modo ni la estimación de una fila de la cola, y una estimación de menos de un minuto decía «unos 1 min».

### Teléfono A063 (T055–T056)

Pendiente, con el permiso del usuario.

## 6. Diferencias frente al wireframe

- **Ingesta:** con el modo activo, la línea de formatos bajo el selector sigue diciendo «TXT · Markdown · PDF», aunque el selector ya ofrece JPG y PNG. El wireframe §5 no cambia esa línea.
- **Detalle de un documento solo Visual:** el interruptor «Incluir en respuestas» mantiene su subtítulo «Disponible para consultas RAG», mientras la nota de debajo dice que todavía no se usa en el chat.
- **Barra inferior al 200 %:** «Bibliot…» se corta, como antes de F19.

## 7. Desviaciones respecto al plan

- **Ficheros de página:** `deleteDocument`, `removeFromIndex` y `publishReindex` no devuelven los ficheros (contrato P4, P5). El llamante los lee con `pageFiles` antes de la transacción y los borra después; al publicar, se borran los de antes que ya no aparecen.
- **Escritura de una página:** el fichero y su fila se escriben con el cerrojo tomado (contrato I6, analyze M6), de modo que el barrido nunca ve un fichero sin fila.
- **Antes de abrir el índice,** `RagIndex.textEmbedder` vale lo que se le da al construirlo (la 270M). Quien necesita el valor del espejo lo toma de `ModelRequirements`, y un índice nuevo se crea con el embedder del espejo (`newIndexEmbedder`, leído antes de tocar el fichero).
- **`ModelRequirements`** da tres listas: `core` (lo que responde: chip, filas y fin del onboarding), `files` (lo que se descarga, prepara y conserva) y `shown` (las filas). La 440M que se descarga para activar el modo no pone el chip en «pendiente».
- **Icono nuevo** `ic_image` (Lucide «image», con las convenciones del generador) para la etapa de páginas, como pide el wireframe §8. El contrato de pantallas decía que no habría piezas nuevas; un icono no es un componente, pero se anota.
- **Pausa nueva** `PauseReason.WAITING_MULTIMODAL` para «Esperando al modo multimodal», solo cuando no queda trabajo y solo quedan documentos con páginas.
- **Espacio de las páginas:** además de la comprobación por página, se comprueba antes de empezar el espacio de todas las que faltan (R14).
- **Cola:** «Esperando al modo multimodal» se ve en Indexación, no en cada fila de la cola.
- **Reindexar en otro modo** un documento en error o fuera del índice no borra las páginas que pudiera tener de un intento anterior.

## 8. Fallos encontrados y corregidos

- `RagIndexWriteTest` no conocía la tabla `pages`.
- El diagnóstico de F18 usaba la misma clave para el motor de imagen y el de texto de la 440M: ahora el motor de imagen lleva `EngineKind.IMAGE_EMBEDDER`.
- `PageRasterizer` estaba definido dos veces en Koin (debug y main): el de depuración se quita.
- Lint: la descripción accesible del anillo en páginas no se usaba, y «al momento» se tomaba por una errata.

## 9. Pendiente

- Fase del teléfono (T055–T056), con permiso del usuario: la activación real borra la 270M del teléfono.
- Golden con el embedder activo (T062).
