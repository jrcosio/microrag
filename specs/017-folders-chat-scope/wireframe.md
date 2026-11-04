# Wireframe: Carpetas y ámbito del chat (F17)

**Estado:** aprobado por el usuario el 2026-11-04, antes de `/speckit-plan` (criterio 1 de F17, constitución VII; D-070). K4 y K11 cambiaron en el clarify.
**Fecha:** 2026-11-04
**Spec:** [spec.md](spec.md) (FR-001 a FR-028; propuestas K1–K14 en «Assumptions»).

Las carpetas y el ámbito no tienen PNG. Todo se compone con piezas del design system `Lisdata*` y sus tokens. Hay tres piezas nuevas:
- un campo de texto para el nombre de la carpeta (K3);
- una casilla de tres estados;
- una fila desplegable (K8).

Los textos van entre «» en español; la versión inglesa está en la spec (K14). Los dibujos son de un teléfono en vertical de unos 390 dp de ancho, sin escala.

## 1. Biblioteca por carpetas (K1)

```
┌─────────────────────────────────────┐
│ ‹  Biblioteca                   ＋  │ ← LisdataTopBar (sin cambios): ＋ importa
├─────────────────────────────────────┤
│ Tus documentos                      │ ← titular de hoy
│ ┌─────────────────────────────────┐ │
│ │ 🔍 Buscar documentos…           │ │ ← LisdataSearchField: busca en todas las carpetas
│ └─────────────────────────────────┘ │
│ CARPETAS                            │ ← LisdataSectionHeader
│ ┌─────────────────────────────────┐ │
│ │ ▢ Legal                       › │ │ ← fila de carpeta (LisdataSettingsRow)
│ │   12 documentos                 │ │   icono ic_folder, nombre, recuento, chevron
│ ├─────────────────────────────────┤ │
│ │ ▢ Motores                     › │ │
│ │   5 documentos                  │ │
│ ├─────────────────────────────────┤ │
│ │ ▢ Sin carpeta                 › │ │ ← solo si tiene documentos; siempre la última
│ │   3 documentos                  │ │
│ ├─────────────────────────────────┤ │
│ │ ＋ Nueva carpeta                 │ │ ← abre el diálogo K3
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
   (barra inferior: Biblioteca seleccionada)
```

**Con texto en la búsqueda:** en lugar de las carpetas, los documentos encontrados en todas, con la fila de documento de hoy. La línea de datos añade la carpeta, por ejemplo «PDF · 24 páginas · Legal».

**Biblioteca vacía:** el estado vacío de hoy, con «Importar», y debajo la fila «Nueva carpeta».

## 2. Dentro de una carpeta (K2)

```
┌─────────────────────────────────────┐
│ ‹  Legal                    ＋  ⋯   │ ← ＋ importa a «Legal»; ⋯: Renombrar carpeta,
├─────────────────────────────────────┤      Eliminar carpeta (no en «Sin carpeta»)
│ ┌─────────────────────────────────┐ │
│ │ 🔍 Buscar en Legal…             │ │ ← busca solo en la carpeta
│ └─────────────────────────────────┘ │
│ [Todos] [Indexados] [Pendientes]    │ ← filtros de hoy (G16)
│ DOCUMENTOS            Incluir en RAG│
│ ┌─────────────────────────────────┐ │
│ │ 📄 Contrato_arrendamiento.pdf ◉ │ │ ← LisdataDocumentRow de hoy, con su interruptor
│ │    PDF · 12 páginas · Indexado  │ │
│ └─────────────────────────────────┘ │
│              ⋮                      │ ← tandas de 50, como hoy
└─────────────────────────────────────┘
```

- **Sin barra inferior.** Es una pantalla encima de la pestaña Biblioteca, y Atrás vuelve a las carpetas.
- **Estados:** carpeta vacía con «Esta carpeta está vacía» y «Importar a Legal»; sin resultados, carga y error, como hoy.

## 3. Crear, renombrar y eliminar (K3, K4)

```
┌───────────────────────────────┐        ┌────────────────────────────────────┐
│ Nueva carpeta                 │        │ ¿Eliminar la carpeta «Legal»?      │
│ ┌───────────────────────────┐ │        │ ◉ Mover sus 12 documentos a        │
│ │ Legal                     │ │ ← campo│   «Sin carpeta»                    │ ← por defecto
│ └───────────────────────────┘ │  nuevo │ ○ Eliminar también sus 12          │
│ Ya hay una carpeta con ese    │ ← aviso│   documentos                       │
│ nombre                        │        │          [Cancelar]  [Eliminar]    │
│       [Cancelar]  [Crear]     │        └────────────────────────────────────┘
└───────────────────────────────┘          LisdataConfirmationDialog + LisdataRadioRow
  LisdataConfirmationDialog con contenido

Si se eligió borrar los documentos, un segundo aviso (constitución V):
┌────────────────────────────────────────────┐
│ ¿Eliminar 12 documentos?                   │
│ Se eliminarán de este teléfono, con su     │
│ texto y su índice. No se puede deshacer.   │
│         [Cancelar]  [Eliminar documentos]  │ ← «Eliminar documentos» en el color de error
└────────────────────────────────────────────┘
```

- **Mientras borra,** el botón dice «Eliminando…». Al terminar, el aviso inferior «Carpeta eliminada». Si falla a medias, «No se pudieron eliminar algunos documentos», y la carpeta queda con ellos.

- **«Renombrar carpeta»:** el mismo diálogo con el nombre actual y «Guardar».
- **Avisos del campo:** «Escribe un nombre», «Ya hay una carpeta con ese nombre», «Ese nombre está reservado» y «Máximo 40 caracteres». TalkBack los lee.
- **El foco** empieza en el campo, y «Crear» o «Guardar» se desactivan mientras el nombre no es válido.

## 4. Mover a carpeta (K5)

```
Detalle del documento                         Hoja «Mover a carpeta»
┌─────────────────────────────────────┐      ┌─────────────────────────────────────┐
│ ‹  Detalle                      ⋯   │      │ Mover a carpeta                     │
│ …                                   │      │ ○ Legal                             │
│ Carpeta            Legal            │ ←    │ ◉ Motores     ← la actual, marcada  │
│ …                                   │ dato │ ○ Sin carpeta                       │
└─────────────────────────────────────┘ nuevo│ ＋ Nueva carpeta…                    │
  ⋯ → «Quitar del índice», «Mover a carpeta»  │        [Cancelar]  [Mover]          │
                                             └─────────────────────────────────────┘
                                               LisdataBottomSheet + LisdataRadioRow
```

- **Al mover:** se cierra la hoja y aparece el aviso inferior «Movido a «Motores»».
- **«Nueva carpeta…»** abre K3, y el documento pasa a la carpeta creada.

## 5. Destino al importar (K6)

```
┌─────────────────────────────────────┐
│ ‹  Importar documentos              │
├─────────────────────────────────────┤
│ …titular de hoy…                    │
│ ┌─────────────────────────────────┐ │
│ │ ▢ Carpeta de destino          › │ │ ← fila nueva (LisdataSettingsRow): abre la hoja de K5
│ │   Sin carpeta                   │ │   por defecto, la carpeta desde la que se abrió
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │  Selecciona archivos            │ │ ← LisdataFilePickerCard (sin cambios)
│ └─────────────────────────────────┘ │
│ COLA                                │
│ │ 📄 informe.pdf · 2 MB · Legal   › │ ← cada fila dice su carpeta
└─────────────────────────────────────┘
```

## 6. Chat: ficha y selector del ámbito (K7, K8)

```
┌─────────────────────────────────────┐
│ ‹  Chat con documentos          ⋯   │
│ …conversación…                      │
│ ┌──────────────────────┐            │
│ │ ▢ Buscar en: Legal ▾ │            │ ← ficha del ámbito (K7), encima del compositor
│ └──────────────────────┘            │
│ ┌─────────────────────────────────┐ │
│ │ Pregunta a tus documentos…    ➤ │ │ ← LisdataChatComposer (sin cambios)
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Resumen de la ficha:**
- «Toda la biblioteca»;
- «Legal»;
- «Legal y Motores»;
- «Legal + 2 documentos»;
- «3 documentos».

Si no cabe, se corta con «…».

```
Hoja «Buscar en» (K8)
┌─────────────────────────────────────┐
│ Buscar en                           │
│ ○ Toda la biblioteca                │ ← opción de radio
│ CARPETAS                            │
│ ☑ Legal (12)                    ⌄   │ ← casilla de tres estados + desplegar (piezas nuevas)
│ ◪ Motores (5)                   ⌃   │ ← «algunos documentos»
│    ☑ Manual_motor_V8.pdf            │
│    ☐ Revisión_2024.pdf              │
│    ☐ Ficha técnica.pdf  Pendiente   │ ← pendiente de indexar: se puede marcar
│    ☐ Notas.txt  No incluido en RAG  │ ← excluido: en gris, sin poder marcarse
│ ☐ Sin carpeta (3)               ⌄   │
│        [Cancelar]  [Aplicar]        │ ← Aplicar desactivado si no hay nada marcado
└─────────────────────────────────────┘
```

- **Marcar «Toda la biblioteca»** desmarca todo lo demás, y marcar cualquier carpeta o documento desmarca «Toda la biblioteca».
- **Una carpeta marcada entera** incluye también los documentos que lleguen después a ella (FR-016).
- **«Nuevo chat»** abre con el último ámbito usado ya en la ficha (K11, cambiado en el clarify). La primera vez, «Toda la biblioteca».
- **Las carpetas vacías no aparecen** en el selector.

## 7. Estados y avisos del chat (K9, K10)

```
┌─────────────────────────────────────┐
│ ‹  Chat con documentos          ⋯   │
│                                     │
│              ▢                      │
│   En este ámbito no hay documentos  │ ← LisdataEmptyState (K9)
│   listos para buscar                │
│   [ Cambiar ámbito ]                │
│                                     │
│ ┌──────────────────────┐            │
│ │ ▢ Buscar en: Legal ▾ │            │
│ └──────────────────────┘            │
│ ┌─────────────────────────────────┐ │
│ │ Pregunta a tus documentos…    ➤ │ │ ← desactivado hasta cambiar el ámbito
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

- **K10, avisos inferiores** al abrir una conversación cuyo ámbito cambió:
  - «Una carpeta del ámbito ya no existe»;
  - «Un documento del ámbito ya no existe»;
  - «El ámbito vuelve a ser toda la biblioteca».
- **Cobertura parcial:** el aviso de hoy, calculado solo con los documentos del ámbito.

## 8. Accesibilidad (K13)

- **48 dp** en filas, casillas, botones de desplegar y la ficha.
- **TalkBack:**
  - carpeta: «Legal, 12 documentos», con la acción «Abrir carpeta»;
  - ficha: «Buscar en: Legal», con la acción «Cambiar ámbito»;
  - casillas: «Motores, 5 documentos, algunos documentos marcados»;
  - documento excluido: «Notas.txt, no incluido en RAG, no se puede marcar».
- **Letra al 200 % y horizontal:** las filas crecen en alto, la ficha se corta con «…» y la hoja se desplaza.
- **Modo oscuro** con los tokens de siempre.
