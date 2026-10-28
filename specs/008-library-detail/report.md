# Informe de cierre — F08 Biblioteca y detalle de documento

**Rama:** `008-library-detail`.
**Fecha:** 2026-10-28.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64;
- AVD `MicroRAG_API33`, API 33;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), con los modelos de F03 descargados y verificados de nuevo en esta sesión (§6).

**Versiones:** sin dependencias nuevas. Esquema del índice v3 (`SCHEMA_VERSION = 3`).

## 1. Qué se ha implementado

- **Esquema v3** (D-044, research R1):
  - migración `from = 2` con `name_norm`, `replaces_id` (`ON DELETE CASCADE`), `idx_documents_created` e `idx_documents_replaces`;
  - `name_norm` se rellena en Kotlin al abrir (`DocumentNames.fold`: NFD, sin marcas diacríticas, minúsculas), en una transacción e idempotente;
  - `offline/` espera la v3.
- **Biblioteca** (`library`, PNG 06):
  - lista de todos los documentos importados, del más reciente al más antiguo, en bloques de 50 sin leer nunca fragmentos;
  - búsqueda por nombre sin acentos y por subcadena, con `%`, `_` y `\` literales;
  - filtros Todos, Indexados y Pendientes (G16: «Pendientes» incluye pendientes, con error y fuera del índice);
  - cabecera «N DOCUMENTOS» con «Incluir en RAG», filas con interruptor y tarjeta final;
  - estados B1–B8: carga, vacía, sin resultados, error, no disponible, filas con error, sin indexar y reindexando, y aviso al volver de un documento borrado o inexistente;
  - la búsqueda, el filtro y la posición se conservan al volver del detalle y al girar.
- **Inclusión:** el interruptor guarda `documents.included` y solo afecta a las búsquedas siguientes. Deshabilitado si el documento no está indexado. Varios toques seguidos: gana el último.
- **Detalle** (`document/{documentId}` con `chunkId` opcional, PNG 07):
  - héroe con el nombre y el chip de estado, rejilla 2×2 con cifras reales e «Incluir en respuestas»;
  - vista previa del primer fragmento con su página o, desde una cita, el fragmento citado resaltado entre la última frase del anterior y la primera del siguiente (D-043);
  - estados D1–D11: pendiente, en indexación, con error, fuera del índice, reindexando, reindexado fallido, cita perdida, borrando, carga, falta la copia y no disponible;
  - un documento inexistente o borrado vuelve atrás con un aviso, sin cierre.
- **Acciones:**
  - **Eliminar**, con diálogo: borra en una transacción el documento, sus fragmentos y su texto completo (en cascada y por trigger) y marca `quant_dirty`. Después lo saca de la tanda y borra la copia privada. Es el mismo camino que «Quitar de la cola» de la Ingesta.
  - **Quitar del índice** (G17, D-043): borra fragmentos y vectores, conserva la copia y deja el documento «Fuera del índice». No vuelve a la cola ni a ninguna tanda.
  - **Reindexar:** un documento sombra se indexa con la tanda de F07. Al terminar, una sola transacción pasa sus fragmentos al original. Mientras tanto, las respuestas usan la versión anterior. Si se quitó del índice, se reindexa directamente.
- **Coherencia con F06 y F07:** la cola de Ingesta y la pantalla de indexación ignoran las sombras y los documentos fuera del índice. `IndexingRepository.add(id)` mete en la tanda solo el documento reindexado.
- **Accesibilidad:** filas y cifras leídas como frases, anuncios educados de los cambios, 48 dp, fuente al 200 % con acciones apiladas, claro y oscuro, textos en español e inglés.
- **Privacidad:** los logs solo llevan ids, cifras y causas.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 114/114 |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| Tests JVM | ✅ 532/532 (F07: 415) |
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 426/426 (F07: 291) |
| Instrumentados en la AVD API 33 (mismos filtros) | ⚠️ 422/426. Fallan las 4 de navegación de F06 §7 (Atrás en la raíz en Android 12–13), igual que con `main` |
| Instrumentados en el teléfono (mismos filtros) | ✅ 426/426. Los datos reales del teléfono quedaron idénticos antes y después (hash de fragmentos y vectores) |
| `@RequiresModels` en el teléfono | ✅ 10/10. Golden de embeddings: coseno mínimo 0,99299 en GPU y 1,0 en CPU. Arranque en caliente de Gemma: 5,88 s |

Tras la última corrección (`7b87b98`, el aviso de la Biblioteca), se repitieron las suites instrumentadas:
- emulador API 37: 426/426 ✅;
- AVD API 33: 422/426 ⚠️, con las mismas 4 de F06 §7;
- teléfono: se cortó en la prueba 140 de 426 porque el teléfono se desconectó, con 137 pasadas y 0 fallos.

La corrección se comprobó a mano en el teléfono (el aviso se ve, §5) y con `NavigationTest`.

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Encontrar cualquier documento y ver su estado | `RagIndexLibraryTest`, `DefaultLibraryRepositoryTest`, `LibraryViewModelTest`, `LibraryScreenTest`; T022 y recorrido en el teléfono | ✅ «guia» encuentra «Guía de instalación.md»; «anual», «Archivo anual.pdf» y «Manual de prueba.pdf». Indexados 6, Pendientes 2 (pendiente y error), Todos 8. Búsqueda, filtro y posición se conservan al volver del detalle y al girar |
| **US2** Decidir qué documentos usa el asistente | `InclusionSearchTest`, `LibraryViewModelTest`; sonda temporal en el teléfono | ✅ Con el interruptor del detalle apagado, 0 de 11 búsquedas (vectorial y de texto, con los vectores y palabras del propio documento) lo encuentran; encendido, 11 de 11 |
| **US3** Ver el detalle real, también desde una cita | `DocumentViewModelTest`, `DocumentScreenTest`, `PreviewTextTest`; capturas | ✅ Cifras reales, «Página N» en PDF y sin chip en TXT y MD. La cita repetía la frase de contexto (§6), corregido |
| **US4** Eliminar, quitar del índice y reindexar | `ReindexFlowTest`, `DocumentDeleteBenchmarkTest`, `IngestRunnerTest`, `DefaultImportQueueRepositoryTest`; T044 y recorrido en el teléfono | ✅ §4. Los 8 documentos de prueba se borraron desde la app: 7 desde el detalle y el pendiente con «Quitar de la cola» |
| **US5** Pantallas fieles, accesibles y coherentes | `LibraryScreenAccessibilityTest`, `DocumentScreenAccessibilityTest`; capturas T049 | ✅ con las diferencias de §5. TalkBack: ⏳ a cargo del usuario |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001: primera página ≤ 1 s y cada bloque ≤ 100 ms con 1.000 documentos | ✅ p95 18,5 ms y 20,0 ms en el teléfono (§4) |
| SC-002: búsqueda y filtros exactos; estado conservado | ✅ Pruebas de índice y ViewModel; recorrido en el teléfono, también al girar |
| SC-003: excluir quita el documento de todas las búsquedas | ✅ 0/11 excluido y 11/11 incluido sobre el índice real |
| SC-004: eliminar no deja restos | ✅ Tras borrar los 8 documentos desde la app: 0 documentos, 0 fragmentos, 0 filas de texto completo, 0 copias privadas; integridad de SQLite, claves foráneas y FTS correctas. La Biblioteca, la Ingesta y el Estado de indexación quedan vacíos |
| SC-005: quitar del índice y reindexar da los mismos fragmentos | ✅ En el teléfono, desde la app, con un MD y un TXT: mismos fragmentos y vectores byte a byte (hash) |
| SC-006: nunca una mezcla ni ninguna versión; el original sobrevive a una muerte | ✅ T044 (§4) |
| SC-007: un documento inexistente nunca cierra la app | ✅ `NavigationTest` (vuelve a la Biblioteca con el aviso); en el teléfono, un id inexistente abierto con la ruta de depuración vuelve a su padre sin cierre |
| SC-008: accesibilidad en todos los estados, claro y oscuro, 200 % | ✅ Las dos pruebas de accesibilidad en el teléfono y en los emuladores |
| SC-009: borrar 4.300 fragmentos sin bloquear y medido | ✅ `LibraryStrictModeTest` con control negativo; en el teléfono, 121 ms de media en el benchmark y 367 ms con el índice real (§4) |
| SC-010: sin red y sin nombres en los logs | ✅ 347 líneas distintas de la app en el teléfono durante el recorrido: solo ids, cifras, causas y nombres de modelo. Los contadores de red de su UID no cambian de 16:00 a 16:58, todo el recorrido salvo los dos minutos tras instalar F08 (15:58), que caen en el tramo horario de la descarga de los modelos (desde las 15:24, tráfico permitido). `NetworkConfinementTest` y `NoTrafficInstrumentedTest` en verde |

## 4. Cifras medidas

### Migración a v3 (T006, teléfono)

Datos v2 creados con la versión de `main` (§6): 8 documentos (6 indexados con 1, 3, 430, 4.300, 2.150 y 1 fragmentos, 1 con error y 1 pendiente) y 6.885 fragmentos.

| Medida | Resultado |
|---|---|
| Apertura con migración 2 → 3 y relleno de 8 nombres | 27 ms, `Compatible` |
| `schema_version` e índices nuevos | 3; `idx_documents_created` e `idx_documents_replaces` |
| `name_norm` | 8/8, igual al nombre plegado |
| Columnas v2 de `documents` y todas las de `chunks` (texto y vectores) | Idénticas a las de antes de migrar |
| Integridad de SQLite, claves foráneas y FTS | Correctas |

### Biblioteca (T022, `LibraryBenchmarkTest`, teléfono, 1.000 documentos)

| Medida | Objetivo | Con 0 fragmentos | Con 50.000 fragmentos |
|---|---|---|---|
| Primera página con recuento | SC-001: ≤ 1 s | p50 13,1 ms · p95 18,5 ms | p50 11,4 ms · p95 15,7 ms |
| Cada bloque siguiente (100…1.000 filas) | SC-001: ≤ 100 ms | p50 15,7 ms · p95 20,0 ms | p50 13,7 ms · p95 17,2 ms |
| Lista entera (1.000 filas) | — | p50 13,1 ms · p95 16,6 ms | p50 16,6 ms · p95 19,9 ms |
| Búsqueda con recuento | — | p50 8,3 ms · p95 10,3 ms | p50 8,0 ms · p95 9,8 ms |

Insertar los 50.000 fragmentos de prueba: 35,4 s (emulador: 12,7 s).

### Borrados (T043 y T053, teléfono)

`DocumentDeleteBenchmarkTest`, 4.300 fragmentos de 256 componentes junto a otros 4.300 que se quedan, 3 rondas:

| Operación | Objetivo | Mín. | Máx. | Media |
|---|---|---|---|---|
| Eliminar | Medir; informar si > 5 s | 104 ms | 149 ms | 121 ms |
| Quitar del índice | Igual | 98 ms | 140 ms | 113 ms |
| Publicar un reindexado | — | 161 ms | 223 ms | 189 ms |

Con la app y el índice real (6.885 fragmentos):

| Documento | En el índice | Acción completa |
|---|---|---|
| Eliminar, 4.300 fragmentos | 354 ms | 367 ms |
| Eliminar, 2.150 fragmentos | 167 ms | 173 ms |
| Eliminar, 430 fragmentos | 48 ms | 60 ms |
| Eliminar, 0–3 fragmentos (5 documentos) | 20–22 ms | 28–121 ms |
| Quitar del índice, 3 fragmentos | 18 ms | 98 ms |

### Reindexado seguro (T044, teléfono, modelo real en la GPU)

**Búsquedas durante el reindexado** (sonda temporal sobre el índice real, sin commit): reindexar el documento de 430 fragmentos mientras se busca sin pausa.

| Medida | Resultado |
|---|---|
| Búsquedas (vectorial y de texto) mientras duró | 1.652 en 95,7 s |
| Con la versión anterior completa | 1.652 (100 %) |
| Con la sombra, vacías o con mezcla | 0 |
| Publicación | Una transacción de 55 ms |
| Resultado | 430 fragmentos con ids nuevos, idénticos por ordinal en texto, página y encabezados; coseno mínimo con los vectores anteriores 1,00000 |

**Muerte a mitad, sin abrir la app** (reindexado lanzado desde el detalle):

| Medida | Resultado |
|---|---|
| Muerte (`kill -9`, app en segundo plano) | Con 201 de 430 embebidos; 192 guardados (3 lotes) |
| Con el proceso muerto | Original intacto (mismo hash de fragmentos y vectores); sombra en `embedding` con 192 fragmentos |
| Vuelta del trabajo | A los 79 s, como job normal (Android no deja pasar a primer plano desde segundo plano) |
| Reanudación | Desde los 192 guardados, sin recalcular |
| Publicación | Una transacción de 131 ms |
| Resultado | 430 fragmentos, ordinales 0..429 sin huecos, ids nuevos, mismo hash que antes; ninguna sombra; integridad correcta |

### Indexación de los datos de prueba (versión de `main`, GPU)

| Documento | Fragmentos | Tiempo | Ritmo |
|---|---|---|---|
| PDF de 200 páginas | 430 | 88,7 s | 4,85 fragmentos/s |
| PDF de 1.000 páginas | 2.150 | 490,4 s | 4,38 fragmentos/s |
| PDF de 2.000 páginas | 4.300 | 902,1 s | 4,77 fragmentos/s |

`IndexingBenchmarkTest` (`@RequiresModels`), 200 páginas: GPU 98,2 s (4,50 fragmentos/s) y CPU 134,7 s (3,23), sin el LLM cargado. F07: 88,6 s y 134,3 s.

## 5. Diferencias frente al mockup

Capturas del teléfono, en claro y en oscuro y sin la barra de estado:
- **Biblioteca:** como el PNG 06, final de la lista, sin resultados, vacía, fila reindexando, aviso tras eliminar y en horizontal.
- **Detalle:** como el PNG 07, desde una cita (los dos extremos), pendiente, con error, MD sin página, menú «⋯», diálogos de quitar y eliminar, fuera del índice y reindexando con sus acciones.

D6, D7, D10 y D11 no se provocaron en el teléfono; los cubren las pruebas JVM y las de accesibilidad.

**Biblioteca (PNG 06):** coincide en estructura, textos, colores, iconos y formato («PDF · 2.150 fragmentos»). Diferencias:
- **Altura de las filas:** ≈ 80 dp frente a ≈ 76 en el PNG, por la escala de texto del sistema de diseño, como en F07.
- **Tarjeta final:** va al final de la lista. El PNG la dibuja tras 4 filas con «24 DOCUMENTOS», cosa que no puede darse a la vez.
- **Estados nuevos:** B1–B8, aprobados.

**Detalle (PNG 07):** coincide en héroe, rejilla, interruptor, tarjeta de vista previa y acciones. Diferencias:
1. **Vista previa larga:** muestra el primer fragmento real entero (FR-014, D-043), de ≈ 1.500 caracteres en los PDF de prueba. El PNG maqueta un extracto de tres líneas. La tarjeta es larga y las acciones quedan bajo el pliegue: hacen falta unos cuatro desplazamientos. Con cita, lo mismo, más las frases de contexto. Propuesta en §7.
2. **Subtítulo del interruptor:** «Disponible para consultas RAG» es fijo, como en el PNG, que solo maqueta el caso incluido. También sale con el documento excluido o sin indexar, y no es cierto (constitución V). Propuesta en §7.
3. **Fecha:** «28 oct», con el formato de la plataforma para el español. El PNG pone «12 oct.».
4. **Chip del héroe:** el de éxito normal, como en el PNG; el contrato pedía la variante para fondo oscuro.
5. **Cita:** se resalta el fragmento entero (D-043); el PNG resalta una frase.
6. **«Eliminar»:** en el PNG, con una letra algo menor. Aquí lleva la del sistema de diseño y relleno compacto (§6).

## 6. Desviaciones respecto al plan y fallos encontrados

### El teléfono, sin la app al empezar

Al conectar el teléfono, MicroRAG no estaba instalada: ni la app ni sus datos, entre ellos el documento propio del usuario, los modelos y los documentos de prueba de F07. Tampoco estaban las carpetas de prueba `MicroRAG-F06`, `MicroRAG-F07` y `MicroRAG-T045`. Solo quedaba el APK de pruebas.

En esta sesión no se desinstaló nada en el teléfono. El usuario eligió recrear datos v2 para probar la migración de verdad:
1. Se instaló la versión de `main`.
2. Se descargaron los modelos desde la app (2,8 GB, verificados por SHA-256).
3. Se copiaron 9 ficheros de prueba a `Download/MicroRAG-F08`, de los que se importaron 8 documentos.
4. Se indexaron, dejando uno pendiente.
5. Se instaló F08 encima.

El documento propio del usuario no se ha recreado. Al cerrar, la Biblioteca queda vacía y la carpeta de prueba borrada; los modelos siguen instalados.

### Decisiones de implementación (propuestas para D-045)

1. **`idx_documents_replaces`:** cuarto paso de la migración. Sin él, la cascada y el estado de reindexado de cada fila recorren la tabla (research R1, data-model §1).
2. **`IndexingRepository.add(id)`:** mete en la tanda un único documento. `start()` habría metido también los pendientes que el usuario no mandó indexar.
3. **`DocumentFiles`:** cerrojo compartido y limpieza de huérfanos una vez por proceso, para la Ingesta y la Biblioteca.
4. **Chip del héroe** con el estilo normal, como el PNG 07.
5. **Causa de D6:** la primera frase del detalle del error, para no repetir «No se pudo reindexar: No se pudo indexar.».
6. **Contexto de la cita sin el párrafo arrastrado:** el `Chunker` del informe repite al principio de un fragmento el último párrafo corto del anterior. Ese párrafo se descarta antes de tomar las frases de contexto (research R7, punto 5).
7. **Acciones del detalle medidas:** el principal ocupa el resto de la fila y «Eliminar» lo que necesita, con el relleno compacto del PNG 07 (`compact` en `LisdataGradientButton` y `LisdataOutlinedButton`). Si no caben en una línea, se apilan, como ya pasaba con la fuente al 150 % o más.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| `NoTrafficInstrumentedTest` fallaba porque «Biblioteca» sale ahora también en la barra superior; una cifra ausente no se leía como palabras; el recorrido con tabulador del título | Emuladores | `748259e` |
| La cita mostraba la frase de contexto repetida al principio del resaltado | Teléfono (T049) | `5da8eed`, con pruebas en JVM y en el dispositivo (`Chunker` real) |
| «Eliminar» se partía en dos líneas («Elimina / r») en el teléfono | Teléfono (T049) | `3e03f87`. Las pruebas de diseño miden texto real (gráficos nativos de Robolectric) y fallan con el código anterior |
| El aviso de la Biblioteca («Documento eliminado», «Este documento ya no existe») no llegaba a verse: consumir el mensaje cancelaba el *snackbar* | Teléfono (T053) | `7b87b98`. La prueba de navegación comprueba que sigue visible al segundo y falla con el código anterior. Era la causa de la prueba inestable que se había atribuido al reloj |

### Comportamientos anotados (sin cambio)

- **Ruta de depuración:** abre el detalle como raíz. «Volver» no tiene adónde ir, y un id inexistente vuelve a Inicio, su padre. Desde la Biblioteca vuelve a la Biblioteca con el aviso.
- **Tras matar el proceso a mitad del reindexado:** Android lo devolvió como job normal a los 79 s, sin primer plano (`ForegroundServiceStartNotAllowedException`), como en F07.
- **Robolectric mide el texto a 1 dp por carácter.** Las pruebas que dependen del ancho del texto usan `@GraphicsMode(NATIVE)`.
- **Notificaciones de otras apps:** dos capturas recogieron notificaciones emergentes de otras apps. Se borraron sin conservar su contenido y se repitieron.

## 7. Pendiente y decisiones del cierre

**Repetición de la suite en el teléfono tras `7b87b98`:** cortada en la prueba 140 de 426 por la desconexión del teléfono (137 pasadas y 0 fallos, §2). Se puede repetir entera al volver a conectarlo.

**Pendiente:**
- **TalkBack** en la Biblioteca y el detalle, a cargo del usuario, como en F01, F03, F06 y F07 (T066 de F07 sigue abierto).
- **Documento propio del usuario:** si quiere tenerlo de nuevo en la app, hay que importarlo otra vez (§6).

**Propuestas para decidir en el cierre:**
1. **Subtítulo del interruptor según el estado** (constitución V). Propuesta:

   | Estado | Subtítulo |
   |---|---|
   | Incluido e indexado | «Disponible para consultas RAG» (PNG) |
   | Excluido | «No se usa en las respuestas» |
   | Sin indexar | «Se podrá incluir cuando esté indexado», el mismo texto que TalkBack lee en la fila de la Biblioteca |
2. **Vista previa larga.** Opciones:
   - **(a)** dejarla como está (FR-014, D-043);
   - **(b)** recortar la vista previa sin cita a unas 6 líneas con «Ver más», para que las acciones se vean como en el PNG 07, y dejar entera la cita.
3. **Registrar D-045** con las decisiones de implementación de §6.
