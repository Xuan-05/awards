package com.university.awards.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.service.DictClassService;
import com.university.awards.log.entity.SysLoginLog;
import com.university.awards.log.service.SysLoginLogService;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.service.RbacService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证与账号相关接口。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>本项目采用 Sa-Token 进行登录态管理，登录成功后返回 token（前端通过 {@code Authorization: Bearer <token>} 发送）。</li>
 *   <li>账号数据来自本地 RBAC 表 {@code sys_user}/{@code sys_role}（后续可替换为外部权威系统同步）。</li>
 *   <li>密码存储为 BCrypt 哈希（{@code password_hash}），接口不返回明文密码。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RbacService rbacService;
    private final DictClassService classService;
    private final SysLoginLogService loginLogService;

    /**
     * 登录接口。
     *
     * <p><b>权限</b>：无需登录。</p>
     * <p><b>失败场景</b>：</p>
     * <ul>
     *   <li>用户名不存在/被禁用/密码错误：返回业务码 401。</li>
     * </ul>
     *
     * @param req 登录请求（用户名/密码）
     * @return token + 当前用户信息（含角色列表）
     */
    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@RequestBody @Valid LoginReq req, HttpServletRequest request) {
        String loginId = req.getUsername() == null ? "" : req.getUsername().trim();
        SysUser user = loadUserByLoginId(loginId);
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            writeLoginLog(loginId, null, 0, "用户名或密码错误", request);
            throw new BizException(401, "用户名或密码错误");
        }
        if (!BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            writeLoginLog(loginId, user.getId(), 0, "用户名或密码错误", request);
            throw new BizException(401, "用户名或密码错误");
        }
        StpUtil.login(user.getId());

        LoginResp resp = new LoginResp();
        resp.setToken(StpUtil.getTokenValue());
        resp.setUserInfo(buildUserInfo(user));
        writeLoginLog(loginId, user.getId(), 1, null, request);
        return ApiResponse.ok(resp);
    }

    /**
     * 获取当前登录用户信息。
     *
     * <p><b>权限</b>：需要登录。</p>
     *
     * @return 当前用户信息（用于前端鉴权与菜单渲染）
     */
    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = rbacService.mustGetUser(userId);
        return ApiResponse.ok(buildUserInfo(user));
    }

    /**
     * 退出登录。
     *
     * <p><b>权限</b>：无需登录（幂等）。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return ApiResponse.ok(null);
    }

    /**
     * 修改密码。
     *
     * <p><b>权限</b>：需要登录。</p>
     * <p><b>失败场景</b>：</p>
     * <ul>
     *   <li>旧密码不正确：返回业务码 400。</li>
     * </ul>
     *
     * @param req 修改密码请求（旧密码/新密码）
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordReq req) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = rbacService.mustGetUser(userId);
        if (!BCrypt.checkpw(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "旧密码不正确");
        }
        String newHash = BCrypt.hashpw(req.getNewPassword(), BCrypt.gensalt(10));
        rbacService.updatePasswordHash(userId, newHash);
        return ApiResponse.ok(null);
    }

    @Data
    public static class LoginReq {
        /**
         * 登录账号（本地账号体系中为 {@code sys_user.username}）。
         * <p>当前实现中也可被用作“学号/工号”的展示字段（不额外建字段）。</p>
         */
        @NotBlank
        private String username;
        /**
         * 明文密码，仅用于接口输入；服务端会进行 BCrypt 校验，不会回传。
         */
        @NotBlank
        private String password;
    }

    @Data
    public static class ChangePasswordReq {
        /**
         * 旧密码（明文）。
         */
        @NotBlank
        private String oldPassword;
        /**
         * 新密码（明文）。
         */
        @NotBlank
        private String newPassword;
    }

    @Data
    public static class LoginResp {
        /**
         * 登录 token（Sa-Token）。
         */
        private String token;
        /**
         * 当前用户信息（用于前端显示与 RBAC 授权）。
         */
        private UserInfo userInfo;
    }

    /**
     * 当前用户信息。
     *
     * @param id 用户 ID（{@code sys_user.id}）
     * @param realName 姓名（{@code sys_user.real_name}）
     * @param userType 用户类型（STUDENT/TEACHER/ADMIN）
     * @param deptId 所属院系 ID（{@code sys_user.dept_id}）
     * @param studentNo 学生学号（仅学生；可空）
     * @param teacherNo 教师工号（仅教师；可空）
     * @param classId 班级 ID（仅学生；可空）
     * @param className 班级名称（仅学生；可空）
     * @param roles 角色编码列表（CAPTAIN/TEACHER/DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN）
     */
    public record UserInfo(
            Long id,
            String realName,
            String userType,
            Long deptId,
            String studentNo,
            String teacherNo,
            Long classId,
            String className,
            String phone,
            String email,
            List<String> roles
    ) {
    }


    /**
     * 更新当前用户手机号、邮箱（所有身份可用）。
     */
    @PutMapping("/profile")
    public ApiResponse<Void> updateMyProfile(@RequestBody UpdateMyProfileReq req) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        String phone = blankToNull(req.getPhone());
        String email = blankToNull(req.getEmail());
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(400, "手机号格式不正确");
        }
        if (email != null && !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new BizException(400, "邮箱格式不正确");
        }
        rbacService.updatePhoneEmail(userId, phone, email);
        return ApiResponse.ok(null);
    }

    private UserInfo buildUserInfo(SysUser user) {
        List<String> roles = rbacService.getRoleCodes(user.getId());
        Long classId = user.getClassId();
        String className = null;
        if (classId != null) {
            DictClass c = classService.getById(classId);
            if (c != null) className = c.getClassName();
        }
        return new UserInfo(
                user.getId(),
                user.getRealName(),
                user.getUserType(),
                user.getDeptId(),
                user.getStudentNo(),
                user.getTeacherNo(),
                classId,
                className,
                user.getPhone(),
                user.getEmail(),
                roles
        );
    }

    @Data
    public static class UpdateMyProfileReq {
        private String phone;
        private String email;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private SysUser loadUserByLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        // 优先按学号/工号识别，兼容旧账号（username）登录。
        SysUser byStudentNo = rbacService.findByStudentNo(loginId);
        if (byStudentNo != null) return byStudentNo;
        SysUser byTeacherNo = rbacService.findByTeacherNo(loginId);
        if (byTeacherNo != null) return byTeacherNo;
        return rbacService.findByUsername(loginId);
    }

    private void writeLoginLog(String username, Long userId, int success, String err, HttpServletRequest request) {
        try {
            SysLoginLog l = new SysLoginLog();
            l.setUsername(username == null ? "" : username);
            l.setUserId(userId);
            l.setSuccessFlag(success);
            l.setErrorMessage(err);
            l.setIp(request == null ? null : request.getRemoteAddr());
            l.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
            l.setCreatedAt(LocalDateTime.now());
            loginLogService.save(l);
        } catch (Throwable ignore) {
        }
    }
}

