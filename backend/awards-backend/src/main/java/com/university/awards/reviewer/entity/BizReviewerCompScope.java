package com.university.awards.reviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_reviewer_comp_scope")
public class BizReviewerCompScope {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewerUserId;
    private Long competitionId;
    private Integer enabled;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

