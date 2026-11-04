-- chat.db, schema version 2: F11 (specs/011-chat-sources/data-model.md §3) and the scope of F17 (research R7).
-- Every statement is IF NOT EXISTS, so it runs on each open; each version adds a ChatMigration and keeps this
-- file as the latest schema.

CREATE TABLE IF NOT EXISTS chat_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversations (
  id          INTEGER PRIMARY KEY,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS conversations_updated ON conversations(updated_at DESC);

CREATE TABLE IF NOT EXISTS turns (
  id               INTEGER PRIMARY KEY,
  conversation_id  INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  ordinal          INTEGER NOT NULL,
  question         TEXT NOT NULL,
  answer           TEXT NOT NULL DEFAULT '',
  state            TEXT NOT NULL CHECK (state IN ('retrieving','generating','completed','cancelled','failed','interrupted')),
  failure          TEXT CHECK (failure IN ('index','generation')),
  refusal          INTEGER NOT NULL DEFAULT 0,
  partial_coverage INTEGER NOT NULL DEFAULT 0,
  prompt_blocks    INTEGER NOT NULL DEFAULT 0,
  created_at       INTEGER NOT NULL,
  finished_at      INTEGER,
  UNIQUE (conversation_id, ordinal)
);

-- No foreign key to the documents: they live in rag.db, and a source outlives its document (FR-027).
CREATE TABLE IF NOT EXISTS sources (
  turn_id        INTEGER NOT NULL REFERENCES turns(id) ON DELETE CASCADE,
  position       INTEGER NOT NULL,
  prompt_number  INTEGER NOT NULL,
  document_id    INTEGER NOT NULL,
  chunk_id       INTEGER NOT NULL,
  first_ordinal  INTEGER NOT NULL,
  last_ordinal   INTEGER NOT NULL,
  document_name  TEXT NOT NULL,
  page           INTEGER,
  PRIMARY KEY (turn_id, position)
);

-- F17: the documents each conversation asks; no rows is the whole library. No foreign keys to rag.db either:
-- folders and documents are kept with their created_at_ms, so a reused id never matches (D-065).
CREATE TABLE IF NOT EXISTS conversation_scope (
  conversation_id    INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  kind               TEXT NOT NULL CHECK (kind IN ('folder','general','document')),
  ref_id             INTEGER NOT NULL,
  ref_created_at_ms  INTEGER,
  PRIMARY KEY (conversation_id, kind, ref_id)
);

-- F17 (FR-020b): the scope «Nuevo chat» starts with; no rows is the whole library.
CREATE TABLE IF NOT EXISTS last_scope (
  kind               TEXT NOT NULL CHECK (kind IN ('folder','general','document')),
  ref_id             INTEGER NOT NULL,
  ref_created_at_ms  INTEGER,
  PRIMARY KEY (kind, ref_id)
);
