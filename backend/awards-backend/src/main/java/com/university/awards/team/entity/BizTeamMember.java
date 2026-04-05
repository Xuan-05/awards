package com.university.awards.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_team_member")
public class BizTeamMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long userId;
    private Integer memberOrderNo;
    private Integer isCaptain;
    private String joinStatus; // ACCEPTED/PENDING
}

