-- Minimal DB init for M2 (dept + dicts + file meta)
-- MySQL 8.x, charset utf8mb4

CREATE DATABASE IF NOT EXISTS awards DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE awards;

-- ---------------- sys_dept ----------------
CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_code VARCHAR(64) NOT NULL UNIQUE,
  dept_name VARCHAR(128) NOT NULL,
  parent_id BIGINT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0
) COMMENT='院系组织表';

-- ---------------- dict_class ----------------
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

-- ---------------- dict_competition_category ----------------
CREATE TABLE IF NOT EXISTS dict_competition_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_name VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL
) COMMENT='竞赛类别字典';

-- ---------------- dict_organizer ----------------
CREATE TABLE IF NOT EXISTS dict_organizer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  organizer_name VARCHAR(255) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL
) COMMENT='主办单位字典';

-- ---------------- dict_award_scope ----------------
CREATE TABLE IF NOT EXISTS dict_award_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scope_name VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0
) COMMENT='奖项范围，如国家级/省部级';

-- ---------------- dict_award_level ----------------
CREATE TABLE IF NOT EXISTS dict_award_level (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  award_scope_id BIGINT NOT NULL,
  award_level_name VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_scope_level_name (award_scope_id, award_level_name)
) COMMENT='奖项等级字典';

-- ---------------- dict_competition ----------------
CREATE TABLE IF NOT EXISTS dict_competition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  competition_name VARCHAR(255) NOT NULL,
  competition_short_name VARCHAR(128) NULL,
  category_id BIGINT NOT NULL,
  default_level VARCHAR(64) NULL,
  organizer_id BIGINT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  INDEX idx_comp_category (category_id),
  INDEX idx_comp_enabled (enabled)
) COMMENT='竞赛字典';

-- ---------------- sys_file (local file metadata) ----------------
CREATE TABLE IF NOT EXISTS sys_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(32) NULL,
  mime_type VARCHAR(128) NULL,
  storage_path VARCHAR(500) NOT NULL,
  preview_type VARCHAR(32) NOT NULL COMMENT 'image/pdf/download_only',
  file_size BIGINT NOT NULL DEFAULT 0,
  uploader_user_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_sys_file_deleted (deleted)
) COMMENT='文件元数据（M2临时表，后续可合并到业务附件表）';

-- seed dept
INSERT INTO sys_dept (dept_code, dept_name, parent_id, enabled, sort_no)
VALUES ('CS', '计算机学院', NULL, 1, 1)
ON DUPLICATE KEY UPDATE dept_name=VALUES(dept_name);

