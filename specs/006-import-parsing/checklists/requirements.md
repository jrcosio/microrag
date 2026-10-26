# Specification Quality Checklist: Importación y parsing (F06)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **Detalles técnicos que forman parte del contrato**, no de la implementación:
  - el SHA-256 como identidad del documento (informe, `content_hash UNIQUE`);
  - el `Chunker` y sus parámetros (constitución III, D-034);
  - los golden compartidos con el escritorio, que garantizan que la evaluación de F05 representa a la app.

  La spec no fija bibliotecas, clases ni estructura de código. Las opciones de extractor de PDF del bloque «Verificado al redactar» son datos para el clarify, no una decisión.
- **Resuelto en `/speckit-clarify`** (sesión 2026-10-26, D-037):
  - extractor de PDF del sistema, sin bibliotecas añadidas (FR-016);
  - límites de 100 MB y 2.000 páginas (FR-005);
  - la cola muestra todo lo no indexado (FR-006);
  - un PDF con contraseña de apertura se rechaza como cifrado (FR-003).

  El detalle de la fila (G15) sigue la propuesta del INVENTARIO: una hoja inferior.
- Re-validación tras el clarify: 16/16, sin cambios de estado.
- **Alcance frente a F07:** F06 deja los documentos en `parsed` con su número de fragmentos. Los embeddings, la inserción, la notificación en primer plano y la pantalla `index` son de F07 (FR-011, FR-012 y Assumptions).
