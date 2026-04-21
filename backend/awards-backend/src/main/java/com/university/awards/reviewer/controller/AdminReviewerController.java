package com.university.awards.reviewer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.mapper.DictCompetitionMapper;
import com.university.awards.rbac.entity.SysRole;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.entity.SysUserRole;
import com.university.awards.rbac.mapper.SysRoleMapper;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.mapper.SysUserRoleMapper;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.reviewer.entity.BizReviewerCompScope;
import com.university.awards.reviewer.mapper.BizReviewerCompScopeMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/reviewers")
@RequiredArgsConstructor
public class AdminReviewerController {

    private static final String USER_TYPE_REVIEWER = "REVIEWER";
    private static final String ROLE_L1 = "COMP_REVIEWER_L1";
    private static final String ROLE_L2 = "COMP_REVIEWER_L2";
    private static final Set<String> REVIEWER_ROLE_CODES = Set.of(ROLE_L1, ROLE_L2);

    private final AuthzService authz;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DictCompetitionMapper competitionMapper;
    private final BizReviewerCompScopeMapper reviewerCompScopeMapper;

    @GetMapping
    public ApiResponse<PageResult<ReviewerRow>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacherNo,
            @RequestParam(required = false) Integer enabled
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, USER_TYPE_REVIEWER)
                        .like(keyword != null && !keyword.isBlank(), SysUser::getRealName, keyword)
                        .like(teacherNo != null && !teacherNo.isBlank(), SysUser::getTeacherNo, teacherNo)
                        .eq(enabled != null, SysUser::getEnabled, enabled)
                        .orderByDesc(SysUser::getId));
        List<SysUser> users = page.getRecords();
        Map<Long, List<String>> roleMap = loadReviewerRoles(users.stream().map(SysUser::getId).toList());
        Map<Long, Long> scopeCountMap = loadScopeCount(users.stream().map(SysUser::getId).toList());
        List<ReviewerRow> rows = users.stream().map(user -> new ReviewerRow(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getTeacherNo(),
                user.getDeptId(),
                user.getEnabled(),
                roleMap.getOrDefault(user.getId(), List.of()),
                scopeCountMap.getOrDefault(user.getId(), 0L),
                user.getCreatedAt(),
                user.getUpdatedAt())).toList();
        return ApiResponse.ok(PageResult.of(page.getTotal(), rows));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid CreateReviewerReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        validateReviewerRoles(req.getRoleCodes());
        ensureCompetitionsExist(req.getScopes());
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setRealName(req.getRealName().trim());
        user.setUserType(USER_TYPE_REVIEWER);
        user.setDeptId(req.getDeptId());
        user.setEnabled(1);
        user.setTeacherNo(req.getTeacherNo().trim());
        user.setPasswordHash(BCrypt.hashpw(req.getPassword() == null || req.getPassword().isBlank() ? "123456" : req.getPassword(), BCrypt.gensalt(10)));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "账号或工号已存在");
        }
        replaceRoles(user.getId(), req.getRoleCodes());
        replaceScopes(user.getId(), req.getScopes());
        return ApiResponse.ok(user.getId());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody @Valid UpdateReviewerReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser old = requireReviewer(id);
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setRealName(req.getRealName() == null ? old.getRealName() : req.getRealName().trim());
        upd.setTeacherNo(req.getTeacherNo() == null ? old.getTeacherNo() : req.getTeacherNo().trim());
        upd.setDeptId(req.getDeptId() == null ? old.getDeptId() : req.getDeptId());
        upd.setEnabled(req.getEnabled() == null ? old.getEnabled() : req.getEnabled());
        upd.setUpdatedAt(LocalDateTime.now());
        try {
            userMapper.updateById(upd);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "工号已存在");
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser user = requireReviewer(id);
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setEnabled(user.getEnabled() != null && user.getEnabled() == 1 ? 0 : 1);
        upd.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(upd);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        requireReviewer(id);
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt(10)));
        upd.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(upd);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<Void> setRoles(@PathVariable Long id, @RequestBody @Valid SetRolesReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        requireReviewer(id);
        validateReviewerRoles(req.getRoleCodes());
        replaceRoles(id, req.getRoleCodes());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/scopes")
    public ApiResponse<List<ReviewerScopeRow>> scopes(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        requireReviewer(id);
        List<BizReviewerCompScope> rows = reviewerCompScopeMapper.selectList(
                new LambdaQueryWrapper<BizReviewerCompScope>()
                        .eq(BizReviewerCompScope::getReviewerUserId, id)
                        .orderByAsc(BizReviewerCompScope::getCompetitionId)
                        .orderByAsc(BizReviewerCompScope::getId));
        if (rows.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Set<Long> compIds = rows.stream().map(BizReviewerCompScope::getCompetitionId).collect(Collectors.toSet());
        Map<Long, String> compNameMap = compIds.isEmpty() ? Map.of() : competitionMapper.selectList(new LambdaQueryWrapper<DictCompetition>()
                        .in(DictCompetition::getId, compIds)).stream()
                .collect(Collectors.toMap(DictCompetition::getId, DictCompetition::getCompetitionName));
        List<ReviewerScopeRow> out = rows.stream().map(scope -> new ReviewerScopeRow(
                scope.getId(),
                scope.getCompetitionId(),
                compNameMap.getOrDefault(scope.getCompetitionId(), String.valueOf(scope.getCompetitionId())),
                scope.getEnabled(),
                scope.getValidFrom(),
                scope.getValidTo())).toList();
        return ApiResponse.ok(out);
    }

    @GetMapping("/my-scopes")
    public ApiResponse<List<ReviewerScopeRow>> myScopes() {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN", "COMP_REVIEWER_L1", "COMP_REVIEWER_L2");
        Long currentUserId = authz.currentUserId();
        List<BizReviewerCompScope> rows = reviewerCompScopeMapper.selectList(
                new LambdaQueryWrapper<BizReviewerCompScope>()
                        .eq(BizReviewerCompScope::getReviewerUserId, currentUserId)
                        .orderByAsc(BizReviewerCompScope::getCompetitionId)
                        .orderByAsc(BizReviewerCompScope::getId));
        if (rows.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Set<Long> compIds = rows.stream().map(BizReviewerCompScope::getCompetitionId).collect(Collectors.toSet());
        Map<Long, String> compNameMap = compIds.isEmpty() ? Map.of() : competitionMapper.selectList(new LambdaQueryWrapper<DictCompetition>()
                        .in(DictCompetition::getId, compIds)).stream()
                .collect(Collectors.toMap(DictCompetition::getId, DictCompetition::getCompetitionName));
        List<ReviewerScopeRow> out = rows.stream().map(scope -> new ReviewerScopeRow(
                scope.getId(),
                scope.getCompetitionId(),
                compNameMap.getOrDefault(scope.getCompetitionId(), String.valueOf(scope.getCompetitionId())),
                scope.getEnabled(),
                scope.getValidFrom(),
                scope.getValidTo())).toList();
        return ApiResponse.ok(out);
    }

    @PutMapping("/{id}/scopes")
    public ApiResponse<Void> setScopes(@PathVariable Long id, @RequestBody @Valid SetScopesReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        requireReviewer(id);
        ensureCompetitionsExist(req.getScopes());
        replaceScopes(id, req.getScopes());
        return ApiResponse.ok(null);
    }

    private SysUser requireReviewer(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "审核员不存在");
        if (!USER_TYPE_REVIEWER.equals(user.getUserType())) throw new BizException(400, "目标用户不是审核员账号");
        return user;
    }

    private void validateReviewerRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BizException(400, "请至少配置一个审核角色");
        }
        if (roleCodes.stream().anyMatch(code -> !REVIEWER_ROLE_CODES.contains(code))) {
            throw new BizException(400, "仅允许配置 COMP_REVIEWER_L1/COMP_REVIEWER_L2 角色");
        }
    }

    private void ensureCompetitionsExist(List<ScopeItem> scopes) {
        if (scopes == null || scopes.isEmpty()) return;
        Set<Long> ids = scopes.stream().map(ScopeItem::getCompetitionId).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        long exists = competitionMapper.selectCount(new LambdaQueryWrapper<DictCompetition>().in(DictCompetition::getId, ids));
        if (exists != ids.size()) {
            throw new BizException(400, "存在无效竞赛ID，请刷新后重试");
        }
    }

    private void replaceRoles(Long userId, List<String> roleCodes) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, roleCodes));
        if (roles.size() != roleCodes.size()) throw new BizException(400, "角色不存在，请先初始化角色数据");
        for (SysRole role : roles) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }
    }

    private void replaceScopes(Long userId, List<ScopeItem> scopes) {
        reviewerCompScopeMapper.delete(new LambdaQueryWrapper<BizReviewerCompScope>().eq(BizReviewerCompScope::getReviewerUserId, userId));
        if (scopes == null || scopes.isEmpty()) return;
        Long operator = authz.currentUserId();
        LocalDateTime now = LocalDateTime.now();
        for (ScopeItem item : scopes) {
            BizReviewerCompScope scope = new BizReviewerCompScope();
            scope.setReviewerUserId(userId);
            scope.setCompetitionId(item.getCompetitionId());
            scope.setEnabled(item.getEnabled() == null ? 1 : item.getEnabled());
            scope.setValidFrom(item.getValidFrom());
            scope.setValidTo(item.getValidTo());
            scope.setCreatedBy(operator);
            scope.setUpdatedBy(operator);
            scope.setCreatedAt(now);
            scope.setUpdatedAt(now);
            reviewerCompScopeMapper.insert(scope);
        }
    }

    private Map<Long, List<String>> loadReviewerRoles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<SysUserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (urs.isEmpty()) return Map.of();
        Set<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, String> roleCodeById = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)).stream()
                .filter(role -> REVIEWER_ROLE_CODES.contains(role.getRoleCode()))
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode));
        Map<Long, List<String>> out = new HashMap<>();
        for (SysUserRole ur : urs) {
            String roleCode = roleCodeById.get(ur.getRoleId());
            if (roleCode == null) continue;
            out.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(roleCode);
        }
        out.values().forEach(list -> list.sort(String::compareTo));
        return out;
    }

    private Map<Long, Long> loadScopeCount(List<Long> userIds) {
        Map<Long, Long> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return map;
        List<BizReviewerCompScope> all = reviewerCompScopeMapper.selectList(
                new LambdaQueryWrapper<BizReviewerCompScope>()
                        .in(BizReviewerCompScope::getReviewerUserId, userIds)
                        .eq(BizReviewerCompScope::getEnabled, 1));
        for (BizReviewerCompScope scope : all) {
            map.put(scope.getReviewerUserId(), map.getOrDefault(scope.getReviewerUserId(), 0L) + 1);
        }
        return map;
    }

    public record ReviewerRow(
            Long id,
            String username,
            String realName,
            String teacherNo,
            Long deptId,
            Integer enabled,
            List<String> roleCodes,
            Long activeScopeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReviewerScopeRow(
            Long id,
            Long competitionId,
            String competitionName,
            Integer enabled,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {
    }

    @Data
    public static class CreateReviewerReq {
        @NotBlank
        private String username;
        @NotBlank
        private String realName;
        @NotBlank
        private String teacherNo;
        private Long deptId;
        private String password;
        @NotEmpty
        private List<@NotBlank String> roleCodes;
        private List<@Valid ScopeItem> scopes;
    }

    @Data
    public static class UpdateReviewerReq {
        private String realName;
        private String teacherNo;
        private Long deptId;
        private Integer enabled;
    }

    @Data
    public static class SetRolesReq {
        @NotEmpty
        private List<@NotBlank String> roleCodes;
    }

    @Data
    public static class SetScopesReq {
        private List<@Valid ScopeItem> scopes;
    }

    @Data
    public static class ScopeItem {
        @NotNull
        private Long competitionId;
        private Integer enabled;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
    }
}

