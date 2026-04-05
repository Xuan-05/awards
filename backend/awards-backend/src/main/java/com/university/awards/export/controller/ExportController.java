package com.university.awards.export.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.export.entity.BizExportTask;
import com.university.awards.export.service.ExportService;
import com.university.awards.rbac.service.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 导出任务接口（Excel 导出）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>导出严格按学校模板填充（优先模板填充，不做复杂表头合并）。</li>
 *   <li>导出任务会落地生成文件并记录任务状态（{@code BizExportTask}）。</li>
 * </ul>
 *
 * <p>权限：</p>
 * <ul>
 *   <li>DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN 可创建导出与查看自己的导出任务。</li>
 *   <li>数据范围由 {@link ExportService} 内部控制（例如 DEPT_ADMIN 按 deptId 过滤已通过记录）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final AuthzService authz;

    /**
     * 创建“明细”导出任务。
     *
     * <p><b>权限</b>：DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN。</p>
     *
     * @param filterJson 过滤条件（JSON 字符串，允许为 null；当前实现可不传）
     * @return 导出任务 ID
     */
    @PostMapping("/detail")
    public ApiResponse<Long> createDetail(@RequestBody(required = false) String filterJson) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN", "DEPT_ADMIN");
        return ApiResponse.ok(exportService.createDetailExport(filterJson));
    }

    /**
     * 创建“汇总”导出任务。
     *
     * <p><b>权限</b>：DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN。</p>
     */
    @PostMapping("/summary")
    public ApiResponse<Long> createSummary(@RequestBody(required = false) String filterJson) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN", "DEPT_ADMIN");
        return ApiResponse.ok(exportService.createSummaryExport(filterJson));
    }

    /**
     * 查询我创建的导出任务列表（分页）。
     *
     * <p><b>权限</b>：DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN。</p>
     */
    @GetMapping("/tasks")
    public ApiResponse<PageResult<BizExportTask>> myTasks(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN", "DEPT_ADMIN");
        return ApiResponse.ok(exportService.myTasks(pageNo, pageSize));
    }

    /**
     * 下载导出文件。
     *
     * <p><b>权限</b>：DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN。</p>
     * <p><b>失败场景</b>：</p>
     * <ul>
     *   <li>任务未成功：400。</li>
     *   <li>文件不存在：404。</li>
     *   <li>无权限：403（例如 DEPT_ADMIN 只能下载自己创建的任务）。</li>
     * </ul>
     */
    @GetMapping("/tasks/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN", "DEPT_ADMIN");
        BizExportTask task = exportService.getTask(id);
        if (!"SUCCESS".equals(task.getTaskStatus())) throw new BizException(400, "任务未成功");
        if (task.getFilePath() == null) throw new BizException(404, "文件不存在");

        String name = task.getFileName() == null ? ("export-" + id + ".xlsx") : task.getFileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(task.getFilePath()));
    }
}

