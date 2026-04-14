package com.university.awards.team.service;

import com.university.awards.common.PageResult;
import com.university.awards.team.dto.TeamAdminListRow;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.entity.BizTeamInvitation;

import java.util.List;

/**
 * 团队领域服务接口。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>团队创建/更新、我的团队查询。</li>
 *   <li>邀请成员/教师加入，受邀人接受/拒绝。</li>
 *   <li>队长移除成员/教师。</li>
 *   <li>团队成员/教师/邀请记录列表查询（用于前端详情页展示）。</li>
 * </ul>
 *
 * <p>权限与数据范围（由实现保证）：</p>
 * <ul>
 *   <li>队长：可邀请/移除。</li>
 *   <li>普通用户：仅能访问自己加入的团队详情及列表数据。</li>
 *   <li>SCHOOL_ADMIN/SYS_ADMIN：可访问任意团队详情及列表数据。</li>
 * </ul>
 */
public interface TeamService {
    /**
     * 创建团队，并自动将当前登录用户写入成员表为队长（ACCEPTED）。
     *
     * @param teamName 团队名称
     * @param ownerDeptId 归属院系 ID
     * @param remark 备注（可选）
     * @return teamId
     */
    Long createTeam(String teamName, Long ownerDeptId, String remark);

    /**
     * 查询我加入的团队列表（仅 ACCEPTED 成员关系）。
     */
    List<BizTeam> myTeams();

    /**
     * 管理端分页查询全部团队（权限由 Controller 校验 SCHOOL_ADMIN/SYS_ADMIN）。
     */
    PageResult<TeamAdminListRow> adminTeamPage(long pageNo, long pageSize, String teamName, String captainRealName, Long ownerDeptId);

    /**
     * 获取团队详情（含数据范围校验）。
     */
    BizTeam getTeam(Long id);

    /**
     * 更新团队基础信息（仅队长可操作）。
     */
    void updateTeam(Long id, String teamName, String remark);

    /**
     * 邀请成员加入团队（仅队长可操作）。
     *
     * @return invitationId
     */
    Long inviteMember(Long teamId, Long memberUserId);

    /**
     * 邀请指导教师加入团队（仅队长可操作）。
     *
     * @return invitationId
     */
    Long inviteTeacher(Long teamId, Long teacherUserId);

    /**
     * 接受邀请（仅被邀请人本人可操作）。
     *
     * <p>副作用：写入成员/教师关联记录，状态为 ACCEPTED。</p>
     */
    void acceptInvitation(Long invitationId);

    /**
     * 拒绝邀请（仅被邀请人本人可操作）。
     */
    void rejectInvitation(Long invitationId);

    /**
     * 查询我收到的邀请列表（跨团队）。
     */
    List<BizTeamInvitation> myInvitations();

    /**
     * 移除成员（仅队长可操作，且不能移除队长本人）。
     */
    void removeMember(Long teamId, Long memberUserId);

    /**
     * 调整团队成员展示顺序（仅队长可操作）。
     *
     * <p>队长固定为第 1 位（{@code member_order_no = 1}）。{@code orderedNonCaptainUserIds}
     * 为除队长外已接受成员的用户 ID 列表，顺序即第 2、3、4…位。</p>
     *
     * @param teamId                     团队 ID
     * @param orderedNonCaptainUserIds 非队长成员 userId 顺序；须与库中当前非队长 ACCEPTED 成员集合一致
     */
    void reorderMembers(Long teamId, List<Long> orderedNonCaptainUserIds);

    /**
     * 移除指导教师（仅队长可操作）。
     */
    void removeTeacher(Long teamId, Long teacherUserId);

    List<TeamMemberVO> listMembers(Long teamId);

    List<TeamTeacherVO> listTeachers(Long teamId);

    List<TeamInvitationVO> listInvitations(Long teamId);

    record UserBrief(Long id, String username, String realName, String userType, Long deptId) {
    }

    /**
     * 团队成员展示对象（面向前端）。
     *
     * <p>{@code user} 字段为用户简要信息：其中 {@code username} 在当前实现中可直接作为“学号/工号”。</p>
     */
    record TeamMemberVO(
            Long id,
            Long teamId,
            Long userId,
            Integer memberOrderNo,
            Integer isCaptain,
            String joinStatus,
            UserBrief user
    ) {
    }

    /**
     * 指导教师展示对象（面向前端）。
     */
    record TeamTeacherVO(
            Long id,
            Long teamId,
            Long teacherUserId,
            Integer teacherOrderNo,
            String joinStatus,
            UserBrief user
    ) {
    }

    /**
     * 团队邀请记录展示对象（面向前端）。
     */
    record TeamInvitationVO(
            Long id,
            Long teamId,
            Long inviteeUserId,
            String inviteeType,
            String status,
            Long inviterUserId,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            UserBrief invitee,
            UserBrief inviter
    ) {
    }
}

