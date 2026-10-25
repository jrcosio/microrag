# Revisión de accesibilidad — F01

**Fecha:** 2026-10-25
**Dispositivo:** emulador `Pixel_10` (AVD, API 37, arm64-v8a, locale en-US), densidad ≈2,75

## 1. Comprobación automática (T056, SC-006)

`CatalogAccessibilityTest` (instrumentado) recorre las 41 entradas del catálogo en claro y oscuro (82 casos). Cada caso:
- pasa los checks del *Accessibility Test Framework* (`enableAccessibilityChecks()`: área táctil, texto hablable, contraste);
- comprueba que todo nodo clicable tiene un área táctil ≥ 48 × 48 dp (tras hacer scroll hasta el nodo);
- comprueba que todo control con estado (interruptor, radio, selección) tiene etiqueta.

**Resultado:** 82/82 casos ✅, más `ManifestInvariantsTest` 4/4 y `SystemBackTest` 1/1. T057 no requirió correcciones, porque ningún check falló.

## 2. Fuente del sistema al 200 % (T058, SC-004)

Se revisó con `adb shell settings put system font_scale 2.0` (escalado no lineal de Android 14+) y la opción «Fuente del sistema» del catálogo.

| Componente | Hallazgo | Acción |
|---|---|---|
| `LisdataProgressRing` | La etiqueta central se partía dentro del anillo de tamaño fijo («COMPLETA-DO», «PROCESSI-NG…») | **Corregido:** el anillo se dimensiona con un `Layout` propio según su etiqueta (sin saltos de línea) y mantiene el tamaño indicado como mínimo |
| Catálogo (debug) | El selector de escala sobrescribía la escala del sistema con un 100 % fijo | **Corregido:** la nueva opción «Fuente del sistema» (por defecto) respeta la escala del sistema |
| `LisdataBottomBar` | Con 200 %, «Biblioteca» se trunca con elipsis («Biblio…»): la barra de M3 tiene altura fija y una línea | Se acepta. TalkBack anuncia la etiqueta completa. Comportamiento estándar de M3 (D-018) |
| Filas de chips o botones (`LisdataFilterChip`, `LisdataCountChip`, `LisdataOutlinedButton` en la misma fila) | Si se ponen en un `Row` fijo, las palabras se parten («Pendi-entes») | Es maquetación de pantalla: el catálogo usa `FlowRow`. **Regla para F06/F08/F11:** las filas de chips o botones usan `FlowRow` o desplazamiento horizontal |
| `LisdataStatCard` en 2 columnas | «FRAGMENTOS» se parte en 2 columnas al 200 % | **Regla para F08/F09:** las rejillas 2×2 pasan a una columna con escala de fuente grande (edge case de la spec) |
| Resto de componentes | Los contenedores crecen y el texto pasa a varias líneas sin recortes; las elipsis solo aparecen donde el diseño las prevé (nombres de documento, títulos de barra superior) | ✅ |

## 3. TalkBack (T059)

**Verificado de forma automática y estructural:**
- Checks de ATF en las 82 variantes del catálogo (texto hablable en todos los controles).
- Árbol de accesibilidad del shell (`uiautomator dump`): flecha Atrás con descripción «Back»/«Volver»; título como encabezado; cuatro pestañas con etiqueta; marcador con título y descripción leídos como texto.
- Por diseño:
  - las filas agrupan su semántica (`mergeDescendants`);
  - los interruptores tienen descripción «Incluir … en las consultas»;
  - el progreso expone `stateDescription` («68 % completado» / «Procesando…») y `ProgressBarRangeInfo`;
  - los iconos decorativos y las órbitas del hero no se anuncian.

**Pendiente:** el recorrido manual con TalkBack activado. Requiere escuchar la salida de voz en un dispositivo, así que lo debe hacer una persona revisora. Se mantiene T059 abierta hasta entonces. Guion propuesto:
1. Recorrer el catálogo con deslizamientos.
2. Comprobar el anuncio de interruptores, radios y chips de filtro.
3. Navegar por las pestañas y usar la flecha Atrás.
