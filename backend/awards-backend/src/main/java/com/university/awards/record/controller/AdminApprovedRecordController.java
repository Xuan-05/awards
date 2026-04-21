package com.university.awards.record.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/records")
@RequiredArgsConstructor
public class AdminApprovedRecordController {

    private final AuthzService authz;
    private final BizAwardRecordMapper recordMapper;

    @GetMapping("/approved")
    public ApiResponse<PageResult<BizAwardRecord>> approvedPage(
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) Long competitionCategoryId,
            @RequestParam(required = false) Long awardLevelId,
            @RequestParam(required = false) Long awardScopeId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate awardDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate awardDateEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submitTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submitTimeEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime approvedTimeEnd,
            @RequestParam(required = false) Integer l2ReviewFlag,
            @RequestParam(required = false) String l2ReviewResult,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        LambdaQueryWrapper<BizAwardRecord> qw = new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getStatus, "APPROVED")
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(recordId != null, BizAwardRecord::getId, recordId)
                .eq(deptId != null, BizAwardRecord::getOwnerDeptId, deptId)
                .eq(competitionId != null, BizAwardRecord::getCompetitionId, competitionId)
                .eq(competitionCategoryId != null, BizAwardRecord::getCompetitionCategoryId, competitionCategoryId)
                .eq(awardLevelId != null, BizAwardRecord::getAwardLevelId, awardLevelId)
                .eq(awardScopeId != null, BizAwardRecord::getAwardScopeId, awardScopeId)
                .eq(semester != null && !semester.isBlank(), BizAwardRecord::getSemester, semester)
                .eq(l2ReviewFlag != null, BizAwardRecord::getL2ReviewFlag, l2ReviewFlag)
                .eq(l2ReviewResult != null && !l2ReviewResult.isBlank(), BizAwardRecord::getL2ReviewResult, l2ReviewResult)
                .like(keyword != null && !keyword.isBlank(), BizAwardRecord::getProjectName, keyword)
                .ge(awardDateStart != null, BizAwardRecord::getAwardDate, awardDateStart)
                .le(awardDateEnd != null, BizAwardRecord::getAwardDate, awardDateEnd)
                .ge(submitTimeStart != null, BizAwardRecord::getSubmitTime, submitTimeStart)
                .le(submitTimeEnd != null, BizAwardRecord::getSubmitTime, submitTimeEnd)
                .ge(approvedTimeStart != null, BizAwardRecord::getFinalAuditTime, approvedTimeStart)
                .le(approvedTimeEnd != null, BizAwardRecord::getFinalAuditTime, approvedTimeEnd);

        if ("awardDate".equalsIgnoreCase(sortBy)) {
            qw.orderBy(true, asc, BizAwardRecord::getAwardDate);
        } else if ("submitTime".equalsIgnoreCase(sortBy)) {
            qw.orderBy(true, asc, BizAwardRecord::getSubmitTime);
        } else if ("projectName".equalsIgnoreCase(sortBy)) {
            qw.orderBy(true, asc, BizAwardRecord::getProjectName);
        } else {
            qw.orderBy(true, asc, BizAwardRecord::getFinalAuditTime);
        }
        qw.orderByDesc(BizAwardRecord::getId);

        Page<BizAwardRecord> page = recordMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }
}
