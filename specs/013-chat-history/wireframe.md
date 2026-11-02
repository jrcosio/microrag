# Wireframe: Historial de chats (F13)

**Estado:** propuesta, pendiente de la aprobación del usuario antes de `/speckit-plan` (criterio 1 de F13, constitución VII).
**Fecha:** 2026-11-02
**Spec:** [spec.md](spec.md) (FR-001 a FR-026; propuestas H1–H11 en «Assumptions»).

F13 no tiene PNG. Esta pantalla se compone solo con piezas del design system `Lisdata*` y sus tokens. La única pieza nueva es la fila de conversación (H3), hecha con `LisdataCard` como las demás filas de tarjeta. Los textos van entre «» en español; la versión inglesa se traduce igual. Los dibujos son de un teléfono en vertical de unos 390 dp de ancho, sin escala.

## 1. Acceso: menú «⋯» del chat (H1)

```
┌─────────────────────────────────────┐
│ ‹  Chat con documentos          ⋯  │ ← LisdataTopBar + LisdataOverflowMenu
│    ● Gemma 4 · Procesamiento local  │   ┌───────────────────────┐
│                                     │   │ Nuevo chat            │
│                                     │   │ Historial             │ ← nuevo (G13)
│                                     │   │ Borrar conversación   │ ← solo con una
│                                     │   └───────────────────────┘   conversación abierta
```

- El orden es el de G13: «Nuevo chat», «Historial», «Borrar conversación».
- «Historial» aparece siempre, también con el chat vacío o sin conversaciones guardadas (FR-001).

**Navegación:**

```
Chat ──⋯ Historial──▶ Historial ──toca una fila──▶ Chat con esa conversación
  ▲                     │  │
  └──────Atrás──────────┘  └──＋ Nuevo chat──▶ Chat vacío
```

- Historial es una pantalla encima del chat, sin barra inferior (FR-002).
- Al abrir una conversación o «Nuevo chat», Historial se cierra y queda la pestaña Chat. Atrás desde el chat va a Inicio, como siempre (FR-003).

## 2. Contenido (H2, H3)

```
┌─────────────────────────────────────┐
│ ‹  Historial                    ＋  │ ← LisdataTopBar: Atrás y la acción «Nuevo chat»
├─────────────────────────────────────┤
│ HOY                                 │ ← LisdataSectionHeader
│ ┌─────────────────────────────────┐ │
│ │ ¿Cuál es el plazo de        🗑  │ │ ← fila de conversación (H3)
│ │ devolución?                     │ │   título: primera pregunta, 2 líneas como mucho
│ │ 10:42 · 3 preguntas  [Abierta]  │ │   datos · chip «Abierta» si es la del chat
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Resume las condiciones de   🗑  │ │
│ │ garantía del manual             │ │
│ │ 08:15 · 1 pregunta              │ │
│ └─────────────────────────────────┘ │
│ AYER                                │
│ ┌─────────────────────────────────┐ │
│ │ ¿Qué documentos necesito    🗑  │ │
│ │ para la solicitud?              │ │
│ │ 18:05 · 4 preguntas             │ │
│ └─────────────────────────────────┘ │
│ ÚLTIMOS 7 DÍAS                      │
│ ┌─────────────────────────────────┐ │
│ │ Diferencias entre la versión 🗑 │ │
│ │ 2 y la 3 del procedimiento…     │ │ ← cortado con «…»
│ │ lunes · 2 preguntas             │ │
│ └─────────────────────────────────┘ │
│ OCTUBRE DE 2026                     │
│ ┌─────────────────────────────────┐ │
│ │ ¿Cuántos días de vacaciones 🗑  │ │
│ │ corresponden?                   │ │
│ │ 12 oct. · 5 preguntas           │ │
│ └─────────────────────────────────┘ │
│              ⋮                      │ ← la siguiente tanda se carga al acercarse al final
└─────────────────────────────────────┘
   (sin barra inferior)
```

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar` con `onBack` y una acción `IconButton` + `LisdataIcon(Plus)`, como el «＋» de la Biblioteca | Título «Historial». La acción se lee como «Nuevo chat» |
| Grupo | `LisdataSectionHeader` | «HOY», «AYER», «ÚLTIMOS 7 DÍAS», «OCTUBRE DE 2026»… (FR-004). Los grupos vacíos no se muestran |
| Fila | Fila de conversación nueva sobre `LisdataCard` (H3) | Título, línea de datos, chip «Abierta» opcional y papelera |
| Final de la lista | Carga por tandas, como `LoadMoreNearTheEnd` de la Biblioteca | Sin botón «Cargar más» |

**La fila de conversación (H3):**
- **Título:** la primera pregunta, con los espacios y saltos de línea normalizados, en el estilo del nombre de las filas de documento, en 2 líneas como mucho y cortada con «…».
- **Línea de datos**, en `textSecondary`:
  - la fecha de la última actividad, según su grupo:
    - en «Hoy» y «Ayer», la hora («10:42»);
    - en «Últimos 7 días», el día de la semana («lunes»);
    - en los grupos de mes, el día y el mes («12 oct.»), en el mismo formato que la fecha de importación del detalle;
  - « · » y el número de preguntas («1 pregunta», «3 preguntas»).
- **Chip «Abierta»** (`LisdataStatusChip`, informativo): solo en la conversación abierta en la pestaña Chat.
- **Papelera** (`IconButton` + `LisdataIcon(Trash)`, 48 dp): a la derecha, alineada con la primera línea del título.
  - Va en `textSecondary`, no en rojo: una columna de iconos rojos llamaría la atención en cada fila. El rojo de acción destructiva queda para el botón «Borrar» del diálogo, como en F11.
  - Sin chevron: el lado derecho lo ocupa la papelera.
- **Al tocar:** el resto de la tarjeta abre la conversación.

**TalkBack:**
- La fila se lee como una frase: «¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas. Abierta.», con la acción «Abrir conversación».
- La papelera es un elemento aparte: «Borrar conversación: ¿Cuál es el plazo de devolución?».
- Las cabeceras de grupo se leen como encabezados.

## 3. Vacío (H4)

```
┌─────────────────────────────────────┐
│ ‹  Historial                    ＋  │
├─────────────────────────────────────┤
│                                     │
│               ( 💬 )                │ ← LisdataEmptyState(icono Chat)
│                                     │
│       Aún no hay conversaciones     │
│                                     │
│   Las conversaciones que tengas en  │
│   el chat aparecerán aquí.          │
│                                     │
│      ┌───────────────────────┐      │
│      │      Nuevo chat       │      │ ← acción del estado vacío
│      └───────────────────────┘      │
│                                     │
└─────────────────────────────────────┘
```

## 4. Cargando y error (H5)

```
Cargando                                 Error
┌─────────────────────────────────────┐  ┌─────────────────────────────────────┐
│ ‹  Historial                    ＋  │  │ ‹  Historial                    ＋  │
├─────────────────────────────────────┤  ├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │  │                                     │
│ │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓             │ │  │   No se pueden abrir tus            │ ← LisdataErrorState
│ │ ▓▓▓▓▓▓▓▓                        │ │  │   conversaciones guardadas.         │   (texto de C17)
│ └─────────────────────────────────┘ │  │                                     │
│   × 4 (LisdataSkeletonRow)          │  │          [ Reintentar ]             │
│                                     │  │                                     │
└─────────────────────────────────────┘  └─────────────────────────────────────┘
```

- Mientras carga, 4 filas esqueleto (`LisdataSkeletonRow`), que TalkBack lee como «Cargando».
- Si no se pueden leer las conversaciones, `LisdataErrorState` con el texto de C17 y «Reintentar», que vuelve a leer. No se borra nada, y la lista no ofrece abrir ni borrar.

## 5. Borrar (H6)

```
┌─────────────────────────────────────┐
│ ┌─────────────────────────────────┐ │
│ │ ¿Borrar esta conversación?      │ │ ← LisdataConfirmationDialog (C15 de F11)
│ │                                 │ │
│ │ Se eliminarán sus preguntas y   │ │
│ │ respuestas de este teléfono.    │ │
│ │ La respuesta en curso se        │ │ ← solo si esa conversación se está respondiendo
│ │ detendrá.                       │ │
│ │                                 │ │
│ │          [Cancelar]  [Borrar]   │ │ ← foco en «Cancelar»; «Borrar» en rojo
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

Tras borrar:                            Si falla:
┌─────────────────────────────────────┐ ┌─────────────────────────────────────┐
│ ...la fila desaparece...            │ │ ...la fila sigue...                 │
│ ┌─────────────────────────────────┐ │ │ ┌─────────────────────────────────┐ │
│ │ Conversación borrada            │ │ │ │ No se pudo borrar la            │ │
│ └─────────────────────────────────┘ │ │ │ conversación.                   │ │
└─────────────────────────────────────┘ │ └─────────────────────────────────┘ │
   aviso inferior (snackbar)            └─────────────────────────────────────┘
```

- Los textos del diálogo son los de F11 (C15), con la línea de F12 (S6) si esa conversación tiene una respuesta en curso.
- Tras borrar, el aviso «Conversación borrada» va en la parte inferior, como «Documento eliminado» en la Biblioteca, y TalkBack lo anuncia.
- Si era la abierta en la pestaña Chat, el chat queda vacío (FR-018).
- Si era la última, la lista pasa al estado vacío (§3).
- No hay «Deshacer».

## 6. Horizontal y letra al 200 % (H10)

```
Horizontal (unos 840 × 390 dp)
┌──────────────────────────────────────────────────────────────────────────┐
│ ‹  Historial                                                         ＋  │
├──────────────────────────────────────────────────────────────────────────┤
│ HOY                                                                      │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ ¿Cuál es el plazo de devolución?                                  🗑  │ │
│ │ 10:42 · 3 preguntas  [Abierta]                                       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ...                                                                      │
└──────────────────────────────────────────────────────────────────────────┘
```

- **En horizontal:** la misma lista a todo el ancho, y el título cabe a menudo en una línea. El estado vacío deja fuera el icono (`compact`), como en las demás pantallas.
- **Con la letra al 200 %:** el título sigue en 2 líneas como mucho y las filas crecen en alto. La línea de datos puede partirse en dos, y el chip «Abierta» baja bajo la fecha si no cabe a su lado, como en las filas de Ajustes. La papelera mantiene sus 48 dp.
- **En modo oscuro:** los mismos tokens que el resto de la app (tarjetas, `textSecondary`, chip), sin colores propios.
