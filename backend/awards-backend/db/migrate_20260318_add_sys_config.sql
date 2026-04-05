-- Add sys_config table for system parameters
USE awards;

CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL UNIQUE,
  config_value VARCHAR(2000) NOT NULL,
  value_type VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/BOOL/JSON',
  remark VARCHAR(255) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='系统参数配置';

-- seed recommended configs (idempotent)
INSERT INTO sys_config (config_key, config_value, value_type, remark)
VALUES
('max_teacher_count', '3', 'INT', '团队指导教师数量上限'),
('record_attachment_required', '0', 'BOOL', '提交/保存是否强制要求至少一个附件'),
('upload_max_single_file_mb', '50', 'INT', '上传单文件大小限制（MB）'),
('allow_admin_supplement_after_deadline', '1', 'BOOL', '截止后是否允许管理员补录（预留）')
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  value_type = VALUES(value_type),
  remark = VALUES(remark);

