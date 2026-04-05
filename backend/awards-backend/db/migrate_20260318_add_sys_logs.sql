-- Add sys_operate_log and sys_login_log
USE awards;

CREATE TABLE IF NOT EXISTS sys_operate_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  username VARCHAR(64) NULL,
  http_method VARCHAR(16) NOT NULL,
  request_uri VARCHAR(255) NOT NULL,
  biz_type VARCHAR(64) NULL,
  action VARCHAR(64) NULL,
  request_body VARCHAR(2000) NULL,
  response_code INT NULL,
  success_flag TINYINT(1) NOT NULL DEFAULT 1,
  error_message VARCHAR(1000) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  cost_ms BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_op_user_time (user_id, created_at),
  INDEX idx_op_uri_time (request_uri, created_at),
  INDEX idx_op_biz (biz_type, created_at)
) COMMENT='操作日志';

CREATE TABLE IF NOT EXISTS sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  user_id BIGINT NULL,
  success_flag TINYINT(1) NOT NULL DEFAULT 1,
  error_message VARCHAR(1000) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_login_username_time (username, created_at),
  INDEX idx_login_user_time (user_id, created_at),
  INDEX idx_login_success_time (success_flag, created_at)
) COMMENT='登录日志';

