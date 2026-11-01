# Specification Quality Checklist: Ajustes e Idiomas (F12)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-01
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain
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

- **Feature de producto.** Mandan los PNG 09 y 10. Los estados sin maqueta van como propuestas S1–S11, que se aprueban antes de implementar (constitución VII).
- **Contrato, no implementación:** el prompt en español sigue siendo el del escritorio (F05, F11). Cualquier cambio en su redacción pasa por una evaluación (constitución III). Los nombres de ficheros solo aparecen en «Fuentes» y en «Verificado al redactar».
- **Quedan tres marcas [NEEDS CLARIFICATION], que se resuelven en `/speckit-clarify`:**
  - FR-006: qué filas abren la ficha de modelo y con qué acciones (G24);
  - FR-024: qué hace «Automático» y cuál es el valor por defecto (G25);
  - FR-032: si se mide la consulta en otro idioma y qué dice la tarjeta «Búsqueda multilingüe».
