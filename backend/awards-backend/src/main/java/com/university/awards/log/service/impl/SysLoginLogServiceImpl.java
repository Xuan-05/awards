package com.university.awards.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.log.entity.SysLoginLog;
import com.university.awards.log.mapper.SysLoginLogMapper;
import com.university.awards.log.service.SysLoginLogService;
import org.springframework.stereotype.Service;

@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {
}

