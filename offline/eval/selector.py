"""Literal port of the report's ContextSelector (§Selección de contexto), F05 research R6.

F11 builds its context the same way: RRF order, at most 3 chunks per document, the next neighbour of the
2 best ones, a token budget, consecutive chunks merged without the chunker overlap, and blocks ordered
by document and ordinal.
"""

from dataclasses import dataclass, replace

from eval.chunker import kt_trim, kt_trim_end, kt_trim_start


@dataclass(frozen=True)
class ChunkView:
    id: int
    document_id: int
    ordinal: int
    heading_path: str | None
    page: int | None
    content: str
    token_estimate: int
    document_name: str


@dataclass(frozen=True)
class ContextBlock:
    document_name: str
    heading_path: str | None
    page: int | None
    document_id: int
    first_ordinal: int
    last_ordinal: int
    text: str
    tokens: int


class ContextSelector:
    def __init__(self, max_blocks=6, max_per_doc=3, budget_tokens=2_200, expand_top=2):
        self.max_blocks = max_blocks
        self.max_per_doc = max_per_doc
        self.budget_tokens = budget_tokens
        self.expand_top = expand_top

    def select(self, ranked, neighbor):
        """[ranked] chunks in RRF order; [neighbor](document_id, ordinal) loads a chunk or returns None."""
        chosen, per_doc, used = {}, {}, 0

        def try_add(chunk):
            nonlocal used
            key = (chunk.document_id, chunk.ordinal)
            if key in chosen:
                return True
            if per_doc.get(chunk.document_id, 0) >= self.max_per_doc:
                return False
            if used + chunk.token_estimate > self.budget_tokens:
                return False
            chosen[key] = chunk
            per_doc[chunk.document_id] = per_doc.get(chunk.document_id, 0) + 1
            used += chunk.token_estimate
            return True

        for rank, chunk in enumerate(ranked):
            if len(chosen) >= self.max_blocks + self.expand_top:
                break
            if try_add(chunk) and rank < self.expand_top:
                next_chunk = neighbor(chunk.document_id, chunk.ordinal + 1)
                if next_chunk is not None:
                    try_add(next_chunk)

        blocks = []
        for chunk in sorted(chosen.values(), key=lambda c: (c.document_id, c.ordinal)):
            last = blocks[-1] if blocks else None
            if last is not None and last.document_id == chunk.document_id and last.last_ordinal + 1 == chunk.ordinal:
                blocks[-1] = replace(
                    last, last_ordinal=chunk.ordinal, text=_merge_without_overlap(last.text, chunk.content),
                    tokens=last.tokens + chunk.token_estimate,
                )
            else:
                blocks.append(ContextBlock(chunk.document_name, chunk.heading_path, chunk.page, chunk.document_id,
                                           chunk.ordinal, chunk.ordinal, chunk.content, chunk.token_estimate))
        return blocks[:self.max_blocks]


def _merge_without_overlap(a, b):
    """Drops the paragraph repeated by the chunker overlap."""
    a_end = kt_trim_end(a)
    last_paragraph = a_end.rsplit("\n\n", 1)[-1]
    b_start = kt_trim_start(b)
    rest = b_start[len(last_paragraph):] if b_start.startswith(last_paragraph) else b
    return a_end + "\n\n" + kt_trim(rest)
