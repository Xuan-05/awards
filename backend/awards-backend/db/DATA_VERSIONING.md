# Database Data Versioning Guide

This project uses a file-based strategy to version database-related changes in Git.

## 1) What should be committed

- Schema and data migration scripts in `db/migration/` (replayable changes).
- Seed scripts intended for all environments (for example `db/seed_students.sql`).
- Curated baseline snapshots when explicitly needed for demo/training.

## 2) What should not be committed

- Runtime business files under `data/files/` (uploaded attachments).
- Environment-specific local dumps.
- Any dataset containing secrets or sensitive personal data without masking.

## 3) Recommended workflow

1. For every structural/data fix, create a migration SQL file in `db/migration/`.
2. Use naming convention:
   - `VYYYYMMDD__short_description.sql`
3. Keep migration scripts idempotent whenever possible.
4. If business data must be archived in Git, export only required tables and mask sensitive fields first.

## 4) Local snapshot export

Use `db/scripts/export_snapshot.ps1` to export selected tables into
`db/snapshots/local/` for local debugging.

Example:

```powershell
powershell -ExecutionPolicy Bypass -File .\db\scripts\export_snapshot.ps1 `
  -Host "127.0.0.1" -Port 3306 -Database "awards" -User "root" -Password "123456"
```

The output directory is ignored by Git by default.

## 5) Team rule

- Do not commit runtime-generated files.
- Prefer migration scripts over raw data dumps.
- If a dump must be committed, include a note in PR about source, masking, and purpose.

## 6) Quick start for your use case

If you create data from frontend pages and want teammates to sync via Git:

1. Create a migration SQL under `db/migration/`.
2. Commit and push that migration file.
3. Teammates pull and execute the migration.

Detailed steps: see `db/TEAM_DATA_SYNC_WORKFLOW.md`.
