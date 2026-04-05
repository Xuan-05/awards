package com.university.awards.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private Long userId;
    private Integer successFlag;
    private String errorMessage;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}

