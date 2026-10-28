# Informe de cierre — F09 Inicio

**Rama:** `009-home`.
**Fecha:** 2026-10-28.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64;
- AVD `MicroRAG_API33`, API 33;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), con los modelos de F03 verificados.

**Versiones:** sin dependencias nuevas. Esquema del índice v3, sin cambios.

## 1. Qué se ha implementado

- **Inicio real** (`home`, PNG 03). Sustituye al marcador y retira los accesos provisionales de F06 y F07 con sus textos.
- **Cabecera:** el logotipo oficial (G20) y el icono de Ajustes, que abre la pestaña Ajustes.
- **Estado de la IA local** (R1, D-047):
  - `ObserveLocalAiUseCase` primero vuelve a leer el registro de verificación, sin calcular huellas, y después sigue los ficheros y sus backends preparados;
  - la regla «listo» es `ModelCatalog.allReady`, la misma que completa el onboarding;
  - sin chip durante la primera comprobación, con el hueco reservado (P2);
  - «Configuración pendiente» va con «Completar configuración» → Preparación (P1);
  - se vuelve a comprobar en cada `ON_RESUME`, manteniendo el último estado comprobado (FR-009).
- **Héroe y accesos:**
  - «Nuevo chat» abre la pestaña Chat con una conversación nueva, sin restaurar la anterior (`openNewChat`);
  - Importar, Documentos, Índice local e Idiomas abren su destino;
  - cada salida solo navega con Inicio en `RESUMED`, así que un doble toque abre un solo destino;
  - la Preparación abierta desde Inicio vuelve a Inicio sin duplicarlo (`leaveSetup`).
- **«TU BIBLIOTECA»** (R2):
  - `RagIndex.librarySummary()` es una consulta sobre `documents`: todos sin sombras, y la suma de `chunk_count` de los indexados;
  - `LibraryRepository.summary()` la recalcula con cada cambio del índice;
  - estados: vacío con «Importar primer documento» (P3), esqueleto al cargar y error con «Reintentar» solo para el resumen (P4);
  - TalkBack lo lee como una frase.
- **Tarjeta de la tanda activa** (R3, P5):
  - va entre el héroe y los accesos, con las cifras y las causas de pausa del Índice;
  - tocarla abre el Índice;
  - desaparece al terminar la tanda.
- **Accesibilidad:**
  - con la fuente al 150 % o más, una columna de accesos y cifras apiladas;
  - horizontal con desplazamiento;
  - claro y oscuro; 48 dp; textos en español e inglés.
- **Sistema de diseño:** `LisdataHeroCard(decorationIcon)` y `LisdataProgressCard(onClick, onClickLabel)`, ambos opcionales.
- **Privacidad:** dos líneas de log, `home ai=… in N ms` y `home summary documents=N chunks=M in N ms`, solo con cifras.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 114/114 |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` (copia limpia del último commit, `--rerun-tasks`) | ✅ «No issues found» |
| Tests JVM (ídem) | ✅ 584/584 (F08: 537) |
| Instrumentados en el emulador API 37 (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 485/485 (F08: 431) |
| Instrumentados en la AVD API 33 (mismos filtros) | ⚠️ 481/485. Fallan las 4 de navegación de F06 §7 (Atrás en la raíz en Android 12–13), igual que en `main` |
| Instrumentados en el teléfono (mismos filtros) | ✅ 485/485 (595 s), con `HomeScreenLayoutTest` y `HomeSummaryBenchmarkTest`. El índice de la app quedó vacío e íntegro y los modelos, instalados |

Las suites de los emuladores se ejecutaron antes de la última corrección de las pruebas (§6, «horizontal»). Después se repitió `HomeScreenAccessibilityTest` en los dos: 48/48 ✅ en cada uno. Un primer intento en el teléfono dio 484/485 por ese mismo fallo, ya corregido.

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Empezar a preguntar o a gestionar con un toque | `HomeScreenTest`, `NavigationTest`, `SystemBackTest`, `ShellInsetsTest`; recorrido en el teléfono | ✅ Cada salida abre su destino y Atrás vuelve a Inicio. Un doble toque en Idiomas deja un solo destino. Atrás desde Inicio cierra la app |
| **US2** Saber si la IA local está lista | `ObserveLocalAiUseCaseTest`, `HomeViewModelTest`, `HomeScreenTest`; recorrido | ✅ Con la fecha del modelo de embeddings cambiada (`touch`, reversible), Inicio dice «Configuración pendiente». «Completar configuración» abre la Preparación, que vuelve a verificar el fichero; al pulsar «Continuar», Inicio dice «IA local disponible» y Atrás cierra la app (sin un segundo Inicio) |
| **US3** Ver de un vistazo cuánto hay en la biblioteca | `RagIndexSummaryTest`, `DefaultLibraryRepositoryTest`, `HomeViewModelTest`, `HomeScreenTest`; recorrido | ✅ §4 |
| **US4** Seguir una indexación en curso | `HomeViewModelTest`, `HomeScreenTest`, `HomeScreenLayoutTest`; recorrido | ✅ La tarjeta sigue la tanda (64 → 192 → 210 de 221) y desaparece al terminar. En pausa dice «En pausa», con las mismas cifras que el Índice. Tocarla abre el Índice |
| **US5** Un Inicio fiel al PNG y accesible | `HomeScreenAccessibilityTest`, `HomeScreenTest`; capturas | ✅ con las diferencias de §5. TalkBack: ⏳ a cargo del usuario |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001: Inicio < 1 s en frío; chip y resumen < 1 s más | ✅ Release: mediana 724 ms (716–779 ms, 10 medidas). El chip y el resumen llegan ≈ 0,43–0,45 s después de crearse Inicio (build de depuración) |
| SC-002: todas las salidas con un toque, Atrás a Inicio | ✅ Pruebas y recorrido |
| SC-003: nunca «disponible» con un modelo ausente, alterado o sin preparar | ✅ Pruebas con todos los casos; en el teléfono, con el fichero alterado |
| SC-004: cifras iguales a la Biblioteca y al índice real | ✅ `RagIndexSummaryTest` en SQLite real (emulador y teléfono) y recorrido (§4) |
| SC-005: resumen p95 ≤ 50 ms con 1.000 documentos y 50.000 fragmentos | ✅ p95 2,15 ms en el teléfono y 0,72 ms en el emulador |
| SC-006: ninguna cifra de ejemplo | ✅ Estado vacío con 0 documentos y esqueleto al cargar |
| SC-007: la tarjeta con las mismas cifras que el Índice | ✅ Recorrido: «64 de 192» en los dos durante el reindexado. En pausa, la tarjeta dice «En pausa», como el Índice, con «152 de 192» |
| SC-008: accesibilidad en todos los estados, claro, oscuro, 200 % y horizontal | ✅ `HomeScreenAccessibilityTest` (48 casos) en el teléfono y los emuladores |
| SC-009: sin red, logs sin nombres | ✅ Los contadores de red de la UID de la app (rx 2.855.600.722, tx 11.173.193) son los mismos que al cerrar F08: solo la descarga de los modelos. Las líneas de la app que quedaban en el log al final del recorrido (7) solo llevan cifras. `NetworkConfinementTest` y `LayeringTest` en verde |

## 4. Cifras medidas

### Resumen (T021, `HomeSummaryBenchmarkTest`, 1.000 documentos y 50.000 fragmentos)

| Dispositivo | p50 | p95 | Máximo |
|---|---|---|---|
| A063 (API 35) | 1,39 ms | 2,15 ms | 2,22 ms |
| Emulador API 37 | 0,53 ms | 0,72 ms | 0,85 ms |

### Arranque en frío con el onboarding completado (10 medidas, `am start -W`)

| Build | Mediana | Rango |
|---|---|---|
| Release (firmada con la clave de depuración) | 724 ms | 716–779 ms |
| Depuración | 1.070 ms | 1.061–1.096 ms |

F03 midió 820 ms con la release.

### Cifras de Inicio frente al índice (recorrido en el teléfono)

| Paso | Inicio | Índice en disco |
|---|---|---|
| Biblioteca vacía | «Tu biblioteca está vacía» | 0 documentos |
| 3 importados, sin indexar | 3 documentos y 0 fragmentos | 3 documentos |
| Indexados | 3 y 221 | 192 + 18 + 11 = 221 filas |
| Reindexando el de 192 | 3 y 221 | la sombra no cuenta |
| Tras reindexar | 3 y 221 | mismos 192 fragmentos (hash igual) |
| Excluir el de 18 | 3 y 221 | — |
| Quitarlo del índice | 3 y 203 | — |
| Eliminar el de 11 | 2 y 192 | 2 documentos, 192 filas |
| Eliminar los otros dos | «Tu biblioteca está vacía» | 0 documentos, 0 fragmentos, 0 copias |

## 5. Diferencias frente al mockup

Coincide en cabecera, chip, saludo, héroe con órbitas y destello, rejilla 2×2, tarjeta «TU BIBLIOTECA» y barra inferior. Diferencias:
1. **Cifras del resumen:** «documentos» va debajo de su cifra, como «fragmentos» (en el PNG va al lado). Anotada en el contrato.
2. **Órbitas:** quedan dentro del héroe; en el PNG salen por arriba. Igual que el resto de héroes de F01.
3. **Resumen bajo el pliegue:** en el teléfono, con su barra de estado y de navegación, la tarjeta «TU BIBLIOTECA» empieza justo en el borde y hay que desplazar un poco. En el PNG se ve entera.
4. **«Completar configuración»:** con la fuente normal no cabe al lado del chip y pasa a la línea siguiente.
5. **Logotipo:** 40 dp de alto, como en la Bienvenida. En el PNG mide unos 42.

## 6. Desviaciones respecto al plan y fallos encontrados

### Decisiones de implementación

1. **`SummaryResult`** va en `LibraryRepository.kt`, junto a `LibraryResult` y `DetailResult`, y no en `LibraryModels.kt` como decía data-model §2.
2. **Hueco del chip durante la comprobación:** una copia invisible del chip «IA local disponible», sin semántica. Reserva el mismo alto con cualquier tamaño de letra.
3. **Logs de Inicio:** los escribe `HomeViewModel`, con `AppLogger` y un reloj inyectables.
4. **Prueba del 200 %:** comprueba también que ninguna palabra se parta entre líneas. Con dos columnas, «Documentos» se partía sin puntos suspensivos, y una prueba que solo buscara puntos suspensivos no lo veía.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| `SystemBackTest` y `ShellInsetsTest` fallaban tras `OnboardingNavigationTest`: «No definition found for type ModelFileRepository». `ReadyModelsRule` y `OnboardingStageRule` descargaban su módulo de Koin al terminar, y eso borraba también la definición de la app. Nada lo necesitaba hasta que Inicio lo usó | Emuladores (suite completa) | `da5a16e`: las reglas guardan la instancia de la app y la vuelven a cargar |
| Una prueba de accesibilidad posterior a la de horizontal fallaba a veces en el teléfono («No compose hierarchies found»): su actividad se recreaba al terminar el giro | Teléfono | `16e2b6d`: la prueba de horizontal espera a cada orientación antes de seguir |

### Comportamientos anotados (sin cambio)

- **Ruta de depuración `home`:** no abre Inicio si la app arranca en la Bienvenida, porque Inicio es una pestaña y la Bienvenida no. Es solo de depuración y ya era así.
- **Ficheros copiados por adb:** el selector del sistema no los ve hasta que el escáner multimedia los indexa. Para el recorrido se escanearon solo los tres ficheros de prueba, y su entrada se borró al final.
- **Barra inferior con la fuente al 200 %:** «Biblioteca» se corta («Bibliot…»). Es la barra de F01 y queda para F15.
- **Android 14+ escala el texto grande de forma no lineal:** al 200 %, los títulos crecen menos que el cuerpo.

## 7. Pendiente y decisiones del cierre

**Pendiente:**
- **TalkBack** en Inicio, a cargo del usuario, como en F01, F03 y F06–F08.

**Propuestas para decidir en el cierre:**
1. **Resumen bajo el pliegue en el teléfono** (§5.3). Opciones:
   - **(a)** dejarlo así (el PNG no tiene barras de sistema);
   - **(b)** compactar el espacio entre bloques para que se vea entero sin desplazar.
2. **«Completar configuración»** (§5.4). Opciones:
   - **(a)** dejarlo en su propia línea;
   - **(b)** hacer tocable el propio chip «Configuración pendiente» y quitar el botón.
3. **Registrar D-048** con las decisiones de implementación de §6.
