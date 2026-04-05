package com.university.awards.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.university.awards.common.BizException;
import com.university.awards.rbac.entity.SysRole;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.entity.SysUserRole;
import com.university.awards.rbac.mapper.SysRoleMapper;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.mapper.SysUserRoleMapper;
import com.university.awards.rbac.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    @Override
    public SysUser findByStudentNo(String studentNo) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentNo, studentNo)
                .eq(SysUser::getUserType, "STUDENT"));
    }

    @Override
    public SysUser findByTeacherNo(String teacherNo) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTeacherNo, teacherNo)
                .eq(SysUser::getUserType, "TEACHER"));
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper
                .selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty())
            return List.of();
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                .stream().map(SysRole::getRoleCode).distinct().toList();
    }

    @Override
    public SysUser mustGetUser(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null)
            throw new BizException(401, "用户不存在");
        return u;
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        return getRoleCodes(userId).contains(roleCode);
    }

    @Override
    public void updatePasswordHash(Long userId, String passwordHash) {
        SysUser upd = new SysUser();
        upd.setId(userId);
        upd.setPasswordHash(passwordHash);
        userMapper.updateById(upd);
    }

    @Override
    public void updatePhoneEmail(Long userId, String phone, String email) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPhone, phone)
                .set(SysUser::getEmail, email));
    }
}
