package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictOrganizer;
import com.university.awards.dict.mapper.DictOrganizerMapper;
import com.university.awards.dict.service.DictOrganizerService;
import org.springframework.stereotype.Service;

@Service
public class DictOrganizerServiceImpl extends ServiceImpl<DictOrganizerMapper, DictOrganizer>
        implements DictOrganizerService {
}

