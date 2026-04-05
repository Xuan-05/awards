package com.university.awards.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operate_log")
public class SysOperateLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String httpMethod;
    private String requestUri;
    private String bizType;
    private String action;
    private String requestBody;
    private Integer responseCode;
    private Integer successFlag;
    private String errorMessage;
    private String ip;
    private String userAgent;
    private Long costMs;
    private LocalDateTime createdAt;
}

