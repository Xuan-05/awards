package com.university.awards.rbac.service;

import com.university.awards.rbac.entity.SysUser;

import java.util.List;

/**
 * RBAC（用户/角色）领域服务接口。
 *
 * <p>
 * 用途：
 * </p>
 * <ul>
 * <li>登录：根据 username 查用户、校验密码哈希。</li>
 * <li>鉴权：查询用户角色编码列表，支持 {@code hasRole} 判定。</li>
 * <li>账号维护：更新密码哈希。</li>
 * </ul>
 */
public interface RbacService {
    /**
     * 根据用户名查询用户（可能返回 null）。
     *
     * @param username 登录名（{@code sys_user.username}）
     */
    SysUser findByUsername(String username);

    /**
     * 根据学号查询学生用户（可能返回 null）。
     *
     * @param studentNo 学号（{@code sys_user.student_no}）
     */
    SysUser findByStudentNo(String studentNo);

    /**
     * 根据工号查询教师用户（可能返回 null）。
     *
     * @param teacherNo 工号（{@code sys_user.teacher_no}）
     */
    SysUser findByTeacherNo(String teacherNo);

    /**
     * 获取用户的角色编码列表（例如 CAPTAIN/SCHOOL_ADMIN 等）。
     *
     * @param userId 用户 ID
     */
    List<String> getRoleCodes(Long userId);

    /**
     * 获取用户（不存在则抛业务异常）。
     */
    SysUser mustGetUser(Long userId);

    /**
     * 判断用户是否拥有某个角色。
     */
    boolean hasRole(Long userId, String roleCode);

    /**
     * 更新密码哈希（BCrypt）。
     *
     * <p>
     * 注意：入参应为哈希后的字符串，不是明文。
     * </p>
     */
    void updatePasswordHash(Long userId, String passwordHash);

    /**
     * 更新当前用户联系方式（手机号、邮箱），允许置空。
     */
    void updatePhoneEmail(Long userId, String phone, String email);
}
