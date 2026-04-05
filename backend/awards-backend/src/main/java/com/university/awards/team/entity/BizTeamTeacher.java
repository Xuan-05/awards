package com.university.awards.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_team_teacher")
public class BizTeamTeacher {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long teacherUserId;
    private Integer teacherOrderNo;
    private String joinStatus; // ACCEPTED/PENDING
}

