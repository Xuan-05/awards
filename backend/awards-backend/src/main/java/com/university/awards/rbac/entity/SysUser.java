package com.university.awards.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String passwordHash;
    private String realName;
    private String userType; // STUDENT/TEACHER/ADMIN
    private Long deptId;
    /**
     * 学生学号（可空；非空唯一）。
     */
    private String studentNo;
    /**
     * 教师工号（可空；非空唯一）。
     */
    private String teacherNo;
    /**
     * 班级 ID（仅学生使用；可空）。
     */
    private Long classId;
    /**
     * 手机号（可空）。
     */
    private String phone;
    /**
     * 邮箱（可空）。
     */
    private String email;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

