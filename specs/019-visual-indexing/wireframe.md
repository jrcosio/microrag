# Wireframe: Indexación visual, modo «Multimodal» opcional (F19)

**Estado:** aprobado por el usuario el 2026-11-05, antes de `/speckit-plan` (criterio 1 de F19, constitución VII, G47).
**Fecha:** 2026-11-05
**Spec:** [spec.md](spec.md) (FR-001 a FR-027).

El modo multimodal y el modo de indexación no tienen PNG. Todo se compone con piezas del design system `Lisdata*` y sus tokens, **sin piezas nuevas**:
- `LisdataSwitchRow`, `LisdataSettingsRow`, `LisdataRadioRow` y `LisdataCardGroup`;
- `LisdataConfirmationDialog` con contenido;
- `LisdataBottomSheet`, `LisdataStatusChip`, `LisdataLinearProgress` y `LisdataFactRow`.

Los textos van entre «» en español; la versión inglesa está al final. Los dibujos son de un teléfono en vertical de unos 390 dp de ancho, sin escala.

## 1. Ajustes, sección «Inteligencia artificial» (FR-001, FR-022, FR-023)

**Modo apagado**, como hoy más el interruptor:

```
┌─────────────────────────────────────┐
│ INTELIGENCIA ARTIFICIAL             │ ← LisdataSectionHeader (como hoy)
│ ┌─────────────────────────────────┐ │
│ │ Gemma 4 E2B            ● Listo › │ │ ← filas de modelo de hoy
│ ├─────────────────────────────────┤ │
│ │ EmbeddingGemma 2 270M  ● Listo › │ │
│ ├─────────────────────────────────┤ │
│ │ Modo multimodal            [  ] │ │ ← LisdataSwitchRow, apagado
│ │ Lee también páginas e imágenes. │ │   subtítulo
│ │ Respuestas más lentas.          │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Modo activo:**

```
│ ┌─────────────────────────────────┐ │
│ │ Gemma 4 E2B            ● Listo › │ │
│ ├─────────────────────────────────┤ │
│ │ EmbeddingGemma 2 440M  ● Listo › │ │ ← sustituye a la 270M (FR-023)
│ ├─────────────────────────────────┤ │
│ │ Modo multimodal            [■■] │ │ ← encendido
│ │ Lee también páginas e imágenes. │ │
│ ├─────────────────────────────────┤ │
│ │ Modo al importar PDF            │ │ ← LisdataSettingsRow + chevron
│ │ Ambos (texto y páginas)       › │ │   abre la pantalla §4
│ └─────────────────────────────────┘ │
```

## 2. Activar: aviso y progreso (FR-002 a FR-006)

**Aviso** (`LisdataConfirmationDialog`, no destructivo):

```
┌───────────────────────────────────┐
│ ¿Activar el modo multimodal?      │
│                                   │
│ La app leerá también las páginas  │
│ de tus PDF y tus imágenes.        │
│                                   │
│ • Descarga un modelo de 369 MB.   │
│ • Cada página tarda unos 3 s en   │
│   indexarse y ocupa unos 100 KB.  │
│ • Las respuestas que miran        │
│   páginas tardan 10–15 s.         │
│ • Tus documentos de texto siguen  │
│   funcionando igual.              │
│                                   │
│        [Cancelar]   [Activar]     │
└───────────────────────────────────┘
```

**Mientras se activa,** la fila del interruptor pasa a mostrar el estado, y el interruptor queda encendido pero sin poder tocarse:

```
│ │ Modo multimodal            [■■] │ │
│ │ Descargando el modelo · 45 %    │ │ ← estado
│ │ ▓▓▓▓▓▓▓▓░░░░░░░░░               │ │ ← LisdataLinearProgress (progreso real)
│ │ [Pausar]          [Cancelar]    │ │ ← LisdataOutlinedButton
```

Los estados siguientes, uno cada vez:
- «Descargando el modelo · 45 %», con «Pausar» y «Cancelar»;
- «Descarga en pausa», con «Reanudar» y «Cancelar»;
- «Comprobando el modelo…», sin botones;
- «Cambiando el modelo de búsqueda…», sin botones;
- «Falta espacio: libera 210 MB», con «Reintentar» y «Cancelar»;
- «Sin conexión. Se reanudará sola», con «Cancelar»;
- «Este modelo no es compatible con tu teléfono», con «Aceptar». El interruptor vuelve a apagado.

Al terminar, un aviso breve: «Modo multimodal activado».

## 3. Desactivar (FR-007)

```
┌───────────────────────────────────┐
│ ¿Desactivar el modo multimodal?   │
│                                   │
│ Solo podrás importar en Texto.    │
│ Las páginas ya indexadas y el     │
│ modelo se conservan: si lo vuelves│
│ a activar, estará listo al        │
│ momento.                          │
│                                   │
│      [Cancelar]   [Desactivar]    │
└───────────────────────────────────┘
```

## 4. «Modo al importar PDF» (FR-022)

Es una pantalla propia, como Tema (F16): `LisdataRadioRow` y «Guardar».

```
┌─────────────────────────────────────┐
│ ‹  Modo al importar PDF             │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ (●) Ambos                       │ │ ← por defecto
│ │     Texto y páginas. Lo más     │ │
│ │     completo y lo más lento.    │ │
│ ├─────────────────────────────────┤ │
│ │ ( ) Texto                       │ │
│ │     Como hasta ahora.           │ │
│ ├─────────────────────────────────┤ │
│ │ ( ) Visual                      │ │
│ │     Solo páginas. Para          │ │
│ │     escaneados y documentos     │ │
│ │     con muchas tablas.          │ │
│ └─────────────────────────────────┘ │
│ Las imágenes JPG y PNG se indexan   │
│ siempre como páginas.               │ ← nota
│ [         Guardar          ]        │ ← LisdataGradientButton
└─────────────────────────────────────┘
```

## 5. Ingesta: modo de la selección (FR-009, FR-010)

Con el modo activo, debajo de la fila de carpeta de destino de F17 aparece una fila igual:

```
│ ┌─────────────────────────────────┐ │
│ │ Carpeta de destino              │ │ ← F17 (sin cambios)
│ │ Legal                         › │ │
│ ├─────────────────────────────────┤ │
│ │ Modo de los PDF                 │ │ ← nueva fila; abre la hoja
│ │ Ambos (texto y páginas)       › │ │
│ └─────────────────────────────────┘ │
│ [   Elegir archivos   ]             │ ← como hoy; acepta también JPG y PNG
```

La hoja (`LisdataBottomSheet`) tiene las mismas tres `LisdataRadioRow` que §4, y se aplica al elegir archivos.

**Progreso en la cola** de un documento visual:

```
│ informe-ine.pdf            Ambos    │ ← LisdataStatusChip con el modo
│ Indexando páginas · 12 / 40         │
│ ▓▓▓▓▓░░░░░░░░░░  · quedan 1 min 30 s│
```

**Imagen con el modo apagado:** se rechaza con el estado de error de la cola de hoy: «Imagen no admitida» y debajo «Activa el modo multimodal en Ajustes para importar imágenes».

## 6. Biblioteca y detalle (FR-019 a FR-021)

**Fila de la Biblioteca:** el subtítulo de hoy gana el modo y las páginas.

```
│ ▢ informe-ine.pdf                   │
│   PDF · Ambos · 40 páginas · 85 fragmentos │
│ ▢ cartel.jpg                        │
│   Imagen · Visual · 1 página        │
```

**Detalle:** en los datos del documento (`LisdataFactRow`):

```
│ Modo de indexación     Ambos        │
│ Páginas                40           │
│ Fragmentos             85           │
```

- Si es solo Visual: «Solo páginas. Todavía no se usa en el chat».
- Con el modo apagado y un documento con páginas: «Sus páginas necesitan el modo multimodal».

**«Reindexar documento»,** con el modo activo, abre primero la hoja de §5 para elegir el modo. La versión anterior sigue respondiendo hasta que la nueva está completa.

**Escaneado en Texto,** con su error «Sin texto»: el detalle añade «Reindexar como páginas», que reindexa en Visual. Solo aparece con el modo activo.

## 7. Textos en inglés

| Español | English |
|---|---|
| Modo multimodal | Multimodal mode |
| Lee también páginas e imágenes. Respuestas más lentas. | Also reads pages and images. Slower answers. |
| ¿Activar el modo multimodal? | Turn on multimodal mode? |
| Descarga un modelo de 369 MB. | Downloads a 369 MB model. |
| Cada página tarda unos 3 s en indexarse y ocupa unos 100 KB. | Each page takes about 3 s to index and uses about 100 KB. |
| Las respuestas que miran páginas tardan 10–15 s. | Answers that look at pages take 10–15 s. |
| Tus documentos de texto siguen funcionando igual. | Your text documents keep working as before. |
| Descargando el modelo · 45 % | Downloading the model · 45% |
| Comprobando el modelo… | Checking the model… |
| Cambiando el modelo de búsqueda… | Switching the search model… |
| Este modelo no es compatible con tu teléfono | This model is not compatible with your phone |
| Modo multimodal activado | Multimodal mode on |
| ¿Desactivar el modo multimodal? | Turn off multimodal mode? |
| Modo al importar PDF | PDF import mode |
| Ambos / Texto / Visual | Both / Text / Visual |
| Modo de los PDF | PDF mode |
| Indexando páginas · 12 / 40 | Indexing pages · 12 / 40 |
| Imagen no admitida | Image not supported |
| Activa el modo multimodal en Ajustes para importar imágenes | Turn on multimodal mode in Settings to import images |
| Modo de indexación | Indexing mode |
| Páginas | Pages |
| Solo páginas. Todavía no se usa en el chat | Pages only. Not used in chat yet |
| Sus páginas necesitan el modo multimodal | Its pages need multimodal mode |
| Reindexar como páginas | Reindex as pages |
