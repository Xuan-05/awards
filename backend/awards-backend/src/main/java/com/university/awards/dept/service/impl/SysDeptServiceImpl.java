package com.university.awards.dept.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dept.entity.SysDept;
import com.university.awards.dept.mapper.SysDeptMapper;
import com.university.awards.dept.service.SysDeptService;
import org.springframework.stereotype.Service;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {
}

