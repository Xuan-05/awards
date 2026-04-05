package com.university.awards.record.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.file.entity.SysFile;
import com.university.awards.file.service.FileStorageService;
import com.university.awards.file.service.SysFileService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.entity.BizAwardRecordFile;
import com.university.awards.record.mapper.BizAwardRecordFileMapper;
import com.university.awards.record.service.AwardRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 获奖记录（竞赛填报）对外接口。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>本系统仅管理“已获奖”的记录（不是参与记录）。</li>
 *   <li>审核流程为单级校审：DRAFT / SCHOOL_REJECTED 可提交 -> PENDING_SCHOOL -> APPROVED 或 SCHOOL_REJECTED。</li>
 *   <li>数据范围：普通用户只能操作自己提交的记录（在 {@code AwardRecordService} 内做校验）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/award-records")
@RequiredArgsConstructor
public class AwardRecordController {

    private final AwardRecordService awardRecordService;
    private final FileStorageService fileStorageService;
    private final SysFileService sysFileService;
    private final BizAwardRecordFileMapper recordFileMapper;

    /**
     * 新建获奖记录（草稿）。
     *
     * <p><b>权限</b>：需要登录。</p>
     * <p><b>失败场景</b>：字段校验失败（400）；无数据权限（403）。</p>
     *
     * @return 新建记录 ID
     */
    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid AwardRecordUpsertReq req) {
        BizAwardRecord e = req.toEntity();
        Long id = awardRecordService.create(e);
        return ApiResponse.ok(id);
    }

    /**
     * 更新获奖记录（通常仅草稿/驳回状态允许修改，具体以 service 逻辑为准）。
     *
     * <p><b>权限</b>：需要登录；数据范围同 create。</p>
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody @Valid AwardRecordUpsertReq req) {
        awardRecordService.update(id, req.toEntity());
        return ApiResponse.ok(null);
    }

    /**
     * 获取获奖记录详情。
     *
     * <p><b>权限</b>：需要登录；普通用户只能访问自己提交的记录。</p>
     */
    @GetMapping("/{id}")
    public ApiResponse<BizAwardRecord> detail(@PathVariable Long id) {
        return ApiResponse.ok(awardRecordService.get(id));
    }

    /**
     * 查询“我提交的记录”分页列表。
     *
     * <p><b>权限</b>：需要登录。</p>
     *
     * @param status 状态过滤（可选）
     * @param teamId 团队过滤（可选）
     * @param competitionId 竞赛过滤（可选）
     * @param semester 学期过滤（可选）
     */
    @GetMapping("/my")
    public ApiResponse<PageResult<BizAwardRecord>> myPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.ok(awardRecordService.myPage(status, teamId, competitionId, semester, pageNo, pageSize));
    }

    /**
     * 提交获奖记录进入校级审核。
     *
     * <p><b>权限</b>：需要登录；普通用户仅可提交自己的记录。</p>
     * <p><b>状态机</b>：DRAFT / SCHOOL_REJECTED -> PENDING_SCHOOL。</p>
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        awardRecordService.submit(id);
        return ApiResponse.ok(null);
    }

    /**
     * 撤回待审记录。
     *
     * <p><b>权限</b>：需要登录；普通用户仅可撤回自己的记录。</p>
     * <p><b>状态机</b>：PENDING_SCHOOL -> DRAFT。</p>
     */
    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable Long id) {
        awardRecordService.withdraw(id);
        return ApiResponse.ok(null);
    }

    /**
     * 删除草稿记录。
     *
     * <p><b>权限</b>：需要登录；仅允许删除草稿（具体以 service 校验为准）。</p>
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long id) {
        awardRecordService.deleteDraft(id);
        return ApiResponse.ok(null);
    }

    // ---------------- attachments ----------------

    /**
     * 上传附件并关联到获奖记录。
     *
     * <p><b>权限</b>：需要登录；普通用户仅可操作自己的记录。</p>
     * <p><b>附件类型</b>：由后端白名单控制（图片/PDF/ZIP；ZIP 仅下载不预览）。</p>
     *
     * @return 记录-文件关联关系（含 fileId）
     */
    @PostMapping("/{id}/files")
    public ApiResponse<BizAwardRecordFile> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        awardRecordService.get(id); // reuse service data-scope check
        SysFile meta = fileStorageService.save(file);
        BizAwardRecordFile rel = new BizAwardRecordFile();
        rel.setRecordId(id);
        rel.setFileId(meta.getId());
        rel.setDeleted(0);
        rel.setCreatedAt(LocalDateTime.now());
        recordFileMapper.insert(rel);
        return ApiResponse.ok(rel);
    }

    /**
     * 获取获奖记录附件列表（关联表 + 原始文件名，便于列表/审核页展示）。
     *
     * <p><b>权限</b>：需要登录；普通用户仅可访问自己的记录。</p>
     */
    @GetMapping("/{id}/files")
    public ApiResponse<List<AwardRecordFileItem>> listFiles(@PathVariable Long id) {
        awardRecordService.get(id); // reuse service data-scope check
        List<BizAwardRecordFile> list = recordFileMapper.selectList(
                new LambdaQueryWrapper<BizAwardRecordFile>()
                        .eq(BizAwardRecordFile::getRecordId, id)
                        .eq(BizAwardRecordFile::getDeleted, 0)
                        .orderByDesc(BizAwardRecordFile::getId)
        );
        List<Long> fileIds = list.stream()
                .map(BizAwardRecordFile::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysFile> metaById = fileIds.isEmpty()
                ? Map.of()
                : sysFileService.listByIds(fileIds).stream()
                        .filter(sf -> sf.getDeleted() == null || sf.getDeleted() != 1)
                        .collect(Collectors.toMap(SysFile::getId, sf -> sf, (a, b) -> a));

        List<AwardRecordFileItem> out = new ArrayList<>(list.size());
        for (BizAwardRecordFile rel : list) {
            AwardRecordFileItem item = new AwardRecordFileItem();
            item.setRecordId(rel.getRecordId());
            item.setFileId(rel.getFileId());
            item.setCreatedAt(rel.getCreatedAt());
            SysFile sf = rel.getFileId() == null ? null : metaById.get(rel.getFileId());
            if (sf != null && sf.getFileName() != null && !sf.getFileName().isBlank()) {
                item.setFileName(sf.getFileName());
            } else if (rel.getFileId() != null) {
                item.setFileName("file-" + rel.getFileId());
            } else {
                item.setFileName("");
            }
            out.add(item);
        }
        return ApiResponse.ok(out);
    }

    /**
     * 删除“获奖记录-附件”关联（逻辑删除关联关系，不删除物理文件）。
     *
     * <p><b>权限</b>：需要登录；普通用户仅可操作自己的记录。</p>
     */
    @DeleteMapping("/{id}/files/{fileId}")
    public ApiResponse<Void> removeFile(@PathVariable Long id, @PathVariable Long fileId) {
        awardRecordService.get(id); // reuse service data-scope check
        BizAwardRecordFile upd = new BizAwardRecordFile();
        upd.setDeleted(1);
        recordFileMapper.update(
                upd,
                new LambdaQueryWrapper<BizAwardRecordFile>()
                        .eq(BizAwardRecordFile::getRecordId, id)
                        .eq(BizAwardRecordFile::getFileId, fileId)
                        .eq(BizAwardRecordFile::getDeleted, 0)
        );
        return ApiResponse.ok(null);
    }

    // ---------------- response (attachments) ----------------

    /**
     * 获奖记录附件行（含展示用文件名）。
     */
    @Data
    public static class AwardRecordFileItem {
        private Long recordId;
        private Long fileId;
        private LocalDateTime createdAt;
        /** 原始文件名；缺失时由服务端回退为 file-{id} */
        private String fileName;
    }

    // ---------------- request ----------------

    @Data
    public static class AwardRecordUpsertReq {
        /**
         * 团队 ID（{@code biz_team.id}）。
         */
        @NotNull
        private Long teamId;
        /**
         * 竞赛字典 ID（{@code dict_competition.id}）。必须从字典选择，不允许自由文本。
         */
        @NotNull
        private Long competitionId;
        /**
         * 获奖范围字典 ID（{@code dict_award_scope.id}）。
         */
        @NotNull
        private Long awardScopeId;
        /**
         * 获奖等级字典 ID（{@code dict_award_level.id}）。
         */
        @NotNull
        private Long awardLevelId;
        /**
         * 获奖日期（YYYY-MM-DD）。
         */
        @NotNull
        private java.time.LocalDate awardDate;
        /**
         * 学期（示例：2025-2026-1）。
         */
        @NotNull
        private String semester;
        /**
         * 项目名称/参赛题目。
         */
        @NotNull
        private String projectName;
        /**
         * 团队获奖人数（可选；用于导出/统计）。
         */
        private Integer teamAwardCount;
        /**
         * 备注（可选）。
         */
        private String remark;

        public BizAwardRecord toEntity() {
            BizAwardRecord e = new BizAwardRecord();
            e.setTeamId(teamId);
            e.setCompetitionId(competitionId);
            e.setAwardScopeId(awardScopeId);
            e.setAwardLevelId(awardLevelId);
            e.setAwardDate(awardDate);
            e.setSemester(semester);
            e.setProjectName(projectName);
            e.setTeamAwardCount(teamAwardCount);
            e.setRemark(remark);
            return e;
        }
    }
}

