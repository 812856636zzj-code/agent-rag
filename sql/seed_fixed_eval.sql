-- Small first-party fixture for repeatable local fixed-eval smoke tests.
-- The document is application knowledge, not a TEST_SPEC or expected answer.
INSERT INTO RAG_DOCUMENTS
    (ID, FILE_NAME, FILE_TYPE, UPLOAD_TIME, STATUS, DOCUMENT_TYPE, CONTENT)
VALUES
    (1001, 'rag-eval-fixture.md', 'text/markdown', SYSTIMESTAMP,
     'READY', 'KNOWLEDGE',
     'RAG_DOCUMENT_CHUNKS stores document chunks. Its columns include DOCUMENT_ID, CHUNK_INDEX, CONTENT, TOKEN_COUNT, CREATE_TIME, and EMBEDDING_STATUS.');

INSERT INTO RAG_DOCUMENT_CHUNKS
    (ID, DOCUMENT_ID, CHUNK_INDEX, CONTENT, TOKEN_COUNT, EMBEDDING_STATUS, CREATE_TIME)
VALUES
    (2001, 1001, 0,
     'RAG_DOCUMENT_CHUNKS stores document chunks. Its columns include DOCUMENT_ID, CHUNK_INDEX, CONTENT, TOKEN_COUNT, CREATE_TIME, and EMBEDDING_STATUS.',
     20, 'READY', SYSTIMESTAMP);
