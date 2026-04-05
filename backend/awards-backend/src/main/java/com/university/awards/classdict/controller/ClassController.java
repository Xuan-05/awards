package com.university.awards.classdict.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.service.DictClassService;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班级字典查询接口。
 *
 * <p>用途：为校管理员“用户组织管理”中的学生资料编辑提供班级下拉选项。</p>
 *
 * <p>权限：需要登录。</p>
 */
@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final DictClassService classService;

    /**
     * 查询启用状态的班级列表。
     *
     * @param deptId 院系 ID（可选；不传则返回全部启用班级）
     */
    @GetMapping
    public ApiResponse<List<DictClass>> list(@RequestParam(required = false) Long deptId) {
        StpUtil.checkLogin();
        try {
            List<DictClass> list = classService.list(new LambdaQueryWrapper<DictClass>()
                    .eq(DictClass::getEnabled, 1)
                    .eq(deptId != null, DictClass::getDeptId, deptId)
                    .orderByAsc(DictClass::getSortNo)
                    .orderByAsc(DictClass::getId));
            return ApiResponse.ok(list);
        } catch (DataAccessException e) {
            // 常见原因：未执行迁移脚本导致 dict_class 表不存在
            throw new BizException(400, "请先执行数据库迁移：创建 dict_class 表");
        }
    }
}

