package com.university.awards.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.audit.entity.BizAwardRecordAudit;
import com.university.awards.audit.mapper.BizAwardRecordAuditMapper;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.message.service.MessageWriteService;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.reviewer.entity.BizReviewerCompScope;
import com.university.awards.reviewer.mapper.BizReviewerCompScopeMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核相关接口（单级校级审核）。
 *
 * <p>审核流程：</p>
 * <ul>
 *   <li>学生/教师提交获奖记录后进入 {@code PENDING_SCHOOL}。</li>
 *   <li>校级审核可通过（-> {@code APPROVED}）或驳回（-> {@code SCHOOL_REJECTED}）。</li>
 *   <li>驳回后由提交人修改并重新提交。</li>
 * </ul>
 *
 * <p>权限：</p>
 * <ul>
 *   <li>仅 {@code SCHOOL_ADMIN}/{@code SYS_ADMIN} 可访问本控制器所有接口。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final BizAwardRecordMapper recordMapper;
    private final BizAwardRecordAuditMapper auditMapper;
    private final AuthzService authz;
    private final MessageWriteService messageWriteService;
    private final BizReviewerCompScopeMapper reviewerCompScopeMapper;
    private final RbacService rbacService;
    private final SysUserMapper userMapper;

    /**
     * 审核任务列表（分页）。
     *
     * <p><b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。</p>
     * <p><b>参数说明</b>：</p>
     * <ul>
     *   <li>{@code nodeType}：当前仅支持 SCHOOL（校级）。</li>
     *   <li>{@code status}：默认 PENDING_SCHOOL，可查询 APPROVED/SCHOOL_REJECTED 以回溯。</li>
     *   <li>{@code deptId/competitionId/semester}：可选过滤条件。</li>
     * </ul>
     *
     * @return 待审（或指定状态）记录列表
     */
    @GetMapping("/tasks")
    public ApiResponse<PageResult<BizAwardRecord>> tasks(
            @RequestParam(defaultValue = "SCHOOL") String nodeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        boolean isAdmin = isAdmin(roleCodes);
        boolean l1Node = isL1Node(nodeType);
        boolean l2Node = "L2".equalsIgnoreCase(nodeType);
        if (!l1Node && !l2Node) {
            throw new BizException(400, "仅支持 L1/L2 审核节点");
        }
        if (l1Node) {
            requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L1");
        } else {
            requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L2");
        }
        String effectiveStatus = status == null || status.isBlank()
                ? (l1Node ? "PENDING_SCHOOL" : "APPROVED")
                : status;
        if (l1Node && !isAdmin && roleCodes.contains("COMP_REVIEWER_L1")) {
            Set<Long> allowedCompetitionIds = loadAllowedCompetitionIds(currentUserId);
            if (allowedCompetitionIds.isEmpty()) {
                return ApiResponse.ok(PageResult.of(0, Collections.emptyList()));
            }
            if (competitionId != null && !allowedCompetitionIds.contains(competitionId)) {
                return ApiResponse.ok(PageResult.of(0, Collections.emptyList()));
            }
            Page<BizAwardRecord> page = recordMapper.selectPage(
                    new Page<>(pageNo, pageSize),
                    new LambdaQueryWrapper<BizAwardRecord>()
                            .eq(BizAwardRecord::getStatus, effectiveStatus)
                            .eq(deptId != null, BizAwardRecord::getOwnerDeptId, deptId)
                            .eq(competitionId != null, BizAwardRecord::getCompetitionId, competitionId)
                            .eq(semester != null && !semester.isBlank(), BizAwardRecord::getSemester, semester)
                            .like(keyword != null && !keyword.isBlank(), BizAwardRecord::getProjectName, keyword)
                            .in(BizAwardRecord::getCompetitionId, allowedCompetitionIds)
                            .eq(BizAwardRecord::getDeleted, 0)
                            .orderByAsc(BizAwardRecord::getSubmitTime)
            );
            return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
        }

        Page<BizAwardRecord> page = recordMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<BizAwardRecord>()
                        .eq(BizAwardRecord::getStatus, effectiveStatus)
                        .eq(deptId != null, BizAwardRecord::getOwnerDeptId, deptId)
                        .eq(competitionId != null, BizAwardRecord::getCompetitionId, competitionId)
                        .eq(semester != null && !semester.isBlank(), BizAwardRecord::getSemester, semester)
                        .like(keyword != null && !keyword.isBlank(), BizAwardRecord::getProjectName, keyword)
                        .eq(BizAwardRecord::getDeleted, 0)
                        .orderByAsc(BizAwardRecord::getSubmitTime)
        );
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 校级审核通过。
     *
     * <p><b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。</p>
     * <p><b>状态机</b>：PENDING_SCHOOL -> APPROVED。</p>
     * <p><b>副作用</b>：写入 {@code biz_award_record_audit} 审核日志。</p>
     * <p><b>副作用</b>：写入 {@code sys_message} 通知提交人审核结果。</p>
     */
    @PostMapping("/{recordId}/school/approve")
    public ApiResponse<Void> schoolApprove(@PathVariable Long recordId) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L1");
        BizAwardRecord e = recordMapper.selectById(recordId);
        if (e == null) throw new BizException(404, "记录不存在");
        if (!"PENDING_SCHOOL".equals(e.getStatus())) throw new BizException(400, "当前状态不允许审核");
        ensureL1Scope(roleCodes, currentUserId, e);

        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(recordId);
        upd.setVersion(e.getVersion());
        upd.setStatus("APPROVED");
        upd.setFinalAuditTime(LocalDateTime.now());
        upd.setL1AuditorUserId(currentUserId);
        upd.setL1ApprovedAt(LocalDateTime.now());
        upd.setL2ReviewFlag(0);
        upd.setL2ReviewResult("PENDING");
        upd.setL2ReviewerUserId(null);
        upd.setL2ReviewedAt(null);
        recordMapper.updateById(upd);

        BizAwardRecordAudit log = new BizAwardRecordAudit();
        log.setRecordId(recordId);
        log.setNodeType("SCHOOL");
        log.setActionType("APPROVE");
        log.setFromStatus("PENDING_SCHOOL");
        log.setToStatus("APPROVED");
        log.setAuditStage("L1_APPROVAL");
        log.setAuditorUserId(currentUserId);
        log.setAuditorWorkNo(loadAuditorWorkNo(currentUserId));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);

        // message to submitter
        if (e.getSubmitterUserId() != null) {
            String title = "审核通过";
            String content = String.format("你提交的获奖填报（ID=%d，项目：%s）已审核通过。", recordId, e.getProjectName() == null ? "-" : e.getProjectName());
            // msgType=AUDIT：审核通知；bizType=AWARD_RECORD：前端可据此做跳转/联动
            messageWriteService.write(e.getSubmitterUserId(), "AUDIT", title, content, "AWARD_RECORD", recordId);
        }

        return ApiResponse.ok(null);
    }

    /**
     * 校级审核驳回。
     *
     * <p><b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。</p>
     * <p><b>状态机</b>：PENDING_SCHOOL -> SCHOOL_REJECTED。</p>
     * <p><b>副作用</b>：写入审核日志（含驳回意见）。</p>
     * <p><b>副作用</b>：写入 {@code sys_message} 通知提交人驳回原因。</p>
     *
     * @param req 驳回请求（comment 为必填，将返回给提交人）
     */
    @PostMapping("/{recordId}/school/reject")
    public ApiResponse<Void> schoolReject(@PathVariable Long recordId, @RequestBody @Valid RejectReq req) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L1");
        BizAwardRecord e = recordMapper.selectById(recordId);
        if (e == null) throw new BizException(404, "记录不存在");
        if (!"PENDING_SCHOOL".equals(e.getStatus())) throw new BizException(400, "当前状态不允许审核");
        ensureL1Scope(roleCodes, currentUserId, e);

        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(recordId);
        upd.setVersion(e.getVersion());
        upd.setStatus("SCHOOL_REJECTED");
        recordMapper.updateById(upd);

        BizAwardRecordAudit log = new BizAwardRecordAudit();
        log.setRecordId(recordId);
        log.setNodeType("SCHOOL");
        log.setActionType("REJECT");
        log.setFromStatus("PENDING_SCHOOL");
        log.setToStatus("SCHOOL_REJECTED");
        log.setCommentText(req.getComment());
        log.setAuditStage("L1_APPROVAL");
        log.setRejectTarget("STUDENT");
        log.setAuditorUserId(currentUserId);
        log.setAuditorWorkNo(loadAuditorWorkNo(currentUserId));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);

        // message to submitter
        if (e.getSubmitterUserId() != null) {
            String title = "审核驳回";
            String comment = req.getComment() == null ? "" : req.getComment();
            String content = String.format("你提交的获奖填报（ID=%d，项目：%s）被驳回：%s", recordId, e.getProjectName() == null ? "-" : e.getProjectName(), comment);
            // 驳回消息同样用 msgType=AUDIT，便于前端在消息中心统一筛选
            messageWriteService.write(e.getSubmitterUserId(), "AUDIT", title, content, "AWARD_RECORD", recordId);
        }

        return ApiResponse.ok(null);
    }

    /**
     * 二级复审通过（仅复审已通过的一审记录）。
     */
    @PostMapping("/{recordId}/l2/pass")
    public ApiResponse<Void> l2Pass(@PathVariable Long recordId) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L2");
        BizAwardRecord e = recordMapper.selectById(recordId);
        if (e == null) throw new BizException(404, "记录不存在");
        if (!"APPROVED".equals(e.getStatus())) throw new BizException(400, "仅允许复审已通过记录");

        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(recordId);
        upd.setVersion(e.getVersion());
        upd.setL2ReviewFlag(1);
        upd.setL2ReviewResult("PASS");
        upd.setL2ReviewerUserId(currentUserId);
        upd.setL2ReviewedAt(LocalDateTime.now());
        recordMapper.updateById(upd);

        BizAwardRecordAudit log = new BizAwardRecordAudit();
        log.setRecordId(recordId);
        log.setNodeType("L2");
        log.setActionType("APPROVE");
        log.setFromStatus("APPROVED");
        log.setToStatus("APPROVED");
        log.setAuditStage("L2_REVIEW");
        log.setAuditorUserId(currentUserId);
        log.setAuditorWorkNo(loadAuditorWorkNo(currentUserId));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);
        return ApiResponse.ok(null);
    }

    /**
     * 二级复审驳回到学生，并通知一审审核人。
     */
    @PostMapping("/{recordId}/l2/reject-to-student")
    public ApiResponse<Void> l2RejectToStudent(@PathVariable Long recordId, @RequestBody @Valid RejectReq req) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L2");
        BizAwardRecord e = recordMapper.selectById(recordId);
        if (e == null) throw new BizException(404, "记录不存在");
        if (!"APPROVED".equals(e.getStatus())) throw new BizException(400, "仅允许复审已通过记录");

        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(recordId);
        upd.setVersion(e.getVersion());
        upd.setStatus("SCHOOL_REJECTED");
        upd.setL2ReviewFlag(1);
        upd.setL2ReviewResult("REJECT");
        upd.setL2ReviewerUserId(currentUserId);
        upd.setL2ReviewedAt(LocalDateTime.now());
        recordMapper.updateById(upd);

        BizAwardRecordAudit log = new BizAwardRecordAudit();
        log.setRecordId(recordId);
        log.setNodeType("L2");
        log.setActionType("REJECT");
        log.setFromStatus("APPROVED");
        log.setToStatus("SCHOOL_REJECTED");
        log.setCommentText(req.getComment());
        log.setAuditStage("L2_REVIEW");
        log.setRejectTarget("STUDENT");
        log.setAuditorUserId(currentUserId);
        log.setAuditorWorkNo(loadAuditorWorkNo(currentUserId));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);

        if (e.getSubmitterUserId() != null) {
            String title = "复审驳回";
            String content = String.format("你提交的获奖填报（ID=%d，项目：%s）被复审驳回：%s", recordId,
                    e.getProjectName() == null ? "-" : e.getProjectName(), req.getComment());
            messageWriteService.write(e.getSubmitterUserId(), "AUDIT", title, content, "AWARD_RECORD", recordId);
        }
        if (e.getL1AuditorUserId() != null) {
            String title = "复审结果通知";
            String content = String.format("你一审通过的获奖填报（ID=%d，项目：%s）被二级复审驳回：%s", recordId,
                    e.getProjectName() == null ? "-" : e.getProjectName(), req.getComment());
            messageWriteService.write(e.getL1AuditorUserId(), "AUDIT", title, content, "AWARD_RECORD", recordId);
        }
        return ApiResponse.ok(null);
    }

    /**
     * 查询某条记录的审核轨迹（按插入顺序升序）。
     *
     * <p><b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。</p>
     *
     * @param recordId 获奖记录 ID
     */
    @GetMapping("/{recordId}/logs")
    public ApiResponse<List<BizAwardRecordAudit>> logs(@PathVariable Long recordId) {
        Long currentUserId = authz.currentUserId();
        Set<String> roleCodes = Set.copyOf(rbacService.getRoleCodes(currentUserId));
        requireAnyRole(roleCodes, "SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L1", "COMP_REVIEWER_L2");
        BizAwardRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(404, "记录不存在");
        }
        if (!isAdmin(roleCodes) && roleCodes.contains("COMP_REVIEWER_L1")) {
            Set<Long> allowedCompetitionIds = loadAllowedCompetitionIds(currentUserId);
            if (record.getCompetitionId() == null || !allowedCompetitionIds.contains(record.getCompetitionId())) {
                throw new BizException(403, "未授权查看该记录审核轨迹");
            }
        }
        List<BizAwardRecordAudit> list = auditMapper.selectList(
                new LambdaQueryWrapper<BizAwardRecordAudit>()
                        .eq(BizAwardRecordAudit::getRecordId, recordId)
                        .orderByAsc(BizAwardRecordAudit::getId)
        );
        return ApiResponse.ok(list);
    }

    @Data
    public static class RejectReq {
        /**
         * 驳回意见（必填）。
         */
        @NotBlank
        private String comment;
    }

    private void ensureL1Scope(Set<String> roleCodes, Long currentUserId, BizAwardRecord record) {
        if (isAdmin(roleCodes)) {
            return;
        }
        if (!roleCodes.contains("COMP_REVIEWER_L1")) {
            throw new BizException(403, "无权限");
        }
        Set<Long> allowedCompetitionIds = loadAllowedCompetitionIds(currentUserId);
        if (record.getCompetitionId() == null || !allowedCompetitionIds.contains(record.getCompetitionId())) {
            throw new BizException(403, "未授权该竞赛的一审权限");
        }
    }

    private Set<Long> loadAllowedCompetitionIds(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return reviewerCompScopeMapper.selectList(
                new LambdaQueryWrapper<BizReviewerCompScope>()
                        .eq(BizReviewerCompScope::getReviewerUserId, userId)
                        .eq(BizReviewerCompScope::getEnabled, 1)
                        .and(w -> w.isNull(BizReviewerCompScope::getValidFrom).or().le(BizReviewerCompScope::getValidFrom, now))
                        .and(w -> w.isNull(BizReviewerCompScope::getValidTo).or().ge(BizReviewerCompScope::getValidTo, now))
        ).stream()
                .map(BizReviewerCompScope::getCompetitionId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private String loadAuditorWorkNo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return "";
        }
        if (user.getTeacherNo() != null && !user.getTeacherNo().isBlank()) {
            return user.getTeacherNo();
        }
        if (user.getStudentNo() != null && !user.getStudentNo().isBlank()) {
            return user.getStudentNo();
        }
        return user.getUsername() == null ? "" : user.getUsername();
    }

    private void requireAnyRole(Set<String> roleCodes, String... requiredCodes) {
        for (String code : requiredCodes) {
            if (roleCodes.contains(code)) {
                return;
            }
        }
        throw new BizException(403, "无权限");
    }

    private boolean isAdmin(Set<String> roleCodes) {
        return roleCodes.contains("SCHOOL_ADMIN") || roleCodes.contains("SYS_ADMIN");
    }

    private boolean isL1Node(String nodeType) {
        return "L1".equalsIgnoreCase(nodeType) || "SCHOOL".equalsIgnoreCase(nodeType);
    }
}

