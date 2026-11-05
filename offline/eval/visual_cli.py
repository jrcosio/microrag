"""Subcommands of the F18 visual evaluation (contracts/visual-harness.md §3), registered in run_eval.py."""

import json
from pathlib import Path


def _ints(text):
    return [int(value) for value in text.split(",") if value]


def _pairs(args, defaults):
    """key=value options with comma lists, over [defaults]."""
    values = dict(defaults)
    for arg in args:
        key, _, value = arg.partition("=")
        if key not in values:
            raise SystemExit(f"unknown option {key}; use {', '.join(values)}")
        values[key] = value
    return values


def visual_raster(args):
    """Renders every page of the visual corpus at each resolution into build/visual/pages (PNG cache)."""
    from eval import corpus, raster, visual_index

    options = _pairs(args, {"px": "768,1024,1536"})
    source = corpus.CORPORA["visual"]
    docs = corpus.load_manifest(source.manifest)
    corpus.verify(docs, source.docs)
    count = 0
    for px in _ints(options["px"]):
        for doc in docs:
            if doc.format != "pdf":
                continue
            for page in visual_index.page_numbers(doc, source.docs):
                raster.cached_page(visual_index.VISUAL_DIR / "pages", doc.sha256, source.docs / doc.file_name, page, px)
                count += 1
    print(json.dumps({"rendered_pages": count}))


def visual_build(args):
    """The text index of the visual corpus, its chunk-to-page map and the page vectors (research R10)."""
    from eval import builds, corpus, index, visual_index

    options = _pairs(args, {"px": "768,1024,1536", "tokens": "70,140"})
    source = corpus.CORPORA["visual"]
    docs = corpus.load_manifest(source.manifest)
    if not builds.index_path("visual-text").exists():
        builds.build("visual-text")
    mapping = visual_index.chunk_pages(builds.index_path("visual-text"), docs, source.docs)
    db = index.connect(builds.index_path("visual-text"))
    info = index.build_info(db)
    db.close()
    unmapped = sum(1 for pages in mapping.values() if not pages)
    print(json.dumps({"text_chunks": info["chunks"], "unmapped_chunks": unmapped}))
    for px in _ints(options["px"]):
        for tokens in _ints(options["tokens"]):
            added = visual_index.build_pages(px, tokens)
            print(json.dumps({"px": px, "tokens": tokens, "embedded_pages": added}))


def _questions():
    from eval import visual_testset

    return visual_testset.load()


def _docs():
    from eval import corpus, visual_testset

    source = corpus.CORPORA["visual"]
    return visual_testset.corpus_pages(corpus.load_manifest(source.manifest), source.docs)


def visual_validate(args):
    """Rules V1–V8 of the visual test set (`--pre-review` checks only the shape)."""
    from eval import visual_testset

    pre = "--pre-review" in args
    questions = _questions()
    errors = visual_testset.validate(questions, _docs(), pre_review=pre)
    print(json.dumps(visual_testset.counts(questions), ensure_ascii=False))
    for error in errors:
        print(error)
    if errors:
        raise SystemExit(f"{len(errors)} problems")


def visual_review_sheet(args):
    from eval import visual_testset

    del args
    visual_testset.write_review_sheet(_questions())
    print(f"wrote {visual_testset.REVIEW}")


def visual_review_apply(args):
    from eval import visual_testset

    del args
    questions = visual_testset.apply_review(_questions())
    visual_testset.save(questions)
    print(json.dumps(visual_testset.counts(questions), ensure_ascii=False))


def visual_freeze(args):
    """Writes the test set's hash in config/visual_rules.json; commit both before any ablation (FR-025)."""
    from eval import visual_rules, visual_testset

    del args
    errors = visual_testset.validate(_questions(), _docs())
    if errors:
        raise SystemExit("the test set does not pass V1–V8: " + "; ".join(errors))
    print(json.dumps({"testset_sha256": visual_rules.freeze(visual_rules.RULES, visual_testset.TESTSET)}))


def visual_retrieve(args):
    """Retrieval ablations by page (research R10): mode × px × tokens × dim, written to build/visual."""
    from eval import builds, index, visual_index, visual_retrieval, visual_rules, visual_testset
    from eval.retrieval import Retriever

    options = _pairs(args, {"px": "768,1024,1536", "tokens": "70,140", "dim": "256,512"})
    stamp = visual_rules.require_frozen(visual_rules.RULES, visual_testset.TESTSET)
    questions = [q for q in _questions() if q.review == "ok" and q.type != "sin_respuesta"]
    embedder = visual_retrieval.QueryEmbedder()
    text_path = builds.index_path("visual-text")
    db = index.connect(text_path)
    retriever = Retriever(db, lambda text: embedder.truncated(text, 256))
    chunk_pages = visual_index.load_chunk_pages()
    chunk_doc = visual_retrieval.chunk_documents(text_path)
    lists = {q.id: (visual_retrieval.to_pages(retriever.vector(q.question, visual_retrieval.CANDIDATES), chunk_pages, chunk_doc),
                    visual_retrieval.to_pages(retriever.fts(q.question, visual_retrieval.CANDIDATES), chunk_pages, chunk_doc))
             for q in questions}
    db.close()

    def rows_for(mode, page_lists):
        rows = []
        for q in questions:
            vector_pages, fts_pages = lists[q.id]
            ranked = visual_retrieval.fuse(mode, vector_pages, fts_pages, page_lists.get(q.id, []))
            rows.append({"id": q.id, "type": q.type, "ranked": ranked[:20],
                         "metrics": visual_retrieval.page_metrics(ranked, {tuple(p) for p in q.pages})})
        return rows

    results = [{"mode": "texto", "px": None, "tokens": None, "dim": None, "summary": visual_retrieval.summarize(rows_for("texto", {}))}]
    for px in _ints(options["px"]):
        for tokens in _ints(options["tokens"]):
            for dim in _ints(options["dim"]):
                pages = visual_index.load_pages(visual_index.pages_path(px, tokens), dim)
                page_lists = {q.id: visual_retrieval.rank_pages(embedder.truncated(q.question, dim), pages) for q in questions}
                for mode in ("visual", "ambos"):
                    rows = rows_for(mode, page_lists)
                    results.append({"mode": mode, "px": px, "tokens": tokens, "dim": dim, "summary": visual_retrieval.summarize(rows)})
                    visual_retrieval.write_json(visual_index.VISUAL_DIR / "runs" / f"retrieval-{mode}-{px}-{tokens}-{dim}.json",
                                                {"stamp": stamp, "rows": rows})
    embedder.save()
    out = visual_index.VISUAL_DIR / "retrieval-ablations.json"
    visual_retrieval.write_json(out, {"stamp": stamp, "questions": len(questions), "results": results})
    for r in results:
        v = r["summary"].get("visual", {})
        print(json.dumps({"mode": r["mode"], "px": r["px"], "tokens": r["tokens"], "dim": r["dim"],
                          "visual_recall@5": round(v.get("recall@5", 0), 3), "all_recall@5": round(r["summary"]["all"]["recall@5"], 3)}))
    print(f"wrote {out}")


def visual_decide(args):
    """The decision in three steps (research R12): --stage retrieval, --stage candidates <stage-A report>, or the rule."""
    from eval import visual_index, visual_retrieval, visual_rules

    rules = visual_rules.load()
    if args[:2] == ["--stage", "retrieval"]:
        ablations = json.loads((visual_index.VISUAL_DIR / "retrieval-ablations.json").read_text())
        rows = [{"mode": r["mode"], "px": r["px"], "tokens": r["tokens"], "dim": r["dim"],
                 "visual_recall@5": r["summary"]["visual"]["recall@5"]} for r in ablations["results"]]
        chosen = visual_rules.choose_retrieval(rows, rules)
        out = visual_index.VISUAL_DIR / "decision-retrieval.json"
        visual_retrieval.write_json(out, {"stamp": ablations["stamp"], **chosen})
        print(json.dumps(chosen))
        return
    metrics = _answer_runs()
    answers = visual_rules.desktop_answers(metrics)
    if args[:2] == ["--stage", "candidates"] and len(args) == 3:
        report = json.loads(Path(args[2]).read_text())
        stage_a, queries = visual_rules.phone_stage(report, "A"), visual_rules.phone_queries(report)
        chosen = visual_rules.candidates(answers, stage_a, queries, rules)
        visual_retrieval.write_json(visual_index.VISUAL_DIR / "decision-candidates.json",
                                    {"candidates": chosen, "stage_a_ms": stage_a, "queries_ms": queries})
        print(json.dumps({"candidates": chosen, "stage_a_e2e_ms": {k: round(visual_rules.e2e_ms(rules, v, *queries), 1)
                                                                    for k, v in stage_a.items()}}))
        return
    options = _pairs(args[1:], {"golden": "", "manual": ""}) if args and not args[0].startswith("--") else None
    if options is None or not options["golden"]:
        raise SystemExit("visual-decide: use --stage retrieval, --stage candidates <stage-A report> or "
                         "<report.json> golden=<variant-golden.json>")
    from eval import visual_manual, visual_text_loss

    report = json.loads(Path(args[0]).read_text())
    queries = visual_rules.phone_queries(report)
    final = visual_rules.choose_final(answers, visual_rules.phone_stage(report, "B"), queries, rules)
    golden = visual_rules.golden_from(json.loads(Path(options["golden"]).read_text()))
    decisive = None
    if final is not None:
        rows = visual_manual.load()
        texto, chosen = visual_manual.figures(rows, "texto"), visual_manual.figures(rows, final["variant"])
        loss = _read_json(visual_text_loss.RUNS / final["variant"] / "result.json")
        if texto is None or chosen is None or loss is None or loss["mix_answer_correct"] is None:
            raise SystemExit(f"the decisive review of texto and {final['variant']} and its text loss must be judged first "
                             f"(visual-manual --decisive variant={final['variant']}, visual-text-loss variant={final['variant']})")
        decisive = {"visual": {"texto": texto["visual_correct"], "ambos": chosen["visual_correct"]},
                    "f05": {"texto": 100 * loss["v4_answer_correct"], "ambos": 100 * loss["mix_answer_correct"]}}
    result = visual_rules.decide(rules, final, decisive, golden)
    result.update({"final": final, "decisive": decisive, "queries_ms": queries, "retrieval": _read_json(
        visual_index.VISUAL_DIR / "decision-retrieval.json"), "rules_git_hash": visual_rules.git_hash(visual_rules.RULES)})
    visual_retrieval.write_json(visual_index.VISUAL_DIR / "decision-final.json", result)
    print(json.dumps(result, ensure_ascii=False))


def _read_json(path):
    return json.loads(Path(path).read_text()) if Path(path).exists() else None


def _answer_runs():
    """metrics.json of each answer run that exists, in the default order."""
    from eval import visual_index

    runs = visual_index.VISUAL_DIR / "runs"
    return {key: json.loads((runs / f"answers-{key}" / "metrics.json").read_text())
            for key in default_variants() if (runs / f"answers-{key}" / "metrics.json").exists()}


# Answer variants (research R11). The candidates of the rule are mixes, so the mix runs the whole grid; «Páginas» only
# informs of the images' value and runs 1–3 pages at 280 and one page in tiles.
def default_variants():
    from eval.vision_runtime import GEMMA_BUDGETS

    keys = ["texto"] + [f"paginas-p{p}-b280-t0" for p in (1, 2, 3)] + ["paginas-p1-b280-t1"]
    keys += [f"mezcla-p{p}-b{b}-t0" for p in (1, 2, 3) for b in GEMMA_BUDGETS] + [f"mezcla-p{p}-b280-t1" for p in (1, 2, 3)]
    return keys


def _parse_variant(key):
    if key == "texto":
        return "texto", 0, None, False
    family, pages, budget, tiles = key.split("-")
    return family, int(pages[1:]), int(budget[1:]), tiles == "t1"


class _AnswerContext:
    """What every variant needs: the text index, its chunk lists, the page ranking and the page images.

    The answer pages come from the list the rule file names (D-076: the page list; the RRF of three lists buries
    the pages without text); the text blocks keep D-034's chunk retrieval."""

    def __init__(self, decision):
        from eval import builds, corpus, index, visual_index, visual_retrieval, visual_rules
        from eval.retrieval import Retriever
        from eval.selector import ContextSelector

        self.px, self.tokens, self.dim = decision["px"], decision["tokens"], decision["dim"]
        self.pages_mode = visual_rules.load()["answer_pages"]
        source = corpus.CORPORA["visual"]
        self.docs_dir = source.docs
        self.docs = {doc.sha256: doc for doc in corpus.load_manifest(source.manifest)}
        self.embedder = visual_retrieval.QueryEmbedder()
        path = builds.index_path("visual-text")
        self.db = index.connect(path)
        self.retriever = Retriever(self.db, lambda text: self.embedder.truncated(text, 256))
        self.chunk_pages = visual_index.load_chunk_pages()
        self.chunk_doc = visual_retrieval.chunk_documents(path)
        self.pages = visual_index.load_pages(visual_index.pages_path(self.px, self.tokens), self.dim)
        params = json.loads((builds.OFFLINE / "config" / "rag_parameters.json").read_text())
        self.selector = ContextSelector(params["max_blocks"], params["max_per_document"], params["budget_tokens"], params["expand_top"])
        self.fused_limit = params["fused_limit"]
        self.chars_per_token = params["chars_per_token"]
        self._images = {}

    def lists(self, question):
        from eval import visual_retrieval
        from eval.retrieval import rrf

        vector_ids = self.retriever.vector(question, visual_retrieval.CANDIDATES)
        fts_ids = self.retriever.fts(question, visual_retrieval.CANDIDATES)
        chunks = rrf([vector_ids, fts_ids], visual_retrieval.RRF_K)
        vector_pages = visual_retrieval.to_pages(vector_ids, self.chunk_pages, self.chunk_doc)
        fts_pages = visual_retrieval.to_pages(fts_ids, self.chunk_pages, self.chunk_doc)
        page_list = visual_retrieval.rank_pages(self.embedder.truncated(question, self.dim), self.pages)
        return chunks, visual_retrieval.fuse(self.pages_mode, vector_pages, fts_pages, page_list)

    def blocks(self, chunk_ids):
        from eval.selector import ChunkView

        ids = list(chunk_ids)[: self.fused_limit]
        if not ids:
            return []
        marks = ",".join("?" * len(ids))
        rows = self.db.execute(
            "SELECT c.id, c.document_id, c.ordinal, c.heading_path, c.page, c.content, c.token_estimate, d.display_name "
            f"FROM chunks c JOIN documents d ON d.id = c.document_id WHERE c.id IN ({marks})", ids).fetchall()
        views = {row[0]: ChunkView(*row) for row in rows}
        return self.selector.select([views[i] for i in ids if i in views], lambda document_id, ordinal: None)

    def block_pages(self, block):
        rows = self.db.execute("SELECT c.id, d.content_hash FROM chunks c JOIN documents d ON d.id = c.document_id "
                               "WHERE c.document_id = ? AND c.ordinal BETWEEN ? AND ?",
                               (block.document_id, block.first_ordinal, block.last_ordinal)).fetchall()
        return frozenset((sha, page) for chunk_id, sha in rows for page in self.chunk_pages.get(chunk_id, []))

    def page_images(self, key, tiles):
        """JPEG bytes of the page at the decided resolution, or of its two halves rendered at 1.8 × that resolution."""
        from eval import raster

        cache_key = (key, tiles)
        if cache_key not in self._images:
            sha, page = key
            doc = self.docs[sha]
            path = self.docs_dir / doc.file_name
            px = round(self.px * 1.8) if tiles else self.px
            image = raster.render_page(path, page, px) if doc.format == "pdf" else raster.scale_image(path, px)
            parts = raster.tiles(image) if tiles else (image,)
            self._images[cache_key] = tuple(raster.encode(part, "jpeg") for part in parts)
        return self._images[cache_key]

    def label(self, key):
        sha, page = key
        doc = self.docs[sha]
        return f"{doc.title}, página {page}" if doc.format == "pdf" else doc.title


def _sources_and_parts(ctx, question, variant):
    from eval import visual_prompt
    from eval.prompt import build_prompt
    from eval.vision_runtime import Text

    family, count, _, tiles = _parse_variant(variant)
    chunks, ranked_pages = ctx.lists(question)
    chosen = ranked_pages[:count]
    text_sources = []
    if family in ("texto", "mezcla"):
        ranked = visual_prompt.without_pages(chunks, ctx.chunk_pages, ctx.chunk_doc, set(chosen)) if chosen else chunks
        blocks = ctx.blocks(ranked)
        for block in blocks:
            where = " › ".join(x for x in (block.document_name, block.heading_path) if x is not None)
            page = f" (p. {block.page})" if block.page is not None else ""
            text_sources.append(visual_prompt.Source("text", f"{where}{page}", ctx.block_pages(block), text=block.text))
        if family == "texto":
            return text_sources, [Text(build_prompt(question, blocks, None, visual_prompt.TEMPLATE))]
    page_sources = [visual_prompt.Source("page", ctx.label(key), frozenset([key]), images=ctx.page_images(key, tiles)) for key in chosen]
    sources = text_sources + page_sources
    return sources, visual_prompt.build_parts(question, sources)


def visual_generate(args):
    """Answer ablations with Gemma 4 E2B (research R11), resumable per variant; the automatic judge per variant."""
    from eval import models, vision_runtime, visual_index, visual_judge, visual_prompt, visual_retrieval, visual_rules, visual_testset

    options = _pairs(args, {"variants": ",".join(default_variants()), "ids": ""})
    stamp = visual_rules.require_frozen(visual_rules.RULES, visual_testset.TESTSET)
    decision = json.loads((visual_index.VISUAL_DIR / "decision-retrieval.json").read_text())
    questions = [q for q in _questions() if q.review == "ok"]
    if options["ids"]:
        wanted = set(options["ids"].split(","))
        questions = [q for q in questions if q.id in wanted]
    ctx = _AnswerContext(decision)
    engine = vision_runtime.open_gemma(models.verify("GEMMA4_E2B"), max_num_images=6)
    try:
        for variant in options["variants"].split(","):
            _, _, budget, _ = _parse_variant(variant)
            folder = visual_index.VISUAL_DIR / "runs" / f"answers-{variant}"
            folder.mkdir(parents=True, exist_ok=True)
            out_path = folder / "answers.jsonl"
            done = {json.loads(line)["id"] for line in out_path.read_text().splitlines()} if out_path.exists() else set()
            with out_path.open("a", encoding="utf-8") as out:
                for q in questions:
                    if q.id in done:
                        continue
                    sources, parts = _sources_and_parts(ctx, q.question, variant)
                    result = vision_runtime.generate(engine, parts, budget, max_output_tokens=512)
                    record = {"id": q.id, "type": q.type, **result,
                              "tokens": visual_prompt.tokens(parts, budget or 0, ctx.chars_per_token),
                              "sources": [{"kind": s.kind, "label": s.label, "pages": sorted(map(list, s.pages))} for s in sources]}
                    out.write(json.dumps(record, ensure_ascii=False) + "\n")
                    out.flush()
            _judge_variant(folder, questions, stamp, decision)
            print(json.dumps({"variant": variant, **json.loads((folder / "metrics.json").read_text())["summary"].get("visual", {})}))
    finally:
        engine.close()
        ctx.embedder.save()


def _judge_variant(folder, questions, stamp, decision):
    from eval import visual_judge, visual_prompt, visual_retrieval

    by_id = {q.id: q for q in questions}
    rows = [json.loads(line) for line in (folder / "answers.jsonl").read_text().splitlines() if line.strip()]
    rows = [row for row in rows if row["id"] in by_id]
    types, judgements = [], []
    for row in rows:
        q = by_id[row["id"]]
        sources = [visual_prompt.Source(s["kind"], s["label"], frozenset(tuple(p) for p in s["pages"])) for s in row["sources"]]
        judgements.append(visual_judge.judge(q.type, row["answer"], q.key_facts, sources, {tuple(p) for p in q.pages}))
        types.append(q.type)
    complete = len(rows) == len(questions)
    visual_retrieval.write_json(folder / "metrics.json", {
        "stamp": stamp, "decision": decision, "complete": complete, "answers": len(rows),
        "summary": visual_judge.summarize(types, judgements),
        "ttft_p50_s": sorted(r["ttft_s"] for r in rows)[len(rows) // 2] if rows else None,
        "judgements": {row["id"]: {"correct": j.correct, "refusal": j.refusal, "citations_ok": j.citations_ok,
                                   "citations_total": j.citations_total} for row, j in zip(rows, judgements)},
    })


# The device set (research R13, data-model §1.6): fixed choices, so the export is reproducible.
PARITY_PAGES = [("ine-ipc-2026-09", 1), ("ine-epa-2026t3", 1), ("aeat-recaudacion-2026-09", 3), ("boe-fiestas-2026", 2),
                ("ine-espana-en-cifras-2026", 5), ("gazeta-1931-12-10", 1), ("gazeta-1900-04-01", 2), ("gazeta-1900-12-07", 1),
                ("gazeta-1797-03-07", 1), ("bne-constitucion-1931", 5)]
PARITY_IMAGES = ["ign-koppen", "piramide-1900", "horario-autobus", "tabla-periodica", "panel-letur",
                 "foto-ine-epa-2026t3-p1", "foto-ine-ipc-2026-09-p1", "foto-boe-fiestas-2026-p2", "foto-ine-ecp-2026t2-p2",
                 "foto-aeat-recaudacion-2026-09-p4"]
RASTER_PDFS = {"text": ("ine-ipc-2026-09", [1, 2, 3, 4, 5]), "scanned": ("gazeta-1931-12-10", [1, 2, 3, 4, 5]),
               "landscape": ("boe-fiestas-2026", [])}
# AEMET's original carries an owner password (research R8); the corpus copy does not, so the phone gets the original.
RESTRICTED_URL = ("https://www.aemet.es/documentos/es/serviciosclimaticos/vigilancia_clima/resumenes_climat/mensuales/2026/"
                  "res_mens_clim_2026_07.pdf")
DECODE_IMAGES = ["panel-letur", "cartel-precios-mercado"]
DECODE_FIXTURES = ["alpha.png", "exif_rotated.jpg", "cmyk.jpg"]
READING_PER_TYPE = 3
CONTEXT_TYPES = {"tabla": 5, "grafico": 4, "escaneado": 4, "imagen": 4, "control": 3}


def _spread(questions, per_type):
    """The first questions of each type, by id, as many as [per_type] asks; reproducible."""
    chosen = []
    for type_, count in per_type.items():
        chosen += sorted((q for q in questions if q.type == type_), key=lambda q: q.id)[:count]
    return chosen


def visual_device_set(args):
    """Exports build/visual/device-set for the phone (research R13): images with the desktop's vectors, PDFs, reading
    pages, the 20 contexts of stage B and the decode images; and the desktop's reading answers, for the parity."""
    import shutil

    from eval import builds, corpus, models, raster, vision_runtime, visual_index, visual_parity, visual_prompt, visual_rules, visual_testset

    del args
    visual_rules.require_frozen(visual_rules.RULES, visual_testset.TESTSET)
    decision = json.loads((visual_index.VISUAL_DIR / "decision-retrieval.json").read_text())
    px, tokens = decision["px"], decision["tokens"]
    source = corpus.CORPORA["visual"]
    docs = {doc.id: doc for doc in corpus.load_manifest(source.manifest)}
    root = visual_index.VISUAL_DIR / "device-set"
    shutil.rmtree(root, ignore_errors=True)
    writer = visual_parity.DeviceSetWriter(root)

    images = {}
    for doc_id, page in PARITY_PAGES:
        data = raster.encode(raster.render_page(source.docs / docs[doc_id].file_name, page, px), "jpeg")
        images[writer.add(f"images/page-{doc_id}-p{page}.jpg", data, "image")["sha256"]] = data
    for doc_id in PARITY_IMAGES:
        data = raster.encode(raster.scale_image(source.docs / docs[doc_id].file_name, px), "jpeg")
        images[writer.add(f"images/{doc_id}.jpg", data, "image")["sha256"]] = data
    rendered = {}
    for role, (doc_id, pages) in RASTER_PDFS.items():
        path = source.docs / docs[doc_id].file_name
        name = f"pdfs/{doc_id}.pdf"
        writer.add(name, path.read_bytes(), "pdf", role=role, pages=pages)
        for page in pages:
            rendered[f"pdf:{name}:{page}:{px}"] = raster.encode(raster.render_page(path, page, px), "jpeg")
    writer.add("pdfs/aemet-original-2026-07.pdf", corpus.http_get(RESTRICTED_URL), "pdf", role="restricted")
    for doc_id in DECODE_IMAGES:
        writer.add(f"decode/{docs[doc_id].file_name}", (source.docs / docs[doc_id].file_name).read_bytes(), "decode")
    fixtures = builds.OFFLINE.parent / "app/src/androidTest/assets/image"
    for name in DECODE_FIXTURES:
        writer.add(f"decode/{name}", (fixtures / name).read_bytes(), "decode")

    vectors = {"images": {}, "pages": {}}
    for count in vision_runtime.EMBEDDER_TOKENS:
        engine = vision_runtime.open_image_embedder(models.verify(models.VISUAL_EMBEDDING), count, cache_dir=builds.BUILD / "litert-cache")
        try:
            for sha, data in images.items():
                vectors["images"].setdefault(sha, {})[str(count)] = vision_runtime.embed_image(engine, data, count)
            for key, data in rendered.items():
                vectors["pages"].setdefault(key, {})[str(count)] = vision_runtime.embed_image(engine, data, count)
        finally:
            engine.close()
    writer.add("vectors.json", json.dumps(vectors).encode(), "vectors")

    questions = [q for q in _questions() if q.review == "ok"]
    ctx = _AnswerContext(decision)
    reading, reading_items = [], []
    for q in _spread(questions, {t: READING_PER_TYPE for t in ("tabla", "grafico", "escaneado", "imagen")}):
        key = tuple(q.pages[0])
        page = visual_prompt.Source("page", ctx.label(key), frozenset([key]), images=ctx.page_images(key, False))
        parts = visual_prompt.build_parts(q.question, [page])
        reading.append({"id": q.id, "parts": writer.parts(parts)})
        reading_items.append((q, parts))
    writer.add("reading.json", json.dumps(reading, ensure_ascii=False).encode(), "reading")
    layouts = ["texto"] + [f"{family}-p{n}-t{t}" for family in ("paginas", "mezcla") for n in (1, 2, 3) for t in (0, 1)]
    contexts = []
    for q in _spread(questions, CONTEXT_TYPES):
        entry = {"id": q.id, "layouts": {}}
        for layout in layouts:
            variant = layout if layout == "texto" else layout.replace("-t", "-b280-t")
            _, parts = _sources_and_parts(ctx, q.question, variant)
            entry["layouts"][layout] = writer.parts(parts)
        contexts.append(entry)
    writer.add("contexts.json", json.dumps(contexts, ensure_ascii=False).encode(), "contexts")
    manifest = writer.finish(parity_px=px, tokens=tokens)
    ctx.embedder.save()

    engine = vision_runtime.open_gemma(models.verify("GEMMA4_E2B"), max_num_images=6)
    try:
        desktop = {q.id: vision_runtime.generate(engine, parts, vision_runtime.GEMMA_BUDGETS[-1], max_output_tokens=64)["answer"]
                   for q, parts in reading_items}
    finally:
        engine.close()
    visual_retrieval_write(visual_index.VISUAL_DIR / "device-set-desktop.json",
                           {"reading": [{"id": q.id, "type": q.type, "key_facts": q.key_facts} for q, _ in reading_items],
                            "answers": desktop})
    print(json.dumps({"files": len(manifest["files"]), "images": len(images), "reading": len(reading), "contexts": len(contexts),
                      "px": px, "tokens": tokens}))


def visual_retrieval_write(path, data):
    from eval import visual_retrieval

    visual_retrieval.write_json(path, data)


def visual_parity(args):
    """Phone ↔ desktop: cosines of the same bytes and of pages rendered on each side, and the reading answers (R13)."""
    from eval import visual_index, visual_parity as parity

    if not args:
        raise SystemExit("usage: visual-parity <phone report .json>")
    phone = json.loads(Path(args[0]).read_text())
    vectors = json.loads((visual_index.VISUAL_DIR / "device-set" / "vectors.json").read_text())
    desktop = json.loads((visual_index.VISUAL_DIR / "device-set-desktop.json").read_text())
    result = {"vectors": parity.compare(vectors, phone["scenarios"]),
              "reading": parity.compare_reading(desktop["reading"], phone["scenarios"], desktop["answers"])}
    visual_retrieval_write(visual_index.VISUAL_DIR / "parity.json", result)
    print(json.dumps({"same_bytes": result["vectors"]["same_bytes"], "rendered_pages": result["vectors"]["rendered_pages"],
                      "reading": {k: v for k, v in result["reading"].items() if k != "rows"}}, ensure_ascii=False))


def visual_manual(args):
    """The judge check (30 per main variant) or, with --decisive variant=<key>, the decisive review (D-075)."""
    from eval import visual_index, visual_manual as manual, visual_rules

    decisive = "--decisive" in args
    options = _pairs([a for a in args if a != "--decisive"], {"variant": ""})
    rules = visual_rules.load()
    by_id = {q.id: q for q in _questions() if q.review == "ok"}
    runs = visual_index.VISUAL_DIR / "runs"
    available = _answer_runs()
    if decisive:
        if not options["variant"]:
            raise SystemExit("visual-manual --decisive needs variant=<the chosen configuration>")
        kind, variants, ids = manual.DECISIVE, ["texto", options["variant"]], sorted(by_id)
    else:
        kind, variants = manual.CHECK, manual.main_variants(available)
        ids = manual.sample_ids(by_id, rules["judge_check_sample"], rules["seeds"]["sample"])
    rows = []
    for variant in variants:
        metrics = available.get(variant)
        if metrics is None or not metrics["complete"]:
            raise SystemExit(f"{variant}: no complete run; run visual-generate first")
        folder = runs / f"answers-{variant}"
        answers = {json.loads(line)["id"]: json.loads(line) for line in (folder / "answers.jsonl").read_text().splitlines() if line.strip()}
        rows += [manual.row(kind, variant, by_id[qid], answers[qid]["answer"], metrics["judgements"][qid], answers[qid]["sources"])
                 for qid in ids]
    out = manual.export(rows)
    print(json.dumps({"sample": kind, "variants": variants, "rows": len(rows),
                      "pending": sum(1 for r in out if r["muestra"] == kind and not r["manual"]),
                      "agreement": manual.agreement(out)}, ensure_ascii=False))


def visual_text_loss(args):
    """The chosen mix on F05's test set: only the answers that change go to reports/visual-text-loss.csv (D-075)."""
    from eval import visual_index, visual_rules, visual_testset, visual_text_loss as text_loss

    options = _pairs(args, {"variant": ""})
    if not options["variant"]:
        raise SystemExit("visual-text-loss needs variant=<the chosen configuration>")
    visual_rules.require_frozen(visual_rules.RULES, visual_testset.TESTSET)
    decision = json.loads((visual_index.VISUAL_DIR / "decision-retrieval.json").read_text())
    print(json.dumps(text_loss.run(options["variant"], decision, visual_rules.load()), ensure_ascii=False))


def visual_report(args):
    """Writes reports/visual-eval-report.md from the files of build/visual (contracts/visual-harness.md §5)."""
    from eval import visual_report as report

    del args
    report.REPORT.write_text(report.build(default_variants()), encoding="utf-8")
    print(f"wrote {report.REPORT}")


HANDLERS = {
    "visual-raster": visual_raster,
    "visual-build": visual_build,
    "visual-validate": visual_validate,
    "visual-review-sheet": visual_review_sheet,
    "visual-review-apply": visual_review_apply,
    "visual-freeze": visual_freeze,
    "visual-retrieve": visual_retrieve,
    "visual-decide": visual_decide,
    "visual-generate": visual_generate,
    "visual-device-set": visual_device_set,
    "visual-parity": visual_parity,
    "visual-manual": visual_manual,
    "visual-text-loss": visual_text_loss,
    "visual-report": visual_report,
}

