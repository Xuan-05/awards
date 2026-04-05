-- Remove legacy "code" columns from four dictionary tables.
-- Run on existing DBs after backup.
-- If dict_award_level has duplicate (award_scope_id, award_level_name), fix data before running.

USE awards;

ALTER TABLE dict_competition_category DROP COLUMN category_code;

ALTER TABLE dict_award_scope DROP COLUMN scope_code;

ALTER TABLE dict_award_level DROP INDEX uk_scope_level;
ALTER TABLE dict_award_level ADD UNIQUE KEY uk_scope_level_name (award_scope_id, award_level_name);
ALTER TABLE dict_award_level DROP COLUMN award_level_code;

ALTER TABLE dict_competition DROP COLUMN competition_code;
