-- Add student/teacher extra fields to sys_user
-- - student_no: nullable, unique when not null
-- - teacher_no: nullable, unique when not null
-- - class_id: nullable (student only)
USE awards;

-- 为兼容不同 MySQL 8.x 小版本，这里使用 information_schema 做条件变更
SET @db := DATABASE();

-- add column student_no
SET @has_student_no := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'student_no'
);
SET @sql := IF(@has_student_no = 0,
  'ALTER TABLE sys_user ADD COLUMN student_no VARCHAR(64) NULL COMMENT ''学生学号（可空；非空唯一）''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- add column teacher_no
SET @has_teacher_no := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'teacher_no'
);
SET @sql := IF(@has_teacher_no = 0,
  'ALTER TABLE sys_user ADD COLUMN teacher_no VARCHAR(64) NULL COMMENT ''教师工号（可空；非空唯一）''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- add column class_id
SET @has_class_id := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'class_id'
);
SET @sql := IF(@has_class_id = 0,
  'ALTER TABLE sys_user ADD COLUMN class_id BIGINT NULL COMMENT ''班级ID（仅学生使用；可空）''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Unique indexes: MySQL allows multiple NULLs in UNIQUE index
SET @has_uk_student := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_student_no'
);
SET @sql := IF(@has_uk_student = 0,
  'CREATE UNIQUE INDEX uk_sys_user_student_no ON sys_user (student_no)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk_teacher := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_teacher_no'
);
SET @sql := IF(@has_uk_teacher = 0,
  'CREATE UNIQUE INDEX uk_sys_user_teacher_no ON sys_user (teacher_no)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

