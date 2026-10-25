# 10 — Idiomas / Configuración lingüística

## Objetivo
Separar tres conceptos: idioma de la interfaz, idioma de las respuestas y capacidad de consultar documentos escritos en distintos idiomas.

## Ruta
`languages`, desde Ajustes. Atrás vuelve a `settings`.

## Composición
- Top bar: `Idiomas`.
- Eyebrow `CONFIGURACIÓN LINGÜÍSTICA`.
- H1 `Habla con tus documentos en tu idioma`.
- Tarjeta `Idioma de la aplicación`: Español, English, Português como propuesta inicial de localizaciones.
- Tarjeta `Idioma de las respuestas`: Automático, Español, English. Ampliable a más idiomas si se valida soporte.
- Tarjeta `Detección del idioma`: activar solo si existe implementación.
- Tarjeta `Búsqueda multilingüe`: describe búsqueda entre idiomas, condicionada a capacidades del modelo.
- CTA `Guardar preferencias`.

## Comportamiento
- Idioma UI: usar mecanismo de locales de Android/AppCompat o API equivalente seleccionada; actualizar recursos sin perder estado.
- Idioma de respuesta: persistir preferencia y aplicarla a la generación local.
- Automático: seguir idioma de pregunta cuando sea compatible; definir fallback.
- Búsqueda multilingüe: no prometer resultados perfectos; evaluar con documentos reales.
- Cambiar idioma UI o idioma de respuesta no exige reindexar. Cambiar modelo de embeddings o configuración incompatible sí puede requerirlo.

## Estados
Guardando, guardado, error de persistencia, idioma de respuesta no compatible, función de detección no disponible. No mostrar switches activos si la función no existe.

## Accesibilidad
Cada selector tiene título, descripción y estado anunciado. Soportar expansión de texto y nombres largos de idiomas.

## Criterios de aceptación
- [ ] Interfaz y respuesta se configuran por separado.
- [ ] Persistencia tras reinicio.
- [ ] Sin reindexación al cambiar idioma UI.
- [ ] Mensajes honestos sobre soporte multilingüe.
- [ ] Traducciones revisadas y fallback definido.
