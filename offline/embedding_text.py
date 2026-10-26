"""Embedding inputs and truncation, exactly as the app builds them (F04 research R5).

- Prefixes and their construction: EmbeddingPrefixes.kt (rag/runtime).
- Matryoshka truncation to 256 and L2 re-normalization: EmbeddingMath.truncateAndNormalize.
"""

import math
import struct

DOCUMENT = "task: search result | text: "
QUERY = "task: search query | text: "
DIMENSIONS = 256


def query_input(text):
    return QUERY + text.strip()


def document_input(content, header=""):
    """[header] is the contextual "document › section" path; it only goes into the embedded text."""
    return DOCUMENT + "\n".join(part for part in (header.strip(), content.strip()) if part)


def to_float32(value):
    return struct.unpack("<f", struct.pack("<f", value))[0]


def truncate_and_normalize(vector, dimensions=DIMENSIONS):
    """First [dimensions] components divided by their L2 norm, in double precision, rounded to float32.

    The norm is a plain left-to-right sum like the Kotlin loop: Python 3.12's sum() of floats is
    compensated and could differ in the last bits.
    """
    if len(vector) < dimensions:
        raise ValueError(f"Embedding has {len(vector)} components, expected >= {dimensions}")
    total = 0.0
    for value in vector[:dimensions]:
        total += float(value) * float(value)
    norm = math.sqrt(total)
    if norm == 0.0:
        raise ValueError("Embedding prefix has zero norm")
    return [to_float32(float(value) / norm) for value in vector[:dimensions]]
