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
- **Resuelto en `/speckit-clarify`** (sesión 2026-10-26, D-032):
  - corpus público versionado con licencias (FR-020);
  - preguntas redactadas por el asistente y revisadas por el usuario (FR-008);
  - juicio automático en todas las variantes y manual en la elegida (FR-021);
  - E4B en F05, en la configuración elegida (FR-015);
  - umbrales: iterar con palancas baratas y, si no basta, consultar (FR-022).
- Re-validación tras el clarify: 16/16, sin cambios de estado.
