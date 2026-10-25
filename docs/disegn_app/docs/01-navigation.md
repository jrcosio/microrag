# Navegación y flujos — LISDATA

## Rutas propuestas
| Ruta lógica | Pantalla | Barra inferior |
|---|---|---|
| `welcome` | Bienvenida | No |
| `setup` | Preparación | No |
| `home` | Home | Sí |
| `chat/{conversationId?}` | Chat | Sí |
| `ingest` | Ingesta | No |
| `library` | Biblioteca | Sí |
| `document/{documentId}` | Detalle | No |
| `index` | Indexación | No |
| `settings` | Ajustes | Sí |
| `languages` | Idiomas | No |

El formato exacto de rutas debe adaptarse a la versión elegida de Navigation Compose. No pasar documentos, vectores ni archivos grandes por argumentos de navegación: pasar IDs y recuperar datos desde repositorios.

## Barra inferior
Cuatro destinos persistentes: Inicio, Chat, Biblioteca, Ajustes. Aparece solo en esas pantallas. Debe conservar estado de búsqueda y scroll cuando sea razonable. El botón Atrás debe comportarse según convenciones Android, sin crear pilas duplicadas de tabs.

## Primer arranque
1. Comprobar si onboarding está completado.
2. Si no, abrir `welcome`.
3. CTA «Comenzar» lleva a `setup`.
4. Verificar almacenamiento, artefactos de modelo y requisitos reales.
5. Si todo está listo, habilitar «Entrar en LISDATA».
6. Si faltan modelos, ofrecer importación desde almacenamiento local o explicar la necesidad de provisionarlos. No declarar «listo» por defecto.
7. Persistir onboarding completado y abrir `home`.

## Flujos principales
- Home → Chat → abrir cita → Detalle documental → volver al chat.
- Home → Importar → selector Android → cola → Indexación → Biblioteca.
- Home → Biblioteca → filtrar → Detalle → reindexar/excluir/eliminar.
- Home → Ajustes → Idiomas → guardar → volver a Ajustes.
- Home → Índice → pausar/reanudar → volver.

## Deep links y seguridad
No habilitar deep links externos en el MVP sin una necesidad concreta. La apertura de un documento desde una cita debe usar un identificador interno y comprobar que el documento existe y está accesible.

## Restauración de estado
Conservar destino, conversación activa y filtros al recrear Activity. No conservar grandes resultados ni vectores en SavedStateHandle. Si una tarea continúa en segundo plano, la UI debe reflejar el estado persistido al volver.

## Estados de navegación especiales
- Documento eliminado: mostrar aviso y volver a Biblioteca.
- Modelo no disponible al entrar en Chat: mostrar pantalla de bloqueo contextual y acceso a Configuración.
- Sin documentos: permitir abrir Chat, pero mostrar sugerencia de importación.
- Ingesta activa al salir: no perder la tarea; informar del comportamiento real.
