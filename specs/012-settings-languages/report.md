# Informe de cierre — F12 Ajustes e Idiomas

> **Estado:** implementada; pendiente de la revisión de cierre del usuario. Nada de F12 está subido.

**Rama:** `012-settings-languages`, creada desde `88bf187`.
**Fecha:** 2026-11-01 y 2026-11-02.
**Dispositivos:**
- emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1);
- sin dependencias nuevas: DataStore se queda en 1.2.1 y no se añade AppCompat (D-059, verificados en Google Maven);
- LiteRT-LM 0.18.1. Lint avisa de que existe la 0.19.0, igual que en F11; no se actualiza en F12;
- esquemas sin cambios: índice v3 y `chat.db` v1.

## 1. Qué se ha implementado

**Escritorio (antes que la app, como pedía el plan)**
- **«Automático» evaluado y descartado (D-061):** la línea «Responde en el idioma de la PREGUNTA» se evaluó con las 426 preguntas en español y con 72 en inglés. Con las españolas quedó 1 negativa y 1,4 puntos de citas por debajo de `v4`; con las inglesas, las 72 respuestas salieron en español. El chat responde siempre en español y el prompt no cambia.
- **Medida entre idiomas (D-062):** un corpus en inglés de 3 libros de en.wikibooks y tres combinaciones juzgadas a mano (§4). El pendiente «Documentos en otro idioma» queda cerrado.
- **Herramientas:** `language-review`, `crosslang-review`, `fetch-corpus corpus=en`, `sources corpus=en`, `build en` y `validate-testset set=crosslang`, con la sección «Idiomas (F12)» del informe de evaluación.

**App**
- **Ajustes** (PNG 09):
  - las dos filas de modelo con el estado real de la Preparación, después de volver a mirar los ficheros: «Local» solo listo, «Modo CPU, más lento» para Gemma en CPU y el chip de la Preparación en los demás casos (P13–P14);
  - la ficha de solo lectura con fichero, tamaño, SHA-256 abreviado, dimensiones, procesador y estado, y «Completar configuración» si no está listo (S2, P15);
  - «Idiomas» con el idioma de la app como subtítulo, «Índice y rendimiento», «Procesamiento 100% local» informativo y «Borrar historial de chat».
- **Borrar el historial** (P9–P12, S6): cuenta antes las conversaciones; el diálogo avisa si hay una respuesta en curso; la sesión del chat la detiene, espera y borra bajo su cerrojo, y el chat vuelve a una conversación vacía. Una pregunta en una conversación borrada empieza otra nueva.
- **Idiomas** (PNG 10): «Español» y «English» para la app, «Español» como única opción de las respuestas (S12), la tarjeta con el texto de D-062 y «Guardar preferencias», con los avisos de lectura y de guardado (S8).
- **Idioma de la app sin AppCompat** (R2, P1–P5):
  - Android 13+: `LocaleManager.setApplicationLocales`, coherente con el ajuste del sistema;
  - Android 12: un fichero en `noBackupFilesDir`, leído al arrancar antes de StrictMode y escrito de forma atómica;
  - paso de Android 12 a 13: el fichero se aplica una vez y se borra;
  - las notificaciones de indexación y de descarga siguen el idioma sin reiniciar;
  - «Preferencias guardadas» se anuncia una vez, ya en el idioma nuevo.
- **Negativa en inglés** (P17): se reconoce también «I can't find that information in the available documents.», en la app y en el escritorio.
- **Textos:** todos en `strings_settings.xml`, en español y en inglés, y la revisión del inglés de toda la app (§5).
- **Navegación:** se retiran los últimos marcadores «Pendiente» y su componente.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 181/181 (F11: 152) |
| `gen_answer_golden.py --check` | ✅ al día: el prompt no cambia |
| `lintDebug` | ✅ 0 errores; solo el aviso de LiteRT-LM 0.19.0, igual que en F11 |
| Tests JVM | ✅ 912/912 (F11: 818) |
| `NetworkConfinementTest` | ✅ |
| `SettingsScreenAccessibilityTest` y `LanguagesScreenAccessibilityTest` en `MicroRAG_API33` | ✅ 72/72: todos los estados de muestra, en claro y oscuro, en horizontal y al 200 % |
| `SqliteChatRepositoryTest` y `PlatformAppLanguageControllerTest` en `MicroRAG_API33` | ✅ 9/9 y 1/1 |
| Batería instrumentada completa en `MicroRAG_API33` | ⚠️ 736 pruebas: 713 bien, 19 omitidas por sus propias condiciones (`assume`) y 4 fallos anteriores a F12 (abajo). Las que necesitan modelos o red se excluyen, como siempre |
| Batería instrumentada completa en `Pixel_10` (API 37) | ✅ 736 pruebas: 730 bien y 6 omitidas por sus propias condiciones (`assume`), ningún fallo |
| Tras los dos últimos arreglos (`fa9bda1`, `55113aa`): `SystemBackTest` y la accesibilidad de Ajustes e Idiomas en `Pixel_10` | ✅ 73/73 |
| Batería instrumentada en el teléfono, con los APK finales | ✅ **OK (736 pruebas)** en 20,7 min, sin fallos, con la pantalla despierta |
| Batería en el teléfono, primer intento | ❌ 174 fallos en 7 clases que abren una actividad («No compose hierarchies found» y esperas agotadas): se lanzó con la pantalla apagada. Esas 7 clases, repetidas con la pantalla despierta: 258/258. La batería completa de arriba lo confirma |

**Los 4 fallos de API 33 no son de F12.** `SystemBackTest` y tres casos de `OnboardingNavigationTest` esperan que Atrás desde la pantalla raíz cierre la actividad (`DESTROYED`), pero en API 33 queda en segundo plano (`CREATED`). Desde Android 12, Atrás en la actividad raíz de una tarea la manda al fondo en lugar de cerrarla. Se comprobó en una copia temporal de `88bf187`, en el mismo emulador: fallan igual las tres de Onboarding y también `SystemBackTest`, en la misma línea. En `Pixel_10` (API 37) pasan las cuatro. Se dejan como están y se anotan para revisarlas fuera de F12.

## 3. Validación por criterio

| Criterio | Cómo se comprueba | Estado |
|---|---|---|
| SC-001 | `ObserveModelOverviewUseCaseTest`, `SettingsViewModelTest` (P14) y `SettingsScreenTest`: «Local» nunca sin `Ready` | ✅ JVM |
| SC-002 | La ficha toma fichero, tamaño y SHA-256 de `ModelCatalog`, el mismo con el que se verificó el fichero (`SettingsScreenTest`); en el teléfono, las dos fichas con «Local», GPU para Gemma y CPU para EmbeddingGemma | ✅ |
| SC-003 | `AppLocaleTest` y `PlatformAppLanguageControllerTest` en `sdk = 32` y `sdk = 36`; notificaciones; anuncio tras la recreación; cambio de ida y vuelta, desde la app y desde el sistema, en API 33 y 37 y en el teléfono, también con Gemma respondiendo y con la indexación en marcha | ✅ |
| SC-004, SC-005 | El prompt no cambia (golden de 426 casos al día); `AnswerReadingTest` con las dos negativas | ✅ |
| SC-006 | `DefaultChatSessionTest` (P9, P10), `ChatViewModelTest` (P11), `SqliteChatRepositoryTest` (índice intacto); sin conversaciones, el aviso en los dos emuladores. Borrar con conversaciones no se prueba a mano: los emuladores no tienen Gemma y en el teléfono el diálogo solo se abre y se cancela | ✅ pruebas y emuladores |
| SC-007 | `LanguagesViewModelTest` y `LanguagesScreenTest` con fallos programados; `SaveLanguageSettingsUseCaseTest` | ✅ JVM |
| SC-008 | ATF en el emulador, 72/72 | ✅ |
| SC-009 | `StringsParityTest` y la revisión del inglés (§5) | ✅ |
| SC-010 | `NetworkConfinementTest`; el único registro nuevo lleva solo el tipo de fallo, y en release no se registra nada; en el teléfono, 0 bytes de la app en toda la sesión y un registro sin nombres ni textos | ✅ |
| SC-011 | Capturas del teléfono frente a los PNG 09 y 10, en claro y oscuro, al 200 % y en horizontal; diferencias en §6 | ✅ |
| SC-012 | D-061 en DECISIONS; Idiomas no ofrece «Automático» | ✅ |
| SC-013 | D-062 en DECISIONS; pendiente cerrado; texto de la tarjeta aprobado por el usuario | ✅ |

## 4. Cifras medidas

### «Automático» con las 426 preguntas en español (D-061)

| Juicio manual | `v4` | «Automático» |
|---|---:|---:|
| Respuestas correctas | 81,1 % (300/370) | 81,1 % (300/370) |
| Negativas correctas | 91,1 % (51/56) | 89,3 % (50/56) |
| Citas correctas | 94,3 % | 92,9 % |

Cambiaron 196 respuestas, juzgadas a mano. Con las 72 preguntas en inglés, las 72 respuestas salieron en español.

### Medida entre idiomas (D-062), respuestas en español

| Combinación | Búsqueda | Correctas | Referencia | Negativas | Citas |
|---|---:|---:|---:|---:|---:|
| Documentos en español, preguntas en inglés | 57,9 % | 50,9 % (29/57) | 82,5 % | 15/15 | 96,1 % |
| Documentos en inglés, preguntas en inglés | 90,0 % | 80,0 % (40/50) | — | 15/15 | 95,5 % |
| Documentos en inglés, preguntas en español | 78,0 % | 64,0 % (32/50) | 80,0 % | 13/15 | 90,8 % |

- Con el mismo idioma, el inglés rinde como el español (80,0 % frente a 81,1 %).
- Con idiomas distintos falla sobre todo la búsqueda: 21 de 28 fallos en la primera combinación y 11 de 18 en la tercera no tenían un fragmento relevante en el contexto.
- La mejora de la búsqueda entre idiomas queda en la ficha de F14.

### Recorridos

**`MicroRAG_API33` (T065).** Sin modelos ni documentos, así que no hay pregunta al chat ni notificación de indexación.

| Paso | Resultado |
|---|---|
| Ajustes | Igual que el PNG 09 salvo §6: las dos filas en «Por configurar», «Idiomas: Español» y «Activo» |
| Ficha de cada modelo | Fichero, tamaño, 12 caracteres del SHA-256, 256 dimensiones solo en embeddings, procesador «—» y «Completar configuración»; el botón lleva a la Preparación y Atrás vuelve a Ajustes con la ficha cerrada |
| Idiomas → English → Guardar | La app pasa a inglés y vuelve a Ajustes con «Languages: English»; el sistema guarda `[en]` |
| Vuelta a Español | Igual, y el sistema guarda `[es]` |
| Idioma desde el sistema (`cmd locale set-app-locales … en`) | Ajustes e Idiomas lo siguen: «English» marcado |
| Atrás sin guardar | Descarta la elección; el sistema sigue en `[en]` |
| Elección sin guardar y giro de pantalla | Se conserva |
| «Borrar historial de chat» sin conversaciones | Aviso «No hay conversaciones guardadas.» y ningún diálogo |
| «Índice y rendimiento» | Abre la Indexación («All up to date» en inglés) |
| Letra al 200 % | Ajustes, Idiomas y la ficha completos y sin palabras partidas, tras los arreglos de §8 |
| Horizontal con letra al 200 % | Las tres pantallas se desplazan hasta el final; la ficha también |
| Modo oscuro | Ajustes, Idiomas y la ficha legibles |

«Preferencias guardadas» no se ve en pantalla: es un anuncio de TalkBack (U7), cubierto por las pruebas. El emulador quedó con su letra, su giro, el modo claro y la app en español.

**`Pixel_10` (API 37) (T065).** Con los mismos límites.

| Paso | Resultado |
|---|---|
| Ajustes y ficha de embeddings | Como en API 33 |
| Idiomas → English → Guardar y vuelta a Español | La app cambia y vuelve a Ajustes; el sistema guarda `[en]` y luego `[es]` |
| Idioma desde el sistema | La pantalla abierta se redibuja en inglés con «Languages: English», e Idiomas marca «English» |
| Atrás sin guardar | Descarta la elección |
| «Clear chat history» sin conversaciones | «There are no saved conversations.» |
| Pestaña Chat abierta y cambio de idioma desde el sistema | La pestaña sigue abierta y pasa a inglés |
| Letra al 200 %, horizontal y modo oscuro | Ajustes, Idiomas y la ficha completos, desplazables y legibles |

El emulador quedó con su letra, su giro, el modo claro y sin idioma por app (`[]`), como estaba.

**Registros de la sesión (T063, P18).** Filtrados al proceso de la app en `Pixel_10`: la app solo escribió «chat db open: OK in 7 ms» y «rag index open: Compatible in 8 ms», sin nombres ni textos. StrictMode (solo en depuración) marcó cinco lecturas de disco en el hilo principal. Cuatro son de crear las dependencias en `DataModule`, que F12 no toca, y vienen de antes. La quinta era de F12 y está corregida (§8).

**Teléfono A063 (T068).** Recorrido acordado con el usuario, con documentos de prueba y cambios temporales de letra, giro y modo.

| Paso | Resultado |
|---|---|
| Ajustes | Las dos filas con «Local»; «Idiomas: Español»; «Activo» |
| Fichas | Gemma 4 E2B: 2,6 GB, GPU, «Local». EmbeddingGemma 2 270M: 165 MB, 256 dimensiones, CPU, «Local» |
| Idiomas → English → Guardar y vuelta | La app cambia sin reiniciarse; el sistema guarda `[en]` y luego `[es]` |
| Idioma desde el sistema | Idiomas marca «English» |
| «Clear chat history» | El diálogo se abrió y se **canceló**; no se borró nada |
| Notificación de indexación | Con la app en inglés, «Indexing documents · 0 of 119 chunks». Al pasar a español sin parar: «Indexando documentos · 49 de 119 fragmentos». La indexación siguió hasta el final |
| Pregunta en inglés (D-061) | «Which typeface and size are recommended for the body text and for chapter titles?» se respondió en español, citando solo «Wikibooks Basic Book Design.txt» |
| Cambio de idioma con Gemma respondiendo, desde el sistema y con el chat a la vista | Primer token a las 06:33:23,8 (hora del teléfono) y cambio a las 06:33:24. La respuesta siguió creciendo en pantalla y el registro tiene una sola respuesta completada, a las 06:34:03 |
| Cambio de idioma con Gemma respondiendo, desde Idiomas (sale del chat) | Primer token hacia las 06:35:57 y guardado hacia las 06:36:01. La respuesta terminó a las 06:36:18,9 con su fuente, sin reiniciarse |
| Modo claro, letra al 200 % y horizontal | Ajustes, Idiomas y la ficha completos, desplazables y sin palabras partidas |
| Red | 0 bytes de la app (uid 10628) desde el arranque del teléfono, antes y después |
| Registro de la app | Solo cifras e identificadores (`retrieval …`, `chat answer outcome=completed …`, `library delete id=3 chunks=70 …`); ninguna mención de los documentos ni de las preguntas |

**Limpieza y estado final.** Los dos documentos se borraron en la app, y su carpeta y sus entradas de medios, del teléfono. En el historial queda la conversación de prueba, como se acordó. El teléfono quedó con letra 1.0, giro automático en vertical, modo oscuro y la app sin idioma propio (`[]`), como estaba.

**Dos cosas que no salieron como estaban propuestas:**
- **La pestaña Chat no se abre vacía.** Abre la última conversación guardada (R13 de F11), que era la del usuario. Se detectó antes de escribir nada, solo con las etiquetas de la app: no se capturó ni se leyó su contenido. Las preguntas de prueba se hicieron en un «Nuevo chat», comprobado vacío. Al terminar, la pestaña abrirá la conversación de prueba, que ahora es la última.
- **La primera batería en el teléfono** se lanzó con la pantalla apagada (§2).

## 5. Revisión del inglés (T062)

Se revisaron los 451 textos de `values-en` frente a los españoles. Se corrigieron 12, en un commit propio (`d19e8eb`):

| Clave | Antes | Ahora |
|---|---|---|
| `citation_fragment`, `citation_fragments` | Fragment / Fragments | Chunk / Chunks, como el resto de la app |
| `chat_open_source` | Open source | Open the source |
| `home_greeting` | What do you want to explore today? | What would you like to explore today? |
| `index_paused_thermal_body` | … will go on by itself once it cools down. | … will resume on its own once it cools down. |
| `index_paused_system_body` | It will go on as soon as it allows it … | Indexing will resume as soon as Android allows it … |
| `index_model_missing_body` | It is needed to index. | It is needed for indexing. |
| `ingest_error_unsupported_format_detail` | Only PDF with text, TXT and Markdown are supported. | Only text-based PDFs, TXT and Markdown files are supported. |
| `document_delete_title` | Delete «%1$s»? | Delete “%1$s”? |
| `model_reason_download_failed` | Could not download (%1$s) | Download failed (%1$s) |
| `confirm_free_space` | … (%2$s left after) | … (%2$s will be left) |
| `confirm_network_none` | … it will start when you connect | … the download will start once you are online |

## 6. Diferencias frente a los PNG

- **Filas de modelo:** las dos llevan chip y chevron (S1). En el PNG, el chip solo está en «Modelo de respuestas» y el chevron solo en «Modelo de embeddings».
- **Chip de Gemma en CPU:** «Modo CPU, más lento» en lugar de «Local» (D-027).
- **Botones de opción:** el aspecto de Material 3 (D-018, G18).
- **Idioma de las respuestas:** una sola opción, «Español», en lugar de «Automático» y «Español» (D-061, S12).
- **Tarjeta «Búsqueda multilingüe»:** «Consulta documentos en español o en inglés. Funciona mejor si preguntas en el idioma del documento.» en lugar del texto del PNG (D-062).
- **Chip al lado o debajo:** con un estado largo («Por configurar») o letra grande, el chip baja bajo los textos para no partir palabras como «EmbeddingGemma 2». Con «Local» y «Activo» y letra normal queda al lado, como en el PNG.
- **Título de modelo en dos líneas en el teléfono:** con «Local» al lado, «Modelo de respuestas» ocupa dos líneas; en el PNG cabe en una. El ancho útil del teléfono es menor que el de la maqueta.
- **Barra de estado y franja de inicio** de iOS: no aplican (G40).

## 7. Desviaciones respecto al plan

- **`ObserveModelOverviewUseCase` vuelve a mirar los ficheros antes de su primera emisión,** como el chip de Inicio, en lugar de dejarlo solo a la pantalla. Así nunca enseña un estado guardado sin comprobar.
- **`IsAnswerInProgressUseCase`,** un caso de uso pequeño que no estaba en el contrato: Ajustes necesita saber si hay una respuesta en curso (P12) sin importar la sesión.
- **`ModelOverview.displayName`:** la ficha muestra el nombre del catálogo («Gemma 4 E2B», «EmbeddingGemma 2 270M»).
- **`SettingsNotices`** expone `savedPending`, `announceSaved()` y `consumeSaved()` en lugar de un `MutableStateFlow` público. El armazón solo anuncia cuando la pantalla ya muestra el idioma nuevo, para no hacerlo con los recursos de la actividad que se va a recrear.
- **Paso de Android 12 a 13:** el fichero se borra también si el sistema ya tenía un idioma por app; ese idioma manda.
- **Corpus en inglés:** Git, Basic Book Design y Knots, sin la versión para imprimir. Sailing se descartó porque casi todo su contenido es un glosario en una sola página.
- **Conjuntos `en-docs`:** la respuesta esperada y los datos clave van en inglés, el idioma del documento, como `es-docs.en` los conserva en español.
- **Componentes:** `LisdataHeroCard` gana `leadingIcon` (el globo del PNG 10) y `LisdataIconTile` gana `tint` e `iconSize`.

## 8. Fallos encontrados y corregidos

- **Prueba del chat con el texto inglés antiguo:** `ChatScreenTest` esperaba «Fragment 86». Se actualizó junto a la revisión del inglés. Por un error de orden en la consola, el commit se hizo antes de ver la prueba roja; se corrigió con `--amend` antes de subir nada.
- **Palabras partidas junto a un chip** (recorrido en API 33): «EmbeddingGemm / a 2» con «Por configurar» a su lado, y «Procesamie / nto» con letra al 200 %. `LisdataSettingsRow` deja el chip al lado solo si la palabra más larga de los textos cabe entera; si no, lo pone debajo. Nueva prueba `SettingsRowChipTest`, que falla sin el arreglo (`b33faa3`).
- **Ficha a media altura** (recorrido en API 33): la de embeddings abría a media pantalla con «Completar configuración» fuera de ella. `LisdataBottomSheet` abre entera y su contenido se desplaza cuando tiene un alto máximo. Afecta también a la ficha de la cola de importación de F06, que gana lo mismo (`34e9d69`).
- **`SystemBackTest` en API 33:** buscaba «Ajustes», que ahora está dos veces (pestaña y título); busca el subtítulo (`0474f33`).
- **Nombre del modelo partido en inglés** (recorrido en API 37): «Not set up» cabía al lado partiendo «EmbeddingGemma / 2». La fila de Ajustes une el nombre con espacios que no permiten salto de línea, así que el chip baja (`fa9bda1`). El texto compartido con la Preparación y las notificaciones no cambia.
- **Lectura de disco en el hilo principal** (StrictMode en API 37): Ajustes recogía el estado de la Preparación en el hilo principal, y ese estado lee de disco el tamaño de las descargas a medias. `SettingsViewModel` lo recoge en segundo plano, como `SetupViewModel` (`55113aa`). Comprobado de nuevo en el emulador: esa lectura ya no aparece.

## 9. Pendiente

- La revisión de cierre del usuario. Después, si la aprueba: integrar en `main` y subir.

**Visto durante las pruebas, fuera de F12** (no se ha tocado):
- **Atrás desde la raíz en API 33:** las 4 pruebas que esperan que la actividad se cierre (§2).
- **StrictMode en depuración:** 4 lecturas de disco al crear las dependencias de `DataModule`, anteriores a F12.
- **Indexación (F07):** el porcentaje de la pantalla va por detrás de la notificación: se quedó en 70 de 119 mientras la notificación pasaba de 89 a 107.
- **Chat (F11, calidad para F14):**
  - las preguntas nuevas dentro de una misma conversación salen como `follow_up=true`;
  - una de ellas, sobre montar un repositorio Git compartido, terminó en negativa aunque el libro lo trata;
  - primer token entre 4,6 y 8,1 s (D-057).
- **Barra inferior al 200 %:** «Biblioteca» se corta como «Bibliot…», igual que antes de F12.
