package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictAwardLevel;
import com.university.awards.dict.mapper.DictAwardLevelMapper;
import com.university.awards.dict.service.DictAwardLevelService;
import org.springframework.stereotype.Service;

@Service
public class DictAwardLevelServiceImpl extends ServiceImpl<DictAwardLevelMapper, DictAwardLevel>
        implements DictAwardLevelService {
}

