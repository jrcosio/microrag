# Especificación de feature: Apariencia: tema e icono de Ajustes (F16)

**Feature Branch**: `016-appearance-theme`

**Created**: 2026-11-03

**Status**: Draft

**Input**: Descripción del usuario: «F16 — Apariencia: tema e icono de Ajustes (docs/roadmap/FEATURES.md §F16, D-066, G45). (1) El icono de Ajustes pasa de sol a engranaje (Lucide `settings`) en la barra inferior, la cabecera de Inicio y el catálogo; sustituye G19. (2) Preferencia de tema de la app: Sistema (por defecto), Claro u Oscuro, en una fila «Tema» de Preferencias en Ajustes que abre una pantalla propia como Idiomas; se aplica a toda la app, a la pantalla de arranque y a las barras del sistema; persiste tras reiniciar; si falla el guardado se mantiene el valor anterior y se avisa; sustituye la parte «sin selector propio» de D-016. Sin mockup: la UI se propone (wireframe con componentes Lisdata*) y se aprueba antes del plan.» Ver `docs/roadmap/FEATURES.md` (F16).

**Fuentes**:
- **Sin mockup para el selector.** El PNG 09 no tiene fila de tema y no hay pantalla de tema. La UI se propone con el design system y el usuario la aprueba antes de implementarla (constitución VII, D-004, G45). Los dibujos están en [wireframe.md](wireframe.md):
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/09-configuracion.png` y `screens/09-settings.md`: sección Preferencias, donde va la fila nueva. El MD pide persistir cada preferencia, reflejarla en la UI y mantener el valor anterior si falla («Mantener valores previos cuando una actualización falle.»);
  - `10-idiomas.png` y `screens/10-languages.md`: patrón de la pantalla de selección (F12).
- `docs/disegn_app/docs/`:
  - `00-design-system.md`: «Dark mode: derivar roles semánticos; no invertir todos los hex» y «Validar contrastes en ambos temas»;
  - `03-components.md`: vistas previas en claro, oscuro, texto largo y letra grande;
  - `04-qa-checklist.md`: «Dark mode y escalado de texto comprobados».
- `docs/roadmap/INVENTARIO.md`:
  - §2.2: paleta oscura derivada de los tokens navy;
  - §2.6: `ic_settings` = Lucide `sun`, «El PNG usa un sol, no un engranaje (G19)»;
  - G07 (modo oscuro derivado), G19 (icono de Ajustes) y G45 (selector de tema, abierto).
- Constitución v1.1.1:
  - I: sin red; nada sale del teléfono;
  - II: las APIs de plataforma se verifican, nunca se suponen;
  - IV: nada de disco ni de inferencia en el hilo principal;
  - V: la UI refleja el estado real;
  - VI: pruebas de los ViewModels y de la capa de datos;
  - VII: lo no maquetado se propone y se aprueba antes; tokens, nunca hex; accesibilidad y textos en recursos.
- DECISIONS:
  - D-004: en UI manda el PNG; lo no maquetado se propone antes;
  - D-016: modo oscuro derivado, «sigue el ajuste del sistema, sin selector propio». F16 sustituye esa parte;
  - D-059: el idioma de la app se aplica sin AppCompat;
  - D-066: F16 entra en el roadmap con el tema en una pantalla propia como Idiomas y el engranaje para Ajustes.
- FEATURES: F16 (criterios 1–8 y riesgos), F12 (criterio 6: si falla la persistencia, se mantienen los valores previos) y F15 («modo oscuro revisado»).
- Lo ya construido:
  - F01: los dos temas, claro y oscuro, con sus tokens y sus pruebas de contraste; el catálogo de depuración tiene su propio interruptor Sistema/Claro/Oscuro;
  - F12: Ajustes con sus secciones y la pantalla Idiomas, que guarda con «Guardar preferencias», avisa si no puede guardar o leer, y conserva la respuesta en curso al recrear la pantalla.

**Verificado al redactar** (2026-11-03):
- **Icono:** `ic_settings` es el Lucide `sun` (círculo y ocho rayos), generado desde `tools/icons/icons.txt` con la línea `ic_settings = sun`. Se usa en la barra inferior (pestaña Ajustes), en el botón de Ajustes de la cabecera de Inicio y en el catálogo y las vistas previas. En el repo no hay ningún engranaje.
- **Tema:** la app sigue siempre el modo oscuro del teléfono. No hay preferencia guardada ni forma de elegirlo, salvo en el catálogo de depuración, que no existe en release.
- **Arranque y barras del sistema:** la pantalla de arranque toma su color del modo del teléfono, y las barras del sistema eligen el color de sus iconos también según el teléfono. Si la app pintara otro tema que el del teléfono, el arranque y las barras no lo seguirían.
- **Plataforma:** desde Android 12 (el mínimo de la app) el sistema ofrece un ajuste de modo oscuro propio de cada app, que el sistema guarda y aplica antes de pintar la primera pantalla. El plan lo verifica (constitución II).
- **Preferencias guardadas:** hoy no hay ningún almacén de ajustes; el idioma de la app lo guarda el sistema (F12, D-059).
- **Idiomas** (F12) es el patrón: eyebrow, titular, una lista de opciones de radio en tarjeta, «Guardar preferencias» (con «Guardando…»), aviso «Preferencias guardadas» al volver a Ajustes, aviso en la propia pantalla si no se pudo guardar y tarjeta si no se pudo leer.
- **Componentes disponibles:** fila de ajustes con icono, título, subtítulo y chevron; fila de radio; grupo de tarjeta; cabecera de sección; tarjeta de aviso; botón con degradado; barra superior con Atrás; aviso inferior.

## Clarifications

### Session 2026-11-03

- Q: En la pantalla Tema, ¿el tema elegido se aplica al tocar «Guardar preferencias» o al marcar la opción? → A: Al guardar, como Idiomas. Marcar una opción no cambia nada; «Guardar preferencias» aplica el tema, vuelve a Ajustes y anuncia «Preferencias guardadas»; Atrás sin guardar no cambia nada (FR-006, FR-007, A3).
- Q: ¿Se aprueban la spec, el wireframe y las propuestas A1–A9? → A: Sí (revisión del clarify, 2026-11-03). Queda en D-067.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Usuario:** quiere ver la app en claro u oscuro aunque el teléfono esté en el otro modo, o dejar que siga al teléfono.

### User Story 1 - Elegir el tema de la app (Priority: P1)

En Ajustes, el usuario ve la fila «Tema» en Preferencias con el tema actual («Sistema», «Claro» u «Oscuro»). La toca, elige otro tema en una pantalla propia y lo guarda. Vuelve a Ajustes con la app ya en el tema elegido.

**Why this priority**: es el objetivo de la feature (D-066). Hoy la app no deja elegir: sigue siempre al teléfono (D-016).

**Independent Test**: con el teléfono en claro y en oscuro, elegir cada uno de los tres temas y guardarlo:
- toda la app pasa al tema elegido;
- la fila «Tema» muestra el valor guardado;
- al volver a la pantalla Tema, la opción guardada está marcada.

**Acceptance Scenarios**:

1. **Given** Ajustes, **When** el usuario mira la sección Preferencias, **Then** ve la fila «Tema» con el tema actual como subtítulo, junto a «Idiomas» e «Índice y rendimiento».
2. **Given** la fila «Tema», **When** el usuario la toca, **Then** se abre la pantalla Tema con tres opciones, «Sistema», «Claro» y «Oscuro», y la actual marcada.
3. **Given** la pantalla Tema con otra opción marcada, **When** el usuario toca «Guardar preferencias», **Then** la app pasa al tema elegido, vuelve a Ajustes y anuncia «Preferencias guardadas».
4. **Given** la pantalla Tema, **When** el usuario marca otra opción y pulsa Atrás sin guardar, **Then** vuelve a Ajustes y el tema no cambia.
5. **Given** la app recién instalada o actualizada, **When** el usuario abre la pantalla Tema por primera vez, **Then** está marcada «Sistema».
6. **Given** el tema guardado es el actual, **When** el usuario toca «Guardar preferencias» sin cambiar nada, **Then** vuelve a Ajustes sin cambiar nada.

---

### User Story 2 - El tema se mantiene y llega a todas partes (Priority: P1)

El tema elegido se ve en todas las pantallas, en los diálogos, en las hojas inferiores, en la pantalla de arranque y en las barras del sistema. Se mantiene al cerrar la app y al reiniciar el teléfono. Con «Sistema», la app cambia sola cuando el usuario cambia el modo oscuro del teléfono.

**Why this priority**: criterios 3 y 5 de F16. Un tema que se pierde al reiniciar, o que deja ver el otro tema al arrancar, no sirve.

**Independent Test**: con cada combinación de tema de la app (Claro u Oscuro) y modo del teléfono (claro u oscuro):
- cerrar la app, matar el proceso y reabrirla: la pantalla de arranque y la primera pantalla ya salen en el tema elegido;
- reiniciar el teléfono: igual;
- recorrer las pestañas, un diálogo y una hoja inferior: todo en el tema elegido, con las barras del sistema legibles.

**Acceptance Scenarios**:

1. **Given** el tema «Oscuro» con el teléfono en claro, **When** el usuario abre la app en frío, **Then** la pantalla de arranque, la primera pantalla y las barras del sistema salen en oscuro, sin ver antes el claro.
2. **Given** el tema «Claro» con el teléfono en oscuro, **When** el usuario abre la app en frío, **Then** todo sale en claro, sin ver antes el oscuro, y los iconos de las barras del sistema se leen sobre el fondo claro.
3. **Given** el tema «Sistema», **When** el usuario cambia el modo oscuro del teléfono con la app abierta, **Then** la app pasa al nuevo modo sin cerrarse.
4. **Given** el tema «Claro» u «Oscuro», **When** el usuario cambia el modo oscuro del teléfono, **Then** la app no cambia.
5. **Given** un tema guardado, **When** el usuario reinicia el teléfono y abre la app, **Then** sigue en ese tema.
6. **Given** cualquier tema, **When** se muestran el onboarding, el chat, la Biblioteca, el detalle, la Indexación, el Historial, los diálogos de confirmación, las hojas inferiores y los avisos, **Then** todos usan los colores de ese tema.

---

### User Story 3 - El engranaje de Ajustes (Priority: P1)

El icono de Ajustes es un engranaje en la barra inferior y en el botón de Ajustes de la cabecera de Inicio, en lugar del sol del mockup.

**Why this priority**: es la mitad del encargo y la convención de los teléfonos. El sol se confunde con un ajuste de brillo o, a partir de esta feature, con el tema (D-066).

**Independent Test**: recorrer la barra inferior y la cabecera de Inicio en claro y en oscuro: el engranaje aparece en los dos sitios con el mismo trazo que los demás iconos, y TalkBack sigue diciendo «Ajustes».

**Acceptance Scenarios**:

1. **Given** cualquier pestaña, **When** el usuario mira la barra inferior, **Then** la pestaña «Ajustes» muestra un engranaje, seleccionada o no.
2. **Given** Inicio, **When** el usuario mira la cabecera, **Then** el botón de Ajustes muestra el mismo engranaje, y TalkBack lo lee como «Ajustes».
3. **Given** el catálogo de componentes de depuración, **When** se muestra la rejilla de iconos, **Then** aparece el engranaje y ya no hay ningún sol.

---

### User Story 4 - Un fallo al guardar o al leer no rompe nada (Priority: P2)

Si la app no puede guardar el tema, sigue con el anterior y lo dice. Si no puede leer el guardado, usa «Sistema» y lo dice en la pantalla Tema.

**Why this priority**: criterio 6 de F16, igual que el criterio 6 de F12, y `09-settings.md`: «Mantener valores previos cuando una actualización falle».

**Independent Test**: con un fallo simulado al guardar y otro al leer:
- al guardar, el tema no cambia, el aviso aparece y la pantalla sigue abierta con la opción elegida marcada;
- un segundo intento que sí funciona guarda;
- al leer, la app usa «Sistema» y la pantalla Tema muestra la tarjeta de aviso.

**Acceptance Scenarios**:

1. **Given** un fallo al guardar, **When** el usuario toca «Guardar preferencias», **Then** el tema sigue siendo el anterior, la pantalla Tema sigue abierta con la opción elegida marcada, y un aviso dice «No se pudieron guardar las preferencias. Se mantienen las anteriores.».
2. **Given** ese fallo, **When** el usuario vuelve a intentarlo y el guardado funciona, **Then** el tema cambia como en US1.
3. **Given** un fallo al leer el tema guardado, **When** se abre la app, **Then** usa «Sistema»; **When** el usuario abre la pantalla Tema, **Then** ve «No se pudieron leer tus preferencias. Se usan las de por defecto.» y «Sistema» marcada.

---

### User Story 5 - Cambiar el tema sin perder nada (Priority: P2)

Cambiar el tema no interrumpe lo que esté pasando: la respuesta del chat sigue generándose, la indexación sigue, y al volver el usuario encuentra la misma pestaña, la misma conversación y el texto que estaba escribiendo.

**Why this priority**: criterio 4 de F16. Cambiar el tema recrea las pantallas, igual que cambiar el idioma en F12, y eso no puede costar una respuesta ni una indexación.

**Independent Test**: con una respuesta generándose y una tanda de indexación en marcha (por separado), cambiar el tema: la respuesta termina con sus citas, la indexación termina, y el chat conserva su conversación y el texto del compositor.

**Acceptance Scenarios**:

1. **Given** una respuesta generándose, **When** el usuario cambia el tema en Ajustes, **Then** la respuesta sigue y termina con sus citas, y la pestaña Chat la muestra al volver.
2. **Given** una tanda de indexación en marcha, **When** el usuario cambia el tema, **Then** la tanda sigue y la notificación no cambia.
3. **Given** texto escrito y sin enviar en el compositor del chat, **When** el usuario cambia el tema y vuelve al chat, **Then** el texto sigue ahí.
4. **Given** cualquier cambio de tema, **When** termina, **Then** no hay reindexación, ni nueva descarga, ni recarga de modelos que no se hubiera producido igualmente.

---

### User Story 6 - Pantalla aprobada, accesible y traducida (Priority: P2)

La fila «Tema» y la pantalla Tema siguen el wireframe aprobado por el usuario, funcionan con TalkBack, con la letra al 200 %, en horizontal y en los dos temas, y tienen sus textos en español y en inglés.

**Why this priority**: criterios 1, 7 y 8 de F16 y constitución VII.

**Independent Test**:
- el wireframe y las propuestas A tienen la aprobación del usuario antes de `/speckit-plan`;
- las comprobaciones automáticas de accesibilidad pasan en Ajustes y en Tema, en claro y en oscuro;
- las capturas del teléfono coinciden con el wireframe aprobado.

**Acceptance Scenarios**:

1. **Given** TalkBack en Ajustes, **When** el usuario llega a la fila «Tema», **Then** la oye como una frase con el tema actual («Tema, Sistema») y la acción de abrirla.
2. **Given** TalkBack en la pantalla Tema, **When** el usuario recorre las opciones, **Then** cada una se lee como opción de radio con su estado («Oscuro, seleccionado») y su subtítulo si lo tiene.
3. **Given** la letra al 200 % o el teléfono en horizontal, **When** se abren Ajustes y Tema, **Then** nada se corta y todo se alcanza desplazando.
4. **Given** la app en inglés, **When** se abren Ajustes y Tema, **Then** todos sus textos salen en inglés.

---

### Edge Cases

- **Tema guardado igual al del teléfono:** «Oscuro» con el teléfono en oscuro se ve igual que «Sistema», pero no cambia si el teléfono pasa a claro.
- **Programación automática del modo oscuro del teléfono** (por horario o al atardecer): con «Sistema», la app cambia cuando cambia el teléfono; con «Claro» u «Oscuro», no.
- **Ahorro de batería que fuerza el modo oscuro:** con «Sistema», la app lo sigue; con «Claro», se queda en claro.
- **Cambio del modo del teléfono con «Sistema» y un diálogo, una hoja inferior o un menú abiertos:** siguen abiertos, ya en el nuevo tema (como al girar, FR-024 de F13).
- **Tocar dos veces seguidas «Guardar preferencias»:** se guarda una sola vez y se vuelve una sola vez a Ajustes.
- **Girar el teléfono en la pantalla Tema con una opción marcada sin guardar:** la opción sigue marcada.
- **Cambiar de tema durante el onboarding:** no es posible, porque Ajustes no se alcanza hasta completarlo. El onboarding usa «Sistema» o el tema guardado si ya existía.
- **Cambio de idioma de la app después de elegir tema:** el tema se mantiene, y los nombres de los temas cambian de idioma.
- **Actualización de la app desde una versión sin tema:** arranca en «Sistema», igual que ahora.
- **Borrar los datos de la app desde los ajustes del teléfono:** el tema vuelve a «Sistema», como el resto de la app.
- **Fallo al guardar con el tema anterior también fallido de leer:** se sigue en «Sistema» y se avisa del fallo al guardar.
- **Catálogo de depuración:** su interruptor Sistema/Claro/Oscuro sigue funcionando para sus vistas previas y no cambia el tema guardado de la app.
- **El engranaje con la pestaña Ajustes seleccionada:** se ve con el mismo color de selección que los demás iconos de la barra.

## Requirements *(mandatory)*

### Functional Requirements

**Icono de Ajustes**

- **FR-001**: El icono de Ajustes MUST ser un engranaje del mismo conjunto de iconos lineales que el resto (Lucide), en la barra inferior, en el botón de Ajustes de la cabecera de Inicio y en el catálogo y las vistas previas. MUST NOT quedar ningún sol en la app.
- **FR-002**: Las etiquetas y descripciones de TalkBack de Ajustes MUST NOT cambiar («Ajustes»).
- **FR-003**: La fuente del mapeo de iconos y el inventario (§2.6 y G19) MUST quedar actualizados con el engranaje y el motivo (D-066), y el procedimiento documentado para regenerar los iconos MUST funcionar tal como está escrito.

**Preferencia de tema**

- **FR-004**: La app MUST ofrecer tres temas: «Sistema» (sigue el modo oscuro del teléfono), «Claro» y «Oscuro». El valor por defecto MUST ser «Sistema», también al actualizar desde una versión anterior.
- **FR-005**: La sección Preferencias de Ajustes MUST tener una fila «Tema» con el tema guardado como subtítulo, que abre la pantalla Tema (A1).
- **FR-006**: La pantalla Tema MUST mostrar las tres opciones como opciones de radio en un grupo, con la guardada marcada al abrirla, y MUST guardar solo al tocar «Guardar preferencias», como Idiomas (A2, A3). Atrás sin guardar MUST NOT cambiar nada. La opción marcada sin guardar MUST conservarse al girar el teléfono.
- **FR-007**: Al guardar, el tema MUST aplicarse al momento en toda la app, la pantalla Tema MUST cerrarse y Ajustes MUST anunciar «Preferencias guardadas». Guardar el mismo tema que ya hay MUST cerrar la pantalla sin cambiar nada.
- **FR-008**: El tema guardado MUST mantenerse al cerrar la app, al matar su proceso y al reiniciar el teléfono.

**Alcance del tema**

- **FR-009**: El tema MUST aplicarse a todas las pantallas, diálogos, hojas inferiores, menús y avisos de la app, con los tokens claros u oscuros del design system, sin colores nuevos (constitución VII, D-016).
- **FR-010**: La pantalla de arranque MUST salir en el tema guardado, y MUST NOT verse un instante el otro tema al abrir la app en frío.
- **FR-011**: Los iconos de las barras del sistema (estado y navegación) MUST seguir el tema de la app, y MUST leerse sobre su fondo en los dos temas.
- **FR-012**: Con «Sistema», la app MUST seguir los cambios del modo oscuro del teléfono mientras está abierta. Con «Claro» u «Oscuro», MUST ignorarlos.

**Cambiar el tema sin perder nada**

- **FR-013**: Cambiar el tema MUST NOT detener ni perder una respuesta en curso ni una tanda de indexación, y MUST NOT provocar una reindexación, una descarga o una recarga de modelos.
- **FR-014**: Tras cambiar el tema, desde la pantalla Tema o, con «Sistema», desde el teléfono, la app MUST conservar la pestaña, la conversación abierta, el texto sin enviar del compositor, la posición de desplazamiento y los diálogos y menús abiertos, como al girar el teléfono.

**Fallos**

- **FR-015**: Si el tema no se puede guardar, la app MUST seguir con el anterior, mantener la pantalla Tema abierta con la opción elegida marcada y avisar con «No se pudieron guardar las preferencias. Se mantienen las anteriores.». Un nuevo intento MUST poder funcionar.
- **FR-016**: Si el tema guardado no se puede leer, la app MUST usar «Sistema», y la pantalla Tema MUST mostrar «No se pudieron leer tus preferencias. Se usan las de por defecto.» con «Sistema» marcada, hasta que un guardado funcione.
- **FR-017**: Aplicar el tema al arrancar MUST NOT retrasar la primera pantalla ni añadir lecturas de disco en el hilo principal (constitución IV).

**Textos, accesibilidad, privacidad y fidelidad**

- **FR-018**: Todos los textos nuevos MUST estar en español y en inglés en los recursos de la app, con las mismas claves. Los que ya dicen lo mismo en Idiomas («Guardar preferencias», «Guardando…», «Preferencias guardadas» y los avisos de fallo al guardar y al leer) MUST reutilizarse.
- **FR-019**: La fila «Tema» y la pantalla Tema MUST:
  - tener objetivos táctiles de 48 dp;
  - leer la fila como una frase con el tema actual y su acción;
  - leer cada opción como opción de radio con su estado;
  - marcar el titular como encabezado;
  - anunciar «Guardando…» y el aviso de fallo con TalkBack;
  - funcionar con la letra al 200 %, en horizontal y en los dos temas, con el contraste de los pares de texto existentes.
- **FR-020**: El tema MUST funcionar sin red y MUST NOT registrarse en los logs de release más allá de un identificador del valor.
- **FR-021**: La UI de la fila «Tema» y de la pantalla Tema MUST seguir el wireframe y las propuestas A aprobadas por el usuario antes de `/speckit-plan`. Las diferencias frente al PNG 09 (la fila nueva) y frente al wireframe MUST reportarse al cierre (constitución VII).

### Key Entities

- **Preferencia de tema:** uno de tres valores, «Sistema», «Claro» u «Oscuro», guardado en el teléfono. Por defecto, «Sistema». Decide si la app usa los tokens claros u oscuros.
- **Tema efectivo:** claro u oscuro, el que se ve. Con «Sistema» sale del modo oscuro del teléfono; con los otros dos, de la preferencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el teléfono de referencia, tras tocar «Guardar preferencias», la app se ve en el tema elegido en menos de 1 s, en el 100 % de las pruebas con los tres temas y los dos modos del teléfono.
- **SC-002**: En 10 arranques en frío por combinación (Claro y Oscuro, con el teléfono en el modo contrario), ningún fotograma de la pantalla de arranque ni de la primera pantalla sale en el otro tema (grabación de pantalla).
- **SC-003**: Tras reiniciar el teléfono, la app abre en el tema guardado en el 100 % de los casos.
- **SC-004**: Con «Sistema», cambiar el modo oscuro del teléfono cambia la app en menos de 1 s sin cerrarla; con «Claro» u «Oscuro», no la cambia, en el 100 % de las pruebas.
- **SC-005**: Al cambiar el tema durante una respuesta, el 100 % de las respuestas terminan con su texto y sus citas, y el 100 % de las tandas de indexación siguen sin volver a empezar. El número de documentos y fragmentos indexados no cambia.
- **SC-006**: Tras un fallo de guardado simulado, el tema sigue siendo el anterior en el 100 % de las pruebas, con el aviso de FR-015.
- **SC-007**: Hay 0 soles en la app: el icono de Ajustes es el engranaje en la barra inferior, en Inicio y en el catálogo.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en Ajustes y en Tema, en claro y en oscuro, con la letra al 200 % y en horizontal. Las pruebas de contraste de los dos temas siguen en verde.
- **SC-009**: Los textos en español y en inglés tienen las mismas claves (100 % de paridad).
- **SC-010**: Las capturas del teléfono coinciden con el wireframe aprobado en estructura, textos y componentes, salvo las diferencias documentadas en el informe de cierre.

## Assumptions

- **Wireframe y propuestas de UI (serie A)** (constitución VII: se aprueban antes de `/speckit-plan`, criterio 1 de F16). F16 no tiene PNG para el tema. Los dibujos están en [wireframe.md](wireframe.md), con los componentes `Lisdata*` de cada zona. Los textos son propuestas en español; la versión inglesa va en A9:
  - **A1 — Fila «Tema»** en Preferencias de Ajustes, entre «Idiomas» e «Índice y rendimiento»:
    - la misma fila de ajustes que «Idiomas», con icono, título «Tema», el tema guardado como subtítulo («Sistema», «Claro» u «Oscuro») y chevron;
    - icono: el Lucide `sun-moon` (sol y luna), que dice «claro u oscuro» sin parecerse al engranaje de la barra inferior;
    - TalkBack: «Tema, Sistema», con la acción de abrir.
  - **A2 — Pantalla Tema**, como Idiomas y sin barra inferior:
    - barra superior «‹ Tema»;
    - eyebrow «Apariencia» y titular «Elige cómo se ve la app» (encabezado);
    - sin tarjeta hero: Idiomas la tiene porque está en su PNG; aquí no aporta información;
    - cabecera de sección «Tema de la aplicación» y un grupo de tarjeta con tres filas de radio:
      - «Sistema», con el subtítulo «Sigue el modo oscuro del teléfono»;
      - «Claro»;
      - «Oscuro»;
    - el botón «Guardar preferencias» al final, como en Idiomas.
  - **A3 — Guardar** (confirmado en el clarify): como en Idiomas. Marcar una opción no cambia nada hasta guardar. Al guardar, el botón dice «Guardando…», la app pasa al tema elegido, se vuelve a Ajustes y aparece el aviso inferior «Preferencias guardadas». No se previsualiza el tema al marcar la opción: así el comportamiento es el mismo que en Idiomas y Atrás nunca deja un tema a medias.
  - **A4 — Fallo al guardar:** la pantalla sigue abierta con la opción elegida y un aviso inferior «No se pudieron guardar las preferencias. Se mantienen las anteriores.» (el de Idiomas).
  - **A5 — Fallo al leer:** tarjeta de aviso sobre el botón, «No se pudieron leer tus preferencias. Se usan las de por defecto.» (la de Idiomas), con «Sistema» marcada.
  - **A6 — Engranaje:** el Lucide `settings` en la barra inferior y en la cabecera de Inicio, con el mismo tamaño y trazo que los demás iconos. La etiqueta «Ajustes» no cambia.
  - **A7 — Alcance:** el tema se aplica a todo, también a la pantalla de arranque y a las barras del sistema, sin colores nuevos. El catálogo de depuración conserva su interruptor propio, que solo afecta a sus vistas previas.
  - **A8 — Accesibilidad:** objetivos de 48 dp; en horizontal, la misma columna desplazable; con la letra al 200 %, los subtítulos bajan de línea sin cortarse.
  - **A9 — Textos nuevos en inglés:**
    - «Tema» → "Theme";
    - «Sistema» → "System", «Claro» → "Light", «Oscuro» → "Dark";
    - «Sigue el modo oscuro del teléfono» → "Follows your phone's dark mode";
    - «Apariencia» → "Appearance";
    - «Elige cómo se ve la app» → "Choose how the app looks";
    - «Tema de la aplicación» → "App theme".

    Los demás textos ya existen en los dos idiomas (FR-018).
- **Cómo se guarda el tema:** lo decide el plan. La opción preferida es el ajuste de modo oscuro por app que ofrece el sistema desde Android 12: lo guarda el propio sistema y lo aplica antes de pintar la primera pantalla, así que la pantalla de arranque sale en el tema correcto sin leer nada al arrancar (FR-010, FR-017). Se verifica en el plan (constitución II); si no sirve, el plan propone otra forma que cumpla FR-008, FR-010 y FR-017.
- **D-016 y G19:** el clarify registra la decisión que sustituye «sin selector propio» de D-016 y el sol de G19 (excepción del usuario a la precedencia del PNG, D-004). La paleta oscura derivada de D-016 no cambia.
- **Teléfono del usuario:** en la fase del teléfono se anotan sus ajustes al empezar (modo oscuro del teléfono, letra, giro, idioma de la app) y se restauran al terminar, también el tema de la app, que queda en «Sistema» salvo que el usuario diga otra cosa. No se abre, captura ni vuelca su conversación ni su documento.
- **Fuera de alcance:**
  - colores dinámicos del fondo de pantalla (D-016 los excluye) y temas de alto contraste o de colores propios;
  - un acceso al tema desde Inicio;
  - previsualizar el tema antes de guardarlo;
  - la auditoría completa del modo oscuro de todas las pantallas, que sigue en F15;
  - cambiar el icono de la app o el logotipo.
- **Dependencias:**
  - F12: Ajustes, la pantalla Idiomas como patrón y sus avisos;
  - F01: los tokens claros y oscuros, el conjunto de iconos y su script de generación, y las pruebas de contraste.
