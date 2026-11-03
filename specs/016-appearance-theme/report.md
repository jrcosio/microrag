# Informe de cierre — F16 Apariencia: tema e icono de Ajustes

> **Estado:** borrador; falta la fase del teléfono (T039–T041).

**Rama:** `016-appearance-theme`, creada desde `e9c8006`.
**Fecha:** 2026-11-03.
**Dispositivos:**
- emuladores `MicroRAG_API32` (API 32, creado en esta feature; D-069), `MicroRAG_API33` (API 33) y `Pixel_10` (API 37);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1);
- sin dependencias nuevas (research R10);
- esquemas sin cambios: índice v3 y `chat.db` v1;
- lint avisa de las dos versiones más nuevas de siempre, LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0. No se actualizan en F16;
- iconos regenerados con `lucide-static` 1.61.0, la versión fijada.

## 1. Qué se ha implementado

**Engranaje de Ajustes** (A6, D-067):
- `ic_settings` pasa a ser el Lucide `settings`. Aparece en la barra inferior, en la cabecera de Inicio y en el catálogo, con el mismo recurso y sin cambiar las etiquetas («Ajustes»).
- Nuevo `ic_sun_moon` (Lucide `sun-moon`) para la fila «Tema».
- La regeneración con `tools/icons` solo cambia esos dos recursos; los otros 30 salen idénticos byte a byte.
- Se corrige el comando de regenerar iconos de `specs/001-…/quickstart.md`, que pasaba `icon-nodes.json` en lugar de la carpeta `icons`.

**Tema de la app** (A1–A5, D-067 a D-069):
- **Fila «Tema»** en Preferencias de Ajustes, entre «Idiomas» e «Índice y rendimiento», con el tema guardado como subtítulo.
- **Pantalla Tema**, con la forma de Idiomas:
  - «Sistema» (por defecto, con «Sigue el modo oscuro del teléfono»), «Claro» y «Oscuro»;
  - se aplica al tocar «Guardar preferencias»;
  - TalkBack anuncia «Preferencias guardadas», como en Idiomas (A3 corregida en el plan y confirmada en el analyze);
  - si el guardado falla, la pantalla lo dice y conserva la opción marcada; si la lectura falla, avisa y marca «Sistema».
- **Cómo se aplica** (research R1, R2): con el modo oscuro por app del sistema, `UiModeManager.setApplicationNightMode`, que el sistema guarda y aplica antes de la primera ventana. `LisdataTheme`, `MainActivity` y los temas XML no cambian.
- **Copia propia** en `noBackupFilesDir/datastore/appearance.preferences_pb`, solo para mostrar el valor, porque el sistema no deja leerlo. Un fichero corrupto se sustituye y se marca como lectura fallida.
- **Orden al guardar:** primero la copia y después el sistema. Si el sistema lo rechaza, se restaura la copia anterior.
- **Reconciliación al arrancar** (variante A, elegida en la puerta T015): en segundo plano vuelve a aplicar la copia, o «Sistema» si no se pudo leer (FR-016). Reaplicar el mismo valor no recrea la actividad.
- **Anuncio:** «Preferencias guardadas» espera un margen de 1 s en la composición. Si la actividad se recrea por el tema, el anuncio sale en la pantalla nueva.

**Otros cambios:**
- `SaveFailure`, el aviso de fallo de Idiomas, pasa a `PreferencesSaveFailure`, compartido por las dos pantallas.
- La ruta de depuración `theme` abre Tema directamente (solo en depuración).
- `LayeringTest` prohíbe `android.app.UiModeManager` en `feature/settings`.

**Textos:** 9 textos nuevos en `strings_settings.xml`, en español y en inglés. Se reutilizan los de Idiomas para guardar, guardando, guardado y los dos fallos.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.024 de 1.024 (36 más que en F13) |
| Lint (`lintDebug`) | 0 errores. 2 avisos de versiones más nuevas (LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0) |
| Escritorio (Python) | 181 pruebas OK; F16 no toca el escritorio |
| `Pixel_10` (API 37), instrumentadas | OK, 815 pruebas (40 más que en F13), en 19,7 min |
| `MicroRAG_API33` (API 33), instrumentadas | 815 pruebas, en 20,6 min: los 4 fallos previos de Atrás desde la raíz y ninguno más |
| `MicroRAG_API32` (API 32) | `PlatformAppThemeControllerTest` 3 de 3 (puerta T015) |
| Teléfono A063, instrumentadas | *pendiente (T039)* |

**Los 4 fallos de API 33 no son de F16.** Son los mismos de F12 y F13: `SystemBackTest` y tres casos de `OnboardingNavigationTest` esperan que Atrás desde la raíz cierre la actividad (`DESTROYED`), y en API 33 queda en `CREATED`. En `Pixel_10` pasan.

**Pruebas nuevas o ampliadas:**
- **JVM:**
  - nuevas: `ThemeUseCasesTest`, `DataStoreAppThemeStoreTest`, `ThemeViewModelTest` y `ThemeScreenTest`;
  - ampliadas: `SettingsSavedAnnouncementTest` (el margen), `SettingsScreenTest`, `SettingsViewModelTest`, `NavigationTest`, `DesignSystemConformanceTest` (el engranaje, SC-007) y `LayeringTest`.
- **Instrumentadas:** `PlatformAppThemeControllerTest` (puerta R1/R3), `ThemeChangeKeepsStateTest` y `ThemeScreenAccessibilityTest` (36 combinaciones).

## 3. Validación por criterio

| Criterio | Resultado |
|---|---|
| SC-001: el tema se ve en menos de 1 s tras guardar | ✅ 153–249 ms en `Pixel_10`, 209–236 ms en API 32 y 182–225 ms en API 33 |
| SC-002: sin destello del otro tema en el arranque en frío | ✅ 0 fotogramas del otro tema en 40 arranques grabados (20 en `Pixel_10`, 10 en API 32 y 10 en API 33). En el teléfono, *pendiente (T040)* |
| SC-003: el tema sigue tras reiniciar | ✅ `Pixel_10` reiniciado con «Claro» y el modo del emulador en oscuro. En el teléfono, *pendiente* |
| SC-004: «Sistema» sigue al teléfono en menos de 1 s, y «Claro» u «Oscuro» no | ✅ 297–352 ms en `Pixel_10`, 378 ms en API 32 y 375–529 ms en API 33; con «Claro», el cambio del emulador no cambia la app |
| SC-005: una respuesta y una indexación siguen durante el cambio | *Pendiente (T040, teléfono)* |
| SC-006: un fallo de guardado mantiene el tema | ✅ `ThemeUseCasesTest` y `ThemeViewModelTest` |
| SC-007: ningún icono del Lucide `sun` | ✅ `DesignSystemConformanceTest` |
| SC-008: ATF en claro y en oscuro, al 200 % y en horizontal | ✅ `ThemeScreenAccessibilityTest` y `SettingsScreenAccessibilityTest` en `Pixel_10` |
| SC-009: paridad ES/EN | ✅ `StringsParityTest` |
| SC-010: las capturas coinciden con el wireframe | ✅ en el emulador (§4). En el teléfono, *pendiente* |

## 4. Cifras medidas

**Cómo se midió** (emuladores, nunca el teléfono):
- **Tiempo hasta el tema nuevo:** un script en el emulador muestrea con `screencap` un píxel del margen izquierdo, que es fondo en Ajustes, en Tema y en la pantalla de arranque, desde el toque en «Guardar preferencias» o desde `cmd uimode night`.
- **Arranques en frío:** con `screenrecord`, y cada fotograma de un recorte de ese fondo se clasifica con `ffmpeg` como fondo claro (#F7FBFD), fondo oscuro (#03161F) u otro (el lanzador).

| Emulador | SC-001 (guardar → visible) | SC-004 («Sistema» → modo del emulador) | SC-002 (fotogramas del otro tema) |
|---|---:|---:|---|
| `Pixel_10` (API 37) | 153–249 ms (3) | 297–352 ms (4) | 0 en 10 + 10 |
| `MicroRAG_API32` (API 32) | 209–236 ms (2) | 378 ms (1) | 0 en 5 + 5 |
| `MicroRAG_API33` (API 33) | 182–225 ms (2) | 375–529 ms (2) | 0 en 5 + 5 |

**Puerta T015** (`PlatformAppThemeControllerTest`, 3 de 3 en los tres emuladores):
- `MODE_NIGHT_AUTO` devuelve la app al modo del emulador en los dos sentidos;
- reaplicar `DARK` deja la misma instancia de la actividad, así que se usa la variante A.

**StrictMode en el arranque en frío** (`Pixel_10`): las mismas 3 lecturas de disco de antes en `DataModule` (`AppDirs`, el almacén del onboarding y el de la indexación, F15). Ninguna del almacén del tema, que se resuelve dentro de la corrutina.

### Recorrido en `Pixel_10` (T037)

- **Tema:** en claro, en oscuro, en inglés, al 200 % y en horizontal, igual que el wireframe §3. En horizontal se desplaza hasta el botón.
- **Ajustes:** la fila «Tema» entre «Idiomas» e «Índice y rendimiento», con «Sistema», «Claro» u «Oscuro», en claro, en oscuro, en inglés y al 200 %.
- **Barras del sistema:** iconos claros sobre «Oscuro» con el emulador en claro, y oscuros sobre «Claro» con el emulador en oscuro. Se leen en los dos casos (FR-011).
- **Engranaje:** en la barra inferior, seleccionado y sin seleccionar, en claro y en oscuro.
- **Hoja abierta:** con «Sistema» y la ficha de modelo abierta, `cmd uimode night yes` y después `no` la dejan abierta.
- **Estado final del emulador:** tema de la app en «Sistema», modo claro, letra, giro e idioma de la app como estaban.

## 5. Revisión del inglés (T034)

Los 9 textos nuevos, revisados y sin correcciones: «Theme», «Appearance», «Choose how the app looks», «App theme», «System», «Follows your phone's dark mode», «Light» y «Dark».

## 6. Diferencias frente al PNG 09 y al wireframe

- **PNG 09:**
  - la fila «Tema» en Preferencias, que el PNG no tiene;
  - el engranaje en lugar del sol, por decisión del usuario (D-067, excepción a la precedencia del PNG de D-004).
- **Wireframe:** A3 se corrigió en el plan, porque «Preferencias guardadas» no se ve, solo lo anuncia TalkBack, como en Idiomas (D-068, confirmado en D-069). No hay más diferencias.

## 7. Desviaciones respecto al plan

- **La reconciliación aplica «Sistema» si la lectura falla** (analyze H2), y el anuncio espera un margen en lugar del modo nocturno (H1): `Resources.getSystem()` ya refleja el modo por app (D-069).
- **`ThemeChangeKeepsStateTest` comprueba Ajustes, no el chat** (T030). En el teléfono abriría la conversación del usuario. El borrador del chat vive en el `SavedStateHandle` y está cubierto en la JVM.
- **Ruta de depuración `theme`:** no estaba en las tareas. Sirve para medir en los emuladores sin completar el onboarding.

## 8. Fallos encontrados y corregidos

- Ninguno en la app. Tres ajustes en pruebas propias durante la implementación: un texto de la barra superior fuera del desplazable, «Ajustes» repetido (titular y pestaña) y un `setContent` doble.

## 9. Pendiente

- **Fase del teléfono (T039–T041):** batería, recorrido acordado, arranques con el muestreo de un píxel, respuesta e indexación durante el cambio, y red.

**Visto durante las pruebas, fuera de F16** (no se ha tocado):
- En Bienvenida, con el tema claro, los iconos de la barra de estado salen oscuros sobre su degradado oscuro. Pasaba igual antes de F16 con el teléfono en claro. Queda para F15.
- La barra inferior al 200 % corta «Biblioteca», como ya se anotó en F12.
