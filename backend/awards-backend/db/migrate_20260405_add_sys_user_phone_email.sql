-- Add phone / email to sys_user (contact info for all user types)
USE awards;

SET @db := DATABASE();

SET @has_phone := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone'
);
SET @sql := IF(@has_phone = 0,
  'ALTER TABLE sys_user ADD COLUMN phone VARCHAR(32) NULL COMMENT ''手机号''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_email := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email'
);
SET @sql := IF(@has_email = 0,
  'ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) NULL COMMENT ''邮箱''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
