package com.university.awards.record.service;

import com.university.awards.common.PageResult;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.vo.MyAwardVO;

import java.util.List;

/**
 * 获奖记录（竞赛填报）领域服务接口。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>封装获奖记录的 CRUD 与状态机（提交/撤回/删除草稿）。</li>
 *   <li>统一进行数据范围校验：普通用户仅能操作自己提交的记录；管理员拥有更高权限。</li>
 * </ul>
 */
public interface AwardRecordService {
    /**
     * 创建获奖记录（通常为草稿）。
     *
     * <p><b>数据范围</b>：会把当前登录用户作为 submitter（若设计如此），并限制后续操作范围。</p>
     *
     * @param record 记录实体（字段需满足必填要求）
     * @return 新建记录 ID
     */
    Long create(BizAwardRecord record);

    /**
     * 更新获奖记录。
     *
     * <p><b>失败场景</b>：</p>
     * <ul>
     *   <li>记录不存在：抛业务异常。</li>
     *   <li>无权限：抛 403。</li>
     *   <li>状态不允许更新：抛 400。</li>
     * </ul>
     */
    void update(Long id, BizAwardRecord record);

    /**
     * 获取获奖记录详情（含数据范围校验）。
     *
     * @param id 记录 ID
     * @return 记录实体
     */
    BizAwardRecord get(Long id);

    /**
     * 查询“我的记录”分页列表。
     *
     * @param status 状态（可选）
     * @param teamId 团队 ID（可选）
     * @param competitionId 竞赛 ID（可选）
     * @param semester 学期（可选）
     */
    PageResult<BizAwardRecord> myPage(String status, Long teamId, Long competitionId, String semester, long pageNo, long pageSize);

    /**
     * 查询“我的获奖”列表（仅审核通过）。
     *
     * @param userId 当前用户 ID
     * @param userType 用户类型：student 或 teacher
     */
    List<MyAwardVO> getMyAwards(Long userId, String userType);

    /**
     * 提交记录进入校级审核。
     *
     * <p><b>状态机</b>：DRAFT / SCHOOL_REJECTED -> PENDING_SCHOOL。</p>
     */
    void submit(Long id);

    /**
     * 撤回待审记录。
     *
     * <p><b>状态机</b>：PENDING_SCHOOL -> DRAFT。</p>
     */
    void withdraw(Long id);

    /**
     * 删除草稿记录。
     *
     * <p>一般仅允许删除 DRAFT 状态记录；具体以实现为准。</p>
     */
    void deleteDraft(Long id);
}

