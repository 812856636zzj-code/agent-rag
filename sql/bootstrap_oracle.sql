-- Run from the sql/ directory as the application user in an EMPTY schema:
--   sqlplus -L user@//localhost:1521/FREEPDB1 @bootstrap_oracle.sql
-- No DROP statements; fail immediately if any object already exists.
WHENEVER SQLERROR EXIT SQL.SQLCODE
@create_base_tables.sql
@day3_entity_tables.sql
@day5_entity_relations.sql
@create_rag_agent_memories.sql
@create_eval_history_tables.sql
@seed_fixed_eval.sql
COMMIT;
EXIT SUCCESS
