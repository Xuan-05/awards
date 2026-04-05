package com.university.awards.export.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.export.entity.BizExportTask;
import com.university.awards.export.mapper.BizExportTaskMapper;
import com.university.awards.export.service.ExportService;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final BizExportTaskMapper taskMapper;
    private final BizAwardRecordMapper recordMapper;
    private final AuthzService authz;
    private final RbacService rbacService;

    @Value("${export.out-dir:./data/exports}")
    private String outDir;

    @Value("${export.template.detail:./templates/detail_template.xlsx}")
    private String detailTemplate;

    @Value("${export.template.summary:./templates/summary_template.xlsx}")
    private String summaryTemplate;

    @Override
    public Long createDetailExport(String filterJson) {
        return createAndRun("DETAIL", filterJson);
    }

    @Override
    public Long createSummaryExport(String filterJson) {
        return createAndRun("SUMMARY", filterJson);
    }

    private Long createAndRun(String type, String filterJson) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();

        BizExportTask task = new BizExportTask();
        task.setExportType(type);
        task.setFilterJson(filterJson);
        task.setTaskStatus("RUNNING");
        task.setCreatorUserId(uid);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        try {
            Path out = generate(type, task.getId());
            task.setFileName(out.getFileName().toString());
            task.setFilePath(out.toString());
            task.setTaskStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            task.setTaskStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new BizException(500, "导出失败：" + e.getMessage());
        }

        return task.getId();
    }

    private Path generate(String type, Long taskId) throws Exception {
        String templatePath = "DETAIL".equals(type) ? detailTemplate : summaryTemplate;
        File template = new File(templatePath);
        if (!template.exists()) {
            throw new BizException(400, "缺少导出模板文件：" + template.getPath());
        }

        Files.createDirectories(Path.of(outDir));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path out = Path.of(outDir, type.toLowerCase() + "_" + taskId + "_" + ts + ".xlsx");

        LambdaQueryWrapper<BizAwardRecord> qw = new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getStatus, "APPROVED");

        // Data scope:
        // - SCHOOL_ADMIN / SYS_ADMIN: all approved
        // - DEPT_ADMIN: only records in own dept (owner_dept_id)
        if (authz.hasRole("DEPT_ADMIN") && !authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")) {
            Long uid = authz.currentUserId();
            SysUser user = rbacService.mustGetUser(uid);
            if (user.getDeptId() == null) throw new BizException(400, "当前用户未绑定院系");
            qw.eq(BizAwardRecord::getOwnerDeptId, user.getDeptId());
        }

        List<BizAwardRecord> records = recordMapper.selectList(qw.orderByAsc(BizAwardRecord::getId).last("limit 2000"));

        // Template contract (recommended):
        // - Fill list placeholder named "records"
        // - Each item exposes keys used in template cells
        List<Map<String, Object>> fillList = records.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("recordId", r.getId());
            m.put("teamId", r.getTeamId());
            m.put("competitionId", r.getCompetitionId());
            m.put("semester", r.getSemester());
            m.put("awardDate", r.getAwardDate());
            m.put("projectName", r.getProjectName());
            m.put("teamAwardCount", r.getTeamAwardCount());
            return m;
        }).toList();

        try (ExcelWriter writer = EasyExcel.write(out.toFile()).withTemplate(template).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(0).build();
            writer.fill(fillList, sheet);
            writer.finish();
        }

        return out;
    }

    @Override
    public PageResult<BizExportTask> myTasks(long pageNo, long pageSize) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        Page<BizExportTask> page = taskMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<BizExportTask>()
                        .eq(BizExportTask::getCreatorUserId, uid)
                        .orderByDesc(BizExportTask::getId)
        );
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public BizExportTask getTask(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizExportTask task = taskMapper.selectById(id);
        if (task == null) throw new BizException(404, "导出任务不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN") && (task.getCreatorUserId() == null || !uid.equals(task.getCreatorUserId()))) {
            throw new BizException(403, "无权限");
        }
        return task;
    }
}

