package com.university.awards.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.message.entity.SysMessage;
import com.university.awards.message.mapper.SysMessageMapper;
import com.university.awards.message.service.SysMessageService;
import org.springframework.stereotype.Service;

@Service
public class SysMessageServiceImpl extends ServiceImpl<SysMessageMapper, SysMessage> implements SysMessageService {
}

