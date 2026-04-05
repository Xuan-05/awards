package com.university.awards.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.log.entity.SysOperateLog;
import com.university.awards.log.mapper.SysOperateLogMapper;
import com.university.awards.log.service.SysOperateLogService;
import org.springframework.stereotype.Service;

@Service
public class SysOperateLogServiceImpl extends ServiceImpl<SysOperateLogMapper, SysOperateLog> implements SysOperateLogService {
}

