package com.university.awards.sync.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部数据同步接口（占位）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>PRD 要求学生/教师基础信息以外部系统为准；本接口用于触发同步（后续实现）。</li>
 *   <li>当前仅提供 skeleton，不做实际同步。</li>
 * </ul>
 *
 * <p>权限：需要登录（后续建议收敛到 SYS_ADMIN 或专门的同步权限）。</p>
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    /**
     * 同步用户基础信息（占位）。
     */
    @PostMapping("/users")
    public ApiResponse<Void> syncUsers() {
        StpUtil.checkLogin();
        // Skeleton: integrate with external SSO/edu system later (M5).
        return ApiResponse.ok(null);
    }
}

