package com.university.awards.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.systemconfig.entity.SysConfig;
import com.university.awards.systemconfig.mapper.SysConfigMapper;
import com.university.awards.systemconfig.service.SysConfigService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 系统参数服务实现。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>sys_config 中统一以字符串存值（config_value）。</li>
 *   <li>getInt/getBool 会做宽松解析：解析失败时回落 defaultValue，避免因配置错误影响主流程。</li>
 * </ul>
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public Optional<SysConfig> findByKey(String key) {
        // 入参为空：直接返回 empty
        if (key == null || key.isBlank()) return Optional.empty();
        // 按 key 精确匹配（getOne 的第二个参数 false 表示“多条时不抛异常”，取第一条）
        SysConfig c = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key.trim()), false);
        return Optional.ofNullable(c);
    }

    @Override
    public String getString(String key, String defaultValue) {
        // 直接返回字符串值；不存在则回落 defaultValue
        return findByKey(key).map(SysConfig::getConfigValue).orElse(defaultValue);
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        // 读取字符串并尝试 parseInt；解析失败回落 defaultValue
        String v = findByKey(key).map(SysConfig::getConfigValue).orElse(null);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Boolean getBool(String key, Boolean defaultValue) {
        // 宽松布尔解析：支持 1/0 true/false yes/no y/n
        String v = findByKey(key).map(SysConfig::getConfigValue).orElse(null);
        if (v == null || v.isBlank()) return defaultValue;
        String s = v.trim().toLowerCase();
        if ("1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s)) return true;
        if ("0".equals(s) || "false".equals(s) || "no".equals(s) || "n".equals(s)) return false;
        return defaultValue;
    }
}

