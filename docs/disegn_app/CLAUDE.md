# CLAUDE.md — LISDATA Android

## Misión
Implementar la aplicación Android nativa LISDATA, una interfaz de inteligencia documental RAG con ejecución íntegra en el dispositivo. El usuario debe poder importar documentos, indexarlos, consultar su contenido mediante un chat con fuentes y controlar su biblioteca.

## Fuente de verdad y orden de lectura
1. `README.md`: alcance y mapa del paquete.
2. `docs/00-design-system.md`: identidad visual y tokens.
3. `docs/01-navigation.md`: rutas y navegación.
4. `docs/02-architecture.md`: arquitectura UI y contratos de estado.
5. `docs/03-components.md`: componentes reutilizables.
6. `screens/*.md`: especificaciones de cada pantalla.
7. `docs/04-qa-checklist.md`: verificación transversal.

Si hay conflicto, prevalecen los requisitos funcionales explícitos de cada pantalla sobre el contenido de demostración del prototipo. No inventar detalles de SDK ni dependencias.

## Stack objetivo
- Android nativo: Kotlin + Jetpack Compose + Material 3.
- Estado: ViewModel + StateFlow, eventos de UI unidireccionales.
- Navegación: Navigation Compose.
- Persistencia local: repositorios e interfaces desacopladas de la UI.
- Inferencia: Gemma 4 y EmbeddingGemma 2 como decisiones de producto; adaptadores concretos por verificar.
- Índice: almacenamiento local con SQLite y motor vectorial seleccionado por el equipo técnico.
- Tareas de indexación: ejecución controlada y recuperable; evaluar WorkManager y requisitos de tareas prolongadas.

## Reglas innegociables
1. El RAG funciona sin conexión. No incorporar endpoints, analítica, telemetría, WebView remoto ni permisos de red sin autorización explícita.
2. Los archivos importados, fragmentos, vectores, conversaciones y preferencias permanecen en el dispositivo.
3. No afirmar que un modelo está listo si no existe una comprobación real de instalación y compatibilidad.
4. Nunca fabricar citas, nombres de archivos, páginas ni porcentajes de progreso. Los datos del prototipo son ejemplos.
5. La UI no invoca directamente librerías nativas de inferencia: usar interfaces y repositorios.
6. Eliminar documentos requiere confirmación y limpieza coherente de texto, embeddings, metadatos y archivos según la opción elegida.
7. Cada pantalla debe contemplar estados vacío, carga, éxito y error, además de accesibilidad.

## Proceso recomendado para el agente
- Crear primero tokens, tema, navegación y componentes compartidos.
- Construir las diez pantallas con previews y estados simulados explícitamente etiquetados.
- Sustituir progresivamente los fakes por repositorios reales sin alterar los contratos de UI.
- Añadir pruebas de ViewModel, navegación y UI; ejecutar compilación y tests.
- No marcar tareas como completas sin verificar el comportamiento.
- Pedir los assets corporativos exportados (SVG/vector) si no están disponibles en el repositorio. No reconstruir el logo a mano.

## Definición de terminado
Cada pantalla debe cumplir sus criterios de aceptación, funcionar con fuentes de datos reales o fakes aislados, tener estados de error y accesibilidad, respetar la identidad LISDATA y no crear dependencias de red.
