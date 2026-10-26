"""Values the app expects, read from its Kotlin sources (F05 research R5).

The evaluation index must describe the same vector space as the app. Reading the values from the
code, instead of copying them, means they cannot drift apart without a failing test.
"""

import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
KOTLIN = REPO / "app/src/main/java/com/lisdatasolutions/microrag"


@dataclass(frozen=True)
class CatalogModel:
    id: str
    file_name: str
    sha256: str
    size_bytes: int


@lru_cache(maxsize=None)
def _source(relative):
    return (KOTLIN / relative).read_text(encoding="utf-8")


def _const(relative, name, pattern):
    match = re.search(rf"const val {name} = {pattern}", _source(relative))
    if match is None:
        raise ValueError(f"{name} not found in {relative}")
    return match.group(1)


def _text(relative, name):
    return _const(relative, name, r'"([^"]*)"')


def _number(relative, name):
    return int(_const(relative, name, r"(\d+)\b"))


@lru_cache(maxsize=None)
def _catalog():
    models = {}
    for block in re.findall(r"ModelFile\((.*?)\n        \)", _source("domain/model/ModelCatalog.kt"), re.S):
        fields = dict(re.findall(r"(\w+) = ([^,\n]+),", block))
        model_id = fields["id"].removeprefix("ModelId.")
        models[model_id] = CatalogModel(
            id=model_id,
            file_name=fields["fileName"].strip('"'),
            sha256=fields["sha256"].strip('"'),
            size_bytes=int(fields["sizeBytes"].rstrip("L").replace("_", "")),
        )
    return models


def model(model_id):
    return _catalog()[model_id]


def dimensions():
    return _number("rag/runtime/EmbeddingMath.kt", "DIMENSIONS")


def document_prefix():
    return _text("rag/runtime/EmbeddingPrefixes.kt", "DOCUMENT")


def query_prefix():
    return _text("rag/runtime/EmbeddingPrefixes.kt", "QUERY")


def normalizer_version():
    return _number("rag/text/SpanishText.kt", "VERSION")


def schema_version():
    return _number("domain/rag/RagIndexConfig.kt", "SCHEMA_VERSION")


def expected_meta(dims=None):
    """`rag_meta` as RagIndexConfig.toMeta() writes it; [dims] overrides the dimension for the dim* variants."""
    return {
        "schema_version": str(schema_version()),
        "embedding_model_sha256": model("EG2_270M").sha256,
        "embedding_dims": str(dims if dims is not None else dimensions()),
        "embedding_doc_prefix": document_prefix(),
        "embedding_query_prefix": query_prefix(),
        "normalizer_version": str(normalizer_version()),
        "quant_dirty": "1",
    }
