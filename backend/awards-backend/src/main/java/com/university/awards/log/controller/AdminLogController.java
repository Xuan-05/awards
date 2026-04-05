package com.university.awards.log.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.log.entity.SysLoginLog;
import com.university.awards.log.entity.SysOperateLog;
import com.university.awards.log.service.SysLoginLogService;
import com.university.awards.log.service.SysOperateLogService;
import com.university.awards.rbac.service.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端日志查询接口。
 *
 * <p>权限：仅 SCHOOL_ADMIN / SYS_ADMIN。</p>
 *
 * <p>说明：</p>
 * <ul>
 *   <li>操作日志：来源 {@code sys_operate_log}，主要记录写操作（POST/PUT/DELETE）。</li>
 *   <li>登录日志：来源 {@code sys_login_log}，记录登录成功/失败。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AuthzService authz;
    private final SysOperateLogService operateLogService;
    private final SysLoginLogService loginLogService;

    /**
     * 操作日志分页查询。
     *
     * @param userId 可选：操作人用户ID
     * @param bizType 可选：业务类型（OperateLogAspect 按 URI 粗略归类）
     * @param action 可选：动作（POST/PUT/DELETE）
     * @param fromTime/toTime 可选：时间范围（LocalDateTime.parse 格式）
     */
    @GetMapping("/operate")
    public ApiResponse<PageResult<SysOperateLog>> operate(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        LocalDateTime from = parseTime(fromTime);
        LocalDateTime to = parseTime(toTime);
        Page<SysOperateLog> page = operateLogService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysOperateLog>()
                        .eq(userId != null, SysOperateLog::getUserId, userId)
                        .eq(bizType != null && !bizType.isBlank(), SysOperateLog::getBizType, bizType)
                        .eq(action != null && !action.isBlank(), SysOperateLog::getAction, action)
                        .ge(from != null, SysOperateLog::getCreatedAt, from)
                        .le(to != null, SysOperateLog::getCreatedAt, to)
                        .orderByDesc(SysOperateLog::getId)
        );
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 登录日志分页查询。
     *
     * @param username 可选：按账号模糊查询
     * @param successFlag 可选：1 成功 / 0 失败
     * @param fromTime/toTime 可选：时间范围（LocalDateTime.parse 格式）
     */
    @GetMapping("/login")
    public ApiResponse<PageResult<SysLoginLog>> login(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer successFlag,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        LocalDateTime from = parseTime(fromTime);
        LocalDateTime to = parseTime(toTime);
        Page<SysLoginLog> page = loginLogService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysLoginLog>()
                        .like(username != null && !username.isBlank(), SysLoginLog::getUsername, username)
                        .eq(successFlag != null, SysLoginLog::getSuccessFlag, successFlag)
                        .ge(from != null, SysLoginLog::getCreatedAt, from)
                        .le(to != null, SysLoginLog::getCreatedAt, to)
                        .orderByDesc(SysLoginLog::getId)
        );
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 解析时间字符串（LocalDateTime.parse 格式）。
     *
     * <p>容错：解析失败返回 null，使得“时间过滤条件”自动不生效。</p>
     */
    private LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}

