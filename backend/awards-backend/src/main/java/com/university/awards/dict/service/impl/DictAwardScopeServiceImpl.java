package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictAwardScope;
import com.university.awards.dict.mapper.DictAwardScopeMapper;
import com.university.awards.dict.service.DictAwardScopeService;
import org.springframework.stereotype.Service;

@Service
public class DictAwardScopeServiceImpl extends ServiceImpl<DictAwardScopeMapper, DictAwardScope>
        implements DictAwardScopeService {
}

