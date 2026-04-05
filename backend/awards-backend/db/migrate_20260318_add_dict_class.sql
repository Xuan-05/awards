-- Add dict_class table for student class dictionary
-- MySQL 8.x, charset utf8mb4
USE awards;

CREATE TABLE IF NOT EXISTS dict_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id BIGINT NOT NULL,
  class_name VARCHAR(128) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_dept_class (dept_id, class_name),
  INDEX idx_class_dept (dept_id),
  INDEX idx_class_enabled (enabled)
) COMMENT='班级字典（按院系归属）';

