# Specification Quality Checklist: Harness offline de evaluación (F05)

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

- **Feature técnica.** Los actores son quien desarrolla, el usuario que aporta el corpus y las features que heredan los parámetros. Las métricas (Recall, MRR, nDCG), RRF, BM25 y la plantilla de prompt forman parte del contrato del informe (constitución III), no de la implementación. La spec no fija librerías, scripts ni estructura de código.
- **Relevantes derivados de la evidencia** (FR-007): el informe identifica los relevantes por `(content_hash, ordinal)`. Como la ablación de tamaño de fragmento cambia los ordinales, cada pregunta guarda además la evidencia literal y los relevantes de cada variante se derivan de ella. Amplía el informe sin contradecirlo.
- **Pendiente de `/speckit-clarify`** (recogido en «Assumptions»):
  - origen y versionado del corpus (G37);
  - cómo se generan las preguntas;
  - cómo se juzgan respuestas y citas;
  - E4B en F05 o en F14;
  - qué pasa si no se alcanzan los umbrales.
