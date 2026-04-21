package com.university.awards.export.service;

import com.university.awards.common.PageResult;
import com.university.awards.export.entity.BizExportTask;

/**
 * 导出服务接口（Excel 模板导出）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>创建导出任务并生成文件，更新任务状态。</li>
 *   <li>控制数据范围：例如 DEPT_ADMIN 导出仅限本院系已通过记录。</li>
 *   <li>控制任务可见性：非校级/系统管理员通常只能查看/下载自己创建的任务。</li>
 * </ul>
 */
public interface ExportService {
    /**
     * 创建导出任务（统一入口）。
     *
     * @param reportCode 报表编码，例如 DISCIPLINE_STATS
     * @param filterJson 过滤条件（JSON 字符串，可为 null）
     * @return 任务 ID
     */
    Long createExport(String reportCode, String filterJson);

    /**
     * 返回可导出的报表列表。
     */
    java.util.List<com.university.awards.export.vo.ExportReportOption> listReports();

    /**
     * 查询我创建的导出任务列表（分页）。
     */
    PageResult<BizExportTask> myTasks(long pageNo, long pageSize);

    /**
     * 获取任务详情，并进行数据范围校验（用于下载等操作）。
     *
     * @param id 任务 ID
     */
    BizExportTask getTask(Long id);
}

