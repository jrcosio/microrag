# Especificación de feature: Ajustes e Idiomas (F12)

**Feature Branch**: `012-settings-languages`

**Created**: 2026-11-01

**Status**: Draft

**Input**: Descripción del usuario: «F12 — Ajustes e Idiomas. Feature de producto: pantallas `09-configuracion.png` y `10-idiomas.png` (manda el PNG), con `screens/09-settings.md` y `screens/10-languages.md` para los estados sin maqueta. El usuario ve en Ajustes el estado real de los modelos y su detalle; elige el idioma de la app (ES/EN) y el de las respuestas (Automático/Español); llega a Índice; ve que todo se procesa en el teléfono; puede borrar su historial de chat. Todo funciona sin red y se guarda en el teléfono.» Ver `docs/roadmap/FEATURES.md` (F12).

**Fuentes**:
- Mockups (mandan sobre el `.md`):
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/09-configuracion.png` y `docs/disegn_app/screens/09-settings.md`;
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/10-idiomas.png` y `docs/disegn_app/screens/10-languages.md`.
- `docs/disegn_app/docs/`:
  - `01-navigation.md`: `settings` con barra inferior, `languages` sin ella; flujo Inicio → Ajustes → Idiomas → guardar → Ajustes;
  - `02-architecture.md`: estados de modelo y preferencias locales;
  - `03-components.md`: fila de estado de modelo, chip «local», diálogo de confirmación;
  - `04-qa-checklist.md`: Idiomas sin pestañas, preferencias locales, traducciones, sin reindexar al cambiar de idioma.
- `docs/roadmap/INVENTARIO.md`:
  - fichas P09 y P10 (textos del PNG frente al MD);
  - flujo 5.5 (Ajustes e idiomas);
  - G08: flecha Atrás en las pestañas;
  - G09: vocabulario de estados de modelo;
  - G21: sin sección de recursos en Indexación;
  - G23: ajustes del MD que no están en el PNG;
  - G24: detalle de modelo;
  - G25: idiomas;
  - G26: texto de la negativa;
  - componentes `LisdataSettingsRow`, `LisdataRadioRow`, `LisdataModelStatusRow`, `LisdataHeroCard`, `LisdataInfoCard`, `LisdataGradientButton` y `LisdataConfirmationDialog`.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - distribución de modelos: verificación SHA-256 antes de usar (L97–109) y metadatos del índice con las dimensiones (L406–418);
  - generación: plantilla del prompt con «Responde en español» y la negativa en español (L731–797);
  - privacidad y copias de seguridad (L1064–1069).
- Constitución v1.1.1:
  - I: sin red; datos en almacenamiento privado sin copia de seguridad; logs sin contenido privado;
  - III: los parámetros del RAG, la plantilla incluida, solo cambian con una evaluación documentada y una entrada en DECISIONS (L96);
  - V: un modelo no se declara listo sin verificar su hash y su compatibilidad (L117); eliminar exige confirmación (L121);
  - VI: pruebas del constructor del prompt y de los ViewModels;
  - VII: UI fiel al PNG, propuestas aprobadas para lo no maquetado, accesibilidad y textos en recursos.
- DECISIONS:
  - D-004: los estados sin maqueta se proponen y se aprueban antes de implementar;
  - D-007: exportar y restaurar quedan en el backlog;
  - D-011: el SHA-256 publicado se verifica antes de cargar nada;
  - D-016: el tema sigue al sistema, sin selector;
  - D-018: radios, interruptores y diálogos de Material 3;
  - D-025: EmbeddingGemma 2 270M;
  - D-027: estados reales de los modelos y aviso «Modo CPU, más lento»;
  - D-029: la app en español por defecto, el inglés elegido expresamente; F12 implementa el selector;
  - D-034 y D-056: plantilla del prompt evaluada (`v4`);
  - D-039: el usuario no elige el backend;
  - D-046 y D-047: la Preparación y el estado real de los modelos;
  - D-053, D-054 y D-055: conversaciones guardadas sin copia de seguridad, «borrar todo» previsto para F12 e idioma de la respuesta como parámetro del prompt;
  - pendiente «Documentos en otro idioma» (sin medir).
- FEATURES: F12 (criterios de aceptación 1–9), F11, F13 («Borrar historial de Ajustes vacía la lista»), F15 (licencias) y el backlog.
- Lo ya construido:
  - F01: barra inferior con la pestaña Ajustes, rutas de Ajustes e Idiomas (hoy marcadores) y los componentes de filas, radios, tarjetas, hoja inferior y diálogo;
  - F03: catálogo de modelos con fichero, tamaño y SHA-256; estados reales de la Preparación; idioma de la app en español por defecto;
  - F07: pantalla de Indexación;
  - F09: acceso «Idiomas» en Inicio;
  - F11: conversaciones guardadas con «borrar todo» sin UI; idioma de la respuesta como parámetro del prompt, hoy siempre español.

**Verificado al redactar** (2026-11-01):
- **Ajustes e Idiomas hoy:** las dos rutas existen, pero muestran un marcador de posición «F12». El icono de ajustes de Inicio abre la pestaña Ajustes y el acceso «Idiomas» de Inicio abre Idiomas por encima de Inicio.
- **Modelos:** el catálogo tiene, para Gemma 4 E2B y EmbeddingGemma 2 270M, el fichero, el tamaño en bytes y el SHA-256 publicado. Las dimensiones de los embeddings son 256. La Preparación (F03) ya calcula el estado real de cada modelo, incluidos el procesador preparado (GPU o CPU), la incompatibilidad y el fallo de verificación, y sabe volver a verificar un fichero.
- **Vocabulario de estados de la Preparación:** «Por configurar», «En cola», «Descargando N %», «Importando N %», «En pausa», «Esperando conexión», «Verificando», «Preparando», «Listo», «Sin espacio», «Incompatible» y «Error». Con Gemma solo en CPU, el detalle dice «Modo CPU, más lento».
- **Historial:** las conversaciones (F11) se guardan sin copia de seguridad y ya existe un «borrar todo» probado, sin UI. No detiene la respuesta en curso. Además, la pestaña Chat recuerda la conversación abierta: tras un borrado total, su siguiente pregunta no se podría guardar.
- **Idioma de las respuestas:** el prompt lleva una única línea que depende del idioma («Responde en español, de forma clara y breve.») y el chat siempre pide español. La negativa del modelo se reconoce comparándola con la frase en español. Ninguna plantilla se ha evaluado en otro idioma, y el escritorio no tiene parámetro de idioma.
- **Idioma de la app:** se aplica al arrancar. En Android 13+ se lee el idioma por app del sistema; en Android 12 la app siempre sale en español, porque no hay dónde guardar la elección. Hay textos en español e inglés con las mismas claves (395 textos y 12 plurales).
- **Preferencias:** ya se guardan otras preferencias (preparación, tanda de indexación) en almacenamiento privado sin copia de seguridad, pero sin manejo de errores de lectura ni de escritura.
- **Textos en conflicto (manda el PNG):**
  - secciones «PREFERENCIAS» y «PRIVACIDAD Y DATOS» frente a «Personalización» y «Privacidad» del MD;
  - «Modelo de respuestas» frente a «Modelo de respuesta»;
  - «Procesamiento 100% local» con el chip «Activo» frente a «Procesamiento sin nube»;
  - H1 de Idiomas «Habla en tu idioma» frente a «Habla con tus documentos en tu idioma»;
  - el MD añade Português (app), English (respuestas), una tarjeta «Detección del idioma» y las filas de G23, que el PNG no tiene.
- **«Índice y rendimiento»:** su subtítulo es «Almacenamiento y procesamiento», pero la pantalla de Indexación no muestra almacenamiento (G21).
- **«Búsqueda multilingüe»:** el PNG dice «Consulta documentos en distintos idiomas desde el mismo chat.», y el MD pide no prometer resultados perfectos. La búsqueda por palabras está pensada para el español y la consulta entre idiomas no se ha medido.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Usuario:** revisa sus modelos, elige idiomas y gestiona su historial.
- **Quien desarrolla:** evalúa en el escritorio cualquier cambio en la redacción del prompt.

### User Story 1 - Ver el estado real de los modelos (Priority: P1)

El usuario abre Ajustes y ve, en «INTELIGENCIA ARTIFICIAL», los dos modelos con su estado real. Al tocar un modelo ve su ficha: fichero, tamaño, SHA-256 abreviado, dimensiones, procesador y estado. Si un modelo no está listo, la ficha le lleva a la Preparación.

**Why this priority**: es el primer criterio de F12 y aplica la constitución V: la app nunca dice que un modelo está listo sin haberlo verificado.

**Independent Test**: con los modelos en distintos estados (los dos listos, Gemma solo en CPU, uno sin descargar, uno incompatible, uno con la verificación fallida):
- cada fila muestra el mismo estado que la Preparación;
- «Local» solo aparece con el modelo verificado y preparado;
- los datos de la ficha coinciden con los del modelo instalado.

**Acceptance Scenarios**:

1. **Given** los dos modelos verificados y preparados en GPU, **When** el usuario abre Ajustes, **Then** «Modelo de respuestas / Gemma 4» y «Modelo de embeddings / EmbeddingGemma 2» muestran el chip «Local».
2. **Given** Gemma preparada solo en CPU, **When** se abre Ajustes, **Then** su fila muestra «Modo CPU, más lento» en lugar de «Local».
3. **Given** un modelo sin descargar, descargándose, incompatible o con la verificación fallida, **When** se abre Ajustes, **Then** su fila muestra el estado de la Preparación y nunca «Local».
4. **Given** un modelo, **When** el usuario abre su ficha, **Then** ve el fichero, el tamaño, el SHA-256 abreviado, las dimensiones (256 en el de embeddings), el procesador y el estado (alcance en FR-006).
5. **Given** un modelo que no está listo, **When** el usuario abre su ficha, **Then** tiene un acceso a la Preparación (D-046).
6. **Given** Ajustes abierto mientras termina una descarga, **When** el modelo queda verificado y preparado, **Then** la fila cambia a «Local» sin salir de la pantalla.

---

### User Story 2 - Elegir el idioma de la app (Priority: P1)

El usuario abre Idiomas, elige «English» en «IDIOMA DE LA APLICACIÓN» y pulsa «Guardar preferencias». La app pasa a inglés en el acto, sin perder lo que estaba haciendo y sin reindexar. Sigue en inglés tras cerrarla.

**Why this priority**: es el selector que D-029 dejó para F12 y el criterio 2 de F12. Hoy, en Android 12 no hay forma de elegir otro idioma.

**Independent Test**: con una conversación abierta en la pestaña Chat y documentos indexados, cambiar a inglés y volver a español, en Android 12 y en Android 13+:
- todos los textos cambian;
- la conversación sigue abierta;
- no se lanza ninguna indexación;
- el idioma se mantiene tras cerrar la app.

**Acceptance Scenarios**:

1. **Given** el teléfono en inglés y ninguna elección hecha, **When** el usuario abre la app, **Then** la app está en español y en Idiomas está marcado «Español» (D-029).
2. **Given** Idiomas con «English» marcado, **When** el usuario pulsa «Guardar preferencias», **Then** todos los textos de la app pasan a inglés y vuelve a la pantalla de origen.
3. **Given** el cambio de idioma con una conversación abierta en la pestaña Chat, **When** el usuario vuelve al chat, **Then** ve la misma conversación, y no ha empezado ninguna indexación.
4. **Given** la app en inglés, **When** se cierra y se vuelve a abrir, **Then** sigue en inglés.
5. **Given** un teléfono con Android 12, **When** el usuario elige inglés y guarda, **Then** la app pasa a inglés igual que en Android 13+.
6. **Given** Android 13+, **When** el usuario cambia el idioma de la app en los ajustes del sistema, **Then** la app usa ese idioma e Idiomas lo muestra marcado.
7. **Given** cambios marcados en Idiomas sin guardar, **When** el usuario pulsa Atrás, **Then** no cambia nada.

---

### User Story 3 - Elegir el idioma de las respuestas (Priority: P1)

El usuario elige en «IDIOMA DE LAS RESPUESTAS» entre «Automático» y «Español» y guarda. Las respuestas siguientes del chat siguen esa elección, sea cual sea el idioma de la app.

**Why this priority**: es el criterio 3 de F12 y completa el parámetro de idioma que F11 dejó preparado (D-055).

**Independent Test**: con cada opción guardada, hacer preguntas en el chat:
- el prompt lleva la regla de idioma de la opción;
- con «Español», el prompt es idéntico al del escritorio;
- la negativa se reconoce en las dos opciones.

**Acceptance Scenarios**:

1. **Given** «Español» guardado y la app en inglés, **When** el usuario pregunta, **Then** el prompt es idéntico al del escritorio y la respuesta pide español.
2. **Given** «Automático» guardado, **When** el usuario pregunta, **Then** el prompt lleva la regla de «Automático» (FR-024).
3. **Given** una respuesta en curso, **When** el usuario cambia el idioma de las respuestas, **Then** esa respuesta no cambia y la elección se aplica a la siguiente pregunta.
4. **Given** cualquiera de las dos opciones, **When** el modelo responde con la negativa, **Then** la app la reconoce y muestra el texto de negativa de F11 en el idioma de la app, sin fuentes.
5. **Given** una opción guardada, **When** se cierra y se vuelve a abrir la app, **Then** sigue elegida.

---

### User Story 4 - Borrar el historial de chat (Priority: P1)

El usuario toca «Borrar historial de chat», confirma, y todas sus conversaciones desaparecen del teléfono. Sus documentos y el índice no cambian, y el chat queda vacío y listo para preguntar.

**Why this priority**: es el criterio 4 de F12, una acción destructiva sobre datos del usuario (constitución V), y la base de F13 («Borrar historial de Ajustes vacía la lista»).

**Independent Test**: con varias conversaciones guardadas, una de ellas abierta en la pestaña Chat:
- cancelar no borra nada;
- confirmar las borra todas, con sus mensajes y fuentes;
- el número de documentos y el índice no cambian;
- la siguiente pregunta en el chat se guarda con normalidad.

**Acceptance Scenarios**:

1. **Given** conversaciones guardadas, **When** el usuario toca «Borrar historial de chat», **Then** aparece un diálogo de confirmación, y «Cancelar» no borra nada.
2. **Given** el diálogo, **When** el usuario confirma, **Then** se eliminan del teléfono todas las conversaciones con sus mensajes y fuentes, y se avisa de que el historial se ha borrado.
3. **Given** una respuesta en curso, **When** el usuario confirma el borrado, **Then** la respuesta se detiene primero y después se borra todo.
4. **Given** el historial borrado, **When** el usuario abre la pestaña Chat y pregunta, **Then** ve un chat vacío, y la pregunta y su respuesta se guardan con normalidad.
5. **Given** ninguna conversación guardada, **When** el usuario toca «Borrar historial de chat», **Then** un aviso lo dice y no aparece el diálogo.
6. **Given** el historial borrado, **When** el usuario abre la Biblioteca, **Then** sus documentos siguen ahí y se puede preguntar por ellos.

---

### User Story 5 - Moverse desde Ajustes (Priority: P2)

Desde Ajustes, el usuario llega a Idiomas y a Índice, y ve que todo se procesa en el teléfono. Desde Inicio también llega a Idiomas.

**Why this priority**: es el criterio 7 de F12. Sustituye los marcadores de posición que hoy aparecen al tocar estas entradas.

**Independent Test**: recorrer todas las entradas de Ajustes y el acceso «Idiomas» de Inicio, con Atrás en cada pantalla.

**Acceptance Scenarios**:

1. **Given** cualquier pestaña, **When** el usuario toca Ajustes en la barra inferior, **Then** ve Ajustes con la pestaña seleccionada, y la flecha Atrás le lleva a Inicio (G08).
2. **Given** Ajustes, **When** el usuario toca «Idiomas», **Then** se abre Idiomas sin barra inferior. La fila de Ajustes muestra como subtítulo el idioma de la app.
3. **Given** Ajustes, **When** el usuario toca «Índice y rendimiento», **Then** se abre la Indexación, y Atrás vuelve a Ajustes.
4. **Given** Inicio, **When** el usuario toca «Idiomas», **Then** se abre Idiomas, y tanto «Guardar preferencias» como Atrás vuelven a Inicio.
5. **Given** Ajustes, **When** el usuario toca «Procesamiento 100% local», **Then** no pasa nada: es un indicador con el chip «Activo», no un interruptor.

---

### User Story 6 - Preferencias que no se pierden (Priority: P2)

Las preferencias se guardan en el teléfono y se mantienen tras cerrar la app. Si guardar o leer falla, la app no se queda en un estado a medias: conserva los valores anteriores y lo dice.

**Why this priority**: es el criterio 6 de F12. Hoy no hay manejo de errores al leer o guardar preferencias.

**Independent Test**: provocar un fallo al guardar y al leer:
- al guardar, se conservan los valores anteriores y aparece el aviso de error;
- al leer, se usan los valores por defecto, aparece un aviso y la app funciona.

**Acceptance Scenarios**:

1. **Given** preferencias guardadas, **When** la app se cierra desde recientes y se vuelve a abrir, **Then** las preferencias son las mismas.
2. **Given** un fallo al guardar, **When** el usuario pulsa «Guardar preferencias», **Then** ve un aviso de error, se mantienen los valores anteriores y puede volver a intentarlo.
3. **Given** un fallo al leer las preferencias, **When** se abre la app, **Then** se usan los valores por defecto, Idiomas avisa y el resto de la app funciona.

---

### User Story 7 - Pantallas fieles al PNG, accesibles y traducidas (Priority: P2)

Ajustes e Idiomas se ven como los PNG 09 y 10, se usan con TalkBack, con la letra al 200 %, en horizontal y en modo oscuro, y todos los textos de la app tienen una versión en inglés revisada.

**Why this priority**: criterios 8 y 9 de F12 y constitución VII.

**Independent Test**:
- capturas comparadas con los PNG;
- comprobaciones automáticas de accesibilidad en claro y en oscuro, con la letra al 200 % y en horizontal;
- revisión de la paridad y de las traducciones de todos los textos.

**Acceptance Scenarios**:

1. **Given** los dos modelos listos y la app en español, **When** se captura Ajustes, **Then** coincide con el PNG 09 en estructura, textos, colores y tipografía, salvo las diferencias documentadas.
2. **Given** Idiomas, **When** se captura, **Then** coincide con el PNG 10 salvo las diferencias documentadas.
3. **Given** TalkBack, **When** el usuario recorre Idiomas, **Then** cada opción se anuncia como botón de opción con su grupo y su estado («seleccionado» o no), y los títulos de sección como encabezados.
4. **Given** la letra al 200 % o el teléfono en horizontal, **When** el usuario recorre las dos pantallas, **Then** puede leer y alcanzar todas las filas y «Guardar preferencias», desplazándose si hace falta.
5. **Given** la app en inglés, **When** el usuario recorre cualquier pantalla, **Then** no aparece ningún texto en español salvo los nombres propios y el contenido de sus documentos.

---

### User Story 8 - Búsqueda multilingüe sin promesas que no se cumplen (Priority: P3)

La tarjeta «Búsqueda multilingüe» de Idiomas informa de lo que la app puede hacer con documentos y preguntas en otro idioma, sin prometer más de lo que se ha medido.

**Why this priority**: el MD pide mensajes honestos sobre el soporte multilingüe, y la consulta entre idiomas está sin medir (pendiente en DECISIONS).

**Independent Test**: el texto de la tarjeta se contrasta con las cifras registradas en DECISIONS.

**Acceptance Scenarios**:

1. **Given** Idiomas, **When** el usuario lee la tarjeta «Búsqueda multilingüe», **Then** es informativa, sin interruptor ni acción, y su texto es el de FR-032.

---

### Edge Cases

- **Cambiar el idioma de la app con una respuesta en curso:** la respuesta sigue sin reiniciarse, como al girar el teléfono (F11). Sus avisos siguientes salen en el idioma nuevo.
- **Cambiar el idioma de la app durante una indexación o una descarga de modelos:** siguen sin interrumpirse. Sus notificaciones pasan al idioma nuevo en cuanto se actualizan.
- **Guardar sin cambios:** vuelve a la pantalla de origen sin escribir nada.
- **Pulsar «Guardar preferencias» dos veces seguidas:** se guarda una sola vez.
- **Abrir Idiomas desde Inicio y desde Ajustes a la vez** (Idiomas abierto desde Inicio y se toca la pestaña Ajustes): cada pila de pestañas conserva lo suyo, y nunca hay dos pantallas de Idiomas en la misma pila.
- **El sistema cambia el idioma de la app en Android 13+ con Idiomas abierto:** Idiomas muestra el idioma nuevo.
- **Borrar el historial con el chat abierto en una conversación:** al volver, el chat está vacío y no muestra la conversación borrada.
- **Borrar el historial mientras se genera una respuesta en otra pestaña:** se detiene primero. No queda ninguna respuesta guardada a medias.
- **Fallo al borrar el historial:** aviso de error; las conversaciones siguen todas o se han borrado todas, nunca solo una parte.
- **Conversaciones guardadas ilegibles** (C17 de F11): «Borrar historial de chat» intenta borrarlas. Si no puede, avisa (FR-014) y no toca nada más.
- **Modelo que cambia de estado con su ficha abierta:** la ficha se actualiza.
- **Fichero de modelo borrado por fuera de la app:** la fila deja de mostrar «Local» al refrescar el estado, como en la Preparación.
- **Gemma preparada en GPU y la de embeddings en CPU** (o al revés): cada fila muestra su propio procesador. Solo Gemma lleva el aviso «Modo CPU, más lento» (D-027).
- **Nombres de idioma largos o letra grande:** las filas crecen en alto y el texto no se corta.
- **Pregunta en un idioma distinto del de la respuesta elegida:** con «Español», la respuesta pide español aunque la pregunta esté en inglés.

## Requirements *(mandatory)*

### Functional Requirements

**Ajustes: pantalla y navegación**

- **FR-001**: Ajustes MUST sustituir al marcador actual en la pestaña Ajustes, con la estructura del PNG 09:
  - barra superior «Configuración» con Atrás;
  - título «Ajustes» y texto «Personaliza tu experiencia local.»;
  - secciones «INTELIGENCIA ARTIFICIAL», «PREFERENCIAS» y «PRIVACIDAD Y DATOS»;
  - barra inferior con Ajustes seleccionada.
- **FR-002**: La flecha Atrás y el gesto Atrás de Ajustes MUST llevar a Inicio, como en las otras pestañas (G08).
- **FR-003**: Ajustes MUST mostrar solo las filas del PNG. Las del MD que el PNG no tiene (protección de la biblioteca, almacenamiento, batería, información y licencias) quedan fuera (G23). Ninguna fila MUST ofrecer una opción sin efecto real.

**Modelos**

- **FR-004**: Las filas «Modelo de respuestas / Gemma 4» y «Modelo de embeddings / EmbeddingGemma 2» MUST mostrar el estado real de su modelo, del mismo origen que la Preparación:
  - «Local» solo si el fichero está verificado y el modelo preparado (constitución V);
  - si Gemma solo está preparada en CPU, «Modo CPU, más lento» (D-027);
  - en cualquier otro estado, el mismo texto que la Preparación.
- **FR-005**: Las filas MUST actualizarse mientras Ajustes está abierto, cuando cambia el estado de un modelo.
- **FR-006**: La ficha de un modelo MUST mostrar el nombre, el propósito, el fichero, el tamaño, el SHA-256 abreviado, las dimensiones (256 en el de embeddings), el procesador preparado (GPU o CPU) y el estado. [NEEDS CLARIFICATION: ¿qué filas abren la ficha (las dos, o solo la de embeddings como en el PNG) y qué acciones ofrece (solo lectura con acceso a la Preparación, o también volver a verificar el SHA-256)?]
- **FR-007**: El SHA-256 de la ficha MUST ser el publicado para ese modelo, el mismo con el que se verificó el fichero (D-011). Si el fichero no está verificado, la ficha MUST decirlo.
- **FR-008**: Si un modelo no está listo, su ficha MUST ofrecer un acceso a la Preparación, que ya descarga, importa y verifica (D-046).

**Preferencias y privacidad**

- **FR-009**: La fila «Idiomas» MUST abrir Idiomas. Su subtítulo MUST ser el nombre del idioma de la app («Español» o «English»).
- **FR-010**: La fila «Índice y rendimiento» MUST abrir la pantalla de Indexación existente, y Atrás MUST volver a Ajustes.
- **FR-011**: «Procesamiento 100% local / Sin enviar documentos a la nube» MUST ser un indicador informativo, con el chip «Activo», sin acción y nunca un interruptor.
- **FR-012**: «Borrar historial de chat» MUST pedir confirmación. Al confirmar, MUST:
  - detener primero la respuesta en curso, si la hay;
  - eliminar del teléfono todas las conversaciones con sus mensajes y fuentes;
  - dejar intactos los documentos, el índice y las preferencias;
  - dejar el chat vacío y usable: la siguiente pregunta y su respuesta se guardan con normalidad.
- **FR-013**: Sin conversaciones guardadas, «Borrar historial de chat» MUST mostrar un aviso en lugar del diálogo.
- **FR-014**: Si el borrado falla, la app MUST avisar, y las conversaciones MUST quedar todas o ninguna.

**Idiomas: pantalla**

- **FR-015**: Idiomas MUST sustituir al marcador actual, con la estructura del PNG 10:
  - barra superior «Idiomas» con Atrás, sin barra inferior;
  - «CONFIGURACIÓN LINGÜÍSTICA» y el título «Habla en tu idioma»;
  - la tarjeta «Tus documentos. / Tu idioma.»;
  - «IDIOMA DE LA APLICACIÓN» con «Español» y «English»;
  - «IDIOMA DE LAS RESPUESTAS» con «Automático / Detectar idioma de la pregunta» y «Español»;
  - la tarjeta «Búsqueda multilingüe» (FR-032);
  - el botón «Guardar preferencias».
- **FR-016**: Idiomas MUST abrirse desde Ajustes y desde Inicio. «Guardar preferencias» MUST guardar las dos elecciones y volver a la pantalla de origen. Atrás sin guardar MUST descartar los cambios.
- **FR-017**: Idiomas MUST ofrecer solo esas opciones, sin interruptores para funciones que no existen (G25, MD 10). Al abrirse, MUST marcar los valores guardados.

**Idioma de la app**

- **FR-018**: La app MUST mostrarse en español mientras el usuario no elija otro idioma. El idioma del sistema por sí solo nunca la cambia (D-029).
- **FR-019**: Al guardar otro idioma, todos los textos de la app MUST pasar a ese idioma: pantallas, avisos, frases de TalkBack y notificaciones. El cambio MUST hacerse sin perder el estado: la pestaña, la conversación abierta y la respuesta en curso. Tampoco MUST lanzar ninguna indexación.
- **FR-020**: La elección MUST funcionar en todas las versiones de Android que soporta la app (12 o superior). En Android 13+, MUST quedar coherente en los dos sentidos con el ajuste de idioma por app del sistema.
- **FR-021**: El idioma de la app y el de las respuestas MUST ser independientes.

**Idioma de las respuestas**

- **FR-022**: El idioma de las respuestas MUST guardarse y aplicarse al prompt de la siguiente pregunta. Una respuesta en curso no cambia.
- **FR-023**: Con «Español», el prompt MUST ser idéntico, carácter a carácter, al del escritorio, sea cual sea el idioma de la app (SC-002 de F11).
- **FR-024**: «Automático» MUST pedir al modelo que responda en el idioma de la pregunta (G25). [NEEDS CLARIFICATION: ¿qué hace «Automático» exactamente, con qué evaluación se adopta y cuál es el valor por defecto, «Español» (F11, D-055) o «Automático» (marcado en el PNG)?]
- **FR-025**: La redacción del prompt MUST cambiar solo con una evaluación documentada en el escritorio y una entrada en DECISIONS (constitución III).
- **FR-026**: La negativa del modelo MUST reconocerse en el idioma en que la escriba con la opción elegida. Se muestra con el texto de negativa de F11 en el idioma de la app, sin fuentes (C1 de F11).

**Persistencia**

- **FR-027**: Las preferencias MUST guardarse en el almacenamiento privado del teléfono, sin copia de seguridad, y mantenerse tras cerrar la app (constitución I).
- **FR-028**: Si guardar falla, MUST mantenerse los valores anteriores, avisar y permitir reintentar. Si leer falla, MUST usarse los valores por defecto (español y español), avisar en Idiomas y seguir funcionando el resto de la app.

**Textos, accesibilidad y privacidad**

- **FR-029**: Todos los textos nuevos MUST estar en español y en inglés en los recursos de la app. Los nombres de los idiomas de la app MUST mostrarse en su propio idioma («Español», «English»). Las traducciones al inglés de todos los textos de la app MUST revisarse, y las correcciones se listan en el informe de cierre.
- **FR-030**: Las dos pantallas MUST:
  - tener objetivos táctiles de 48 dp;
  - marcar los títulos de sección como encabezados;
  - anunciar cada opción con su rol, su grupo y su estado;
  - leer los chips de estado;
  - anunciar con TalkBack «Guardando», «Preferencias guardadas», el error al guardar y «Historial borrado»;
  - funcionar con la letra al 200 %, en horizontal y en modo oscuro.
- **FR-031**: Ajustes e Idiomas MUST funcionar sin red, y los registros MUST NOT contener contenido privado (constitución I).
- **FR-032**: La tarjeta «Búsqueda multilingüe» MUST ser informativa, sin interruptor ni acción. [NEEDS CLARIFICATION: ¿se mide en F12 la consulta con documentos y preguntas en otro idioma, y qué texto lleva la tarjeta: el del PNG o uno ajustado a lo medido?]

### Key Entities

- **Preferencias de idioma:** el idioma de la app («Español» o «English») y el de las respuestas («Automático» o «Español»). Tienen valores por defecto (FR-018, FR-024) y se guardan en el teléfono.
- **Ficha de modelo:** vista de un modelo del catálogo con su nombre, propósito, fichero, tamaño, SHA-256 publicado, dimensiones, procesador preparado y estado real. Se lee; F12 no la modifica.
- **Historial de chat:** todas las conversaciones guardadas por F11, con sus mensajes y fuentes. F12 solo lo borra entero.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En las pruebas con cada estado de modelo (listo en GPU, solo en CPU, sin descargar, descargando, verificando, incompatible, verificación fallida), la fila muestra «Local» solo cuando el modelo está verificado y preparado: 0 casos de «Local» sin verificación.
- **SC-002**: El fichero, el tamaño y el SHA-256 de la ficha coinciden con los del modelo instalado en el 100 % de las pruebas.
- **SC-003**: Tras cambiar el idioma de la app, en Android 12 y en Android 13+:
  - el 100 % de los textos de las pantallas recorridas salen en el idioma elegido;
  - la conversación abierta se conserva;
  - no se lanza ninguna indexación;
  - el idioma se mantiene tras cerrar la app.
- **SC-004**: Con «Español», el prompt es idéntico al del escritorio para todas las preguntas del conjunto de evaluación, con la app en español y en inglés.
- **SC-005**: Con cada opción guardada, el 100 % de las preguntas siguientes usan la regla de idioma de esa opción, y la negativa se reconoce con cada una.
- **SC-006**: Tras «Borrar historial de chat» confirmado:
  - quedan 0 conversaciones guardadas;
  - el número de documentos y de fragmentos indexados no cambia;
  - una respuesta en curso se detiene en menos de 1 s;
  - la siguiente pregunta se guarda.
- **SC-007**: Con fallos simulados al guardar y al leer, en el 100 % de los casos se conservan los valores anteriores (o los por defecto al leer), aparece el aviso y la app sigue funcionando.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en Ajustes, en la ficha de modelo, en Idiomas y en el diálogo de borrado, en claro y en oscuro, con la letra al 200 % y en horizontal.
- **SC-009**: Los textos en español y en inglés tienen las mismas claves (100 % de paridad), y la revisión de las traducciones al inglés queda en el informe de cierre.
- **SC-010**: Ajustes e Idiomas no hacen ninguna llamada de red, y los registros de una sesión de prueba no contienen contenido privado.
- **SC-011**: Las capturas del teléfono coinciden con los PNG 09 y 10 en estructura, textos, colores y tipografía, salvo las diferencias documentadas en el informe de cierre.

## Assumptions

- **Propuestas de UI para estados sin PNG** (constitución VII: se aprueban antes de implementar). Los textos son propuestas en español; la versión inglesa se traduce igual:
  - **S1 — Chip de estado de los modelos:** las dos filas llevan chip. Con el modelo listo dice «Local», en color de éxito como en el PNG. Con Gemma solo en CPU dice «Modo CPU, más lento», en color de aviso. En los demás estados lleva el chip de la Preparación («Por configurar», «Descargando N %», «Verificando», «Incompatible», «Error»…). El PNG solo muestra chip en «Modelo de respuestas»; se añade al de embeddings para no ocultar su estado.
  - **S2 — Ficha de modelo** (contenido final según FR-006): hoja inferior con el nombre y el propósito, y las filas:
    - «Fichero»;
    - «Tamaño»;
    - «SHA-256»: los 12 primeros caracteres seguidos de «…»;
    - «Dimensiones»: «256», solo en el de embeddings;
    - «Procesador»: «GPU» o «CPU»;
    - «Estado».

    Si el modelo no está listo, lleva el botón «Completar configuración», que abre la Preparación.
  - **S3 — Subtítulo de «Idiomas»:** el nombre del idioma de la app, «Español» o «English».
  - **S4 — «Índice y rendimiento»:** abre la Indexación existente y mantiene el subtítulo del PNG.
  - **S5 — «Procesamiento 100% local»:** TalkBack lo lee como «Procesamiento 100 % local: activo. Sin enviar documentos a la nube.», sin acción.
  - **S6 — «Borrar historial de chat»:**
    - icono en rojo y título en el color normal del texto, como en el PNG;
    - diálogo «¿Borrar el historial de chat?», con el texto «Se eliminarán todas tus conversaciones de este teléfono. Tus documentos y el índice no cambian.» y los botones «Cancelar» (foco por defecto) y «Borrar»;
    - con una respuesta en curso, el texto añade «La respuesta en curso se detendrá.»;
    - al terminar, el aviso «Historial borrado»;
    - sin conversaciones, el aviso «No hay conversaciones guardadas.»;
    - si falla, el aviso «No se pudo borrar el historial.».
  - **S7 — Idiomas:**
    - al abrirse, marca lo guardado;
    - «Guardar preferencias» guarda y vuelve a la pantalla de origen (Ajustes o Inicio);
    - Atrás sin guardar descarta los cambios sin preguntar.
  - **S8 — Guardado y errores:**
    - mientras guarda, el botón dice «Guardando…» y no se puede pulsar;
    - si falla, el aviso «No se pudieron guardar las preferencias. Se mantienen las anteriores.», y el usuario puede volver a pulsar;
    - si falla la lectura, Idiomas muestra «No se pudieron leer tus preferencias. Se usan las de por defecto.».
  - **S9 — Cambio del idioma de la app:** al guardar, la app se redibuja en el idioma nuevo y vuelve a la pantalla de origen. TalkBack anuncia «Preferencias guardadas» en el idioma nuevo.
  - **S10 — Tarjeta «Búsqueda multilingüe»:** el texto según FR-032.
  - **S11 — Nombres de opciones con la app en inglés:** los idiomas de la app siguen como «Español» y «English». Las respuestas pasan a «Automatic / Detect the question's language» y «Spanish».
- **Ficha de modelo:** solo muestra los dos modelos de producto (Gemma 4 E2B y EmbeddingGemma 2 270M). Los modelos de diagnóstico de debug no aparecen.
- **Idioma de la app:** español e inglés. Otros idiomas se añaden después por el mismo mecanismo (D-029).
- **Revisión del inglés:** la hace quien desarrolla sobre todos los textos de la app. Las correcciones se listan en el informe de cierre para que el usuario las revise.
- **Fuera de alcance:**
  - protección de la biblioteca, gestión del almacenamiento, batería, información y licencias: al backlog (G23); las licencias, en F15;
  - Português en la app, inglés como idioma fijo de respuesta y un detector de idioma: al backlog (G25);
  - selector de tema (D-016) y elección del procesador (D-039);
  - lista e historial de conversaciones (F13);
  - INT8 y palancas de rendimiento (F14);
  - exportar y restaurar (D-007);
  - cambiar de modelo de embeddings, y por tanto reindexar desde Ajustes.
- **Dependencias:**
  - F03: estado real de los modelos, Preparación e idioma de la app por defecto;
  - F07: pantalla de Indexación;
  - F09: acceso «Idiomas» en Inicio;
  - F11: conversaciones guardadas e idioma de la respuesta como parámetro del prompt.
