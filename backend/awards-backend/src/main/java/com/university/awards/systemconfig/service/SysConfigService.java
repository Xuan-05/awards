package com.university.awards.systemconfig.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.university.awards.systemconfig.entity.SysConfig;

import java.util.Optional;

public interface SysConfigService extends IService<SysConfig> {
    Optional<SysConfig> findByKey(String key);

    String getString(String key, String defaultValue);

    Integer getInt(String key, Integer defaultValue);

    Boolean getBool(String key, Boolean defaultValue);
}

