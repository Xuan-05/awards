package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.mapper.DictCompetitionMapper;
import com.university.awards.dict.service.DictCompetitionService;
import org.springframework.stereotype.Service;

@Service
public class DictCompetitionServiceImpl extends ServiceImpl<DictCompetitionMapper, DictCompetition>
        implements DictCompetitionService {
}

