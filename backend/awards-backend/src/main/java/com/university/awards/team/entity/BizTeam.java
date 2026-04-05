package com.university.awards.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_team")
public class BizTeam {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String teamName;
    private Long captainUserId;
    private Long ownerDeptId;
    private String status; // ACTIVE/ARCHIVED
    private String remark;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

