# Checklist QA — LISDATA

## Identidad
- [ ] Se usan los siete colores corporativos como tokens, sin tonos arbitrarios para acciones principales.
- [ ] El logo proviene del asset oficial.
- [ ] Iconografía consistente y autorizada.
- [ ] Dark mode y escalado de texto comprobados.

## Navegación
- [ ] Primer arranque muestra onboarding; siguientes arranques abren Home cuando procede.
- [ ] Tabs conservan estado razonable y Atrás funciona correctamente.
- [ ] Citas abren el documento correcto y permiten volver al chat.
- [ ] No aparecen tabs en bienvenida, preparación, ingesta, detalle, índice e idiomas.

## RAG y veracidad
- [ ] Ninguna respuesta muestra citas inexistentes.
- [ ] La ausencia de evidencia se comunica claramente.
- [ ] La UI distingue recuperación, generación, cancelación y error.
- [ ] El modelo no se declara disponible sin verificación.
- [ ] No hay conteos ni progresos ficticios en producción.

## Documentos
- [ ] Selector SAF admite múltiples archivos según formatos soportados.
- [ ] Errores de PDF vacío, cifrado, corrupto o sin texto se muestran.
- [ ] Duplicados se detectan o gestionan explícitamente.
- [ ] Excluir documento afecta a las búsquedas futuras.
- [ ] Borrar exige confirmación y limpia datos asociados según contrato.
- [ ] La biblioteca maneja 100k fragmentos sin cargar todo en UI.

## Offline y privacidad
- [ ] No hay llamadas de red ni dependencias cloud para uso normal.
- [ ] Archivos, vectores, conversaciones y preferencias son locales.
- [ ] Los permisos de archivo son mínimos.
- [ ] No se registran contenidos privados en logs.
- [ ] Se verifica comportamiento en modo avión.

## Accesibilidad y localización
- [ ] TalkBack anuncia controles y estados.
- [ ] Objetivos táctiles de al menos 48 dp.
- [ ] Textos externalizados y traducciones de UI disponibles.
- [ ] Cambio de idioma no provoca reindexación injustificada.
- [ ] Se prueban textos largos y tamaños de fuente grandes.

## Pruebas de regresión
- [ ] Biblioteca vacía.
- [ ] Un documento listo.
- [ ] Miles de documentos y 100k fragmentos.
- [ ] Indexación interrumpida y recuperada.
- [ ] Almacenamiento insuficiente.
- [ ] Modelo ausente o incompatible.
- [ ] Documento eliminado mientras se visualiza una cita.
- [ ] Rotación, cierre y reapertura de la app.
