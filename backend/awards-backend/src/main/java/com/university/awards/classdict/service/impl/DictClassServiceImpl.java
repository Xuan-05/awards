package com.university.awards.classdict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.mapper.DictClassMapper;
import com.university.awards.classdict.service.DictClassService;
import org.springframework.stereotype.Service;

@Service
public class DictClassServiceImpl extends ServiceImpl<DictClassMapper, DictClass> implements DictClassService {
}

