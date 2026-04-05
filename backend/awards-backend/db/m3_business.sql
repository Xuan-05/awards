-- M3 business tables: team + invitations + award record + attachments + audit log
USE awards;

CREATE TABLE IF NOT EXISTS biz_team (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_name VARCHAR(255) NOT NULL,
  captain_user_id BIGINT NOT NULL,
  owner_dept_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_team_captain (captain_user_id),
  INDEX idx_team_dept (owner_dept_id)
) COMMENT='团队表';

CREATE TABLE IF NOT EXISTS biz_team_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_order_no INT NOT NULL DEFAULT 1,
  is_captain TINYINT(1) NOT NULL DEFAULT 0,
  join_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
  UNIQUE KEY uk_team_user (team_id, user_id),
  INDEX idx_tm_team (team_id)
) COMMENT='团队成员表';

CREATE TABLE IF NOT EXISTS biz_team_teacher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  teacher_user_id BIGINT NOT NULL,
  teacher_order_no INT NOT NULL DEFAULT 1,
  join_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
  INDEX idx_tt_team (team_id)
) COMMENT='团队指导教师表';

CREATE TABLE IF NOT EXISTS biz_team_invitation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  invitee_user_id BIGINT NOT NULL,
  invitee_type VARCHAR(16) NOT NULL COMMENT 'MEMBER/TEACHER',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  inviter_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_inv_team (team_id),
  INDEX idx_inv_invitee (invitee_user_id)
) COMMENT='团队邀请表';

CREATE TABLE IF NOT EXISTS biz_award_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id BIGINT NOT NULL,
  competition_id BIGINT NOT NULL,
  competition_category_id BIGINT NOT NULL,
  organizer_id BIGINT NULL,
  award_scope_id BIGINT NOT NULL,
  award_level_id BIGINT NOT NULL,
  award_date DATE NOT NULL,
  semester VARCHAR(32) NOT NULL,
  project_name VARCHAR(255) NOT NULL,
  team_award_count INT NOT NULL DEFAULT 1,
  owner_dept_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  submitter_user_id BIGINT NULL,
  submit_time DATETIME NULL,
  final_audit_time DATETIME NULL,
  remark VARCHAR(255) NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_record_team (team_id),
  INDEX idx_record_competition (competition_id),
  INDEX idx_record_status (status),
  INDEX idx_record_dept (owner_dept_id)
) COMMENT='获奖记录表';

CREATE TABLE IF NOT EXISTS biz_award_record_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL COMMENT 'sys_file.id',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file_record (record_id)
) COMMENT='获奖附件关联表';

CREATE TABLE IF NOT EXISTS biz_award_record_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL,
  node_type VARCHAR(32) NOT NULL COMMENT 'SCHOOL',
  action_type VARCHAR(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/RESUBMIT',
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  comment_text VARCHAR(1000) NULL,
  auditor_user_id BIGINT NOT NULL,
  auditor_dept_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_record (record_id)
) COMMENT='审核轨迹表';

