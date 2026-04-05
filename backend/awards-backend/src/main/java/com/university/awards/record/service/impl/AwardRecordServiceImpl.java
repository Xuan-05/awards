package com.university.awards.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.audit.entity.BizAwardRecordAudit;
import com.university.awards.audit.mapper.BizAwardRecordAuditMapper;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.service.DictCompetitionService;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.record.mapper.BizAwardRecordFileMapper;
import com.university.awards.record.service.AwardRecordService;
import com.university.awards.systemconfig.service.SysConfigService;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.mapper.BizTeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AwardRecordServiceImpl implements AwardRecordService {

    private final BizAwardRecordMapper recordMapper;
    private final BizAwardRecordAuditMapper auditMapper;
    private final DictCompetitionService competitionService;
    private final BizTeamMapper teamMapper;
    private final AuthzService authz;
    private final BizAwardRecordFileMapper recordFileMapper;
    private final SysConfigService configService;

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
