-- Day 3 entity extraction tables for Oracle
-- This script creates:
-- 1. RAG_ENTITIES
-- 2. RAG_DOCUMENT_ENTITY_LINKS
-- 3. related sequences and indexes
--
-- Notes:
-- - No existing tables are dropped.
-- - No MySQL AUTO_INCREMENT syntax is used.
-- - Foreign keys are intentionally omitted for compatibility with the current schema state.

------------------------------------------------------------
-- 1. Entity master table
------------------------------------------------------------
CREATE TABLE RAG_ENTITIES (
    ID NUMBER PRIMARY KEY,
    ENTITY_NAME VARCHAR2(255) NOT NULL,
    ENTITY_TYPE VARCHAR2(50) NOT NULL,
    NORMALIZED_NAME VARCHAR2(255) NOT NULL,
    CREATED_AT DATE DEFAULT SYSDATE
);

-- ENTITY_TYPE + NORMALIZED_NAME should be unique
ALTER TABLE RAG_ENTITIES
    ADD CONSTRAINT UK_RAG_ENTITIES_TYPE_NORM
    UNIQUE (ENTITY_TYPE, NORMALIZED_NAME);

-- Sequence for RAG_ENTITIES.ID
CREATE SEQUENCE RAG_ENTITIES_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

------------------------------------------------------------
-- 2. Document-chunk to entity link table
------------------------------------------------------------
CREATE TABLE RAG_DOCUMENT_ENTITY_LINKS (
    ID NUMBER PRIMARY KEY,
    DOCUMENT_ID NUMBER NOT NULL,
    CHUNK_ID NUMBER,
    ENTITY_ID NUMBER NOT NULL,
    SOURCE_TEXT VARCHAR2(1000),
    CREATED_AT DATE DEFAULT SYSDATE
);

-- Sequence for RAG_DOCUMENT_ENTITY_LINKS.ID
CREATE SEQUENCE RAG_DOCUMENT_ENTITY_LINKS_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

------------------------------------------------------------
-- 3. Query indexes
------------------------------------------------------------
CREATE INDEX IDX_RAG_DOC_ENTITY_LINKS_DOC_ID
    ON RAG_DOCUMENT_ENTITY_LINKS (DOCUMENT_ID);

CREATE INDEX IDX_RAG_DOC_ENTITY_LINKS_CHUNK_ID
    ON RAG_DOCUMENT_ENTITY_LINKS (CHUNK_ID);

CREATE INDEX IDX_RAG_DOC_ENTITY_LINKS_ENTITY_ID
    ON RAG_DOCUMENT_ENTITY_LINKS (ENTITY_ID);
