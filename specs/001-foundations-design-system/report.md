# Informe de cierre — F01 Fundaciones y design system

**Fecha:** 2026-10-25
**Rama:** `001-foundations-design-system`
**Estado:** implementada; pendiente de la revisión del usuario.
**Abierta:** T059, el recorrido manual con TalkBack.

## 1. Qué se ha implementado

**Toolchain y configuración**
- AGP 9.3.3 (D-021), Kotlin 2.4.21 y Gradle 9.8.2 (D-020).
- compileSdk 37.1, targetSdk 36 y **minSdk 31** (D-017). Java 17.
- ABI `arm64-v8a`; `x86_64` solo en debug.
- `allowBackup=false` y reglas de extracción que excluyen todos los dominios, también en la transferencia entre dispositivos.
- Ningún permiso de red, normal ni peligroso. Solo el permiso interno de firma de AndroidX.

**Estructura**
- Un único módulo con paquetes `core.designsystem`, `core.navigation`, `core.di`, `core.dispatchers`, `core.logging`, `domain`, `data` y `feature` (D-009).
- Koin con un módulo por capa y verificación del grafo.
- `AppDispatchers` con hilos dedicados `microrag-db`, `microrag-llm` y `microrag-embed`.
- `AppLogger`, que solo registra en debug.

**Design system LISDATA**
- **Tokens:** colores claro y oscuro (D-015, D-016, D-019), degradados, Google Sans variable, espaciado y formas.
- **Iconos:** 30 de Lucide (ISC), generados con `tools/icons/lucide_to_vector.py`.
- **Marca:** logo oficial.
- **Componentes:** ~40 `Lisdata*` con previews en claro, oscuro, fuente al 200 %, texto largo y error. El catálogo tiene 39 entradas.

**Navegación**
- 10 rutas tipadas.
- Barra inferior solo en las raíces de pestaña, oculta con el teclado.
- Una sola instancia de cada pestaña; Atrás vuelve a Inicio.
- Marcadores «Pendiente · Fxx» en lugar de las pantallas de producto.

**Herramientas de debug**
- Lanzador «MicroRAG Debug» con el catálogo de componentes (tema y escala de fuente conmutables) y el menú de rutas.
- No existen en la build de release (verificado).

## 2. Build, lint y tests

| Comprobación | Resultado |
|---|---|
| `./gradlew clean assembleDebug assembleRelease` | ✅ |
| `./gradlew lintDebug` | ✅ 0 incidencias. Desactivados con justificación: `ChromeOsAbiSupport` (solo ARM64) y `AndroidGradlePluginVersion` (D-021) |
| `./gradlew testDebugUnitTest` | ✅ **23/23**: contraste (4), conformidad del design system (4), Koin (1), dispatchers (2), logger (2), navegación (8) y restauración (2) |
| `./gradlew connectedDebugAndroidTest` (emulador `Pixel_10`, API 37) | ✅ **83/83**: accesibilidad del catálogo con ATF (78 = 39 entradas × claro y oscuro), invariantes del manifiesto (4) y Atrás real del sistema (1) |

## 3. Diferencias frente al mockup

Están detalladas en [checklists/mockup-differences.md](checklists/mockup-differences.md). Las principales:
- **Colores de texto:** oscurecidos por contraste AA, igual que el inicio del degradado de las CTA (D-015, D-019).
- **Controles estándar M3:** interruptor, chips de filtro y barra inferior tienen otros tamaños (D-018). El interruptor apagado sigue el estilo M3 por contraste no textual.
- **Marca:**
  - etiqueta del asistente «MICRORAG» (D-014);
  - logo oficial en lugar de la aproximación del PNG;
  - icono del lanzador genérico, pendiente del asset (G43).
- **Pantallas:** de momento son marcadores; cada feature sustituye las suyas.

## 4. Cifras medidas

| Métrica | Valor | Objetivo |
|---|---|---|
| Arranque en frío (release sin R8, emulador `Pixel_10`, mediana de 5) | **181 ms** | < 1000 ms (SC-009) ✅ |
| Contraste mínimo de texto en tokens (claro y oscuro) | ≥ 4,5:1 en texto normal; texto blanco sobre la CTA ≥ 3,23:1 | WCAG AA ✅ |
| Tamaño del APK de release sin R8 | 28,9 MB (4,8 MB son la fuente) | — (se revisa en F15) |

Detalle en [checklists/release-verification.md](checklists/release-verification.md).

## 5. Hallazgos durante la implementación

- **D-020:** AGP exigía Gradle ≥ 9.6 y AndroidX exige compileSdk 37.
- **D-021:** el Android Studio del equipo solo admite AGP 9.3.x. Además, renombrar el proyecto rompía la sincronización del IDE, así que se mantiene «MIcroRAG».
- **Robolectric con SDK 36:** necesita `--add-exports` en JDK 21 y Espresso 3.7.0 fijado (Compose arrastra la 3.5.0).
- **Navigation 2.10:** el Atrás del sistema llega por `NavigationEvent`, que Robolectric no despacha. Se cubre con un test instrumentado.
- **Fuente al 200 %:** el anillo de progreso partía su etiqueta; se corrigió. Quedan reglas para las features de pantalla (ver [checklists/accessibility-review.md](checklists/accessibility-review.md)):
  - filas de chips con `FlowRow`;
  - rejillas a una columna con fuente grande.
- **Nuevos elementos de diseño:** `ic_close` (Lucide `x`) para borrar la búsqueda e `IconTileStyle.Error` para las etapas fallidas.

## 6. Pendiente

- **T059:** recorrido manual con TalkBack. El árbol semántico está verificado; falta escuchar la salida de voz en un dispositivo.
- **G20:** el SVG oficial del logo. G43 (icono del lanzador) quedó resuelto tras el cierre con el icono aportado por el usuario.
- **SC-009 en el teléfono de referencia:** se mide en F02.
