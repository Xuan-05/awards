-- Make dict_competition.competition_code nullable (optional)
-- MySQL allows multiple NULLs in a UNIQUE column.
USE awards;

ALTER TABLE dict_competition
  MODIFY COLUMN competition_code VARCHAR(64) NULL;

