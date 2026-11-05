# Specification Quality Checklist: Chat con páginas, modo «Multimodal» opcional (F20)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-05
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

- **Parámetros fijados:** son parámetros del producto que fijan F18 y D-078, no detalles de implementación, así que se mantienen en los requisitos, como en F18 y F19:
  - 1 página por respuesta;
  - 70, 140 o 280 tokens por imagen;
  - la visión en CPU;
  - la ventana de 8.192 tokens;
  - la plantilla v5-visual.
- **«Verificado al redactar»** nombra piezas del código para fijar el punto de partida, como en F19. Los requisitos no dependen de ellas.
- **Resuelto en el clarify (2026-11-05):**
  - la página entra siempre que haya páginas en el ámbito, sin umbral (FR-004);
  - Equilibrado es el detalle por defecto (FR-012);
  - la página citada se amplía en un visor a pantalla completa (FR-016).
- **Aprobado por el usuario el 2026-11-05:** el wireframe G47. La constitución 1.2.0 ya cubre F20, así que no hay enmienda.
