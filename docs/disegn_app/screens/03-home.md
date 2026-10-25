# 03 — Home / Panel principal

## Objetivo
Ofrecer acceso inmediato al chat y a la gestión documental sin exponer complejidad técnica innecesaria.

## Ruta
`home`. Tab Inicio seleccionada. Desde aquí: Chat, Ingesta, Biblioteca, Indexación, Idiomas y Ajustes.

## Composición
1. Cabecera: logo LISDATA pequeño a la izquierda y botón de Ajustes a la derecha.
2. Estado de sistema: chip `IA disponible sin conexión` solo si los modelos están listos; de lo contrario `Configuración pendiente` con CTA.
3. Saludo: `¿Qué necesitas consultar hoy?`
4. Hero oscuro con degradado y forma corporativa. Título `Pregunta a tus documentos`; explicación breve y CTA `Nuevo chat`.
5. Cuadrícula 2×2:
   - Importar → `ingest`.
   - Documentos → `library`.
   - Índice local → `index`.
   - Idiomas → `languages`.
6. Resumen de biblioteca: número real de documentos y fragmentos listos.

## Interacciones
Todos los accesos son botones completos, no solo iconos. `Nuevo chat` crea o abre conversación nueva. La cabecera abre Ajustes. Barra inferior persistente.

## Estados
- Primera instalación sin documentos: mostrar `Tu biblioteca está vacía` y CTA `Importar primer documento`; no mostrar estadísticas de ejemplo.
- Modelos no disponibles: aviso de configuración; chat puede mostrar bloqueo contextual.
- Indexación en curso: pequeña tarjeta con estado y acceso al detalle.
- Error al cargar resumen: mantener accesos principales y mostrar reintento local.

## Rendimiento
Consultar agregados mediante repositorio; no cargar listas completas de fragmentos para construir los contadores.

## Criterios de aceptación
- [ ] Acceso a Chat en una interacción.
- [ ] Cuatro acciones rápidas funcionales.
- [ ] Contadores reales y actualizados.
- [ ] Home útil con biblioteca vacía.
- [ ] Responsive y accesible con texto grande.
