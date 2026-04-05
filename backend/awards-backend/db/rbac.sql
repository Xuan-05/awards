USE awards;

-- ---------------- sys_user ----------------
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  user_type VARCHAR(16) NOT NULL COMMENT 'STUDENT/TEACHER/ADMIN',
  dept_id BIGINT NOT NULL,
  student_no VARCHAR(64) NULL UNIQUE COMMENT '学生学号（可空；非空唯一）',
  teacher_no VARCHAR(64) NULL UNIQUE COMMENT '教师工号（可空；非空唯一）',
  class_id BIGINT NULL COMMENT '班级ID（仅学生使用；可空）',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='本地用户表（后续可被外部同步覆盖基础字段）';

-- ---------------- sys_role ----------------
CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(32) NOT NULL UNIQUE,
  role_name VARCHAR(64) NOT NULL
) COMMENT='角色表';

-- ---------------- sys_user_role ----------------
CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id),
  INDEX idx_ur_user (user_id),
  INDEX idx_ur_role (role_id)
) COMMENT='用户-角色关联';

-- seed roles (Note: CAPTAIN role removed - team captain is determined by biz_team.captain_id, not a global role)
INSERT INTO sys_role (role_code, role_name) VALUES
('TEACHER', '指导教师'),
('DEPT_ADMIN', '院系管理员'),
('SCHOOL_ADMIN', '校级管理员'),
('SYS_ADMIN', '系统管理员')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);

