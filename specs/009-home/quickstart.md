# Quickstart: validar Inicio (F09)

Comprobaciones para demostrar que F09 funciona de principio a fin. Los detalles de los tipos y las reglas están en [data-model.md](data-model.md) y [contracts/](contracts/).

**Requisitos:**
- JBR 21 de Android Studio;
- emulador `Pixel_10` (API 37) y AVD `MicroRAG_API33`;
- teléfono de referencia A063 con los modelos de F03 ya verificados.

## 1. Build, lint y pruebas JVM

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Esperado:
- todo en verde y lint sin avisos, ejecutado en una copia limpia del commit, sin cambios ajenos en el árbol;
- pasan las pruebas nuevas:
  - `ObserveLocalAiUseCaseTest`;
  - `DefaultLibraryRepositoryTest` (resumen);
  - `HomeViewModelTest`;
  - `HomeScreenTest`;
  - `NavigationTest` (Inicio real, sin los accesos provisionales; Preparación sin duplicar Inicio);
  - `LayeringTest` (`feature/home`).

## 2. Pruebas instrumentadas

Sin `@RequiresModels` ni `@RequiresNetwork`, en el emulador API 37, la AVD API 33 y el teléfono:

```bash
adb shell am instrument -w -e notAnnotation \
  com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

Esperado:
- verde, salvo las 4 de navegación ya conocidas en API 33 (F06 §7);
- `SystemBackTest` y `ShellInsetsTest` reconocen Inicio por su saludo y no por el marcador;
- `RagIndexSummaryTest` (SQLite real) cumple la regla H5 con documentos en todos los estados y una sombra;
- `HomeScreenAccessibilityTest` pasa en todos los estados, en claro y oscuro, al 200 % y en horizontal;
- solo en el teléfono:
  - `HomeSummaryBenchmarkTest` da un p95 ≤ 50 ms con 1.000 documentos y 50.000 fragmentos;
  - `HomeScreenLayoutTest` comprueba que, con una tanda activa, la tarjeta de indexación y la primera fila de accesos se ven sin desplazar.

## 3. Recorrido manual en el teléfono

Con las normas de privacidad de siempre:
- logs filtrados por el pid de la app;
- capturas solo de la app y sin la barra de estado;
- ficheros de prueba en una carpeta propia (`Download/MicroRAG-F09`), que se borra al final.

1. **Arranque en frío** con el onboarding completado: `am start -W`, 10 veces. Se anota la mediana de `TotalTime` (SC-001 < 1 s) y los milisegundos de las líneas `home ai=…` y `home summary …`.
2. **IA lista:** el chip dice «IA local disponible» y nunca aparece antes de la comprobación (se graba el arranque).
3. **IA no lista, de forma reversible:**
   - se cambia la fecha de modificación del modelo de embeddings con `run-as … touch`, sin borrarlo ni descargar nada;
   - al volver a Inicio aparece «Configuración pendiente»;
   - «Completar configuración» abre la Preparación, que vuelve a verificar el fichero;
   - al volver, Inicio dice «IA local disponible» y no hay dos Inicio en la pila (Atrás cierra la app).
4. **Biblioteca vacía:** «Tu biblioteca está vacía» y «Importar primer documento», que abre la Ingesta.
5. **Cifras:**
   - importar 3 documentos de prueba: el resumen da «3 documentos · 0 fragmentos» sin tocar nada;
   - indexarlos: la tarjeta de indexación aparece con las mismas cifras que el Índice y la notificación; al pausar muestra «En pausa»; al terminar desaparece y los fragmentos coinciden con la Biblioteca;
   - excluir uno no cambia las cifras; quitar otro del índice resta sus fragmentos; eliminar el tercero resta un documento.
6. **Accesos:** cada uno abre su destino con un toque, y Atrás vuelve a Inicio; un doble toque rápido abre un solo destino.
7. **Fuente al 200 %, oscuro y horizontal:** capturas y comparación con el PNG 03. Cada ajuste del sistema que se cambie se pide antes y se restaura.
8. **Privacidad:**
   - los logs de la app solo llevan cifras;
   - `netstats` de la UID de la app no cambia durante el recorrido.
9. **Limpieza:** se borran desde la app los documentos de prueba y la carpeta `MicroRAG-F09`.
