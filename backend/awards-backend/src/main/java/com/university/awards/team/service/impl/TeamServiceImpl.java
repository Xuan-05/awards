package com.university.awards.team.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.common.BizException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.PageResult;
import com.university.awards.team.dto.TeamAdminListRow;
import com.university.awards.message.service.MessageWriteService;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.systemconfig.service.SysConfigService;
import com.university.awards.team.entity.*;
import com.university.awards.team.mapper.*;
import com.university.awards.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper memberMapper;
    private final BizTeamTeacherMapper teacherMapper;
    private final BizTeamInvitationMapper invitationMapper;
    private final AuthzService authz;
    private final SysUserMapper userMapper;
    private final MessageWriteService messageWriteService;
    private final SysConfigService configService;

    /**
     * 创建团队。
     *
     * <p>默认规则：</p>
     * <ul>
     *   <li>创建者即队长（CAPTAIN），并自动成为团队成员（isCaptain=1）。</li>
     *   <li>团队状态默认 ACTIVE。</li>
     * </ul>
     */
    @Override
    @Transactional
    public Long createTeam(String teamName, Long ownerDeptId, String remark) {
        StpUtil.checkLogin();
        Long captainId = StpUtil.getLoginIdAsLong();

        BizTeam team = new BizTeam();
        team.setTeamName(teamName);
        team.setCaptainUserId(captainId);
        team.setOwnerDeptId(ownerDeptId);
        team.setStatus("ACTIVE");
        team.setRemark(remark);
        team.setDeleted(0);
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.insert(team);

        BizTeamMember captain = new BizTeamMember();
        captain.setTeamId(team.getId());
        captain.setUserId(captainId);
        captain.setMemberOrderNo(1);
        captain.setIsCaptain(1);
        captain.setJoinStatus("ACCEPTED");
        memberMapper.insert(captain);

        return team.getId();
    }

    @Override
    public List<BizTeam> myTeams() {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();

        List<Long> myTeamIds = memberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getUserId, uid)
                .eq(BizTeamMember::getJoinStatus, "ACCEPTED"))
                .stream().map(BizTeamMember::getTeamId).distinct().toList();

        if (myTeamIds.isEmpty()) return List.of();
        return teamMapper.selectList(new LambdaQueryWrapper<BizTeam>()
                .in(BizTeam::getId, myTeamIds)
                .eq(BizTeam::getDeleted, 0)
                .orderByDesc(BizTeam::getUpdatedAt));
    }

    @Override
    public PageResult<TeamAdminListRow> adminTeamPage(long pageNo, long pageSize, String teamName,
            String captainRealName, Long ownerDeptId) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        LambdaQueryWrapper<BizTeam> w = new LambdaQueryWrapper<>();
        w.eq(BizTeam::getDeleted, 0);
        if (teamName != null && !teamName.isBlank()) {
            w.like(BizTeam::getTeamName, teamName.trim());
        }
        if (ownerDeptId != null) {
            w.eq(BizTeam::getOwnerDeptId, ownerDeptId);
        }
        if (captainRealName != null && !captainRealName.isBlank()) {
            List<SysUser> capUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .like(SysUser::getRealName, captainRealName.trim()));
            Set<Long> capIds = capUsers.stream().map(SysUser::getId).collect(Collectors.toSet());
            if (capIds.isEmpty()) {
                return PageResult.of(0, List.of());
            }
            w.in(BizTeam::getCaptainUserId, capIds);
        }
        w.orderByDesc(BizTeam::getUpdatedAt);
        Page<BizTeam> page = teamMapper.selectPage(new Page<>(pageNo, pageSize), w);
        List<BizTeam> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(page.getTotal(), List.of());
        }
        Set<Long> teamIds = records.stream().map(BizTeam::getId).collect(Collectors.toSet());
        Set<Long> captainIds = records.stream().map(BizTeam::getCaptainUserId).collect(Collectors.toSet());
        Map<Long, SysUser> captainUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, captainIds)).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<BizTeamMember> allMembers = memberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                .in(BizTeamMember::getTeamId, teamIds)
                .eq(BizTeamMember::getJoinStatus, "ACCEPTED"));
        Map<Long, Long> memberCount = allMembers.stream()
                .collect(Collectors.groupingBy(BizTeamMember::getTeamId, Collectors.counting()));

        List<BizTeamTeacher> allTeachers = teacherMapper.selectList(new LambdaQueryWrapper<BizTeamTeacher>()
                .in(BizTeamTeacher::getTeamId, teamIds)
                .eq(BizTeamTeacher::getJoinStatus, "ACCEPTED"));
        Map<Long, Long> teacherCount = allTeachers.stream()
                .collect(Collectors.groupingBy(BizTeamTeacher::getTeamId, Collectors.counting()));

        List<TeamAdminListRow> rows = records.stream().map(t -> {
            SysUser cap = captainUsers.get(t.getCaptainUserId());
            String capName = cap != null ? cap.getRealName() : "-";
            long mc = memberCount.getOrDefault(t.getId(), 0L);
            long tc = teacherCount.getOrDefault(t.getId(), 0L);
            return new TeamAdminListRow(
                    t.getId(),
                    t.getTeamName(),
                    t.getCaptainUserId(),
                    capName,
                    t.getOwnerDeptId(),
                    mc,
                    tc,
                    t.getStatus(),
                    t.getCreatedAt());
        }).toList();
        return PageResult.of(page.getTotal(), rows);
    }
    @Override
    public BizTeam getTeam(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizTeam team = teamMapper.selectById(id);
        if (team == null || team.getDeleted() != null && team.getDeleted() == 1) throw new BizException(404, "团队不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")) {
            boolean inTeam = memberMapper.selectCount(new LambdaQueryWrapper<BizTeamMember>()
                    .eq(BizTeamMember::getTeamId, id)
                    .eq(BizTeamMember::getUserId, uid)
                    .eq(BizTeamMember::getJoinStatus, "ACCEPTED")) > 0;
            if (!inTeam) throw new BizException(403, "无权限");
        }
        return team;
    }

    @Override
    public void updateTeam(Long id, String teamName, String remark) {
        StpUtil.checkLogin();
        BizTeam team = teamMapper.selectById(id);
        if (team == null) throw new BizException(404, "团队不存在");
        if (!team.getCaptainUserId().equals(StpUtil.getLoginIdAsLong())) throw new BizException(403, "无权限操作团队");

        BizTeam upd = new BizTeam();
        upd.setId(id);
        upd.setTeamName(teamName);
        upd.setRemark(remark);
        teamMapper.updateById(upd);
    }

    @Override
    public Long inviteMember(Long teamId, Long memberUserId) {
        // 邀请普通成员
        return createInvitation(teamId, memberUserId, "MEMBER");
    }

    @Override
    public Long inviteTeacher(Long teamId, Long teacherUserId) {
        // 按系统参数限制指导教师人数（sys_config.max_teacher_count，默认 3）
        Integer max = configService.getInt("max_teacher_count", 3);
        if (max != null && max > 0) {
            long current = teacherMapper.selectCount(new LambdaQueryWrapper<BizTeamTeacher>()
                    .eq(BizTeamTeacher::getTeamId, teamId)
                    .eq(BizTeamTeacher::getJoinStatus, "ACCEPTED"));
            if (current >= max) throw new BizException(400, "指导教师数量已达上限：" + max);
        }
        return createInvitation(teamId, teacherUserId, "TEACHER");
    }

    /**
     * 创建邀请并写入“团队邀请”消息。
     *
     * <p>消息联动约定：</p>
     * <ul>
     *   <li>msgType=INVITATION（消息类型：团队邀请类）</li>
     *   <li>bizType=TEAM_INVITATION（业务类型：用于前端决定展示“邀请处理”卡片）</li>
     *   <li>bizId=invitation.id（用于前端加载邀请状态/处理）</li>
     * </ul>
     */
    private Long createInvitation(Long teamId, Long inviteeUserId, String type) {
        StpUtil.checkLogin();
        BizTeam team = teamMapper.selectById(teamId);
        if (team == null) throw new BizException(404, "团队不存在");
        if (!team.getCaptainUserId().equals(StpUtil.getLoginIdAsLong())) throw new BizException(403, "仅队长可邀请");

        BizTeamInvitation inv = new BizTeamInvitation();
        inv.setTeamId(teamId);
        inv.setInviteeUserId(inviteeUserId);
        inv.setInviteeType(type);
        inv.setStatus("PENDING");
        inv.setInviterUserId(StpUtil.getLoginIdAsLong());
        inv.setCreatedAt(LocalDateTime.now());
        inv.setUpdatedAt(LocalDateTime.now());
        invitationMapper.insert(inv);

        // 写一条消息给被邀请人：消息中心会展示，并可根据 bizType/bizId 联动“邀请处理”
        Long inviterId = StpUtil.getLoginIdAsLong();
        SysUser inviter = userMapper.selectById(inviterId);
        String inviterName = inviter == null ? ("用户#" + inviterId) : inviter.getRealName();
        String title = "团队邀请";
        String content = String.format("%s 邀请你加入团队「%s」（%s）。", inviterName, team.getTeamName(), "MEMBER".equals(type) ? "成员" : "指导教师");
        messageWriteService.write(inviteeUserId, "INVITATION", title, content, "TEAM_INVITATION", inv.getId());
        return inv.getId();
    }

    @Override
    @Transactional
    public void acceptInvitation(Long invitationId) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizTeamInvitation inv = invitationMapper.selectById(invitationId);
        if (inv == null) throw new BizException(404, "邀请不存在");
        if (!uid.equals(inv.getInviteeUserId())) throw new BizException(403, "无权限");
        if (!"PENDING".equals(inv.getStatus())) return;

        inv.setStatus("ACCEPTED");
        inv.setUpdatedAt(LocalDateTime.now());
        invitationMapper.updateById(inv);

        if ("MEMBER".equals(inv.getInviteeType())) {
            List<BizTeamMember> existing = memberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                    .eq(BizTeamMember::getTeamId, inv.getTeamId())
                    .eq(BizTeamMember::getJoinStatus, "ACCEPTED"));
            int nextOrder = existing.stream().mapToInt(BizTeamMember::getMemberOrderNo).max().orElse(0) + 1;
            if (nextOrder < 2) {
                nextOrder = 2;
            }
            BizTeamMember m = new BizTeamMember();
            m.setTeamId(inv.getTeamId());
            m.setUserId(uid);
            m.setMemberOrderNo(nextOrder);
            m.setIsCaptain(0);
            m.setJoinStatus("ACCEPTED");
            memberMapper.insert(m);
        } else if ("TEACHER".equals(inv.getInviteeType())) {
            BizTeamTeacher t = new BizTeamTeacher();
            t.setTeamId(inv.getTeamId());
            t.setTeacherUserId(uid);
            t.setTeacherOrderNo(1);
            t.setJoinStatus("ACCEPTED");
            teacherMapper.insert(t);
        }

        // 写一条消息给邀请人（通常是队长）：告知“邀请被接受”
        BizTeam team = teamMapper.selectById(inv.getTeamId());
        SysUser invitee = userMapper.selectById(uid);
        String inviteeName = invitee == null ? ("用户#" + uid) : invitee.getRealName();
        String title = "邀请已接受";
        String content = String.format("%s 已接受你对团队「%s」的邀请（%s）。", inviteeName, team == null ? ("#" + inv.getTeamId()) : team.getTeamName(), inv.getInviteeType());
        messageWriteService.write(inv.getInviterUserId(), "INVITATION", title, content, "TEAM_INVITATION", inv.getId());
    }

    @Override
    public void rejectInvitation(Long invitationId) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizTeamInvitation inv = invitationMapper.selectById(invitationId);
        if (inv == null) throw new BizException(404, "邀请不存在");
        if (!uid.equals(inv.getInviteeUserId())) throw new BizException(403, "无权限");
        if (!"PENDING".equals(inv.getStatus())) return;

        inv.setStatus("REJECTED");
        inv.setUpdatedAt(LocalDateTime.now());
        invitationMapper.updateById(inv);

        // 写一条消息给邀请人（通常是队长）：告知“邀请被拒绝”
        BizTeam team = teamMapper.selectById(inv.getTeamId());
        SysUser invitee = userMapper.selectById(uid);
        String inviteeName = invitee == null ? ("用户#" + uid) : invitee.getRealName();
        String title = "邀请已拒绝";
        String content = String.format("%s 已拒绝你对团队「%s」的邀请（%s）。", inviteeName, team == null ? ("#" + inv.getTeamId()) : team.getTeamName(), inv.getInviteeType());
        messageWriteService.write(inv.getInviterUserId(), "INVITATION", title, content, "TEAM_INVITATION", inv.getId());
    }

    @Override
    public List<BizTeamInvitation> myInvitations() {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        return invitationMapper.selectList(new LambdaQueryWrapper<BizTeamInvitation>()
                .eq(BizTeamInvitation::getInviteeUserId, uid)
                .orderByDesc(BizTeamInvitation::getCreatedAt));
    }

    @Override
    public void removeMember(Long teamId, Long memberUserId) {
        StpUtil.checkLogin();
        BizTeam team = teamMapper.selectById(teamId);
        if (team == null) throw new BizException(404, "团队不存在");
        Long uid = StpUtil.getLoginIdAsLong();
        if (!team.getCaptainUserId().equals(uid)) throw new BizException(403, "仅队长可移除成员");
        if (team.getCaptainUserId().equals(memberUserId)) throw new BizException(400, "不能移除队长");

        memberMapper.delete(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getTeamId, teamId)
                .eq(BizTeamMember::getUserId, memberUserId));
    }

    @Override
    @Transactional
    public void reorderMembers(Long teamId, List<Long> orderedNonCaptainUserIds) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizTeam team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(404, "团队不存在");
        }
        if (!team.getCaptainUserId().equals(uid)) {
            throw new BizException(403, "仅队长可调整成员顺序");
        }
        if (orderedNonCaptainUserIds == null) {
            orderedNonCaptainUserIds = List.of();
        }
        if (orderedNonCaptainUserIds.size() != new HashSet<>(orderedNonCaptainUserIds).size()) {
            throw new BizException(400, "成员顺序列表存在重复用户");
        }

        List<BizTeamMember> members = memberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getTeamId, teamId)
                .eq(BizTeamMember::getJoinStatus, "ACCEPTED"));
        Set<Long> expectedNonCaptain = members.stream()
                .filter(m -> m.getIsCaptain() == null || m.getIsCaptain() == 0)
                .map(BizTeamMember::getUserId)
                .collect(Collectors.toSet());
        if (orderedNonCaptainUserIds.size() != expectedNonCaptain.size()
                || !new HashSet<>(orderedNonCaptainUserIds).equals(expectedNonCaptain)) {
            throw new BizException(400, "成员顺序列表与当前团队不一致");
        }

        for (BizTeamMember m : members) {
            if (m.getIsCaptain() != null && m.getIsCaptain() == 1) {
                m.setMemberOrderNo(1);
                memberMapper.updateById(m);
            }
        }
        for (int i = 0; i < orderedNonCaptainUserIds.size(); i++) {
            Long userId = orderedNonCaptainUserIds.get(i);
            BizTeamMember m = members.stream()
                    .filter(x -> Objects.equals(x.getUserId(), userId)
                            && (x.getIsCaptain() == null || x.getIsCaptain() == 0))
                    .findFirst()
                    .orElseThrow(() -> new BizException(400, "成员不存在"));
            m.setMemberOrderNo(i + 2);
            memberMapper.updateById(m);
        }
    }

    @Override
    public void removeTeacher(Long teamId, Long teacherUserId) {
        StpUtil.checkLogin();
        BizTeam team = teamMapper.selectById(teamId);
        if (team == null) throw new BizException(404, "团队不存在");
        Long uid = StpUtil.getLoginIdAsLong();
        if (!team.getCaptainUserId().equals(uid)) throw new BizException(403, "仅队长可移除指导老师");

        teacherMapper.delete(new LambdaQueryWrapper<BizTeamTeacher>()
                .eq(BizTeamTeacher::getTeamId, teamId)
                .eq(BizTeamTeacher::getTeacherUserId, teacherUserId));
    }

    @Override
    public List<TeamMemberVO> listMembers(Long teamId) {
        // reuse existing data-scope check
        getTeam(teamId);

        List<BizTeamMember> list = memberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getTeamId, teamId)
                .orderByDesc(BizTeamMember::getIsCaptain)
                .orderByAsc(BizTeamMember::getMemberOrderNo)
                .orderByAsc(BizTeamMember::getId));

        Map<Long, UserBrief> users = loadUserBriefs(list.stream().map(BizTeamMember::getUserId).collect(Collectors.toSet()));
        return list.stream()
                .map(m -> new TeamMemberVO(
                        m.getId(),
                        m.getTeamId(),
                        m.getUserId(),
                        m.getMemberOrderNo(),
                        m.getIsCaptain(),
                        m.getJoinStatus(),
                        users.get(m.getUserId())
                ))
                .toList();
    }

    @Override
    public List<TeamTeacherVO> listTeachers(Long teamId) {
        getTeam(teamId);

        List<BizTeamTeacher> list = teacherMapper.selectList(new LambdaQueryWrapper<BizTeamTeacher>()
                .eq(BizTeamTeacher::getTeamId, teamId)
                .orderByAsc(BizTeamTeacher::getTeacherOrderNo)
                .orderByAsc(BizTeamTeacher::getId));

        Map<Long, UserBrief> users = loadUserBriefs(list.stream().map(BizTeamTeacher::getTeacherUserId).collect(Collectors.toSet()));
        return list.stream()
                .map(t -> new TeamTeacherVO(
                        t.getId(),
                        t.getTeamId(),
                        t.getTeacherUserId(),
                        t.getTeacherOrderNo(),
                        t.getJoinStatus(),
                        users.get(t.getTeacherUserId())
                ))
                .toList();
    }

    @Override
    public List<TeamInvitationVO> listInvitations(Long teamId) {
        getTeam(teamId);

        List<BizTeamInvitation> list = invitationMapper.selectList(new LambdaQueryWrapper<BizTeamInvitation>()
                .eq(BizTeamInvitation::getTeamId, teamId)
                .orderByDesc(BizTeamInvitation::getCreatedAt)
                .orderByDesc(BizTeamInvitation::getId));

        Set<Long> ids = list.stream()
                .flatMap(x -> java.util.stream.Stream.of(x.getInviteeUserId(), x.getInviterUserId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserBrief> users = loadUserBriefs(ids);

        return list.stream()
                .map(inv -> new TeamInvitationVO(
                        inv.getId(),
                        inv.getTeamId(),
                        inv.getInviteeUserId(),
                        inv.getInviteeType(),
                        inv.getStatus(),
                        inv.getInviterUserId(),
                        inv.getCreatedAt(),
                        inv.getUpdatedAt(),
                        users.get(inv.getInviteeUserId()),
                        users.get(inv.getInviterUserId())
                ))
                .toList();
    }

    private Map<Long, UserBrief> loadUserBriefs(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(userIds).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(SysUser::getId, this::toBrief, (a, b) -> a));
    }

    private UserBrief toBrief(SysUser u) {
        if (u == null) return null;
        return new UserBrief(u.getId(), u.getUsername(), u.getRealName(), u.getUserType(), u.getDeptId());
    }
}

