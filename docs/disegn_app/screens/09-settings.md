# 09 — Configuración general

## Objetivo
Centralizar preferencias técnicas y de privacidad sin saturar la experiencia cotidiana.

## Ruta
`settings`. Tab Ajustes. Subrutas: `languages` e `index`.

## Estructura
Título `Configuración`, con secciones:

### Inteligencia artificial
- `Modelo de respuesta`: Gemma 4; mostrar artefacto, versión local, tamaño y estado real.
- `Modelo de embeddings`: EmbeddingGemma 2; mostrar dimensiones configuradas y estado.
- Acción de importar/validar modelos locales cuando sea necesaria.

### Personalización
- `Idiomas` → `languages`.
- `Índice y rendimiento` → `index`.
- Preferencias de batería y procesamiento.

### Privacidad
- `Procesamiento sin nube`: indicador informativo de arquitectura, no un switch que habilite servidores.
- `Protección de biblioteca`: si se implementa, usar bloqueo biométrico/PIN del sistema con comportamiento real.
- `Borrar historial de chat`: acción destructiva confirmada.
- `Gestionar almacenamiento`: desglose de modelos, originales, fragmentos e índices.

### Información
- Versión de aplicación, licencias y diagnóstico local sin contenido sensible.

## Interacciones
Todas las preferencias deben persistirse y reflejarse en UI. Cambiar modelo de embeddings requiere explicar que puede necesitar reindexación completa; no ejecutar silenciosamente. No exponer opciones que el backend no soporte.

## Estados
Modelo ausente, importando, incompatible, listo; falta de espacio; error de lectura de preferencias. Mantener valores previos cuando una actualización falle.

## Criterios de aceptación
- [ ] Todos los ajustes tienen efecto real o están explícitamente deshabilitados.
- [ ] Cambios sensibles requieren confirmación.
- [ ] No hay switch engañoso de «offline».
- [ ] Ajustes persisten tras reiniciar.
- [ ] Navegación a Idiomas e Índice funciona.
