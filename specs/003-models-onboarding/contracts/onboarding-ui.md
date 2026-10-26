# Contrato — UI de Bienvenida y Preparación

Textos en `res/values/strings_onboarding.xml` (es, por defecto) y `res/values-en/strings_onboarding.xml` (en). Los textos visibles en los PNG se copian literalmente (D-004). Los que no aparecen en ellos son propuestas aprobadas con la spec (D-027).

## P01 — Bienvenida (`welcome`)

| Elemento | Componente o token | es | en |
|---|---|---|---|
| Fondo | `LisdataTheme.gradients.welcome` | — | — |
| Logo | `LisdataLogo` (PNG oficial; no se reconstruye) | cd: «LISDATA» | cd: «LISDATA» |
| Chip | `LisdataStatusChip(onDark = true)` con punto | «100% EN TU DISPOSITIVO» | «100% ON YOUR DEVICE» |
| Ilustración | `WelcomeIllustration` (Compose: órbitas, documento y tiles con `ic_file_text`, `ic_sparkle` e `ic_shield_check`, solo tokens). `clearAndSetSemantics {}` | — | — |
| H1, línea 1 | blanco | «Tu conocimiento.» | «Your knowledge.» |
| H1, línea 2 | `ice200` | «Solo tuyo.» | «Only yours.» |
| Texto | blanco / blanco 80 % | «Pregunta a tus documentos con IA.» / «Sin nube. Sin comprometer tu privacidad.» | «Ask your documents with AI.» / «No cloud. No compromise on privacy.» |
| CTA | `LisdataGradientButton` con `ic_arrow_right`, 52 dp | «Comenzar» | «Get started» |
| Pie | Eyebrow sobre oscuro | «PRIVADO · INTELIGENTE · LOCAL» | «PRIVATE · SMART · LOCAL» |

**Comportamiento:**
- **Altura compacta:** por debajo de 640 dp de alto, la ilustración se reduce a la mitad; por debajo de 560 dp, se oculta. El contenido es desplazable para que la CTA nunca quede recortada.
- **Escalado de fuente:** con escala ≥ 1,5 se aplica la misma regla que en la altura compacta.
- **Navegación:** «Comenzar» → `StartOnboardingUseCase` + `navigate(Setup)`. Atrás → `finish()`.

## P02 — Preparación inicial (`setup`)

**Estructura** (PNG 02, de arriba abajo):
1. `LisdataTopBar`: «Preparación inicial», con Atrás → `Welcome`.
2. `LisdataStepIndicator(step, 3)`: «PASO N DE 3» y barra a N/3 con `gradients.stepProgress`.
3. H1 «Tu IA, en tu móvil.» y texto «Configura los modelos para empezar a consultar documentos sin conexión.»
4. `LisdataModelStatusRow` **Gemma 4** / «Modelo de respuestas», con `ic_sparkle`.
5. `LisdataModelStatusRow` **EmbeddingGemma 2** / «Búsqueda semántica», con `ic_layers`.
6. Tarjeta **Almacenamiento local** / «Índice vectorial y archivos privados», con `ic_database` y el chip de almacenamiento.
7. Si las notificaciones están denegadas: `LisdataInfoCard` (aviso) «Activa las notificaciones para ver el progreso fuera de la app.»
8. `LisdataInfoCard` con `ic_shield_check`: «Tus documentos nunca salen de este dispositivo.» / «La instalación se verifica localmente.»
9. Si `continueEnabled = false`, texto de bloqueo (`textSecondary`, centrado) y CTA «Continuar», deshabilitada (`disabledContainer`) o habilitada (`gradients.cta`).

**Estados de fila** (FR-007):

| `ModelSetupStatus` | Chip es | Chip en | Detalle es | Barra |
|---|---|---|---|---|
| `Unconfigured` | Por configurar | Not set up | — | — |
| `Queued` | En cola | Queued | Empezará al terminar EmbeddingGemma 2 | — |
| `Downloading` | Descargando %1$d % | Downloading %1$d% | %1$s de %2$s | determinada |
| `Importing` | Importando %1$d % | Importing %1$d% | %1$s de %2$s | determinada |
| `Paused` | En pausa | Paused | %1$s de %2$s · Toca para reanudar | determinada, sin animación |
| `WaitingForNetwork` | Esperando conexión | Waiting for connection | %1$s de %2$s | determinada |
| `Verifying` | Verificando | Verifying | Comprobando la huella · %1$d % | determinada |
| `Preparing` | Preparando | Preparing | Probando el modelo en este dispositivo | indeterminada |
| `Ready` (GPU) | Listo | Ready | %1$s | — |
| `Ready` (CPU) | Listo | Ready | %1$s · Modo CPU, más lento | — |
| `NoSpace` | Sin espacio | No space | Faltan %1$s · Toca para liberar espacio | — |
| `Incompatible` | Incompatible | Incompatible | %1$s (motivo) · Toca para elegir otra opción | — |
| `Failed` | Error | Error | %1$s (motivo) · Toca para reintentar | — |

- **Formato de tamaños:** `Formatter.formatShortFileSize` (base 10), por ejemplo «880 MB» y «2,59 GB».
- **Motivos traducidos:**
  - `HashMismatch`: «La descarga no superó la verificación»;
  - `Http`/`Network`: «No se pudo descargar (%s)»;
  - `ForbiddenHost`/`InsecureScheme`: «Origen de descarga no permitido»;
  - importación de un fichero ajeno: «El archivo no corresponde a %s»;
  - prueba fallida: «Este dispositivo no puede ejecutar el modelo».

**Chip de almacenamiento:**
- `Offline` (éxito, como el PNG): «Offline»;
- `NoSpace`: «Sin espacio»;
- `Preparing`: «Preparando».

**Texto de bloqueo** (FR-008):

| `ContinueBlocker` | es | en |
|---|---|---|
| `ModelsNotConfigured` | Configura los dos modelos para continuar. | Set up both models to continue. |
| `InProgress` | Esperando a que termine la descarga. | Waiting for the download to finish. |
| `NeedsAttention` | Revisa los modelos marcados para continuar. | Check the highlighted models to continue. |
| `NoSpace` | Libera espacio para continuar. | Free up space to continue. |

## Confirmación de descarga (diálogo)

- **Componente:** `LisdataConfirmationDialog` con `destructive = false`, ampliado con un slot de contenido si hace falta. El cambio va en el design system, con preview.
- **Título:** «Descargar modelos».
- **Cuerpo:**
  - una línea por modelo pendiente: «Gemma 4 · 2,59 GB»;
  - «Total: %s»;
  - «Espacio libre: %1$s (quedarán %2$s)»;
  - «Red: Wi-Fi», «Red: datos móviles» o «Sin conexión: empezará al conectarte»;
  - con datos móviles, el aviso (`warning`): «La descarga usará tus datos móviles.»;
  - con poca RAM, el aviso: «Este dispositivo tiene menos de 7,5 GB de memoria. Las respuestas pueden ser más lentas.»
- **Acciones:** «Descargar» (principal), «Seleccionar archivo del dispositivo» (secundaria) y «Cancelar».
- **Sin espacio:** si al abrirla ya no hay espacio suficiente, «Descargar» se deshabilita y se muestra «Faltan %s».
- **«Seleccionar archivo del dispositivo»:** con varios modelos pendientes, primero se elige el modelo («¿Qué modelo vas a importar?»: Gemma 4 / EmbeddingGemma 2). Después se abre `ActivityResultContracts.OpenDocument(arrayOf("*/*"))`.
- **Permiso de notificaciones** (API ≥ 33 y sin conceder): al pulsar «Descargar» se muestra antes un diálogo, «Avisos de descarga» / «Te avisaremos del progreso aunque salgas de la app.», con «Continuar». Después se pide el permiso del sistema.

## Notificación de descarga

- **Canal:** «Descarga de modelos» (`model_downloads`), importancia baja.
- **Contenido:**
  - título «Descargando Gemma 4» (o EmbeddingGemma 2);
  - texto «%1$d % · %2$s de %3$s»;
  - progreso determinado;
  - acción «Detener»;
  - al tocarla, abre la app.
- **Tras «Detener»:** la notificación se retira. La fila muestra «En pausa».

## Accesibilidad

**Fila de modelo:** `mergeDescendants`, con la `contentDescription` «%1$s, %2$s, %3$s» (nombre, papel, estado verbal). Ejemplos de estado verbal:
- «descargando, 34 por ciento, 880 megabytes de 2,59 gigabytes»;
- «listo»;
- «error: la descarga no superó la verificación, doble toque para reintentar».

**Otras reglas:**
- `liveRegion = Polite` en el chip de estado. Los cambios de porcentaje se anuncian como mucho cada 10 puntos, para no saturar TalkBack.
- La CTA deshabilitada expone el texto de bloqueo como `stateDescription`.
- Todos los elementos tocables miden al menos 48 dp. Los chips de 27 dp amplían su área táctil a 48 dp, como en F01.
- Con la fuente al 200 % no hay recortes: la pantalla es desplazable y la CTA sigue alcanzable.
- En la Bienvenida, solo se anuncia el logo; la ilustración no.

## Navegación

| Desde | Acción | Destino | Pila |
|---|---|---|---|
| arranque (`NOT_STARTED`) | — | `Welcome` | `[Welcome]` |
| arranque (`STARTED`) | — | `Setup` | `[Welcome, Setup]` |
| arranque (`COMPLETED`) | — | `Home` | `[Home]` |
| `Welcome` | «Comenzar» | `Setup` | `[Welcome, Setup]` |
| `Welcome` | Atrás | cierra la app | — |
| `Setup` | Atrás o flecha | `Welcome` | `[Welcome]`. Las descargas siguen |
| `Setup` | «Continuar» | `Home` | `[Home]` |
