ALTER TABLE biz_award_record
    ADD COLUMN l1_auditor_user_id BIGINT NULL COMMENT '一级审核人用户ID' AFTER final_audit_time,
    ADD COLUMN l1_approved_at DATETIME NULL COMMENT '一级审核通过时间' AFTER l1_auditor_user_id,
    ADD COLUMN l2_review_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已二级复审：0否 1是' AFTER l1_approved_at,
    ADD COLUMN l2_review_result VARCHAR(16) NULL COMMENT '二级复审结果：PENDING/PASS/REJECT' AFTER l2_review_flag,
    ADD COLUMN l2_reviewer_user_id BIGINT NULL COMMENT '二级复审人用户ID' AFTER l2_review_result,
    ADD COLUMN l2_reviewed_at DATETIME NULL COMMENT '二级复审时间' AFTER l2_reviewer_user_id;

ALTER TABLE biz_award_record_audit
    ADD COLUMN audit_stage VARCHAR(32) NULL COMMENT '审核阶段：SUBMIT/L1_APPROVAL/L2_REVIEW' AFTER comment_text,
    ADD COLUMN auditor_work_no VARCHAR(32) NULL COMMENT '审核人工号/登录标识' AFTER auditor_user_id,
    ADD COLUMN reject_target VARCHAR(16) NULL COMMENT '驳回对象：STUDENT/L1' AFTER auditor_dept_id;

UPDATE biz_award_record
SET l2_review_flag = 0
WHERE l2_review_flag IS NULL;
