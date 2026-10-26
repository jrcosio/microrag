# Especificación de feature: Modelos y primer arranque (F03)

**Feature Branch**: `003-models-onboarding`

**Created**: 2026-10-26

**Status**: Draft

**Input**: Descripción del usuario: «F03 — Modelos y primer arranque. Onboarding real que deja el dispositivo preparado para trabajar sin conexión: Bienvenida → Preparación inicial en 3 pasos → Inicio, con descarga reanudable y verificada de los dos modelos elegidos en F02.» Ver `docs/roadmap/FEATURES.md` (F03).

**Fuentes**:
- Mockups (mandan en UI, D-004):
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/01-bienvenida.png`;
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/02-preparacion.png`.
- Especificaciones de pantalla, para lo no maquetado: `screens/01-welcome.md` y `screens/02-onboarding.md`.
- `docs/roadmap/INVENTARIO.md`: P01, P02, G02, G09, G10, G30 y G31.
- `docs/RAG_Movil/informe-rag-movil-v2.md`: Distribución de modelos; Memoria y ciclo de vida; Privacidad y copias de seguridad.
- Constitución v1.1.0, principios I, II, IV, V, VI y VII.
- DECISIONS D-004, D-011, D-014, D-016, D-017, D-022, D-023 y D-025.

**Modelos requeridos** (catálogo de F02, URL fijada a commit y SHA-256 publicado, D-011):

| Modelo | Papel en la UI | Fichero | Tamaño | Decisión |
|---|---|---|---:|---|
| Gemma 4 E2B | «Modelo de respuestas» | `gemma-4-E2B-it.litertlm` | 2.588.147.712 bytes (≈ 2,59 GB) | D-023 |
| EmbeddingGemma 2 270M | «Búsqueda semántica» | `embeddinggemma-2-text-270m.litertlm` | 164.626.432 bytes (≈ 165 MB) | D-025 |
| **Total** | | | **≈ 2,75 GB** | |

## Clarifications

### Session 2026-10-26

- Q: ¿Se ofrece también importar los modelos desde un archivo del teléfono, además de descargarlos? (G02) → A: sí. La descarga es el camino principal y la confirmación ofrece además «Seleccionar archivo del dispositivo», con la misma verificación de huella y compatibilidad.
- Q: ¿Qué red puede usar la descarga de los modelos (≈ 2,75 GB)? → A: cualquier red disponible (Wi-Fi o datos móviles) una vez que el usuario confirma el tamaño. La confirmación indica la red actual y avisa si son datos móviles.
- Q: ¿Cómo se presentan los pasos 2 y 3 de la Preparación, que no están maquetados, y cómo empieza la descarga? (G10) → A: en una sola pantalla, la del PNG 02. El chip «PASO N DE 3» y la barra reflejan el estado real. La descarga empieza al tocar una fila de modelo, que abre la confirmación. «Continuar» solo se habilita en el paso 3, y mientras tanto un texto explica qué falta.
- Q: ¿Cómo se comprueba que Gemma 4 funciona en el teléfono antes de marcarlo «Listo», y qué pasa si no arranca en la GPU? → A: con una prueba real de arranque durante la preparación (≈ 17 s, que deja la caché del motor lista). Si falla en GPU, se prueba en CPU: si arranca, queda «Listo» con el aviso «Modo CPU, más lento»; si no, queda «Incompatible». EmbeddingGemma 2 se prueba en CPU.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bienvenida y entrada al primer uso (Priority: P1)

La primera vez que se abre la app aparece la Bienvenida, tal como está en el PNG 01. «Comenzar» lleva a la Preparación inicial. Cuando el onboarding ya está completado, la app abre Inicio y la Bienvenida no vuelve a aparecer.

**Why this priority**: es la entrada al producto y fija la regla de arranque de la que dependen todas las demás pantallas.

**Independent Test**:
1. Instalar la app limpia y abrirla: aparece la Bienvenida.
2. Pulsar «Comenzar»: aparece la Preparación inicial.
3. Marcar el onboarding como completado (con modelos de prueba) y relanzar: aparece Inicio.

**Acceptance Scenarios**:

1. **Given** una instalación limpia, **When** se abre la app, **Then** aparece la Bienvenida con los textos del PNG 01, sin barra inferior y sin pedir permisos.
2. **Given** la Bienvenida, **When** se pulsa «Comenzar», **Then** se abre la Preparación inicial.
3. **Given** la Bienvenida, **When** se pulsa Atrás, **Then** la app se cierra (no se abre Inicio).
4. **Given** un usuario que ya pasó por la Bienvenida pero no terminó el onboarding, **When** relanza la app, **Then** se abre directamente la Preparación inicial con el estado real de cada modelo.
5. **Given** el onboarding completado, **When** se relanza la app, **Then** se abre Inicio y Atrás desde Inicio cierra la app.
6. **Given** TalkBack activo en la Bienvenida, **When** se recorre la pantalla, **Then** el logo se anuncia con su descripción y la ilustración no se anuncia.

---

### User Story 2 - Descargar los modelos con confirmación previa (Priority: P1)

En la Preparación inicial (paso 1, PNG 02) los dos modelos aparecen «Por configurar». Al tocar cualquiera de ellos, la app pide confirmación antes de empezar. Esa confirmación indica los modelos, el tamaño total (≈ 2,75 GB), el espacio libre, la red por la que se descargará (con aviso si son datos móviles) y, si procede, el aviso de memoria. Al aceptar, empiezan las descargas y la pantalla pasa al paso 2, con el progreso real de cada modelo. La descarga continúa aunque el usuario salga de la app, y una notificación muestra el progreso.

**Why this priority**: sin los modelos no hay producto, y es la única operación de red de toda la app (constitución I).

**Independent Test**:
1. Con Wi-Fi, abrir la Preparación inicial y tocar «Gemma 4».
2. Comprobar el contenido de la confirmación y aceptar.
3. Ver avanzar el progreso en la fila y en la notificación, con bytes reales.
4. Salir de la app y volver: el progreso ha seguido avanzando.

**Acceptance Scenarios**:

1. **Given** el paso 1 con ambos modelos «Por configurar», **When** se observa la pantalla, **Then** coincide con el PNG 02 y «Continuar» está deshabilitado, con un texto que explica por qué.
2. **Given** el paso 1, **When** se toca una fila de modelo, **Then** se abre la confirmación con los dos modelos, sus tamaños, el total, el espacio libre que quedará y la red que se usará. Ofrece «Descargar», «Seleccionar archivo del dispositivo» y «Cancelar».
3. **Given** un dispositivo con menos de 7,5 GB de RAM total, **When** se abre la confirmación, **Then** incluye un aviso de que el rendimiento puede ser limitado, sin bloquear la descarga.
4. **Given** Android 13 o superior y el permiso de notificaciones sin conceder, **When** se acepta la primera descarga, **Then** se explica para qué sirve la notificación y se pide el permiso. Si se deniega, la descarga empieza igualmente y la pantalla avisa de que el progreso solo se verá dentro de la app.
5. **Given** las descargas en curso, **When** se observa la pantalla, **Then** se muestra «PASO 2 DE 3». Cada fila muestra su estado (p. ej. «Descargando 34 %» con «880 MB de 2,59 GB», o «En cola»), y el porcentaje sale de los bytes realmente recibidos.
6. **Given** una descarga en curso, **When** el usuario sale de la app, **Then** la descarga sigue y una notificación muestra el modelo, el porcentaje y una acción para detenerla.
7. **Given** una descarga en curso, **When** el usuario la detiene (desde la fila o la notificación), **Then** queda «En pausa», conserva lo descargado y se reanuda al tocar la fila.

---

### User Story 3 - Nunca «Listo» sin verificación real (Priority: P1)

Al terminar cada descarga, la app comprueba la huella del fichero contra la publicada y que el fichero funciona de verdad en el dispositivo. Solo entonces el modelo pasa a «Listo». Un fichero con la huella incorrecta se descarta y nunca se carga.

**Why this priority**: es el principio V de la constitución y el criterio de aceptación central del MD 02 («Nunca aparece Listo sin verificación real»).

**Independent Test**:
1. Completar una descarga y ver la secuencia «Verificando» → «Preparando» → «Listo».
2. Simular un fichero alterado (1 byte distinto): termina en «Error», con «Reintentar», y el fichero se ha eliminado.
3. Un test automatizado recorre todas las transiciones y comprueba que «Listo» solo aparece tras la huella correcta y la prueba de compatibilidad.

**Acceptance Scenarios**:

1. **Given** una descarga completa, **When** termina, **Then** la fila muestra «Verificando», con progreso real si lo hay o con un indicador indeterminado si no.
2. **Given** una huella correcta, **When** se completa la verificación, **Then** se comprueba la compatibilidad («Preparando»):
   - el fichero se abre con el motor de inferencia y declara los backends esperados;
   - Gemma 4 arranca una vez en la GPU del dispositivo;
   - EmbeddingGemma 2 produce un vector de la dimensión esperada.

   Solo tras superarlo, el modelo pasa a «Listo».
3. **Given** una huella incorrecta, **When** termina la verificación, **Then** el fichero se elimina, la fila muestra «Error» con el motivo («La descarga no superó la verificación») y «Reintentar» vuelve a descargar desde cero.
4. **Given** que Gemma 4 no arranca en la GPU, **When** se prepara, **Then** se prueba en CPU. Si arranca, queda «Listo» con el aviso «Funcionará en modo CPU, más lento»; si tampoco, queda «Incompatible», con la explicación.
5. **Given** un fichero con la huella correcta que el motor no puede abrir, **When** se prepara, **Then** queda «Incompatible» con la explicación y no se marca «Listo».

---

### User Story 4 - Descarga robusta: reanudar sin volver a empezar (Priority: P1)

Una descarga de 2,6 GB en un móvil se interrumpe. La app reanuda desde donde se quedó después de:
- matar el proceso;
- reiniciar el teléfono;
- perder la red.

Nunca vuelve a bajar lo que ya tenía.

**Why this priority**: sin reanudación, una descarga de este tamaño fracasa en la práctica. Es un criterio de aceptación de FEATURES (F03-2).

**Independent Test**:
1. Iniciar la descarga de Gemma y forzar el cierre de la app al ≈ 50 %.
2. Volver a abrirla: continúa desde ≈ 50 %.
3. Activar el modo avión durante la descarga: pasa a «Esperando conexión» y continúa sola al volver la red.

**Acceptance Scenarios**:

1. **Given** una descarga al 50 %, **When** se mata el proceso y se vuelve a abrir la app, **Then** la descarga continúa desde ≈ 50 % y el total transferido no supera el tamaño del fichero en más de un 5 %.
2. **Given** una descarga en curso, **When** se reinicia el teléfono, **Then** la descarga se reanuda automáticamente cuando hay red, sin abrir la app.
3. **Given** una descarga en curso, **When** se pierde la conexión, **Then** la fila muestra «Esperando conexión» y la descarga continúa sola al volver la red.
4. **Given** que el servidor no admite reanudar o que el fichero remoto ha cambiado, **When** se reanuda, **Then** la descarga vuelve a empezar desde cero de forma segura, sin mezclar datos, y la verificación de huella lo protege igualmente.
5. **Given** una descarga en curso por Wi-Fi, **When** el teléfono pasa a datos móviles, **Then** la descarga continúa por datos móviles sin volver a preguntar, porque el usuario ya confirmó el tamaño.

---

### User Story 5 - Almacenamiento listo y entrada a Inicio (Priority: P2)

Con ambos modelos «Listo», la app prepara el almacenamiento privado (paso 3) y habilita «Continuar». Al pulsarlo, se completa el onboarding y se abre Inicio.

**Why this priority**: cierra el flujo, pero depende de US2 y US3.

**Independent Test**:
1. Con los dos modelos verificados (descargados o importados), comprobar «PASO 3 DE 3» y el almacenamiento «Offline».
2. Pulsar «Continuar»: se abre Inicio.
3. Relanzar: se abre Inicio directamente.

**Acceptance Scenarios**:

1. **Given** los dos modelos «Listo» y el almacenamiento preparado, **When** se observa la pantalla, **Then** se muestra «PASO 3 DE 3», la tarjeta de almacenamiento con «Offline» y «Continuar» habilitado.
2. **Given** «Continuar» habilitado, **When** se pulsa, **Then** el onboarding queda completado y se abre Inicio sin que Atrás vuelva a la Preparación.
3. **Given** que falta cualquier requisito, **When** se observa «Continuar», **Then** está deshabilitado y un texto visible indica qué falta, por ejemplo:
   - «Configura los dos modelos para continuar»;
   - «Esperando a que termine la descarga»;
   - «Libera espacio para continuar».

---

### User Story 6 - Importar un modelo desde el dispositivo (Priority: P3)

Como alternativa a la descarga, el usuario elige con el selector del sistema un fichero de modelo que ya tiene en el teléfono. La app lo copia a su almacenamiento privado, lo verifica igual que una descarga y lo marca «Listo» solo si es el fichero esperado.

**Why this priority**: el MD 02 lo pide para funcionar sin Internet (G02, confirmado en clarify), pero la descarga cubre el caso principal.

**Independent Test**:
1. Copiar el fichero de EmbeddingGemma 2 a «Descargas».
2. En la confirmación, elegir «Seleccionar archivo del dispositivo» y seleccionarlo.
3. Ver «Importando» → «Verificando» → «Preparando» → «Listo».
4. Repetir con un fichero distinto: queda «Incompatible».

**Acceptance Scenarios**:

1. **Given** el fichero correcto seleccionado, **When** termina la copia, **Then** se verifica la huella y la compatibilidad, como en US3, y el modelo queda «Listo». El fichero original del usuario no se modifica.
2. **Given** un fichero que no corresponde a ningún modelo requerido, **When** se verifica, **Then** la copia se elimina y la fila muestra «Incompatible» con «El archivo no corresponde a Gemma 4» (o a EmbeddingGemma 2).
3. **Given** una importación en curso, **When** se cierra la app, **Then** al volver la importación aparece interrumpida y se puede reintentar. Nunca queda un fichero a medias marcado como válido.

---

### Edge Cases

- **Espacio insuficiente:**
  - antes de empezar, o entre un modelo y otro, el espacio libre no cubre lo que falta por descargar más el margen;
  - la fila y la tarjeta de almacenamiento muestran «Sin espacio» con la cantidad necesaria y una acción que abre los ajustes de almacenamiento del sistema;
  - se vuelve a comprobar al volver a la app.
- **Disco lleno durante la descarga:** se detiene sin corromper lo descargado y pasa a «Sin espacio».
- **Fichero borrado o alterado después del onboarding** (por ejemplo, al borrar los datos de la app desde los ajustes del sistema): el estado real del modelo deja de ser «Listo». Inicio mostrará «Configuración pendiente» con el acceso a la Preparación (MD 03, F09).
- **Fichero parcial huérfano** de una descarga anterior que ya no corresponde al catálogo: se elimina.
- **Fichero completo pero sin verificar** (el proceso murió durante la verificación): se vuelve a verificar al abrir la app. Nunca se da por bueno.
- **Usuario que vuelve a la Bienvenida** desde la Preparación con descargas en curso: las descargas siguen. Al volver a «Comenzar», la Preparación muestra el estado real.
- **Rotación, multiventana y escalado de fuente al 200 %:** el contenido se reorganiza sin recortes. En pantallas compactas, la ilustración de la Bienvenida cede espacio a los textos y la CTA.
- **Permiso de notificaciones denegado** de forma permanente: la descarga funciona y la pantalla recuerda que el progreso solo se ve dentro de la app.
- **El sistema detiene el trabajo en segundo plano** (ahorro de batería, límites de tiempo): la fila queda «En cola» con los bytes ya descargados (o «Esperando conexión» si no hay red) y la descarga se reanuda sin perder lo descargado.
- **Red cautiva** (Wi-Fi con portal de inicio de sesión) **o servidor con error:** se reintenta con espera creciente. Tras varios fallos seguidos, la fila muestra «Error» con «Reintentar».
- **Modelos ya presentes** antes de empezar (por ejemplo, copiados por la herramienta de diagnóstico de depuración): se verifican y, si son correctos, no se vuelven a descargar.

## Requirements *(mandatory)*

### Functional Requirements

**Arranque y navegación**

- **FR-001**: Al arrancar, la app MUST abrir:
  - Inicio, si el onboarding está completado;
  - la Preparación inicial, si el usuario ya pasó por la Bienvenida;
  - la Bienvenida, en cualquier otro caso.
- **FR-002**: La Bienvenida MUST reproducir el PNG 01:
  - fondo degradado de bienvenida;
  - logo oficial con descripción accesible;
  - chip «100% EN TU DISPOSITIVO»;
  - ilustración decorativa no anunciada;
  - «Tu conocimiento.» / «Solo tuyo.»;
  - «Pregunta a tus documentos con IA.» / «Sin nube. Sin comprometer tu privacidad.»;
  - CTA «Comenzar»;
  - pie «PRIVADO · INTELIGENTE · LOCAL».

  No tiene barra inferior ni pide permisos.
- **FR-003**: Atrás en la Bienvenida MUST cerrar la app. Atrás en la Preparación MUST volver a la Bienvenida sin cancelar las descargas. Desde Inicio, tras completar el onboarding, Atrás MUST cerrar la app.
- **FR-004**: El progreso del onboarding (no iniciado, iniciado, completado) MUST guardarse en el almacenamiento privado, excluido de copias de seguridad, y sobrevivir a cierres y reinicios.

**Preparación inicial**

- **FR-005**: La Preparación MUST reproducir el PNG 02:
  - barra superior «‹ Preparación inicial»;
  - chip de paso con barra de progreso;
  - «Tu IA, en tu móvil.» y «Configura los modelos para empezar a consultar documentos sin conexión.»;
  - filas «Gemma 4 / Modelo de respuestas» y «EmbeddingGemma 2 / Búsqueda semántica», con su icono, chip de estado y chevron;
  - tarjeta «Almacenamiento local / Índice vectorial y archivos privados»;
  - tarjeta «Tus documentos nunca salen de este dispositivo. / La instalación se verifica localmente.»;
  - CTA «Continuar».
- **FR-006**: El paso MUST derivarse del estado real, en una sola pantalla (G10, confirmado en clarify), sin pantallas adicionales:
  - **paso 1** («PASO 1 DE 3», barra a 1/3): algún modelo está «Por configurar» y no hay ninguna operación en marcha;
  - **paso 2** («PASO 2 DE 3», 2/3): hay alguna descarga, importación, verificación o preparación en curso, en pausa, en espera o con error;
  - **paso 3** («PASO 3 DE 3», 3/3): ambos modelos están «Listo».
- **FR-007**: Cada fila MUST mostrar exactamente uno de estos estados, con texto y nunca solo con color (G09):

  | Estado | Chip | Detalle bajo la fila | Acción al tocar |
  |---|---|---|---|
  | Por configurar | «Por configurar» (aviso) | — | Abre la confirmación |
  | En cola | «En cola» | «Empezará al terminar …» | — |
  | Descargando | «Descargando N %» | «X MB de Y» | Ofrece detener |
  | Importando | «Importando N %» | «X MB de Y» | — |
  | En pausa | «En pausa» | «X MB de Y» | Reanuda |
  | Esperando conexión | «Esperando conexión» | «X MB de Y» | — |
  | Verificando | «Verificando» (con % si es fiable) | «Comprobando la huella» | — |
  | Preparando | «Preparando» (indeterminado) | «Probando el modelo en este dispositivo» | — |
  | Listo | «Listo» (éxito) | Tamaño; «Modo CPU, más lento» si aplica | — |
  | Sin espacio | «Sin espacio» (aviso) | «Faltan X GB» | Abre los ajustes de almacenamiento |
  | Incompatible | «Incompatible» (error) | El motivo | Abre la confirmación |
  | Error | «Error» (error) | El motivo y «Reintentar» | Reintenta |

- **FR-008**: Mientras «Continuar» esté deshabilitado, la pantalla MUST mostrar un texto que indique el requisito pendiente, y TalkBack MUST anunciarlo junto al botón.
- **FR-009**: Antes de la primera descarga, la app MUST pedir confirmación. La confirmación muestra:
  - los modelos pendientes con su tamaño y el total;
  - el espacio libre actual y el que quedará;
  - la red actual. Si son datos móviles, un aviso de que la descarga los usará;
  - el aviso de RAM si la total es < 7,5 GB (G31).

  Acciones: «Descargar», «Seleccionar archivo del dispositivo» y «Cancelar».
- **FR-010**: En Android 13 o superior, justo antes de la primera descarga y no en la Bienvenida, la app MUST explicar y pedir el permiso de notificaciones (G30). Si se deniega, la descarga MUST continuar y la Preparación MUST indicar que el progreso solo se ve dentro de la app.

**Descarga**

- **FR-011**: La app MUST descargar únicamente los ficheros del catálogo, desde sus URL fijadas a commit y por conexión cifrada. Solo MUST seguir redirecciones a dominios de Hugging Face.
- **FR-012**: Las descargas MUST ejecutarse como trabajo largo en primer plano, con una notificación de progreso. MUST sobrevivir a que el usuario salga de la app, a la muerte del proceso y al reinicio del teléfono, reanudándose solas cuando haya red.
- **FR-013**: Al reanudar, la app MUST pedir solo los bytes que faltan. Si el servidor no admite reanudar o el fichero remoto ha cambiado, MUST empezar de cero, descartando lo parcial.
- **FR-014**: Los modelos MUST descargarse de uno en uno: primero EmbeddingGemma 2 y después Gemma 4.
- **FR-015**: Tras la confirmación, la descarga MUST usar cualquier red disponible, Wi-Fi o datos móviles, sin volver a preguntar al cambiar de red. La confirmación MUST indicar la red actual y avisar si son datos móviles.
- **FR-016**: Antes de empezar y antes de cada modelo, la app MUST comprobar que el espacio libre cubre los bytes que faltan más un margen de 500 MB. Si no, MUST mostrar «Sin espacio», con la cantidad necesaria, sin empezar.
- **FR-017**: El progreso (porcentaje y bytes) MUST salir de los bytes realmente escritos y coincidir en la pantalla y en la notificación. Nunca hay porcentajes inventados ni barras que avancen solas.
- **FR-018**: El usuario MUST poder detener una descarga desde la fila o desde la notificación. Detenerla conserva lo descargado. Tocar la fila en pausa la reanuda.
- **FR-019**: Ante errores de red o del servidor, la app MUST reintentar con espera creciente. Tras 5 fallos seguidos, MUST mostrar «Error» con el motivo y «Reintentar».

**Verificación y compatibilidad**

- **FR-020**: Antes de marcar «Listo», la app MUST calcular el SHA-256 del fichero completo y compararlo con el del catálogo. Con una huella incorrecta, MUST eliminar el fichero, mostrar «Error» («La descarga no superó la verificación») y no cargarlo nunca.
- **FR-021**: Tras la huella correcta, la app MUST comprobar la compatibilidad real («Preparando», confirmado en clarify):
  1. El fichero se abre con el motor de inferencia y declara los backends esperados.
  2. Gemma 4 se arranca una vez en la GPU. Si falla, se prueba en CPU: si arranca, queda «Listo» con el aviso «Modo CPU, más lento»; si no, queda «Incompatible».
  3. EmbeddingGemma 2 se arranca en CPU y produce un vector de la dimensión esperada.

  La prueba libera los motores al terminar y respeta la regla de un único motor en GPU.
- **FR-022**: El resultado de la verificación MUST guardarse: huella, tamaño, fecha de modificación y backend comprobado.
  - En cada arranque, la app MUST comprobar de forma barata que cada fichero «Listo» sigue existiendo con el mismo tamaño y fecha, sin recalcular la huella.
  - Si algo difiere, MUST volver a verificarlo antes de declararlo «Listo».
  - Si falta, MUST pasar a «Por configurar».
- **FR-023**: Un fichero completo pero sin verificación registrada MUST verificarse antes de usarse. Los ficheros parciales de modelos que ya no están en el catálogo MUST eliminarse.

**Importación desde el dispositivo** (G02)

- **FR-024**: «Seleccionar archivo del dispositivo» MUST abrir el selector del sistema, sin permisos de almacenamiento amplios.
  - El fichero elegido se copia al almacenamiento privado con progreso real («Importando»).
  - Pasa la misma verificación de FR-020 y FR-021.
  - Si la huella no corresponde al modelo, la copia se elimina y queda «Incompatible» («El archivo no corresponde a …»).
  - El fichero original no se modifica.

**Almacenamiento**

- **FR-025**: La app MUST preparar las carpetas privadas, excluidas de copias de seguridad, para modelos, documentos e índice. La tarjeta de almacenamiento MUST mostrar:
  - «Offline» cuando están listas y hay espacio (como en el PNG);
  - «Sin espacio» cuando no.

  La base de datos y el índice se crean en F04.

**Red y privacidad**

- **FR-026**: El acceso a red MUST quedar confinado al componente de descarga de modelos. Una comprobación automática en las pruebas MUST fallar si cualquier otro código de la app abre conexiones de red.
- **FR-027**: Los permisos de la app MUST limitarse a los que necesita la descarga: red, estado de la red, notificaciones y trabajo en primer plano. `allowBackup` MUST seguir en `false`. Las pruebas de invariantes de plataforma de F01 MUST actualizarse a esta lista cerrada.
- **FR-028**: Las peticiones de descarga MUST contener solo lo necesario para obtener el fichero, sin identificadores del usuario ni del dispositivo. Ningún log MUST incluir rutas de ficheros del usuario ni contenido privado.
- **FR-029**: Con los modelos «Listo», la app MUST funcionar con la red desactivada. Sin descargas pendientes, MUST NOT abrir conexiones.

**Compatibilidad con F02**

- **FR-030**: La herramienta de diagnóstico de depuración (F02) MUST seguir funcionando con el mismo almacén de modelos: importación por adb, verificación y mediciones. Los ficheros que deja allí cuentan para la Preparación si son los requeridos y están verificados.

**Accesibilidad y fidelidad**

- **FR-031**: Cada fila MUST anunciarse como una unidad con nombre, papel, estado y progreso verbal, por ejemplo «Gemma 4, modelo de respuestas, descargando, 34 por ciento, 880 megabytes de 2,59 gigabytes». Los cambios de estado MUST anunciarse sin robar el foco.
- **FR-032**: Todos los elementos interactivos MUST tener al menos 48 dp de área táctil. El contraste MUST cumplir WCAG AA (D-015). El texto MUST escalar sin recortes, y todos los textos MUST estar en recursos.
- **FR-033**: Las pantallas MUST usar los tokens y componentes `Lisdata*`. El modo oscuro MUST derivarse de los tokens (D-016). Las diferencias frente a los PNG MUST reportarse al cerrar la feature.

### Key Entities *(include if feature involves data)*

- **Modelo requerido**: entrada del catálogo con identificador, nombre visible, papel («Modelo de respuestas» o «Búsqueda semántica»), fichero, tamaño, huella SHA-256, repositorio y revisión fijada. En F03 son dos: Gemma 4 E2B genérico y EmbeddingGemma 2 270M.
- **Estado del modelo**: uno de los de FR-007. Lleva:
  - bytes hechos y bytes totales, si aplica;
  - motivo, si hay error o incompatibilidad;
  - backend comprobado, si está «Listo».
- **Verificación registrada**: huella comprobada, tamaño, fecha de modificación, backend y fecha de la comprobación. Permite el chequeo barato en cada arranque.
- **Descarga**: modelo, bytes recibidos, total, si se puede reanudar y número de reintentos.
- **Progreso del onboarding**: no iniciado, iniciado o completado.
- **Almacenamiento local**: carpetas privadas preparadas, espacio libre y espacio necesario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100 % de los casos de prueba, ningún modelo aparece «Listo» sin huella correcta y prueba de compatibilidad superada. Un fichero con 1 byte alterado se detecta siempre y nunca se carga.
- **SC-002**: Tras matar la app con la descarga de Gemma al ≈ 50 %, la descarga termina transfiriendo como máximo un 5 % más que el tamaño del fichero.
- **SC-003**: Tras cortar la red durante al menos 1 minuto, la descarga continúa sola en los 30 s siguientes a que vuelva, sin intervención del usuario.
- **SC-004**: El progreso mostrado nunca retrocede, salvo un reinicio explícito tras un error, y coincide con los bytes escritos con un margen del 1 %.
- **SC-005**: En el teléfono de referencia con Wi-Fi, completar el onboarding requiere como máximo 4 toques de la app (Comenzar, fila, Descargar, Continuar), sin contar los diálogos de permisos del sistema.
- **SC-006**: Al relanzar en cualquier paso, la app vuelve al mismo paso con los mismos estados.
- **SC-007**: Con el onboarding completado, la app abre Inicio en el teléfono de referencia en menos de 1 s, mediana en frío. El chequeo de arranque no recalcula huellas.
- **SC-008**: La comprobación automática detecta cualquier apertura de red fuera del componente de descarga. Con los modelos listos y en modo avión, el recorrido completo de la app no abre ninguna conexión.
- **SC-009**: Las comprobaciones automáticas de accesibilidad pasan en todos los estados de las dos pantallas, en claro y en oscuro: 48 dp y etiquetas. TalkBack anuncia el estado de cada modelo.
- **SC-010**: La prueba de compatibilidad de Gemma 4 en el teléfono de referencia termina en menos de 30 s y deja la caché del motor preparada. El primer arranque posterior de Gemma tarda como en la medición «con caché» de F02 (≈ 5,4 s) y no como en frío (≈ 16,9 s).

## Assumptions

- **Origen de los modelos:** Hugging Face `litert-community`, público y sin autenticación, con la revisión fijada en el catálogo de F02 (D-011). Si el repositorio cambia, la huella fijada lo detecta.
- **Margen de espacio:** 500 MB sobre los bytes que faltan, para que tras la descarga quede sitio para empezar a importar documentos. Se puede ajustar en el plan.
- **Orden de descarga:** primero EmbeddingGemma 2 (≈ 165 MB), para que un modelo quede «Listo» pronto; después Gemma 4.
- **Textos no maquetados:** los textos de la tabla de FR-007, de FR-008 y de la confirmación son propuestas que siguen el tono de los MD. Se aprueban con esta spec (constitución VII).
- **Modelos ausentes tras el onboarding:** Inicio todavía es un marcador de posición. En F09, Inicio mostrará «Configuración pendiente» con acceso a la Preparación (MD 03). F03 entrega el estado real que lo hace posible y mantiene abierta la ruta a la Preparación.
- **RAM:** el aviso con < 7,5 GB es informativo y no bloquea (constitución IV, G31).
- **Fuera de alcance:**
  - base de datos e índice (F04);
  - importar documentos (F06);
  - Inicio real (F09);
  - gestión de modelos en Ajustes (F12);
  - Play Asset Delivery (D-011).
- **Ficheros de diagnóstico:** los modelos que solo usa la herramienta de diagnóstico (Gemma `-gpu`, EmbeddingGemma 2 de 440M y 740M) no aparecen en la Preparación ni se descargan en la app.
