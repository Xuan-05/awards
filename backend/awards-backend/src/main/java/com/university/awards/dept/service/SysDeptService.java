package com.university.awards.dept.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.university.awards.dept.entity.SysDept;

/**
 * 院系服务（基础数据）。
 *
 * <p>用于：</p>
 * <ul>
 *   <li>院系列表查询（启用项）。</li>
 *   <li>RBAC 用户绑定院系、以及 DEPT_ADMIN 数据范围过滤。</li>
 * </ul>
 */
public interface SysDeptService extends IService<SysDept> {
}

