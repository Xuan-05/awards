package com.university.awards.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.file.entity.SysFile;
import com.university.awards.file.mapper.SysFileMapper;
import com.university.awards.file.service.SysFileService;
import org.springframework.stereotype.Service;

@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {
}

