"""Public evaluation corpus: manifest, verified fetch and attributions (F05 research R2, FR-020, D-032).

The documents are versioned in offline/corpus/docs. Fetching only fills in missing files, and a file is
written only if its SHA-256 matches the manifest. `fetch` entries pin what is downloaded:
- boe: a consolidated law from the BOE open data API, converted to Markdown;
- pdf: a PDF URL, optionally reduced to a page range;
- wikibooks: Wikilibros pages at fixed revisions, converted to plain text.
"""

import hashlib
import io
import json
import re
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
CORPUS = OFFLINE / "corpus"
MANIFEST = CORPUS / "manifest.json"
DOCS = CORPUS / "docs"
SOURCES = CORPUS / "SOURCES.md"

USER_AGENT = "MicroRAG-eval/0.1 (desktop evaluation tooling; https://github.com/jrcosio/microrag)"
FIELDS = (
    "id", "title", "format", "kind", "source_url", "publisher", "license", "attribution",
    "source_updated", "retrieved", "transformations", "fetch", "sha256",
)
LICENSES = ("BOE: reutilización con atribución", "AEAT: RD 1495/2011", "CC BY-SA 4.0")
FORMATS = ("md", "pdf", "txt")
KINDS = ("normativa", "guia", "manual")


@dataclass(frozen=True)
class Doc:
    id: str
    title: str
    format: str
    kind: str
    source_url: str
    publisher: str
    license: str
    attribution: str
    source_updated: str
    retrieved: str
    transformations: str
    fetch: dict
    sha256: str

    @property
    def file_name(self):
        return f"{self.id}.{self.format}"

    @property
    def mime_type(self):
        return {"md": "text/markdown", "pdf": "application/pdf", "txt": "text/plain"}[self.format]


def parse_manifest(entries):
    docs, seen = [], set()
    for entry in entries:
        missing = [field for field in FIELDS if not entry.get(field)]
        if missing:
            raise ValueError(f"{entry.get('id', '?')}: missing {missing}")
        if entry["license"] not in LICENSES:
            raise ValueError(f"{entry['id']}: license not allowed: {entry['license']}")
        if entry["format"] not in FORMATS or entry["kind"] not in KINDS:
            raise ValueError(f"{entry['id']}: unknown format or kind")
        if entry["id"] in seen:
            raise ValueError(f"{entry['id']}: duplicated id")
        seen.add(entry["id"])
        docs.append(Doc(**{field: entry[field] for field in FIELDS}))
    return docs


def load_manifest(path=MANIFEST):
    return parse_manifest(json.loads(path.read_text(encoding="utf-8")))


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


PENDING = "pending"


def fetch(docs, docs_dir=DOCS, fetcher=None):
    """Downloads the missing documents; a hash that differs from the manifest stops everything."""
    fetcher = fetcher or default_fetcher
    docs_dir.mkdir(parents=True, exist_ok=True)
    for doc in docs:
        target = docs_dir / doc.file_name
        if target.exists():
            continue
        if doc.sha256 == PENDING:
            raise SystemExit(f"{doc.id}: not recorded yet; run fetch-corpus --record")
        data = fetcher(doc)
        found = sha256_bytes(data)
        if found != doc.sha256:
            raise SystemExit(f"{doc.id}: sha256 {found} does not match the manifest")
        target.write_bytes(data)
        print(f"fetched {doc.id} ({len(data)} bytes)")


def record(manifest=MANIFEST, docs_dir=DOCS, fetcher=None):
    """Adds a newly selected document: downloads entries marked `pending` and writes their hash."""
    fetcher = fetcher or default_fetcher
    entries = json.loads(manifest.read_text(encoding="utf-8"))
    docs_dir.mkdir(parents=True, exist_ok=True)
    for entry in entries:
        if entry.get("sha256") != PENDING:
            continue
        doc = parse_manifest([entry])[0]
        data = fetcher(doc)
        (docs_dir / doc.file_name).write_bytes(data)
        entry["sha256"] = sha256_bytes(data)
        manifest.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"recorded {doc.id} ({len(data)} bytes)")


def verify(docs, docs_dir=DOCS):
    for doc in docs:
        found = sha256_bytes((docs_dir / doc.file_name).read_bytes())
        if found != doc.sha256:
            raise SystemExit(f"{doc.id}: file does not match the manifest (sha256 {found})")


def sources_markdown(docs):
    lines = [
        "# Corpus de evaluación — fuentes y licencias",
        "",
        "Generado desde `manifest.json` (`run_eval.py sources`); no se edita a mano.",
        "Documentos públicos reutilizados para la evaluación offline de MicroRAG (F05, D-032).",
        "No forman parte de la app y no se distribuyen con ella.",
        "",
    ]
    for doc in docs:
        lines += [
            f"## {doc.title}",
            "",
            f"- **Fichero:** `docs/{doc.file_name}` ({doc.kind}, {doc.format})",
            f"- **Origen:** {doc.publisher} — {doc.source_url}",
            f"- **Licencia:** {doc.license}",
            f"- **Atribución:** {doc.attribution}",
            f"- **Actualización del original:** {doc.source_updated}; descargado el {doc.retrieved}",
            f"- **Transformaciones:** {doc.transformations}",
            f"- **SHA-256:** `{doc.sha256}`",
            "",
        ]
    return "\n".join(lines)


# --- fetchers ----------------------------------------------------------------------------------------

def http_get(url, accept=None, pause=0.0):
    headers = {"User-Agent": USER_AGENT}
    if accept:
        headers["Accept"] = accept
    time.sleep(pause)
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=120) as response:
        return response.read()


def fetch_boe(spec, doc):
    from eval.boe import API, boe_to_markdown

    xml = http_get(API.format(boe_id=spec["boe_id"]), accept="application/xml").decode("utf-8")
    return boe_to_markdown(xml, spec["boe_id"], doc.source_updated).encode("utf-8")


def fetch_pdf(spec, doc):
    data = http_get(spec["url"])
    pages = spec.get("pages")
    if not pages:
        return data
    from pypdf import PdfReader, PdfWriter
    from pypdf.generic import ArrayObject, ByteStringObject

    reader = PdfReader(io.BytesIO(data))
    writer = PdfWriter()
    first, last = pages
    for number in range(first, last + 1):
        writer.add_page(reader.pages[number - 1])
    # Fixed metadata and file id, so the same page range always gives the same bytes.
    writer.add_metadata({"/Title": doc.title, "/Producer": "MicroRAG eval (pypdf)"})
    file_id = ByteStringObject(hashlib.md5(doc.id.encode()).digest())
    writer._ID = ArrayObject([file_id, file_id])  # noqa: SLF001 (deterministic id)
    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()


WIKI_API = "https://es.wikibooks.org/w/index.php"


def fetch_wikibooks(spec, doc):
    parts = []
    for page in spec["pages"]:
        query = urllib.parse.urlencode({"oldid": page["revid"], "action": "raw"})
        wikitext = http_get(f"{WIKI_API}?{query}", pause=1.5).decode("utf-8")
        text = wikitext_to_text(wikitext)
        if text:
            parts.append(page["title"].split("/")[-1] + "\n\n" + text)
    return ("\n\n".join(parts) + "\n").encode("utf-8")


def default_fetcher(doc):
    kind = doc.fetch["type"]
    if kind == "boe":
        return fetch_boe(doc.fetch, doc)
    if kind == "pdf":
        return fetch_pdf(doc.fetch, doc)
    if kind == "wikibooks":
        return fetch_wikibooks(doc.fetch, doc)
    raise ValueError(f"{doc.id}: unknown fetch type {kind}")


# --- wikitext ----------------------------------------------------------------------------------------

def _strip_nested(text, opening, closing):
    out, depth, i = [], 0, 0
    while i < len(text):
        if text.startswith(opening, i):
            depth += 1
            i += len(opening)
        elif depth and text.startswith(closing, i):
            depth -= 1
            i += len(closing)
        else:
            if depth == 0:
                out.append(text[i])
            i += 1
    return "".join(out)


def wikitext_to_text(wikitext):
    """Plain text of a wiki page: templates, tables, references, files and markup removed."""
    text = re.sub(r"<!--.*?-->", "", wikitext, flags=re.S)
    text = re.sub(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    text = _strip_nested(text, "{{", "}}")
    text = _strip_nested(text, "{|", "|}")
    text = re.sub(r"\[\[(?:Archivo|File|Imagen|Image|Categoría|Category):[^\[\]]*(?:\[\[[^\]]*\]\][^\[\]]*)*\]\]", "", text, flags=re.I)
    text = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[https?://[^\s\]]+\s([^\]]*)\]", r"\1", text)
    text = re.sub(r"\[https?://[^\]]+\]", "", text)
    text = re.sub(r"'{2,}", "", text)
    text = re.sub(r"^=+\s*(.*?)\s*=+\s*$", r"\1", text, flags=re.M)
    text = re.sub(r"^[*#:;]+\s*", "", text, flags=re.M)
    text = re.sub(r"</?(?:syntaxhighlight|source|pre|code|tt|nowiki|div|span|center|small|big|sup|sub|br|blockquote|poem)[^>]*>", "", text, flags=re.I)
    text = re.sub(r"__[A-Z]+__", "", text)
    lines = [line.rstrip() for line in text.splitlines()]
    text = "\n".join(lines)
    return re.sub(r"\n{3,}", "\n\n", text).strip()
