package com.university.awards.systemconfig.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.systemconfig.entity.SysConfig;
import com.university.awards.systemconfig.service.SysConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统参数接口。
 *
 * <p>说明：按 PRD/OpenAPI 提供可配置参数（如团队教师上限、上传大小等）。</p>
 *
 * <p>本系统的参数设计：</p>
 * <ul>
 *   <li>参数以 key/value 形式存于 {@code sys_config}。</li>
 *   <li>{@code config_value} 统一用字符串存储；{@code value_type} 仅用于提示与前端友好控件渲染。</li>
 * </ul>
 *
 * <p>权限：</p>
 * <ul>
 *   <li>查询：需要登录。</li>
 *   <li>修改：SCHOOL_ADMIN/SYS_ADMIN。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysConfigService configService;
    private final AuthzService authz;

    /**
     * 查询系统参数列表。
     *
     * <p>用途：系统设置页展示全部配置项。</p>
     *
     * @param keyPrefix 可选：按 key 模糊过滤（前端也会做一次关键字过滤）
     */
    @GetMapping
    public ApiResponse<List<SysConfig>> list(@RequestParam(required = false) String keyPrefix) {
        StpUtil.checkLogin();
        String prefix = (keyPrefix == null ? null : keyPrefix.trim());
        List<SysConfig> list = configService.list(new LambdaQueryWrapper<SysConfig>()
                .like(prefix != null && !prefix.isBlank(), SysConfig::getConfigKey, prefix)
                .orderByAsc(SysConfig::getConfigKey)
                .orderByAsc(SysConfig::getId));
        return ApiResponse.ok(list);
    }

    /**
     * 保存/更新单个系统参数（覆盖式）。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>若 key 不存在：创建一条新记录。</li>
     *   <li>若 key 已存在：按 id 更新该记录。</li>
     * </ul>
     *
     * @param key 参数 key（路径参数）
     * @param req 参数值、类型、说明
     */
    @PutMapping("/{key}")
    public ApiResponse<Void> put(@PathVariable String key, @RequestBody @Valid UpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        String k = key == null ? "" : key.trim();
        if (k.isBlank()) throw new BizException(400, "key 不能为空");

        SysConfig exists = configService.findByKey(k).orElse(null);
        if (exists == null) {
            SysConfig e = new SysConfig();
            e.setConfigKey(k);
            e.setConfigValue(req.getConfigValue().trim());
            e.setValueType(req.getValueType() == null ? "STRING" : req.getValueType().trim());
            e.setRemark(req.getRemark());
            e.setUpdatedBy(authz.currentUserId());
            e.setUpdatedAt(LocalDateTime.now());
            configService.save(e);
            return ApiResponse.ok(null);
        }

        SysConfig upd = new SysConfig();
        upd.setId(exists.getId());
        upd.setConfigValue(req.getConfigValue().trim());
        if (req.getValueType() != null && !req.getValueType().isBlank()) upd.setValueType(req.getValueType().trim());
        upd.setRemark(req.getRemark());
        upd.setUpdatedBy(authz.currentUserId());
        upd.setUpdatedAt(LocalDateTime.now());
        configService.updateById(upd);
        return ApiResponse.ok(null);
    }

    @Data
    public static class UpsertReq {
        /**
         * 参数值（字符串）。
         *
         * <p>例如：</p>
         * <ul>
         *   <li>BOOL：0/1 或 true/false</li>
         *   <li>INT：数字字符串</li>
         * </ul>
         */
        @NotBlank
        private String configValue;
        /**
         * 类型提示（STRING/INT/BOOL/JSON）。
         * <p>当前后端不强校验，主要用于前端展示与友好控件。</p>
         */
        private String valueType;
        /**
         * 中文说明（可选）。
         */
        private String remark;
    }
}

