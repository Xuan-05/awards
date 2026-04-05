USE awards;

CREATE TABLE IF NOT EXISTS biz_export_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  export_type VARCHAR(32) NOT NULL COMMENT 'DETAIL/SUMMARY',
  file_name VARCHAR(255) NULL,
  file_path VARCHAR(500) NULL,
  filter_json JSON NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  creator_user_id BIGINT NOT NULL,
  error_message VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  INDEX idx_export_creator (creator_user_id),
  INDEX idx_export_status (task_status)
) COMMENT='导出任务表';

