-- Add sys_message table for in-app message center
USE awards;

CREATE TABLE IF NOT EXISTS sys_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receiver_user_id BIGINT NOT NULL COMMENT '接收者 sys_user.id',
  msg_type VARCHAR(32) NOT NULL COMMENT 'INVITATION/AUDIT/NOTICE',
  title VARCHAR(255) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  biz_type VARCHAR(32) NULL COMMENT 'TEAM_INVITATION/AWARD_RECORD等',
  biz_id BIGINT NULL COMMENT '业务主键，如 invitationId/recordId',
  read_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_msg_receiver_created (receiver_user_id, created_at),
  INDEX idx_msg_receiver_read (receiver_user_id, read_flag)
) COMMENT='站内消息表';

