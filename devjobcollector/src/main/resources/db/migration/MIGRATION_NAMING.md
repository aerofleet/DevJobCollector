# Flyway Naming Convention

- Format: V<version>__withbuddy_<description>.sql
- Example:
  - V1__withbuddy_init_schema.sql
  - V2__withbuddy_add_task_memo.sql
  - V3__withbuddy_add_indexes.sql

Notes:
- Keep version strictly increasing per database.
- Use lowercase with underscores for description.
- Place files under: src/main/resources/db/migration