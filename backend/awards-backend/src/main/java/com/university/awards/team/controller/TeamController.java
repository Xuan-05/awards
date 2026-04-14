package com.university.awards.team.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.service.RbacService;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.entity.BizTeamInvitation;
import com.university.awards.team.service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 团队模块对外接口。
 *
 * <p>
 * 核心概念：
 * </p>
 * <ul>
 * <li><b>团队</b>：{@code biz_team}，包含队长、归属院系等信息。</li>
 * <li><b>成员</b>：{@code biz_team_member}，含是否队长、排序、加入状态等。</li>
 * <li><b>指导教师</b>：{@code biz_team_teacher}。</li>
 * <li><b>邀请</b>：{@code biz_team_invitation}，队长邀请成员/教师加入，受邀人可接受/拒绝。</li>
 * </ul>
 *
 * <p>
 * 权限与数据范围：
 * </p>
 * <ul>
 * <li>普通用户：仅能查看自己所在团队的详情/列表数据。</li>
 * <li>SCHOOL_ADMIN/SYS_ADMIN：可查看任意团队详情/列表数据（用于监管）。</li>
 * <li>队长：可邀请/移除成员与指导教师。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final RbacService rbacService;

    /**
     * 创建团队。
     *
     * <p>
     * <b>权限</b>：需要登录；业务上建议仅队长角色创建（前端已做按钮限制）。
     * </p>
     *
     * @param req 创建请求
     * @return 新建团队 ID
     */
    @PostMapping("/teams")
    public ApiResponse<Long> createTeam(@RequestBody @Valid TeamCreateReq req) {
        return ApiResponse.ok(teamService.createTeam(req.getTeamName(), req.getOwnerDeptId(), req.getRemark()));
    }

    /**
     * 查询我加入的团队列表（仅 ACCEPTED 成员关系的团队）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     */
    @GetMapping("/teams/my")
    public ApiResponse<List<BizTeam>> myTeams() {
        return ApiResponse.ok(teamService.myTeams());
    }

    /**
     * 获取团队详情。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     * <p>
     * <b>数据范围</b>：非 SCHOOL_ADMIN/SYS_ADMIN 必须是团队成员（ACCEPTED）。
     * </p>
     */
    @GetMapping("/teams/{id}")
    public ApiResponse<BizTeam> teamDetail(@PathVariable Long id) {
        return ApiResponse.ok(teamService.getTeam(id));
    }

    /**
     * 获取团队成员列表（含用户简要信息）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     * <p>
     * <b>数据范围</b>：同 {@link #teamDetail(Long)}。
     * </p>
     *
     * @return 成员列表（每项包含 {@code user}：username/realName/userType/deptId）
     */
    @GetMapping("/teams/{id}/members")
    public ApiResponse<List<TeamService.TeamMemberVO>> members(@PathVariable Long id) {
        return ApiResponse.ok(teamService.listMembers(id));
    }

    /**
     * 获取团队指导教师列表（含用户简要信息）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     * <p>
     * <b>数据范围</b>：同 {@link #teamDetail(Long)}。
     * </p>
     */
    @GetMapping("/teams/{id}/teachers")
    public ApiResponse<List<TeamService.TeamTeacherVO>> teachers(@PathVariable Long id) {
        return ApiResponse.ok(teamService.listTeachers(id));
    }

    /**
     * 获取团队邀请记录列表（含邀请人/被邀请人用户简要信息）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     * <p>
     * <b>数据范围</b>：同 {@link #teamDetail(Long)}。
     * </p>
     */
    @GetMapping("/teams/{id}/invitations")
    public ApiResponse<List<TeamService.TeamInvitationVO>> invitations(@PathVariable Long id) {
        return ApiResponse.ok(teamService.listInvitations(id));
    }

    /**
     * 更新团队基本信息（名称/备注）。
     *
     * <p>
     * <b>权限</b>：需要登录；仅队长可更新。
     * </p>
     */
    @PutMapping("/teams/{id}")
    public ApiResponse<Void> updateTeam(@PathVariable Long id, @RequestBody @Valid TeamUpdateReq req) {
        teamService.updateTeam(id, req.getTeamName(), req.getOwnerDeptId(), req.getRemark());
        return ApiResponse.ok(null);
    }

    /**
     * 删除团队（软删除）。
     *
     * <p>
     * <b>权限</b>：需要登录；队长或 SCHOOL_ADMIN/SYS_ADMIN 可操作。
     * </p>
     */
    @DeleteMapping("/teams/{id}")
    public ApiResponse<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ApiResponse.ok(null);
    }

    /**
     * 邀请成员加入团队。
     *
     * <p>
     * <b>权限</b>：需要登录；仅队长可邀请。
     * </p>
     *
     * @param id  团队 ID
     * @param req 邀请请求（被邀请用户 ID）
     * @return 邀请记录 ID
     */
    @PostMapping("/teams/{id}/members/invite")
    public ApiResponse<Long> inviteMember(@PathVariable Long id, @RequestBody @Valid InviteReq req) {
        return ApiResponse.ok(teamService.inviteMember(id, req.getUserId()));
    }

    /**
     * 邀请指导教师加入团队。
     *
     * <p>
     * <b>权限</b>：需要登录；仅队长可邀请。
     * </p>
     */
    @PostMapping("/teams/{id}/teachers/invite")
    public ApiResponse<Long> inviteTeacher(@PathVariable Long id, @RequestBody @Valid InviteReq req) {
        return ApiResponse.ok(teamService.inviteTeacher(id, req.getUserId()));
    }

    /**
     * 调整团队成员顺序（仅队长）。
     *
     * <p>路径使用 {@code .../members/sort}，避免与 {@code DELETE .../members/{userId}} 在字面路径
     * {@code order} 上被误解析为 userId 而导致 PUT 返回 405。</p>
     *
     * <p>请求体 {@link ReorderMembersReq#getOrderedUserIds()} 为 <b>非队长</b> 成员的用户 ID 顺序；
     * 队长固定为第 1 位（服务端写 {@code member_order_no = 1}）。仅队长本人可调。</p>
     */
    @PutMapping("/teams/{id}/members/sort")
    public ApiResponse<Void> reorderMembers(@PathVariable Long id, @RequestBody @Valid ReorderMembersReq req) {
        List<Long> ids = req.getOrderedUserIds() != null ? req.getOrderedUserIds() : new ArrayList<>();
        teamService.reorderMembers(id, ids);
        return ApiResponse.ok(null);
    }

    /**
     * 移除团队成员。
     *
     * <p>
     * <b>权限</b>：需要登录；仅队长可移除；不能移除队长本人。
     * </p>
     * <p>路径为 {@code .../members/user/{userId}}，避免与 {@code .../members/sort} 等字面路径冲突。</p>
     */
    @DeleteMapping("/teams/{id}/members/user/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMember(id, userId);
        return ApiResponse.ok(null);
    }

    /**
     * 移除指导教师。
     *
     * <p>
     * <b>权限</b>：需要登录；仅队长可移除。
     * </p>
     */
    @DeleteMapping("/teams/{id}/teachers/{userId}")
    public ApiResponse<Void> removeTeacher(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeTeacher(id, userId);
        return ApiResponse.ok(null);
    }

    /**
     * 查询“我收到的邀请”列表（跨团队）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     */
    @GetMapping("/team-invitations/my")
    public ApiResponse<List<BizTeamInvitation>> myInvitations() {
        return ApiResponse.ok(teamService.myInvitations());
    }

    /**
     * 接受邀请。
     *
     * <p>
     * <b>权限</b>：需要登录；仅邀请的接收者本人可操作。
     * </p>
     */
    @PostMapping("/team-invitations/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        teamService.acceptInvitation(id);
        return ApiResponse.ok(null);
    }

    /**
     * 拒绝邀请。
     *
     * <p>
     * <b>权限</b>：需要登录；仅邀请的接收者本人可操作。
     * </p>
     */
    @PostMapping("/team-invitations/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        teamService.rejectInvitation(id);
        return ApiResponse.ok(null);
    }

    /**
     * 根据学号查询学生信息（用于邀请队员时预览）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     *
     * @param studentNo 学号
     * @return 用户简要信息（id, username, realName, userType, deptId）
     */
    @GetMapping("/users/by-student-no/{studentNo}")
    public ApiResponse<UserBriefVO> findByStudentNo(@PathVariable String studentNo) {
        SysUser u = rbacService.findByStudentNo(studentNo);
        if (u == null)
            return ApiResponse.ok(null);
        return ApiResponse
                .ok(new UserBriefVO(u.getId(), u.getUsername(), u.getRealName(), u.getUserType(), u.getDeptId()));
    }

    /**
     * 根据工号查询教师信息（用于邀请指导教师时预览）。
     *
     * <p>
     * <b>权限</b>：需要登录。
     * </p>
     *
     * @param teacherNo 工号
     * @return 用户简要信息
     */
    @GetMapping("/users/by-teacher-no/{teacherNo}")
    public ApiResponse<UserBriefVO> findByTeacherNo(@PathVariable String teacherNo) {
        SysUser u = rbacService.findByTeacherNo(teacherNo);
        if (u == null)
            return ApiResponse.ok(null);
        return ApiResponse
                .ok(new UserBriefVO(u.getId(), u.getUsername(), u.getRealName(), u.getUserType(), u.getDeptId()));
    }

    /**
     * 用户简要信息 VO（用于邀请时预览）。
     */
    @Data
    public static class UserBriefVO {
        private Long id;
        private String username;
        private String realName;
        private String userType;
        private Long deptId;

        public UserBriefVO(Long id, String username, String realName, String userType, Long deptId) {
            this.id = id;
            this.username = username;
            this.realName = realName;
            this.userType = userType;
            this.deptId = deptId;
        }
    }

    @Data
    public static class TeamCreateReq {
        /**
         * 团队名称。
         */
        @NotBlank
        private String teamName;
        /**
         * 团队归属院系 ID（用于统计与数据范围，通常取队长所属院系）。
         */
        @NotNull
        private Long ownerDeptId;
        /**
         * 备注（可选）。
         */
        private String remark;
    }

    @Data
    public static class TeamUpdateReq {
        /**
         * 团队名称。
         */
        @NotBlank
        private String teamName;
        /**
         * 团队归属院系 ID。
         */
        @NotNull
        private Long ownerDeptId;
        /**
         * 备注（可选）。
         */
        private String remark;
    }

    @Data
    public static class InviteReq {
        /**
         * 被邀请用户 ID（{@code sys_user.id}）。
         */
        @NotNull
        private Long userId;
    }

    @Data
    public static class ReorderMembersReq {
        /**
         * 非队长成员的用户 ID 顺序（与展示顺序一致）；可为空数组（团队仅队长时）。
         */
        private List<Long> orderedUserIds;
    }
}
