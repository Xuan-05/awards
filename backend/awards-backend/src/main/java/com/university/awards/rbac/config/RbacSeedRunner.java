package com.university.awards.rbac.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.dept.entity.SysDept;
import com.university.awards.dept.mapper.SysDeptMapper;
import com.university.awards.rbac.entity.SysRole;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.entity.SysUserRole;
import com.university.awards.rbac.mapper.SysRoleMapper;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RbacSeedRunner implements CommandLineRunner {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void run(String... args) {
        SysDept dept = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeptCode, "CS"));
        if (dept == null)
            return;

        // Note: CAPTAIN role is removed - team captain is determined by
        // biz_team.captain_id, not a global role
        Map<String, String> roleMap = Map.of(
                "TEACHER", "指导教师",
                "DEPT_ADMIN", "院系管理员",
                "SCHOOL_ADMIN", "校级管理员",
                "SYS_ADMIN", "系统管理员");

        // Ensure roles exist
        roleMap.forEach((code, name) -> {
            SysRole r = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (r == null) {
                SysRole ins = new SysRole();
                ins.setRoleCode(code);
                ins.setRoleName(name);
                roleMapper.insert(ins);
            }
        });

        // Seed default users if absent (password: Admin123!)
        seedUser("school_admin", "校级管理员", "ADMIN", dept.getId(), "SCHOOL_ADMIN");
        seedUser("dept_admin", "院系管理员", "ADMIN", dept.getId(), "DEPT_ADMIN");
        seedUser("student1", "学生1", "STUDENT", dept.getId(), null); // No global role - captain is per-team
        seedUser("teacher1", "教师1", "TEACHER", dept.getId(), "TEACHER");
    }

    private void seedUser(String username, String realName, String userType, Long deptId, String roleCode) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (u == null) {
            SysUser ins = new SysUser();
            ins.setUsername(username);
            ins.setRealName(realName);
            ins.setUserType(userType);
            ins.setDeptId(deptId);
            ins.setEnabled(1);
            ins.setPasswordHash(BCrypt.hashpw("Admin123!", BCrypt.gensalt(10)));
            userMapper.insert(ins);
            u = ins;
        }

        // Skip role assignment if roleCode is null
        if (roleCode == null)
            return;

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null)
            return;

        SysUserRole ur = userRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, u.getId())
                .eq(SysUserRole::getRoleId, role.getId()));
        if (ur == null) {
            SysUserRole insUr = new SysUserRole();
            insUr.setUserId(u.getId());
            insUr.setRoleId(role.getId());
            userRoleMapper.insert(insUr);
        }
    }
}
