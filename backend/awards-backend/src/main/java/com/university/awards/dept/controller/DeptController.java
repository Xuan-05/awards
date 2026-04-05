package com.university.awards.dept.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.common.ApiResponse;
import com.university.awards.dept.entity.SysDept;
import com.university.awards.dept.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 院系列表接口。
 *
 * <p>用途：</p>
 * <ul>
 *   <li>前端用于展示院系下拉与管理页面（占位/基础数据）。</li>
 *   <li>RBAC 中用户绑定 {@code dept_id}，也可用于数据范围过滤（例如 DEPT_ADMIN）。</li>
 * </ul>
 *
 * <p>权限：需要登录（当前未做细分）。</p>
 */
@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DeptController {
    private final SysDeptService deptService;

    /**
     * 查询启用状态的院系列表（按 sortNo、id 升序）。
     */
    @GetMapping
    public ApiResponse<List<SysDept>> list() {
        List<SysDept> list = deptService.list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getEnabled, 1)
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getId));
        return ApiResponse.ok(list);
    }
}

