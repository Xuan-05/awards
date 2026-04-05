package com.university.awards.dashboard.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.common.ApiResponse;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.mapper.BizTeamMapper;
import com.university.awards.team.mapper.BizTeamInvitationMapper;
import com.university.awards.message.entity.SysMessage;
import com.university.awards.message.service.SysMessageService;
import com.university.awards.team.entity.BizTeamInvitation;
import com.university.awards.team.mapper.BizTeamInvitationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作台/控制台数据接口。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>当前为 MVP 版本，返回的统计项较少（团队总数、待审数量、通过数量）。</li>
 *   <li>后续可按角色（学生/教师/院系管理员/校级管理员）返回不同口径的数据。</li>
 * </ul>
 *
 * <p>权限：需要登录。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final BizTeamMapper teamMapper;
    private final BizAwardRecordMapper recordMapper;
    private final AuthzService authz;
    private final SysMessageService messageService;
    private final BizTeamInvitationMapper invitationMapper;

    /**
     * 汇总统计。
     *
     * @return Map 结构：teamCount / pendingAuditCount / approvedCount
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        StpUtil.checkLogin();

        long teamCount = teamMapper.selectCount(new LambdaQueryWrapper<BizTeam>().eq(BizTeam::getDeleted, 0));
        long pendingCount = recordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getStatus, "PENDING_SCHOOL"));
        long approvedCount = recordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getStatus, "APPROVED"));

        return ApiResponse.ok(Map.of(
                "teamCount", teamCount,
                "pendingAuditCount", pendingCount,
                "approvedCount", approvedCount
        ));
    }

    /**
     * 趋势数据（占位）。
     */
    @GetMapping("/trend")
    public ApiResponse<TrendResp> trend() {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        boolean isAdmin = authz.hasAnyRole("DEPT_ADMIN", "SCHOOL_ADMIN", "SYS_ADMIN");

        // 简化实现：拉取一定数量记录后在 Java 内聚合
        List<BizAwardRecord> recent = recordMapper.selectList(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .isNotNull(BizAwardRecord::getSubmitTime)
                .eq(!isAdmin, BizAwardRecord::getSubmitterUserId, uid)
                .orderByDesc(BizAwardRecord::getSubmitTime)
                .last("limit 2000"));

        java.time.LocalDate today = java.time.LocalDate.now();
        List<String> days = new java.util.ArrayList<>();
        List<Long> submitCounts = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            days.add(d.toString());
            long cnt = recent.stream().filter(r -> r.getSubmitTime() != null && d.equals(r.getSubmitTime().toLocalDate())).count();
            submitCounts.add(cnt);
        }

        return ApiResponse.ok(new TrendResp(days, submitCounts));
    }

    /**
     * 待办列表（占位）。
     */
    @GetMapping("/todos")
    public ApiResponse<TodosResp> todos() {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();

        long unread = messageService.count(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getReceiverUserId, uid)
                .eq(SysMessage::getReadFlag, 0));

        long pendingInv = invitationMapper.selectCount(new LambdaQueryWrapper<BizTeamInvitation>()
                .eq(BizTeamInvitation::getInviteeUserId, uid)
                .eq(BizTeamInvitation::getStatus, "PENDING"));

        long draft = recordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getSubmitterUserId, uid)
                .eq(BizAwardRecord::getStatus, "DRAFT"));

        long rejected = recordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getSubmitterUserId, uid)
                .eq(BizAwardRecord::getStatus, "SCHOOL_REJECTED"));

        return ApiResponse.ok(new TodosResp(unread, pendingInv, draft, rejected));
    }

    /**
     * 管理端控制台统计（用于图表）。
     *
     * <p><b>权限</b>：DEPT_ADMIN / SCHOOL_ADMIN / SYS_ADMIN。</p>
     *
     * <p>当前提供两类数据：</p>
     * <ul>
     *   <li>近 7 天提交趋势（按 submitTime 统计）。</li>
     *   <li>各院系待审核数量分布（ownerDeptId 聚合）。</li>
     * </ul>
     */
    @GetMapping("/admin/metrics")
    public ApiResponse<AdminMetrics> adminMetrics() {
        authz.requireAnyRole("DEPT_ADMIN", "SCHOOL_ADMIN", "SYS_ADMIN");

        // 简化实现：Java 内部聚合（数据量不大时足够）；后续可改 SQL group by
        List<BizAwardRecord> recent = recordMapper.selectList(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .isNotNull(BizAwardRecord::getSubmitTime)
                .orderByDesc(BizAwardRecord::getSubmitTime)
                .last("limit 2000"));

        java.time.LocalDate today = java.time.LocalDate.now();
        List<String> days = new java.util.ArrayList<>();
        List<Long> submitCounts = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            days.add(d.toString());
            long cnt = recent.stream().filter(r -> r.getSubmitTime() != null && d.equals(r.getSubmitTime().toLocalDate())).count();
            submitCounts.add(cnt);
        }

        List<BizAwardRecord> pending = recordMapper.selectList(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getStatus, "PENDING_SCHOOL"));
        java.util.Map<Long, Long> pendingByDept = pending.stream()
                .filter(r -> r.getOwnerDeptId() != null)
                .collect(java.util.stream.Collectors.groupingBy(BizAwardRecord::getOwnerDeptId, java.util.stream.Collectors.counting()));
        List<DeptPending> deptPendings = pendingByDept.entrySet().stream()
                .sorted(java.util.Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> new DeptPending(e.getKey(), e.getValue()))
                .toList();

        AdminMetrics m = new AdminMetrics(days, submitCounts, deptPendings);
        return ApiResponse.ok(m);
    }

    public record DeptPending(Long deptId, Long pendingCount) {
    }

    public record AdminMetrics(
            List<String> days,
            List<Long> submitCounts,
            List<DeptPending> pendingByDept
    ) {
    }

    public record TrendResp(List<String> days, List<Long> submitCounts) {
    }

    public record TodosResp(
            Long unreadMessageCount,
            Long pendingInvitationCount,
            Long draftRecordCount,
            Long rejectedRecordCount
    ) {
    }
}

