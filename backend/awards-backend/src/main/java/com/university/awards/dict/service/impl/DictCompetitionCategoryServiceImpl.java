package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictCompetitionCategory;
import com.university.awards.dict.mapper.DictCompetitionCategoryMapper;
import com.university.awards.dict.service.DictCompetitionCategoryService;
import org.springframework.stereotype.Service;

@Service
public class DictCompetitionCategoryServiceImpl
        extends ServiceImpl<DictCompetitionCategoryMapper, DictCompetitionCategory>
        implements DictCompetitionCategoryService {
}

