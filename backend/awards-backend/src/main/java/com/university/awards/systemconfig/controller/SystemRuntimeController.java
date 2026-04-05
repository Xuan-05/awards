package com.university.awards.systemconfig.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.rbac.service.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统运行时配置（只读展示）。
 *
 * <p>用途：为管理端“系统设置”页提供当前生效的存储/导出路径展示，避免前端硬编码。</p>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>本接口只读展示当前生效配置。</li>
 *   <li>配置来源通常是 application.yml / 环境变量 / 启动参数。</li>
 *   <li>若未来允许在 UI 中修改这些配置，往往需要重启服务才能生效（取决于实现）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemRuntimeController {

    private final AuthzService authz;

    @Value("${storage.base-dir:./data/files}")
    private String storageBaseDir;

    @Value("${export.out-dir:./data/exports}")
    private String exportOutDir;

    @Value("${export.template.detail:./templates/detail_template.xlsx}")
    private String exportTemplateDetail;

    @Value("${export.template.summary:./templates/summary_template.xlsx}")
    private String exportTemplateSummary;

    /**
     * 获取运行时配置摘要。
     *
     * <p>权限：SCHOOL_ADMIN/SYS_ADMIN。</p>
     */
    @GetMapping("/runtime")
    public ApiResponse<RuntimeInfo> runtime() {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        return ApiResponse.ok(new RuntimeInfo(storageBaseDir, exportOutDir, exportTemplateDetail, exportTemplateSummary));
    }

    /**
     * 返回给前端的运行时配置结构。
     *
     * @param storageBaseDir 文件存储根目录
     * @param exportOutDir 导出文件输出目录
     * @param exportTemplateDetail 明细导出模板路径
     * @param exportTemplateSummary 汇总导出模板路径
     */
    public record RuntimeInfo(String storageBaseDir, String exportOutDir, String exportTemplateDetail, String exportTemplateSummary) {
    }
}

