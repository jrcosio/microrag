"""Subcommands of the F18 visual evaluation (contracts/visual-harness.md §3), registered in run_eval.py."""

import json


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
                 "visual_recall@5": r["summary"]["visual"]["recall@5"]} for r in ablations["results"] if r["mode"] == "ambos"]
        chosen = visual_rules.choose_retrieval(rows, rules)
        out = visual_index.VISUAL_DIR / "decision-retrieval.json"
        visual_retrieval.write_json(out, {"stamp": ablations["stamp"], **chosen})
        print(json.dumps(chosen))
        return
    raise SystemExit("visual-decide: use --stage retrieval (the other steps come with the phone report)")


HANDLERS = {
    "visual-raster": visual_raster,
    "visual-build": visual_build,
    "visual-validate": visual_validate,
    "visual-review-sheet": visual_review_sheet,
    "visual-review-apply": visual_review_apply,
    "visual-freeze": visual_freeze,
    "visual-retrieve": visual_retrieve,
    "visual-decide": visual_decide,
}

