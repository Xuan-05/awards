package com.university.awards.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.audit.entity.BizAwardRecordAudit;
import com.university.awards.audit.mapper.BizAwardRecordAuditMapper;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.dict.entity.DictAwardLevel;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.mapper.DictAwardLevelMapper;
import com.university.awards.dict.mapper.DictCompetitionMapper;
import com.university.awards.dict.service.DictCompetitionService;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.record.mapper.BizAwardRecordFileMapper;
import com.university.awards.record.service.AwardRecordService;
import com.university.awards.record.vo.MyAwardVO;
import com.university.awards.systemconfig.service.SysConfigService;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.entity.BizTeamMember;
import com.university.awards.team.entity.BizTeamTeacher;
import com.university.awards.team.mapper.BizTeamMapper;
import com.university.awards.team.mapper.BizTeamMemberMapper;
import com.university.awards.team.mapper.BizTeamTeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwardRecordServiceImpl implements AwardRecordService {

    private final BizAwardRecordMapper recordMapper;
    private final BizAwardRecordAuditMapper auditMapper;
    private final DictCompetitionService competitionService;
    private final DictCompetitionMapper competitionMapper;
    private final DictAwardLevelMapper awardLevelMapper;
    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final BizTeamTeacherMapper teamTeacherMapper;
    private final AuthzService authz;
    private final BizAwardRecordFileMapper recordFileMapper;
    private final SysConfigService configService;
    private static final String USER_TYPE_TEACHER = "teacher";
    private static final String ROLE_CAPTAIN = "队长";
    private static final String ROLE_MEMBER = "队员";
    private static final String ROLE_TEACHER = "指导教师";
    private static final String TEAM_NAME_FALLBACK = "已解散的团队";

    /**
     * 新建获奖记录（草稿）。
     *
     * <p>
     * 关键点：
     * </p>
     * <ul>
     * <li>创建后状态固定为 DRAFT。</li>
     * <li>ownerDeptId 取团队的 ownerDeptId（用于后续管理员数据范围/统计）。</li>
     * <li>submitterUserId 固定为创建者（普通用户的数据范围校验依据）。</li>
     * </ul>
     */
    @Override
    @Transactional
    public Long create(BizAwardRecord record) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        if (record.getTeamId() == null)
            throw new BizException(400, "teamId 必填");
        if (record.getCompetitionId() == null)
            throw new BizException(400, "competitionId 必填");

        BizTeam team = teamMapper.selectById(record.getTeamId());
        if (team == null)
            throw new BizException(404, "团队不存在");

        DictCompetition comp = competitionService.getById(record.getCompetitionId());
        if (comp == null)
            throw new BizException(400, "竞赛字典不存在");

        BizAwardRecord e = new BizAwardRecord();
        e.setTeamId(record.getTeamId());
        e.setCompetitionId(record.getCompetitionId());
        e.setCompetitionCategoryId(comp.getCategoryId());
        e.setAwardScopeId(record.getAwardScopeId());
        e.setAwardLevelId(record.getAwardLevelId());
        e.setAwardDate(record.getAwardDate());
        e.setSemester(record.getSemester());
        e.setProjectName(record.getProjectName());
        e.setTeamAwardCount(record.getTeamAwardCount() == null ? 1 : record.getTeamAwardCount());
        e.setOwnerDeptId(team.getOwnerDeptId());
        e.setStatus("DRAFT");
        e.setSubmitterUserId(uid); // 作为创建者（数据范围依据）
        e.setVersion(0);
        e.setDeleted(0);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setRemark(record.getRemark());

        recordMapper.insert(e);
        return e.getId();
    }

    /**
     * 更新获奖记录。
     *
     * <p>
     * 状态机约束：
     * </p>
     * <ul>
     * <li>仅 DRAFT / SCHOOL_REJECTED 允许修改。</li>
     * </ul>
     *
     * <p>
     * 数据权限：
     * </p>
     * <ul>
     * <li>普通用户：只能修改自己提交的记录（submitterUserId=自己）。</li>
     * <li>管理员：SCHOOL_ADMIN/SYS_ADMIN 可修改（MVP 允许，后续可收紧）。</li>
     * </ul>
     */
    @Override
    public void update(Long id, BizAwardRecord record) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizAwardRecord old = recordMapper.selectById(id);
        if (old == null)
            throw new BizException(404, "记录不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")
                && (old.getSubmitterUserId() == null || !uid.equals(old.getSubmitterUserId()))) {
            throw new BizException(403, "无权限");
        }
        if (!"DRAFT".equals(old.getStatus()) && !"SCHOOL_REJECTED".equals(old.getStatus())) {
            throw new BizException(400, "当前状态不允许修改");
        }

        DictCompetition comp = competitionService.getById(record.getCompetitionId());
        if (comp == null)
            throw new BizException(400, "竞赛字典不存在");

        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(id);
        upd.setVersion(old.getVersion());
        upd.setCompetitionId(record.getCompetitionId());
        upd.setCompetitionCategoryId(comp.getCategoryId());
        upd.setAwardScopeId(record.getAwardScopeId());
        upd.setAwardLevelId(record.getAwardLevelId());
        upd.setAwardDate(record.getAwardDate());
        upd.setSemester(record.getSemester());
        upd.setProjectName(record.getProjectName());
        upd.setTeamAwardCount(record.getTeamAwardCount());
        upd.setRemark(record.getRemark());
        recordMapper.updateById(upd);
    }

    /**
     * 获取记录详情（含数据权限校验）。
     */
    @Override
    public BizAwardRecord get(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizAwardRecord e = recordMapper.selectById(id);
        if (e == null || e.getDeleted() != null && e.getDeleted() == 1)
            throw new BizException(404, "记录不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")
                && (e.getSubmitterUserId() == null || !uid.equals(e.getSubmitterUserId()))) {
            throw new BizException(403, "无权限");
        }
        return e;
    }

    /**
     * 查询“我提交的记录”分页列表。
     *
     * <p>
     * MVP 规则：按 submitterUserId=当前用户过滤。
     * </p>
     */
    @Override
    public PageResult<BizAwardRecord> myPage(String status, Long teamId, Long competitionId, String semester,
            long pageNo, long pageSize) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        // MVP：仅返回当前用户提交的记录（submitterUserId=自己）
        Page<BizAwardRecord> page = recordMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<BizAwardRecord>()
                        .eq(status != null && !status.isBlank(), BizAwardRecord::getStatus, status)
                        .eq(teamId != null, BizAwardRecord::getTeamId, teamId)
                        .eq(competitionId != null, BizAwardRecord::getCompetitionId, competitionId)
                        .eq(semester != null && !semester.isBlank(), BizAwardRecord::getSemester, semester)
                        .eq(BizAwardRecord::getDeleted, 0)
                        .eq(BizAwardRecord::getSubmitterUserId, uid)
                        .orderByDesc(BizAwardRecord::getUpdatedAt));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public List<MyAwardVO> getMyAwards(Long userId, String userType) {
        Map<Long, String> roleByTeam = USER_TYPE_TEACHER.equalsIgnoreCase(userType)
                ? getTeacherTeams(userId)
                : getStudentTeams(userId);
        if (roleByTeam.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> teamIds = new ArrayList<>(roleByTeam.keySet());
        List<BizAwardRecord> records = recordMapper.selectList(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getStatus, "APPROVED")
                .eq(BizAwardRecord::getDeleted, 0)
                .in(BizAwardRecord::getTeamId, teamIds)
                .orderByDesc(BizAwardRecord::getAwardDate)
                .orderByDesc(BizAwardRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, BizTeam> teamMap = loadTeamMap(records);
        Map<Long, String> competitionNameMap = loadCompetitionNameMap(records);
        Map<Long, String> awardLevelNameMap = loadAwardLevelNameMap(records);
        List<MyAwardVO> out = new ArrayList<>(records.size());
        for (BizAwardRecord record : records) {
            MyAwardVO vo = new MyAwardVO();
            BizTeam team = teamMap.get(record.getTeamId());
            vo.setRecordId(record.getId());
            vo.setTeamId(record.getTeamId());
            vo.setTeamDeleted(team != null && Integer.valueOf(1).equals(team.getDeleted()));
            vo.setTeamName(team != null && team.getTeamName() != null && !team.getTeamName().isBlank()
                    ? team.getTeamName()
                    : TEAM_NAME_FALLBACK);
            vo.setCompetitionName(competitionNameMap.get(record.getCompetitionId()));
            vo.setAwardName(record.getProjectName());
            vo.setAwardLevel(awardLevelNameMap.get(record.getAwardLevelId()));
            vo.setAwardTime(record.getAwardDate());
            vo.setRole(roleByTeam.getOrDefault(record.getTeamId(), ROLE_MEMBER));
            out.add(vo);
        }
        return out;
    }

    private Map<Long, String> getStudentTeams(Long userId) {
        List<BizTeamMember> memberships = teamMemberMapper.selectList(new LambdaQueryWrapper<BizTeamMember>()
                .eq(BizTeamMember::getUserId, userId)
                .eq(BizTeamMember::getJoinStatus, "ACCEPTED"));
        if (memberships.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> roleByTeam = new LinkedHashMap<>();
        for (BizTeamMember member : memberships) {
            if (member.getTeamId() == null) {
                continue;
            }
            String role = Integer.valueOf(1).equals(member.getIsCaptain()) ? ROLE_CAPTAIN : ROLE_MEMBER;
            roleByTeam.merge(member.getTeamId(), role, (oldRole, newRole) -> ROLE_CAPTAIN.equals(oldRole) ? oldRole : newRole);
        }
        return roleByTeam;
    }

    private Map<Long, String> getTeacherTeams(Long userId) {
        List<BizTeamTeacher> relations = teamTeacherMapper.selectList(new LambdaQueryWrapper<BizTeamTeacher>()
                .eq(BizTeamTeacher::getTeacherUserId, userId)
                .eq(BizTeamTeacher::getJoinStatus, "ACCEPTED"));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> roleByTeam = new LinkedHashMap<>();
        for (BizTeamTeacher relation : relations) {
            if (relation.getTeamId() != null) {
                roleByTeam.put(relation.getTeamId(), ROLE_TEACHER);
            }
        }
        return roleByTeam;
    }

    private Map<Long, BizTeam> loadTeamMap(List<BizAwardRecord> records) {
        Set<Long> teamIds = records.stream().map(BizAwardRecord::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BizTeam> teams = teamMapper.selectBatchIds(teamIds);
        return teams.stream().collect(Collectors.toMap(BizTeam::getId, t -> t, (a, b) -> a));
    }

    private Map<Long, String> loadCompetitionNameMap(List<BizAwardRecord> records) {
        Set<Long> competitionIds = records.stream().map(BizAwardRecord::getCompetitionId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (competitionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DictCompetition> competitions = competitionMapper.selectBatchIds(competitionIds);
        return competitions.stream().collect(Collectors.toMap(DictCompetition::getId, DictCompetition::getCompetitionName, (a, b) -> a));
    }

    private Map<Long, String> loadAwardLevelNameMap(List<BizAwardRecord> records) {
        Set<Long> awardLevelIds = records.stream().map(BizAwardRecord::getAwardLevelId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (awardLevelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DictAwardLevel> levels = awardLevelMapper.selectBatchIds(awardLevelIds);
        return levels.stream().collect(Collectors.toMap(DictAwardLevel::getId, DictAwardLevel::getLevelName, (a, b) -> a));
    }

    /**
     * 提交记录进入校级审核队列。
     *
     * <p>
     * 状态机：
     * </p>
     * DRAFT / SCHOOL_REJECTED -> PENDING_SCHOOL
     *
     * <p>
     * 配置影响：
     * </p>
     * <ul>
     * <li>record_attachment_required=true 时，提交前必须至少上传 1 个附件。</li>
     * </ul>
     */
    @Override
    @Transactional
    public void submit(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizAwardRecord e = recordMapper.selectById(id);
        if (e == null)
            throw new BizException(404, "记录不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")
                && (e.getSubmitterUserId() == null || !uid.equals(e.getSubmitterUserId()))) {
            throw new BizException(403, "无权限");
        }
        if (!"DRAFT".equals(e.getStatus()) && !"SCHOOL_REJECTED".equals(e.getStatus())) {
            throw new BizException(400, "当前状态不允许提交");
        }

        DictCompetition comp = competitionService.getById(e.getCompetitionId());
        if (comp == null || comp.getEnabled() == null || comp.getEnabled() != 1) {
            throw new BizException(400, "竞赛字典不存在或未启用");
        }

        // 系统参数：是否要求“提交前必须上传附件”（默认 false）
        Boolean required = configService.getBool("record_attachment_required", false);
        if (Boolean.TRUE.equals(required)) {
            long fileCnt = recordFileMapper
                    .selectCount(new LambdaQueryWrapper<com.university.awards.record.entity.BizAwardRecordFile>()
                            .eq(com.university.awards.record.entity.BizAwardRecordFile::getRecordId, id)
                            .eq(com.university.awards.record.entity.BizAwardRecordFile::getDeleted, 0));
            if (fileCnt <= 0)
                throw new BizException(400, "请先上传至少一个附件");
        }

        String from = e.getStatus();
        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(id);
        upd.setVersion(e.getVersion());
        upd.setStatus("PENDING_SCHOOL");
        upd.setSubmitterUserId(uid);
        upd.setSubmitTime(LocalDateTime.now());
        recordMapper.updateById(upd);

        BizAwardRecordAudit audit = new BizAwardRecordAudit();
        audit.setRecordId(id);
        audit.setNodeType("SCHOOL");
        audit.setActionType("SUBMIT");
        audit.setFromStatus(from);
        audit.setToStatus("PENDING_SCHOOL");
        audit.setAuditorUserId(uid);
        audit.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(audit);
    }

    /**
     * 撤回待审记录回到草稿。
     *
     * <p>
     * 状态机：
     * </p>
     * PENDING_SCHOOL -> DRAFT
     */
    @Override
    public void withdraw(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizAwardRecord e = recordMapper.selectById(id);
        if (e == null)
            throw new BizException(404, "记录不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")
                && (e.getSubmitterUserId() == null || !uid.equals(e.getSubmitterUserId()))) {
            throw new BizException(403, "无权限");
        }
        if (!"PENDING_SCHOOL".equals(e.getStatus())) {
            throw new BizException(400, "当前状态不允许撤回");
        }
        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(id);
        upd.setVersion(e.getVersion());
        upd.setStatus("DRAFT");
        recordMapper.updateById(upd);
    }

    /**
     * 删除草稿（逻辑删除 deleted=1）。
     *
     * <p>
     * 状态机约束：仅 DRAFT 可删除。
     * </p>
     */
    @Override
    public void deleteDraft(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizAwardRecord e = recordMapper.selectById(id);
        if (e == null)
            return;
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")
                && (e.getSubmitterUserId() == null || !uid.equals(e.getSubmitterUserId()))) {
            throw new BizException(403, "无权限");
        }
        if (!"DRAFT".equals(e.getStatus()))
            throw new BizException(400, "仅草稿可删除");
        BizAwardRecord upd = new BizAwardRecord();
        upd.setId(id);
        upd.setVersion(e.getVersion());
        upd.setDeleted(1);
        recordMapper.updateById(upd);
    }
}
