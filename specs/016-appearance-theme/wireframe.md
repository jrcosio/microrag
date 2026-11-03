# Wireframe: Apariencia: tema e icono de Ajustes (F16)

**Estado:** aprobado por el usuario el 2026-11-03, antes de `/speckit-plan` (criterio 1 de F16, constitución VII; D-067).
**Fecha:** 2026-11-03
**Spec:** [spec.md](spec.md) (FR-001 a FR-021; propuestas A1–A9 en «Assumptions»).

El selector de tema no tiene PNG. La fila nueva de Ajustes y la pantalla Tema se componen solo con piezas del design system `Lisdata*` y sus tokens, como Idiomas (F12). No hay piezas nuevas: solo dos iconos Lucide nuevos, `settings` (engranaje) y `sun-moon`. Los textos van entre «» en español; la versión inglesa está en §6. Los dibujos son de un teléfono en vertical de unos 390 dp de ancho, sin escala.

## 1. El engranaje (A6)

**Barra inferior:**

```
┌─────────────────────────────────────┐
│   ⌂        ▭        ▢        ⚙     │ ← LisdataBottomBar
│ Inicio    Chat  Biblioteca  Ajustes │   pestaña Ajustes: engranaje (Lucide `settings`)
└─────────────────────────────────────┘   en lugar del sol (G19)
```

**Cabecera de Inicio:**

```
┌─────────────────────────────────────┐
│ lisdata ›                       ⚙   │ ← botón de Ajustes de la cabecera: el mismo engranaje
│                                     │   TalkBack: «Ajustes» (sin cambios)
```

- El engranaje sustituye al sol en todos sus usos (FR-001), con el mismo tamaño, trazo y colores de selección que los demás iconos.
- El catálogo de depuración muestra el engranaje en su rejilla de iconos.

## 2. Ajustes: la fila «Tema» (A1)

```
┌─────────────────────────────────────┐
│ ‹  Configuración                    │ ← LisdataTopBar (sin cambios)
├─────────────────────────────────────┤
│ Ajustes                             │
│ Personaliza tu experiencia local.   │
│                                     │
│ INTELIGENCIA ARTIFICIAL             │
│ ┌─────────────────────────────────┐ │
│ │ ✦ Modelo de respuestas   [Listo]›│ │ (sin cambios)
│ │ ▤ Modelo de embeddings   [Listo]›│ │
│ └─────────────────────────────────┘ │
│ PREFERENCIAS                        │ ← LisdataSectionHeader
│ ┌─────────────────────────────────┐ │
│ │ 🌐 Idiomas                     › │ │
│ │    Español                      │ │
│ ├─────────────────────────────────┤ │
│ │ ◐ Tema                         › │ │ ← fila nueva: LisdataSettingsRow
│ │    Sistema                      │ │   icono Lucide `sun-moon`, subtítulo = tema guardado
│ ├─────────────────────────────────┤ │
│ │ ⛁ Índice y rendimiento         › │ │
│ │    Almacenamiento y procesamiento│ │
│ └─────────────────────────────────┘ │
│ PRIVACIDAD Y DATOS                  │ (sin cambios)
│ …                                   │
└─────────────────────────────────────┘
```

| Zona | Componente | Contenido |
|---|---|---|
| Fila «Tema» | `LisdataSettingsRow(icon = SunMoon, title, subtitle, trailing = Chevron)`, entre `LisdataDivider` | Título «Tema» y subtítulo «Sistema», «Claro» u «Oscuro» según el guardado. Abre la pantalla Tema |
| TalkBack | La semántica de la fila | «Tema, Sistema», con la acción de abrir |

- La fila va entre «Idiomas» e «Índice y rendimiento». Es la única diferencia frente al PNG 09 y se reporta al cierre (FR-021).

## 3. Pantalla Tema (A2, A3)

```
┌─────────────────────────────────────┐
│ ‹  Tema                             │ ← LisdataTopBar con onBack
├─────────────────────────────────────┤
│ APARIENCIA                          │ ← LisdataEyebrow
│ Elige cómo se ve la app             │ ← titular (headlineLarge, encabezado)
│                                     │
│ TEMA DE LA APLICACIÓN               │ ← LisdataSectionHeader
│ ┌─────────────────────────────────┐ │
│ │ ◉ Sistema                       │ │ ← LisdataRadioRow, dentro de un
│ │   Sigue el modo oscuro del      │ │   LisdataCardGroup(selectableGroup)
│ │   teléfono                      │ │
│ ├─────────────────────────────────┤ │
│ │ ○ Claro                         │ │
│ ├─────────────────────────────────┤ │
│ │ ○ Oscuro                        │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │     Guardar preferencias   ✓    │ │ ← LisdataGradientButton, como en Idiomas
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
   (sin barra inferior)
```

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar` con `onBack` | Título «Tema». Atrás sin guardar no cambia nada |
| Cabecera | `LisdataEyebrow` y `Text(headlineLarge)` con `heading()` | «APARIENCIA» y «Elige cómo se ve la app» |
| Opciones | `LisdataSectionHeader` + `LisdataCardGroup(Modifier.selectableGroup())` con tres `LisdataRadioRow` separadas por `LisdataDivider` | «Sistema» (subtítulo «Sigue el modo oscuro del teléfono»), «Claro», «Oscuro». Al abrir, la guardada va marcada |
| Botón | `LisdataGradientButton(trailingIcon = Check)` | «Guardar preferencias»; mientras guarda, «Guardando…», que TalkBack lee una vez |

- **Sin tarjeta hero.** Idiomas la tiene porque está en su PNG; aquí no aporta información.
- **Marcar no cambia nada** hasta guardar. No hay previsualización (A3).
- **Al guardar:** la app pasa al tema elegido, la pantalla se cierra y Ajustes muestra el aviso inferior «Preferencias guardadas». Guardar lo mismo que ya había cierra sin cambiar nada.

**Navegación:**

```
Ajustes ──toca «Tema»──▶ Tema ──Guardar preferencias──▶ Ajustes (tema aplicado, «Preferencias guardadas»)
   ▲                      │
   └───────Atrás──────────┘ (sin cambios)
```

## 4. Estados (A4, A5)

**Guardando:**

```
│ ┌─────────────────────────────────┐ │
│ │        Guardando…               │ │ ← botón deshabilitado mientras guarda
│ └─────────────────────────────────┘ │
```

**Fallo al guardar** (FR-015):

```
│ │ ○ Sistema                       │ │
│ │ ◉ Oscuro   ← sigue marcada      │ │
│ ┌─────────────────────────────────┐ │
│ │     Guardar preferencias   ✓    │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ No se pudieron guardar las      │ │ ← aviso inferior (snackbar), como en Idiomas
│ │ preferencias. Se mantienen las  │ │   TalkBack lo lee una vez
│ │ anteriores.                     │ │
│ └─────────────────────────────────┘ │
```

- La pantalla sigue abierta y el tema no cambia. Se puede volver a intentar.

**Fallo al leer** (FR-016):

```
│ │ ◉ Sistema                       │ │
│ │ ○ Claro                         │ │
│ │ ○ Oscuro                        │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ ⚠ No se pudieron leer tus       │ │ ← LisdataInfoCard(icon = Warning), la de Idiomas
│ │   preferencias. Se usan las de  │ │
│ │   por defecto.                  │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │     Guardar preferencias   ✓    │ │
│ └─────────────────────────────────┘ │
```

## 5. Accesibilidad y alcance (A7, A8)

- Objetivos de 48 dp en la fila, en cada opción y en el botón.
- TalkBack:
  - la fila de Ajustes: «Tema, Sistema», con la acción de abrir;
  - cada opción: «Oscuro, seleccionado» u «Oscuro, no seleccionado»; «Sistema» añade su subtítulo;
  - el titular se lee como encabezado.
- **En horizontal:** la misma columna, desplazable, con el botón al final.
- **Con la letra al 200 %:** los subtítulos bajan de línea sin cortarse; el botón crece en alto.
- **Alcance:** el tema se ve en todas las pantallas, diálogos, hojas, menús y avisos, en la pantalla de arranque y en las barras del sistema, con los tokens de siempre y sin colores nuevos.

## 6. Textos nuevos (A9)

| Español | Inglés |
|---|---|
| Tema | Theme |
| Sistema | System |
| Claro | Light |
| Oscuro | Dark |
| Sigue el modo oscuro del teléfono | Follows your phone's dark mode |
| Apariencia | Appearance |
| Elige cómo se ve la app | Choose how the app looks |
| Tema de la aplicación | App theme |

Se reutilizan los de Idiomas: «Guardar preferencias», «Guardando…», «Preferencias guardadas», «No se pudieron guardar las preferencias. Se mantienen las anteriores.» y «No se pudieron leer tus preferencias. Se usan las de por defecto.».
