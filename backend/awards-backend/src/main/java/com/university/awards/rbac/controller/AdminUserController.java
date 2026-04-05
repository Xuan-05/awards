package com.university.awards.rbac.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.service.DictClassService;
import com.university.awards.rbac.entity.SysRole;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.entity.SysUserRole;
import com.university.awards.rbac.mapper.SysRoleMapper;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.mapper.SysUserRoleMapper;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端用户管理接口（校级管理员/系统管理员）。
 *
 * <p>数据来源：本地 RBAC 表（sys_user / sys_role / sys_user_role）。</p>
 *
 * <p>权限规则：</p>
 * <ul>
 *   <li>SCHOOL_ADMIN / SYS_ADMIN 可访问。</li>
 *   <li>SCHOOL_ADMIN 可以分配角色，但不能分配/撤销 SYS_ADMIN。</li>
 *   <li>若目标用户本身拥有 SYS_ADMIN，只有 SYS_ADMIN 才能对其进行角色变更/启停/重置密码等操作。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthzService authz;
    private final RbacService rbacService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DictClassService classService;

    /**
     * 可分配角色列表。
     *
     * <p>SCHOOL_ADMIN 获取时会过滤掉 SYS_ADMIN。</p>
     */
    @GetMapping("/roles")
    public ApiResponse<List<RoleVO>> roles() {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        boolean isSysAdmin = authz.hasRole("SYS_ADMIN");
        List<SysRole> list = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        return ApiResponse.ok(list.stream()
                .filter(r -> isSysAdmin || !"SYS_ADMIN".equals(r.getRoleCode()))
                .map(r -> new RoleVO(r.getId(), r.getRoleCode(), r.getRoleName()))
                .toList());
    }

    /**
     * 用户分页查询。
     *
     * <p>支持过滤：username/realName/userType/deptId/enabled/roleCode。</p>
     */
    @GetMapping("/users")
    public ApiResponse<PageResult<UserRow>> pageUsers(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String teacherNo,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String roleCode
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");

        Set<Long> roleUserIds = null;
        if (roleCode != null && !roleCode.isBlank()) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode.trim()));
            if (role == null) {
                return ApiResponse.ok(PageResult.of(0, List.of()));
            }
            List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, role.getId()))
                    .stream().map(SysUserRole::getUserId).distinct().toList();
            roleUserIds = new HashSet<>(ids);
            if (roleUserIds.isEmpty()) {
                return ApiResponse.ok(PageResult.of(0, List.of()));
            }
        }

        Page<SysUser> page = userMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .like(username != null && !username.isBlank(), SysUser::getUsername, username)
                        .like(realName != null && !realName.isBlank(), SysUser::getRealName, realName)
                        .eq(userType != null && !userType.isBlank(), SysUser::getUserType, userType)
                        .like(studentNo != null && !studentNo.isBlank(), SysUser::getStudentNo, studentNo)
                        .like(teacherNo != null && !teacherNo.isBlank(), SysUser::getTeacherNo, teacherNo)
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .eq(enabled != null, SysUser::getEnabled, enabled)
                        .in(roleUserIds != null, SysUser::getId, roleUserIds)
                        .orderByDesc(SysUser::getId)
        );

        List<SysUser> users = page.getRecords();
        Map<Long, List<String>> roleCodesByUser = getRoleCodesByUsers(users.stream().map(SysUser::getId).toList());

        List<UserRow> rows = users.stream()
                .map(u -> new UserRow(
                        u.getId(),
                        u.getUsername(),
                        u.getRealName(),
                        u.getUserType(),
                        u.getDeptId(),
                        u.getStudentNo(),
                        u.getTeacherNo(),
                        u.getClassId(),
                        u.getEnabled(),
                        roleCodesByUser.getOrDefault(u.getId(), List.of())
                ))
                .toList();
        return ApiResponse.ok(PageResult.of(page.getTotal(), rows));
    }

    /**
     * 编辑用户扩展资料（院系/班级/学号/工号）。
     *
     * <p><b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。</p>
     * <p><b>限制</b>：非 SYS_ADMIN 不允许操作 SYS_ADMIN 用户。</p>
     */
    @PutMapping("/users/{id}/profile")
    public ApiResponse<Void> updateProfile(@PathVariable Long id, @RequestBody @Valid UpdateProfileReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(404, "用户不存在");
        guardTargetSysAdmin(id);

        String type = u.getUserType();
        if ("STUDENT".equals(type)) {
            if (req.getDeptId() == null) throw new BizException(400, "学生必须设置院系");
            if (req.getClassId() != null) {
                DictClass c = classService.getById(req.getClassId());
                if (c == null || c.getEnabled() == null || c.getEnabled() != 1) throw new BizException(400, "班级不存在或已停用");
                if (!Objects.equals(c.getDeptId(), req.getDeptId())) throw new BizException(400, "班级与院系不匹配");
            }
            SysUser upd = new SysUser();
            upd.setId(id);
            upd.setDeptId(req.getDeptId());
            upd.setClassId(req.getClassId());
            upd.setStudentNo(blankToNull(req.getStudentNo()));
            try {
                userMapper.updateById(upd);
            } catch (DuplicateKeyException e) {
                throw new BizException(400, "学号已存在");
            } catch (DataAccessException e) {
                // 常见原因：未执行迁移脚本导致 sys_user 缺少 student_no/class_id 列
                throw new BizException(400, "请先执行数据库迁移：扩展 sys_user(student_no/class_id)");
            }
            return ApiResponse.ok(null);
        }
        if ("TEACHER".equals(type)) {
            if (req.getDeptId() == null) throw new BizException(400, "教师必须设置院系");
            SysUser upd = new SysUser();
            upd.setId(id);
            upd.setDeptId(req.getDeptId());
            upd.setTeacherNo(blankToNull(req.getTeacherNo()));
            // ensure student-only fields cleared when editing teacher
            upd.setClassId(null);
            upd.setStudentNo(null);
            try {
                userMapper.updateById(upd);
            } catch (DuplicateKeyException e) {
                throw new BizException(400, "工号已存在");
            } catch (DataAccessException e) {
                // 常见原因：未执行迁移脚本导致 sys_user 缺少 teacher_no 列
                throw new BizException(400, "请先执行数据库迁移：扩展 sys_user(teacher_no)");
            }
            return ApiResponse.ok(null);
        }
        // ADMIN: allow deptId update only (optional)
        if ("ADMIN".equals(type)) {
            SysUser upd = new SysUser();
            upd.setId(id);
            if (req.getDeptId() != null) upd.setDeptId(req.getDeptId());
            userMapper.updateById(upd);
            return ApiResponse.ok(null);
        }
        throw new BizException(400, "不支持的用户类型: " + type);
    }

    /**
     * 启停用户（enabled 取反）。
     */
    @PostMapping("/users/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(404, "用户不存在");
        guardTargetSysAdmin(id);

        Integer next = (u.getEnabled() != null && u.getEnabled() == 1) ? 0 : 1;
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setEnabled(next);
        userMapper.updateById(upd);
        return ApiResponse.ok(null);
    }

    /**
     * 重置密码（默认 Admin123!）。
     */
    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(404, "用户不存在");
        guardTargetSysAdmin(id);

        String newHash = BCrypt.hashpw("Admin123!", BCrypt.gensalt(10));
        rbacService.updatePasswordHash(id, newHash);
        return ApiResponse.ok(null);
    }

    /**
     * 设置用户角色（覆盖式）。
     *
     * <p>限制：</p>
     * <ul>
     *   <li>SCHOOL_ADMIN 不允许分配/撤销 SYS_ADMIN。</li>
     *   <li>若目标用户是 SYS_ADMIN，仅 SYS_ADMIN 可修改。</li>
     * </ul>
     */
    @PutMapping("/users/{id}/roles")
    public ApiResponse<Void> setRoles(@PathVariable Long id, @RequestBody @Valid SetRolesReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(404, "用户不存在");
        guardTargetSysAdmin(id);

        boolean isSysAdmin = authz.hasRole("SYS_ADMIN");
        Set<String> roleCodes = new LinkedHashSet<>(req.getRoleCodes());
        if (!isSysAdmin && roleCodes.contains("SYS_ADMIN")) {
            throw new BizException(403, "无权限分配 SYS_ADMIN");
        }

        // load roles by codes
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, roleCodes));
        Map<String, SysRole> roleByCode = roles.stream().collect(Collectors.toMap(SysRole::getRoleCode, r -> r));
        for (String code : roleCodes) {
            if (!roleByCode.containsKey(code)) throw new BizException(400, "角色不存在: " + code);
        }

        // if not sysadmin, keep SYS_ADMIN role untouched (cannot remove/add)
        if (!isSysAdmin) {
            List<String> current = rbacService.getRoleCodes(id);
            if (current.contains("SYS_ADMIN")) {
                throw new BizException(403, "无权限修改 SYS_ADMIN 用户的角色");
            }
        }

        // replace bindings
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        for (SysRole r : roles) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(r.getId());
            userRoleMapper.insert(ur);
        }
        return ApiResponse.ok(null);
    }

    // ---------------- helpers ----------------

    private Map<Long, List<String>> getRoleCodesByUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<SysUserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (urs.isEmpty()) return Map.of();
        Set<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, SysRole> roleById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        Map<Long, List<String>> map = new HashMap<>();
        for (SysUserRole ur : urs) {
            SysRole r = roleById.get(ur.getRoleId());
            if (r == null) continue;
            map.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(r.getRoleCode());
        }
        map.values().forEach(list -> list.sort(String::compareTo));
        return map;
    }

    /**
     * 若目标用户拥有 SYS_ADMIN，只有 SYS_ADMIN 才允许继续。
     */
    private void guardTargetSysAdmin(Long targetUserId) {
        boolean isSysAdmin = authz.hasRole("SYS_ADMIN");
        if (isSysAdmin) return;
        if (rbacService.getRoleCodes(targetUserId).contains("SYS_ADMIN")) {
            throw new BizException(403, "无权限操作 SYS_ADMIN 用户");
        }
    }

    // ---------------- DTO/VO ----------------

    public record RoleVO(Long id, String roleCode, String roleName) {
    }

    public record UserRow(
            Long id,
            String username,
            String realName,
            String userType,
            Long deptId,
            String studentNo,
            String teacherNo,
            Long classId,
            Integer enabled,
            List<String> roles
    ) {
    }

    @Data
    public static class UpdateProfileReq {
        /**
         * 院系 ID。
         */
        private Long deptId;
        /**
         * 班级 ID（学生可选）。
         */
        private Long classId;
        /**
         * 学号（学生可选；非空唯一）。
         */
        private String studentNo;
        /**
         * 工号（教师可选；非空唯一）。
         */
        private String teacherNo;
    }

    @Data
    public static class SetRolesReq {
        @NotEmpty
        private List<@NotNull String> roleCodes;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

