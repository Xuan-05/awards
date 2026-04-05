package com.university.awards.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_team_invitation")
public class BizTeamInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long inviteeUserId;
    private String inviteeType; // MEMBER/TEACHER
    private String status; // PENDING/ACCEPTED/REJECTED
    private Long inviterUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

