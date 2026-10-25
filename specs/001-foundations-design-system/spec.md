# Especificación de feature: Fundaciones y design system (F01)

**Feature Branch**: `001-foundations-design-system`

**Created**: 2026-10-25

**Status**: Draft

**Input**: Descripción del usuario: «F01 — Fundaciones y design system de MicroRAG (marca visual LISDATA): base de app compilable y verificable con configuración acorde a la constitución, estructura por capas, navegación con todas las rutas lógicas y barra inferior, design system completo (tokens, tipografía, iconos, logo, componentes Lisdata* con previews y catálogo interno) y accesibilidad básica. Fuera de alcance: lógica de pantallas de producto, red, modelos y base de datos.» El texto completo está en `docs/roadmap/FEATURES.md` (F01).

**Fuentes**:
- `docs/disegn_app/docs/00-design-system.md`, `01-navigation.md`, `02-architecture.md`, `03-components.md` y `04-qa-checklist.md`.
- Los PNG de `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/`.
- `docs/roadmap/INVENTARIO.md`: §2 (tokens), §4 (componentes) y §6 (huecos G05, G06, G07, G08, G18, G19, G20, G32, G33, G39 y G42).
- Constitución, principios II, V y VII.

## Clarifications

### Session 2026-10-25

- Q: ¿Qué nombre ve la persona usuaria en el lanzador y dentro de la app? → A: «MicroRAG» en el lanzador y en todos los textos de la app (por ejemplo, la etiqueta del asistente en el chat o «Entrar en MicroRAG»). El logo de LISDATA se mantiene como marca de la empresa en las cabeceras (Bienvenida e Inicio).
- Q: ¿Cómo se resuelven los colores de texto del mockup que no alcanzan WCAG AA? → A: se mantienen fondos, chips y degradados del mockup y se oscurecen solo los textos: secundario `#54798A`, aviso `#8A5A12`, éxito `#087A65` y error `#C23F4D`. El degradado de las CTA se ajusta solo si el tramo bajo la etiqueta no llega a 3:1.
- Q: ¿Qué se hace con el tema oscuro, si no hay mockups oscuros? → A: tema oscuro derivado de los tokens navy de la marca (`INVENTARIO.md` §2.2). Sigue el ajuste del sistema, no hay selector propio y se valida con las previews oscuras de cada componente.
- Q: ¿Cuál es la versión mínima de Android? → A: Android 12 (API 31).
- Q: ¿Se replican píxel a píxel los controles con medidas no estándar (interruptor, chips, barra inferior) o se usan los estándar tematizados? → A: controles estándar de Material 3 tematizados con colores, tipografía y formas de LISDATA. Las diferencias de tamaño frente al PNG se documentan en la entrega.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Identidad visual LISDATA coherente en toda la app (Priority: P1)

Quien usa la app la reconoce como un producto LISDATA:
- colores, tipografía, iconos, formas y logo coinciden con los mockups aprobados;
- todo funciona igual de bien en tema claro y oscuro;
- quien revisa el diseño puede comprobar cada pieza visual frente a los PNG sin tener que esperar a que existan las pantallas de producto.

**Why this priority**: todas las pantallas de las features siguientes se construyen con estas piezas. Un error de identidad o de contraste aquí se multiplicaría en las 10 pantallas.

**Independent Test**:
1. Abrir el catálogo interno de componentes en la variante de depuración.
2. Recorrer cada componente y variante en claro y en oscuro.
3. Compararlos con los PNG de referencia y con `INVENTARIO.md` §2 y §4.

**Acceptance Scenarios**:

1. **Given** la variante de depuración instalada, **When** la persona revisora abre el catálogo de componentes, **Then** ve todos los componentes de `INVENTARIO.md` §4 con sus variantes y estados (normal, deshabilitado, error, cargando cuando aplique), ilustrados con datos de ejemplo etiquetados como tales.
2. **Given** el sistema en tema oscuro, **When** se abre cualquier vista de la app, **Then** se aplica la paleta oscura derivada (§2.2), sin colores invertidos automáticamente ni colores del fondo de pantalla del sistema.
3. **Given** cualquier componente con texto, **When** se mide el contraste de cada par texto/fondo de los tokens, **Then** todos cumplen WCAG AA (4,5:1 en texto normal y 3:1 en texto grande).
4. **Given** un componente que muestra la marca, **When** se renderiza el logo, **Then** se usa el asset oficial de LISDATA, con proporciones intactas y una descripción accesible.

---

### User Story 2 - Moverse por la estructura de la app (Priority: P1)

Quien usa la app se desplaza entre los cuatro destinos principales (Inicio, Chat, Biblioteca y Ajustes) con la barra inferior. También llega a las pantallas secundarias (bienvenida, preparación, importación, detalle de documento, indexación e idiomas), que se muestran sin barra inferior.

El botón Atrás se comporta como se espera en Android, y al rotar el dispositivo o cambiar el tema no se pierde el lugar en que se estaba.

Los destinos cuyo contenido llega en features posteriores muestran un marcador temporal claramente etiquetado, nunca una pantalla de producto inventada.

**Why this priority**: es el armazón donde se insertan todas las features. Sin él no se puede entregar ni probar ninguna pantalla.

**Independent Test**: recorrer todas las rutas lógicas desde la barra inferior y desde un menú de rutas de depuración, y verificar:
- la presencia o ausencia de la barra inferior;
- el comportamiento de Atrás;
- que se conserva el destino tras rotar y tras cambiar el tema.

**Acceptance Scenarios**:

1. **Given** la app abierta en Inicio, **When** se toca Chat, Biblioteca o Ajustes en la barra inferior, **Then** se muestra ese destino con un solo toque y la pestaña aparece seleccionada con indicador, icono y etiqueta.
2. **Given** un destino de pestaña distinto de Inicio, **When** se pulsa Atrás (del sistema o la flecha de la barra superior), **Then** se vuelve a Inicio. Si se pulsa Atrás desde Inicio, la app se cierra.
3. **Given** una navegación repetida entre pestañas, **When** se pulsa Atrás varias veces, **Then** nunca aparece dos veces la misma pestaña en el historial.
4. **Given** un destino secundario (`welcome`, `setup`, `ingest`, `document/{documentId}`, `index` o `languages`), **When** se muestra, **Then** la barra inferior no aparece.
5. **Given** cualquier destino, **When** se rota el dispositivo o se cambia entre tema claro y oscuro, **Then** se conservan el destino, la pestaña seleccionada y la posición de scroll.
6. **Given** un destino cuyo contenido pertenece a una feature posterior, **When** se abre, **Then** muestra un marcador «Pendiente · Fxx» con el nombre del destino y sus argumentos, sin datos ni controles de producto.

---

### User Story 3 - Privacidad garantizada desde la instalación (Priority: P2)

Quien instala la app tiene la garantía, desde la primera versión, de que:
- la app no puede conectarse a Internet;
- no envía sus datos a copias de seguridad en la nube ni a transferencias entre dispositivos;
- no pide permisos innecesarios.

**Why this priority**: es el principio I de la constitución (no negociable). Tiene que quedar fijado en la base para que ninguna feature posterior lo rompa por accidente.

**Independent Test**: inspeccionar el paquete instalado (permisos declarados y configuración de copia de seguridad) e intentar una copia de seguridad del dispositivo.

**Acceptance Scenarios**:

1. **Given** la app instalada, **When** se consultan sus permisos declarados, **Then** no hay ningún permiso de red ni ningún otro permiso.
2. **Given** la app con datos locales, **When** se lanza una copia de seguridad o una transferencia entre dispositivos, **Then** no se incluye ningún dato de la app.
3. **Given** la variante de publicación, **When** se usa la app, **Then** no se emiten registros de depuración.

---

### User Story 4 - Accesibilidad básica en todas las piezas (Priority: P2)

Quien usa TalkBack, tamaños de fuente grandes o necesita objetivos táctiles amplios puede usar cualquier componente:
- los controles anuncian su nombre y su estado;
- ningún texto se recorta con la fuente al 200 %;
- todo lo que es interactivo tiene al menos 48 dp de área táctil.

**Why this priority**: la constitución (VII) exige accesibilidad básica y el checklist QA la verifica. Es mucho más barato construirla en los componentes que añadirla luego pantalla a pantalla.

**Independent Test**:
- Recorrer el catálogo con TalkBack activado y con la fuente al 200 %.
- Ejecutar la verificación automática de tamaños táctiles y contraste.

**Acceptance Scenarios**:

1. **Given** TalkBack activo, **When** se enfoca un interruptor, una fila con estado o un indicador de progreso, **Then** se anuncian su etiqueta, su estado (activado/desactivado, listo/error…) y el progreso en palabras.
2. **Given** un icono decorativo, **When** TalkBack recorre la vista, **Then** el icono no se anuncia. Los iconos con significado propio sí tienen descripción.
3. **Given** la fuente del sistema al 200 %, **When** se muestra cualquier componente del catálogo, **Then** el texto no se recorta ni se solapa: los contenedores crecen o el texto pasa a varias líneas, y los nombres largos se truncan con elipsis solo donde el diseño lo prevé.
4. **Given** un chip o filtro visualmente pequeño (≈27 dp), **When** se toca, **Then** su área táctil efectiva es de al menos 48 dp.

---

### Edge Cases

- **Nombres muy largos** (documentos, idiomas, traducciones al inglés): en las filas se truncan con elipsis en una o dos líneas; en los hero y en las cabeceras de detalle se muestran completos en varias líneas.
- **Progreso con total desconocido**: los componentes de progreso tienen modo indeterminado y nunca muestran un porcentaje inventado (constitución V).
- **Estado de error en un componente** (cita a una fuente borrada, chip de error, fila con error): la variante de error se distingue sin depender solo del color, con icono y texto.
- **Teclado en pantalla visible** en un destino con barra inferior: la barra inferior se oculta mientras el teclado está visible, para que el contenido y el compositor no queden tapados (G39).
- **Pantallas compactas** (anchura ≤ 360 dp) y **pantallas grandes**: los componentes se adaptan a la anchura disponible respetando el margen lateral de 20 dp. La rejilla 2×2 mantiene dos columnas salvo con la fuente al 200 %, en que puede pasar a una.
- **Muerte del proceso en segundo plano**: al volver, la app restaura el destino y la pestaña.
- **Idioma del sistema no soportado**: la interfaz se muestra en español, el idioma por defecto.
- **Ruta con un identificador inválido o ausente** (por ejemplo `document/` sin id): no hay cierre inesperado; el marcador indica el argumento recibido. El tratamiento definitivo llega en la feature de la pantalla.
- **Reselección de la pestaña activa**: vuelve a la raíz de esa pestaña.

## Requirements *(mandatory)*

### Functional Requirements

**Identidad visual y tokens**

- **FR-001**: La app MUST definir la paleta clara de `INVENTARIO.md` §2.1 (marca, superficies y semánticos) como su única fuente de color. Ninguna vista usa colores definidos fuera del sistema de diseño.
- **FR-002**: La app MUST ofrecer un tema oscuro derivado de los tokens navy de la marca según `INVENTARIO.md` §2.2 (fondo `#03161F`, superficie `#072634`, contenedor informativo `#0D3F51`, acción `#02B0E2`, texto `#E6F4F9`/`#9FBCC8` y semánticos aclarados). Se aplica según el ajuste del sistema, sin selector de tema propio, y también cumple FR-008. MUST NOT adoptar colores dinámicos derivados del fondo de pantalla del dispositivo.
- **FR-003**: El sistema de diseño MUST proporcionar los degradados de §2.3 (CTA, hero, bienvenida y barra de pasos) para que los usen los componentes.
- **FR-004**: Todos los textos MUST usar la familia Google Sans aportada en `docs/disegn_app/Tipografias/`, con la escala de §2.4. El texto MUST respetar el tamaño de fuente del sistema, sin alturas fijas que recorten el contenido.
- **FR-005**: El sistema de diseño MUST definir espaciado (unidad de 4 dp, margen lateral de 20 dp), radios (tarjeta 16 dp, botón 12 dp, tile 12 dp, chip en píldora), bordes (1 dp) y elevación (0, borde en lugar de sombra) según §2.5.
- **FR-006**: La app MUST incluir los iconos de §2.6 como recursos vectoriales de un único estilo lineal (Lucide, D-008), tintables por token y con los nombres `ic_*` del inventario. El icono de Ajustes MUST ser el sol de los mockups (G19).
- **FR-007**: La marca MUST mostrarse con el asset oficial `logoLISDATA.png`, sin redibujarlo ni aproximarlo, con sus proporciones originales y una descripción accesible.
- **FR-008**: Todos los pares texto/fondo definidos por los tokens MUST cumplir WCAG AA: 4,5:1 en texto normal y 3:1 en texto grande (≥ 18,66 px en negrita o ≥ 24 px). Para ello, los rellenos, chips y degradados del mockup se mantienen y se oscurecen solo los colores de texto: secundario `#54798A` (en lugar de `#658797`), aviso `#8A5A12` (en lugar de `#A46C19`), éxito `#087A65` (en lugar de `#129D83`) y error `#C23F4D` (en lugar de `#DB5865`). El degradado de las CTA solo se ajusta si el tramo bajo la etiqueta no alcanza 3:1 (G05, G06, G42).

**Componentes**

- **FR-009**: El sistema de diseño MUST incluir todos los componentes de `INVENTARIO.md` §4 con las variantes descritas. Son componentes de presentación: reciben datos y callbacks y no contienen lógica de negocio ni acceden a datos. Los controles estándar (interruptor, radio, chips de filtro, barra inferior, campo de texto y diálogos) MUST ser los de Material 3, tematizados con los tokens, y no réplicas a medida. Sus diferencias de tamaño frente al PNG (por ejemplo, el interruptor de 52×32 dp frente a 42×24 dp) se documentan en la entrega de la feature (G18).
- **FR-010**: Cada componente MUST tener vistas previas en tema claro, tema oscuro, texto largo y fuente al 200 %. Los que tienen estado de error también MUST tener una vista previa de ese estado.
- **FR-011**: La variante de depuración MUST incluir un catálogo navegable que muestre todos los componentes y sus variantes con datos de ejemplo etiquetados. El catálogo MUST NOT existir en la variante de publicación.
- **FR-012**: El sistema de diseño MUST ofrecer componentes de estado compartidos: vacío con acción opcional, error con «Reintentar», carga (skeleton), botón ocupado e indicadores de progreso con modo determinado e indeterminado.
- **FR-013**: Los indicadores de estado (chips, etapas, filas de modelo) MUST combinar texto e icono o punto. Nunca comunican un estado solo con color.
- **FR-014**: Todo elemento interactivo MUST tener un área táctil efectiva de al menos 48 × 48 dp, aunque su tamaño visual sea menor.
- **FR-015**: Los componentes MUST exponer semántica accesible:
  - interruptores y radios con etiqueta y estado;
  - filas agrupadas en un solo elemento enfocable;
  - iconos decorativos ocultos al lector de pantalla;
  - progreso descrito en palabras.

**Navegación**

- **FR-016**: La app MUST definir los destinos lógicos `welcome`, `setup`, `home`, `chat/{conversationId?}`, `ingest`, `library`, `document/{documentId}`, `index`, `settings` y `languages`. Entre destinos solo se pasan identificadores, nunca contenidos.
- **FR-017**: La barra inferior MUST mostrar cuatro destinos (Inicio, Chat, Biblioteca y Ajustes) con icono, etiqueta e indicador de selección. Solo aparece en `home`, `chat`, `library` y `settings`.
- **FR-018**: El botón Atrás MUST seguir las convenciones de Android sin duplicar pestañas en el historial:
  - desde una pestaña distinta de Inicio, vuelve a Inicio;
  - desde Inicio, sale de la app.

  Las barras superiores de las pestañas Chat, Biblioteca y Ajustes MUST mostrar la flecha Atrás de los mockups, con el mismo efecto (G08).
- **FR-019**: La app MUST conservar el destino actual, la pestaña seleccionada y la posición de scroll al rotar, al cambiar de tema y al volver tras la muerte del proceso.
- **FR-020**: La barra inferior MUST ocultarse mientras el teclado en pantalla está visible (G39).
- **FR-021**: Los destinos cuyo contenido pertenece a features posteriores MUST mostrar un marcador temporal etiquetado «Pendiente · Fxx» (con el destino y los argumentos recibidos), sin datos ni controles de producto. Su eliminación completa se verifica en F15.
- **FR-022**: Hasta F03 (onboarding), el destino inicial MUST ser `home`. La lógica de primer arranque no forma parte de esta feature.
- **FR-023**: La app MUST NOT aceptar enlaces profundos externos.
- **FR-024**: La variante de depuración MUST ofrecer un acceso a un menú de rutas que permita abrir cualquier destino, con argumentos de prueba, para la verificación manual.

**Privacidad y plataforma**

- **FR-025**: La app MUST NOT declarar permisos de red ni ningún otro permiso en esta feature.
- **FR-026**: La app MUST quedar excluida de las copias de seguridad en la nube y de las transferencias entre dispositivos.
- **FR-027**: La variante de publicación MUST NOT emitir registros de depuración.
- **FR-028**: La app MUST distribuirse solo para dispositivos ARM de 64 bits. La arquitectura x86_64 se admite únicamente en la variante de depuración, para el emulador de desarrollo.
- **FR-029**: La versión mínima de Android MUST ser Android 12 (API 31). Cubre los dispositivos objetivo (ARM64 con 8 GB de RAM o más) y supera el mínimo técnico de API 26 de los componentes de búsqueda local (G33, D-006).

**Base técnica para las features siguientes**

- **FR-030**: La base MUST separar presentación, dominio y datos (D-009) y ofrecer un registro de dependencias que se verifique automáticamente. Cada feature posterior añade sus piezas sin modificar la base.
- **FR-031**: La base MUST ofrecer colas de trabajo dedicadas y secuenciales para base de datos, modelo de lenguaje y embeddings, fuera de la interacción, de modo que ningún trabajo pesado bloquee la interfaz (constitución IV).
- **FR-032**: Todos los textos de interfaz MUST estar en recursos localizables, con español como idioma por defecto e inglés como segundo idioma. Los componentes no contienen textos literales.
- **FR-033**: El nombre visible de la app MUST ser «MicroRAG», tanto en el lanzador como en todos los textos de interfaz que nombran el producto o el asistente (G32). El logo de LISDATA se mantiene como marca de la empresa en las cabeceras donde aparece en los mockups (FR-007).

### Key Entities *(include if feature involves data)*

- **Token de diseño**: valor con nombre del sistema visual (color, degradado, tipografía, espaciado, radio, borde). Tiene un valor para el tema claro y otro para el oscuro cuando aplica, y su origen trazable (PNG, doc o propuesta).
- **Componente LISDATA**: pieza visual reutilizable con sus variantes y estados, parametrizada por datos. Tiene vistas previas y una entrada en el catálogo.
- **Destino de navegación**: pantalla lógica identificada por su ruta, con argumentos opcionales por identificador, visibilidad de la barra inferior y feature que aporta su contenido.
- **Marcador de destino**: contenido temporal de un destino aún no implementado. Indica la feature que lo sustituirá.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los componentes de `INVENTARIO.md` §4 aparecen en el catálogo con todas sus variantes, y la revisión visual frente a los PNG no encuentra diferencias que no estén documentadas.
- **SC-002**: El 100 % de los pares texto/fondo de los tokens, en tema claro y oscuro, cumplen WCAG AA en una verificación automática.
- **SC-003**: Cada destino principal se alcanza con 1 toque desde cualquier otro. En el 100 % de los recorridos de prueba, Atrás nunca deja dos instancias de la misma pestaña.
- **SC-004**: Con la fuente del sistema al 200 %, ninguna vista previa ni entrada del catálogo muestra texto recortado o solapado en la revisión.
- **SC-005**: El paquete instalado declara 0 permisos, y una copia de seguridad del dispositivo contiene 0 datos de la app.
- **SC-006**: El 100 % de los elementos interactivos del catálogo superan la verificación automática de área táctil mínima de 48 dp.
- **SC-007**: En el 100 % de los casos de prueba de rotación, cambio de tema y recreación, se conserva el destino y la pestaña.
- **SC-008**: Una verificación automática no encuentra ningún color, tamaño de texto o texto literal definido fuera del sistema de diseño o de los recursos.
- **SC-009**: La primera vista aparece en menos de 1 segundo desde un arranque en frío en el teléfono de referencia.
- **SC-010**: La base compila y pasa el análisis estático y todas las pruebas automáticas sin errores.

## Assumptions

- **Contraste (G05, G06, G42)**: resuelto en clarify (ver FR-008).
- **Modo oscuro (G07)**: resuelto en clarify (ver FR-002).
- **Flecha Atrás en las pestañas (G08)**: se muestra como en los mockups y lleva a Inicio.
- **Componentes estándar tematizados (G18)**: resuelto en clarify (ver FR-009).
- **Logo (G20)**: se usa el PNG oficial hasta tener el vectorial. Las versiones de una sola tinta que aparecen en los mockups no se recrean.
- **Nombre visible (G32)**: resuelto en clarify. «MicroRAG» como nombre de la app y logo de LISDATA como marca de la empresa.
- **Versión mínima de Android (G33)**: resuelto en clarify (ver FR-029).
- **Idiomas**: español por defecto e inglés como segundo idioma (el PNG de Idiomas ofrece ES/EN). Cualquier otro idioma del sistema muestra español.
- **Marcadores**: los destinos sin contenido se muestran con marcadores etiquetados en todas las variantes mientras dure el desarrollo. Ninguna build de esta feature se distribuye a usuarios finales.
- **Sin dependencias**: es la primera feature del roadmap y no depende de ninguna otra.
- **Fuera de alcance**:
  - el contenido de las pantallas de producto (Bienvenida, Preparación, Inicio, Chat, Ingesta, Biblioteca, Detalle, Indexación, Ajustes e Idiomas);
  - el primer arranque y el onboarding;
  - red y descarga de modelos;
  - motores de inferencia;
  - base de datos;
  - cambio de idioma desde la app (llega en F12).
