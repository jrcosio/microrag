-- MicroRAG index schema, version 2 (rag_meta.schema_version).
-- Shared by the app and the offline Python tools. It is the RAG report schema (§Esquema SQLite)
-- plus the document columns and states of INVENTARIO G27 (F04, D-030) and skipped_pages (F06, D-038).
-- Always the latest version, with IF NOT EXISTS; older files are upgraded by RagMigrations.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS rag_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id              INTEGER PRIMARY KEY,
    source          TEXT NOT NULL CHECK (source IN ('base','user')),
    uri             TEXT,
    display_name    TEXT NOT NULL,
    mime_type       TEXT,
    content_hash    TEXT NOT NULL UNIQUE,
    status          TEXT NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','parsed','embedding','done','error','paused','cancelled')),
    chunk_count     INTEGER,
    error           TEXT,
    included        INTEGER NOT NULL DEFAULT 1 CHECK (included IN (0,1)),
    size_bytes      INTEGER,
    page_count      INTEGER,
    local_path      TEXT,
    skipped_pages   INTEGER,
    created_at_ms   INTEGER NOT NULL,
    updated_at_ms   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chunks (
    id              INTEGER PRIMARY KEY,
    document_id     INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    ordinal         INTEGER NOT NULL,
    heading_path    TEXT,
    page            INTEGER,
    content         TEXT NOT NULL,
    title_norm      TEXT NOT NULL,
    body_norm       TEXT NOT NULL,
    token_estimate  INTEGER NOT NULL,
    embedding       BLOB NOT NULL,
    UNIQUE (document_id, ordinal)
);

CREATE INDEX IF NOT EXISTS idx_chunks_doc ON chunks(document_id, ordinal);

CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
    title_norm,
    body_norm,
    content = 'chunks',
    content_rowid = 'id',
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
    INSERT INTO chunks_fts(rowid, title_norm, body_norm)
    VALUES (new.id, new.title_norm, new.body_norm);
END;

CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
    INSERT INTO chunks_fts(chunks_fts, rowid, title_norm, body_norm)
    VALUES ('delete', old.id, old.title_norm, old.body_norm);
END;

CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE OF title_norm, body_norm ON chunks BEGIN
    INSERT INTO chunks_fts(chunks_fts, rowid, title_norm, body_norm)
    VALUES ('delete', old.id, old.title_norm, old.body_norm);
    INSERT INTO chunks_fts(rowid, title_norm, body_norm)
    VALUES (new.id, new.title_norm, new.body_norm);
END;
