package com.university.awards.classdict.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.service.DictClassService;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.service.AuthzService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端班级字典维护接口。
 *
 * <p><b>权限</b>：SCHOOL_ADMIN / SYS_ADMIN。</p>
 */
@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
public class AdminClassController {

    private final DictClassService classService;
    private final AuthzService authz;
    private final SysUserMapper userMapper;

    /**
     * 班级分页查询（支持院系/启停/名称关键字过滤）。
     */
    @GetMapping
    public ApiResponse<PageResult<DictClass>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String className
    ) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        Page<DictClass> page = classService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictClass>()
                        .eq(deptId != null, DictClass::getDeptId, deptId)
                        .eq(enabled != null, DictClass::getEnabled, enabled)
                        .like(className != null && !className.isBlank(), DictClass::getClassName, className)
                        .orderByAsc(DictClass::getDeptId)
                        .orderByAsc(DictClass::getSortNo)
                        .orderByAsc(DictClass::getId)
        );
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增班级。
     */
    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid UpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictClass e = new DictClass();
        e.setDeptId(req.getDeptId());
        e.setClassName(req.getClassName().trim());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        try {
            classService.save(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "同院系下班级名称已存在");
        }
        return ApiResponse.ok(e.getId());
    }

    /**
     * 编辑班级。
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody @Valid UpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictClass upd = new DictClass();
        upd.setId(id);
        upd.setDeptId(req.getDeptId());
        upd.setClassName(req.getClassName().trim());
        upd.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        try {
            classService.updateById(upd);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "同院系下班级名称已存在");
        }
        return ApiResponse.ok(null);
    }

    /**
     * 启停班级（enabled 取反）。
     */
    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictClass e = classService.getById(id);
        if (e == null) return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        classService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除班级（仅停用状态可删除；仍有学生绑定该班级时不允许删除）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictClass e = classService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的班级不能删除，请先停用");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getClassId, id));
        if (users > 0) {
            return ApiResponse.fail(1, "仍有学生绑定该班级，无法删除");
        }
        classService.removeById(id);
        return ApiResponse.ok(null);
    }

    @Data
    public static class UpsertReq {
        @NotNull
        private Long deptId;
        @NotBlank
        private String className;
        private Integer sortNo;
    }
}

