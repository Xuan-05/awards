package com.university.awards.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_award_record_audit")
public class BizAwardRecordAudit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private String nodeType; // SCHOOL
    private String actionType; // SUBMIT/APPROVE/REJECT/RESUBMIT
    private String fromStatus;
    private String toStatus;
    private String commentText;
    private String auditStage;
    private Long auditorUserId;
    private String auditorWorkNo;
    private Long auditorDeptId;
    private String rejectTarget;
    private LocalDateTime createdAt;
}

