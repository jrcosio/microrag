"""Evaluation metrics (F05 research R10). Relevance is binary; ids are chunk ids of the evaluated index."""

import math


def recall_at(ranked, relevant, k):
    """Share of the relevant chunks found in the top k."""
    if not relevant:
        return 0.0
    return len(set(ranked[:k]) & set(relevant)) / len(relevant)


def reciprocal_rank(ranked, relevant):
    for position, item in enumerate(ranked, start=1):
        if item in relevant:
            return 1.0 / position
    return 0.0


def ndcg_at(ranked, relevant, k=10):
    dcg = sum(1.0 / math.log2(i + 2) for i, item in enumerate(ranked[:k]) if item in relevant)
    ideal = sum(1.0 / math.log2(i + 2) for i in range(min(len(relevant), k)))
    return dcg / ideal if ideal else 0.0


def blocks_hit(blocks, relevant_pairs):
    """1 if a selected block covers a relevant (document_id, ordinal); the report's Recall@6 per question."""
    for block in blocks:
        for document_id, ordinal in relevant_pairs:
            if block.document_id == document_id and block.first_ordinal <= ordinal <= block.last_ordinal:
                return 1.0
    return 0.0


def mean(values):
    values = list(values)
    return sum(values) / len(values) if values else None


def percentile(values, p):
    """Nearest-rank percentile, as the app diagnostics use."""
    ordered = sorted(values)
    if not ordered:
        return None
    rank = max(1, min(len(ordered), math.ceil(p / 100 * len(ordered))))
    return ordered[rank - 1]
