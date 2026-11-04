# Contrato: pantallas de carpetas y ámbito (F17)

Manda el wireframe aprobado ([wireframe.md](../wireframe.md), K1–K14, D-070). Rutas:
- `Library`: pestaña, raíz con carpetas;
- `LibraryFolder(folderId: Long?)`: nueva, sin barra inferior; `null` es «Sin carpeta»;
- `Ingest(folderId: Long? = null)`: gana su destino;
- `Document`, `Chat` e `History`: las de siempre.

## 1. Biblioteca (K1, K2)

| Zona | Componente | Contenido |
|---|---|---|
| Raíz, sin búsqueda | `LisdataSectionHeader` + `LisdataCardGroup` con `LisdataSettingsRow(icon = Folder, title = nombre, subtitle = «N documentos», Chevron)` | Las carpetas por `name_norm`; «Sin carpeta» al final si tiene documentos; «Nueva carpeta» (`icon = Plus`, sin chevron) |
| Raíz, con búsqueda | La lista de F08 con `LibraryFolderFilter.All` | `LisdataDocumentRow` con la carpeta en la línea de datos |
| Carpeta | `LibraryScreen` de F08 con su filtro | Barra «‹ nombre», «＋» (importar con `Ingest(folderId)`) y «⋯» (`LisdataOverflowMenu`: «Renombrar carpeta» y «Eliminar carpeta», no en «Sin carpeta»). Placeholder «Buscar en Legal…». Vacío: «Esta carpeta está vacía» con «Importar a Legal» |
| Diálogos | `LisdataConfirmationDialog` con `LisdataTextField` (K3), y con `LisdataRadioRow` y un segundo `LisdataConfirmationDialog` (K4) | Textos de la spec |

## 2. Detalle e Ingesta (K5, K6)

- **Detalle:**
  - fila de dato «Carpeta», con el nombre o «Sin carpeta»;
  - «⋯» con «Quitar del índice» (si procede) y «Mover a carpeta»;
  - hoja `FolderChoiceSheet`;
  - aviso «Movido a «nombre»».
- **Ingesta:**
  - `LisdataSettingsRow(icon = Folder, title = «Carpeta de destino», subtitle = nombre o «Sin carpeta»)` sobre `LisdataFilePickerCard`;
  - abre `FolderChoiceSheet`;
  - la línea de datos de la cola añade «· nombre».

`FolderChoiceSheet` (`feature/folders`) es un `LisdataBottomSheet` con:
- título;
- `LisdataRadioRow` por carpeta y «Sin carpeta»;
- «Nueva carpeta…», que abre K3;
- «Cancelar» y la acción («Mover» o «Elegir»).

## 3. Chat (K7–K10)

- **Ficha:** `LisdataScopeChip` encima de `LisdataChatComposer`, con:
  - texto (FR-014): «Toda la biblioteca», «Legal», «Legal y Motores», «Legal + 2 documentos», «3 documentos»;
  - TalkBack: `contentDescription` «Buscar en: …» y `onClickLabel` «Cambiar ámbito».
- **Selector:** `ScopeSheet` (`LisdataBottomSheet`), con:
  - `LisdataRadioRow` «Toda la biblioteca»;
  - `LisdataSectionHeader` «CARPETAS» y `LisdataExpandableRow` por carpeta no vacía;
  - dentro de cada una, `LisdataCheckRow` por documento: los excluidos desactivados con «No incluido en RAG», y los pendientes con «Pendiente de indexar»;
  - «Cancelar» y «Aplicar», desactivado sin nada marcado.
- **Ámbito sin documentos listos:** `LisdataEmptyState` «En este ámbito no hay documentos listos para buscar» con el botón «Cambiar ámbito». El compositor queda desactivado.
- **Avisos** (`SnackbarHost` del chat, con región en vivo): «Una carpeta del ámbito ya no existe», «Un documento del ámbito ya no existe» y «El ámbito vuelve a ser toda la biblioteca».

## 4. Textos nuevos

- **Ficheros:**
  - `strings_library.xml`: carpetas, diálogos y avisos;
  - `strings_chat.xml`: ámbito y selector;
  - `strings_ingest.xml`: destino.

  Siempre en español y en inglés (K14).
- **Plurales:** «%d documentos» y «+ %d documentos».
- **Formato:** «Buscar en: %s», «¿Eliminar la carpeta «%s»?» y «Movido a «%s»».
- **Paridad:** `StringsParityTest`.

## 5. Accesibilidad (K13, FR-024)

- **U1. 48 dp** en todas las filas, casillas, botones de desplegar y la ficha.
- **U2. Frases:**
  - carpeta: «Legal, 12 documentos», con «Abrir carpeta»;
  - fila de documento del selector: nombre, estado y nota;
  - casilla de carpeta: `ToggleableState` y «algunos documentos» como `stateDescription` cuando es `Indeterminate`.
- **U3. Encabezados:** los titulares y las cabeceras de sección.
- **U4. Avisos y errores** anunciados una vez.
- **U5. Letra al 200 % y horizontal:** todo desplazable, y la ficha con elipsis sin tapar el compositor.
- **U6. Capas** (`LayeringTest`): `feature/*` no importa `data.`, SQLite ni `File`.
