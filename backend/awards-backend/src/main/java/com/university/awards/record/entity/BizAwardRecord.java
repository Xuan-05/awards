package com.university.awards.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_award_record")
public class BizAwardRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long competitionId;
    private Long competitionCategoryId;
    private Long awardScopeId;
    private Long awardLevelId;
    private LocalDate awardDate;
    private String semester;
    private String projectName;
    private Integer teamAwardCount;
    private Long ownerDeptId;
    private String status; // DRAFT/PENDING_SCHOOL/SCHOOL_REJECTED/APPROVED/ARCHIVED

    private Long submitterUserId;
    private LocalDateTime submitTime;
    private LocalDateTime finalAuditTime;
    private String remark;

    @Version
    private Integer version;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
