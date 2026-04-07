package com.university.awards.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.dict.entity.*;
import com.university.awards.dict.service.*;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 字典管理接口。
 *
 * <p>
 * 字典的用途：
 * </p>
 * <ul>
 * <li>竞赛、竞赛类别、主办方、获奖范围、获奖等级等均必须从字典选择，业务表不允许自由文本录入。</li>
 * <li>字典项支持启停（enabled），前端通常只展示 enabled=1 的项供选择。</li>
 * </ul>
 *
 * <p>
 * 权限：
 * </p>
 * <ul>
 * <li>查询接口：登录后可访问（用于填报页面选择）。</li>
 * <li>新增/修改/启停：仅 SCHOOL_ADMIN/SYS_ADMIN。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictCompetitionService competitionService;
    private final DictCompetitionCategoryService categoryService;
    private final DictOrganizerService organizerService;
    private final DictAwardScopeService awardScopeService;
    private final DictAwardLevelService awardLevelService;
    private final DictCompetitionOrganizerService competitionOrganizerService;
    private final AuthzService authz;
    private final BizAwardRecordMapper awardRecordMapper;

    // ---------------- categories ----------------

    /**
     * 竞赛类别分页查询。
     *
     * @param enabled 启用状态过滤（1 启用 / 0 停用，可选）
     */
    @GetMapping("/categories")
    public ApiResponse<PageResult<DictCompetitionCategory>> pageCategories(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Integer enabled) {
        Page<DictCompetitionCategory> page = categoryService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictCompetitionCategory>()
                        .eq(enabled != null, DictCompetitionCategory::getEnabled, enabled)
                        .orderByAsc(DictCompetitionCategory::getSortNo)
                        .orderByAsc(DictCompetitionCategory::getId));
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增竞赛类别。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @PostMapping("/categories")
    public ApiResponse<Long> createCategory(@RequestBody @Valid CategoryUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetitionCategory e = new DictCompetitionCategory();
        e.setCategoryName(req.getCategoryName());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        categoryService.save(e);
        return ApiResponse.ok(e.getId());
    }

    /**
     * 更新竞赛类别。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @PutMapping("/categories/{id}")
    public ApiResponse<Void> updateCategory(@PathVariable Long id, @RequestBody @Valid CategoryUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetitionCategory e = new DictCompetitionCategory();
        e.setId(id);
        e.setCategoryName(req.getCategoryName());
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        categoryService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 启停竞赛类别（enabled 取反）。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @PostMapping("/categories/{id}/toggle")
    public ApiResponse<Void> toggleCategory(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetitionCategory e = categoryService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        categoryService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除竞赛类别（仅停用状态可删除；有竞赛引用时不允许删除）。
     */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetitionCategory e = categoryService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的竞赛类别不能删除，请先停用");
        }
        long n = competitionService.count(new LambdaQueryWrapper<DictCompetition>()
                .eq(DictCompetition::getCategoryId, id));
        if (n > 0) {
            return ApiResponse.fail(1, "仍有竞赛引用该类别，无法删除");
        }
        categoryService.removeById(id);
        return ApiResponse.ok(null);
    }

    // ---------------- organizers ----------------

    /**
     * 主办方分页查询。
     */
    @GetMapping("/organizers")
    public ApiResponse<PageResult<DictOrganizer>> pageOrganizers(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Integer enabled) {
        Page<DictOrganizer> page = organizerService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictOrganizer>()
                        .eq(enabled != null, DictOrganizer::getEnabled, enabled)
                        .orderByAsc(DictOrganizer::getSortNo)
                        .orderByAsc(DictOrganizer::getId));
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增主办方。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @PostMapping("/organizers")
    public ApiResponse<Long> createOrganizer(@RequestBody @Valid OrganizerUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictOrganizer e = new DictOrganizer();
        e.setOrganizerName(req.getOrganizerName());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        organizerService.save(e);
        return ApiResponse.ok(e.getId());
    }

    /**
     * 更新主办方。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @PutMapping("/organizers/{id}")
    public ApiResponse<Void> updateOrganizer(@PathVariable Long id, @RequestBody @Valid OrganizerUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictOrganizer e = new DictOrganizer();
        e.setId(id);
        e.setOrganizerName(req.getOrganizerName());
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        organizerService.updateById(e);
        return ApiResponse.ok(null);
    }

    @PostMapping("/organizers/{id}/toggle")
    public ApiResponse<Void> toggleOrganizer(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictOrganizer e = organizerService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        organizerService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除主办方（仅停用状态可删除；仍被竞赛关联时不允许删除）。
     */
    @DeleteMapping("/organizers/{id}")
    public ApiResponse<Void> deleteOrganizer(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictOrganizer e = organizerService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的主办方不能删除，请先停用");
        }
        long n = competitionOrganizerService.count(new LambdaQueryWrapper<DictCompetitionOrganizer>()
                .eq(DictCompetitionOrganizer::getOrganizerId, id));
        if (n > 0) {
            return ApiResponse.fail(1, "仍有竞赛关联该主办方，无法删除");
        }
        organizerService.removeById(id);
        return ApiResponse.ok(null);
    }

    // ---------------- award scopes ----------------

    @GetMapping("/award-scopes")
    public ApiResponse<PageResult<DictAwardScope>> pageAwardScopes(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "50") long pageSize,
            @RequestParam(required = false) Integer enabled) {
        Page<DictAwardScope> page = awardScopeService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictAwardScope>()
                        .eq(enabled != null, DictAwardScope::getEnabled, enabled)
                        .orderByAsc(DictAwardScope::getSortNo)
                        .orderByAsc(DictAwardScope::getId));
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/award-scopes")
    public ApiResponse<Long> createAwardScope(@RequestBody @Valid AwardScopeUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardScope e = new DictAwardScope();
        e.setScopeName(req.getScopeName());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        awardScopeService.save(e);
        return ApiResponse.ok(e.getId());
    }

    @PutMapping("/award-scopes/{id}")
    public ApiResponse<Void> updateAwardScope(@PathVariable Long id, @RequestBody @Valid AwardScopeUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardScope e = new DictAwardScope();
        e.setId(id);
        e.setScopeName(req.getScopeName());
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        awardScopeService.updateById(e);
        return ApiResponse.ok(null);
    }

    @PostMapping("/award-scopes/{id}/toggle")
    public ApiResponse<Void> toggleAwardScope(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardScope e = awardScopeService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        awardScopeService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除获奖范围（仅停用状态可删除；有等级或填报引用时不允许删除）。
     */
    @DeleteMapping("/award-scopes/{id}")
    public ApiResponse<Void> deleteAwardScope(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardScope e = awardScopeService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的获奖范围不能删除，请先停用");
        }
        long levels = awardLevelService.count(new LambdaQueryWrapper<DictAwardLevel>()
                .eq(DictAwardLevel::getAwardScopeId, id));
        if (levels > 0) {
            return ApiResponse.fail(1, "仍有获奖等级引用该范围，无法删除");
        }
        long records = awardRecordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getAwardScopeId, id)
                .and(w -> w.eq(BizAwardRecord::getDeleted, 0).or().isNull(BizAwardRecord::getDeleted)));
        if (records > 0) {
            return ApiResponse.fail(1, "仍有获奖填报引用该范围，无法删除");
        }
        awardScopeService.removeById(id);
        return ApiResponse.ok(null);
    }

    // ---------------- award levels ----------------

    @GetMapping("/award-levels")
    public ApiResponse<PageResult<DictAwardLevel>> pageAwardLevels(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "50") long pageSize,
            @RequestParam(required = false) Long awardScopeId,
            @RequestParam(required = false) Integer enabled) {
        Page<DictAwardLevel> page = awardLevelService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictAwardLevel>()
                        .eq(awardScopeId != null, DictAwardLevel::getAwardScopeId, awardScopeId)
                        .eq(enabled != null, DictAwardLevel::getEnabled, enabled)
                        .orderByAsc(DictAwardLevel::getSortNo)
                        .orderByAsc(DictAwardLevel::getId));
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/award-levels")
    public ApiResponse<Long> createAwardLevel(@RequestBody @Valid AwardLevelUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardLevel e = new DictAwardLevel();
        e.setCompetitionId(req.getCompetitionId());
        e.setAwardScopeId(req.getAwardScopeId());
        e.setLevelName(req.getLevelName());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        awardLevelService.save(e);
        return ApiResponse.ok(e.getId());
    }

    @PutMapping("/award-levels/{id}")
    public ApiResponse<Void> updateAwardLevel(@PathVariable Long id, @RequestBody @Valid AwardLevelUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardLevel e = new DictAwardLevel();
        e.setId(id);
        e.setCompetitionId(req.getCompetitionId());
        e.setAwardScopeId(req.getAwardScopeId());
        e.setLevelName(req.getLevelName());
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        awardLevelService.updateById(e);
        return ApiResponse.ok(null);
    }

    @PostMapping("/award-levels/{id}/toggle")
    public ApiResponse<Void> toggleAwardLevel(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardLevel e = awardLevelService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        awardLevelService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除获奖等级（仅停用状态可删除）。
     *
     * <p>
     * <b>权限</b>：SCHOOL_ADMIN/SYS_ADMIN。
     * </p>
     */
    @DeleteMapping("/award-levels/{id}")
    public ApiResponse<Void> deleteAwardLevel(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictAwardLevel e = awardLevelService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的获奖等级不能删除，请先停用");
        }
        awardLevelService.removeById(id);
        return ApiResponse.ok(null);
    }

    // ---------------- competitions ----------------

    /**
     * 竞赛分页查询（含主办方名称列表）
     */
    @GetMapping("/competitions")
    public ApiResponse<PageResult<CompetitionVO>> pageCompetitions(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer enabled) {
        Page<DictCompetition> page = competitionService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DictCompetition>()
                        .eq(categoryId != null, DictCompetition::getCategoryId, categoryId)
                        .eq(enabled != null, DictCompetition::getEnabled, enabled)
                        .orderByAsc(DictCompetition::getSortNo)
                        .orderByAsc(DictCompetition::getId));

        // 转换为VO，包含主办方名称
        java.util.List<CompetitionVO> voList = new java.util.ArrayList<>();
        for (DictCompetition comp : page.getRecords()) {
            CompetitionVO vo = new CompetitionVO();
            vo.setId(comp.getId());
            vo.setCompetitionName(comp.getCompetitionName());
            vo.setCompetitionShortName(comp.getCompetitionShortName());
            vo.setCategoryId(comp.getCategoryId());
            vo.setEnabled(comp.getEnabled());
            vo.setSortNo(comp.getSortNo());
            vo.setRemark(comp.getRemark());

            // 查询主办方名称
            java.util.List<String> organizerNames = getOrganizerNamesByCompetitionId(comp.getId());
            vo.setOrganizerNames(organizerNames);

            // 兼容旧前端：提供 organizerId / organizerName（字符串）
            Long primaryOrganizerId = getPrimaryOrganizerIdByCompetitionId(comp.getId());
            vo.setOrganizerId(primaryOrganizerId);
            vo.setOrganizerName(
                    organizerNames == null || organizerNames.isEmpty() ? null : String.join(",", organizerNames));

            voList.add(vo);
        }

        return ApiResponse.ok(PageResult.of(page.getTotal(), voList));
    }

    /**
     * 根据竞赛ID获取主办方名称列表
     */
    private java.util.List<String> getOrganizerNamesByCompetitionId(Long competitionId) {
        java.util.List<DictCompetitionOrganizer> organizers = competitionOrganizerService
                .listByCompetitionId(competitionId);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (DictCompetitionOrganizer co : organizers) {
            DictOrganizer org = organizerService.getById(co.getOrganizerId());
            if (org != null) {
                String name = org.getOrganizerName();
                if (co.getIsPrimary() != null && co.getIsPrimary() == 1) {
                    name = name + "(主)";
                }
                names.add(name);
            }
        }
        return names;
    }

    private Long getPrimaryOrganizerIdByCompetitionId(Long competitionId) {
        java.util.List<DictCompetitionOrganizer> organizers = competitionOrganizerService
                .listByCompetitionId(competitionId);
        if (organizers == null || organizers.isEmpty())
            return null;
        for (DictCompetitionOrganizer co : organizers) {
            if (co.getIsPrimary() != null && co.getIsPrimary() == 1) {
                return co.getOrganizerId();
            }
        }
        return organizers.get(0).getOrganizerId();
    }

    /**
     * 获取竞赛详情（含主办方列表）
     */
    @GetMapping("/competitions/{id}")
    public ApiResponse<CompetitionDetailVO> getCompetition(@PathVariable Long id) {
        DictCompetition comp = competitionService.getById(id);
        if (comp == null) {
            return ApiResponse.ok(null);
        }
        // 查询主办方关联
        java.util.List<DictCompetitionOrganizer> organizers = competitionOrganizerService.listByCompetitionId(id);
        // 查询主办方详情
        java.util.List<OrganizerVO> organizerVOs = new java.util.ArrayList<>();
        for (DictCompetitionOrganizer co : organizers) {
            DictOrganizer org = organizerService.getById(co.getOrganizerId());
            if (org != null) {
                OrganizerVO vo = new OrganizerVO();
                vo.setId(org.getId());
                vo.setOrganizerName(org.getOrganizerName());
                vo.setIsPrimary(co.getIsPrimary());
                vo.setSortNo(co.getSortNo());
                organizerVOs.add(vo);
            }
        }
        CompetitionDetailVO vo = new CompetitionDetailVO();
        vo.setId(comp.getId());
        vo.setCompetitionName(comp.getCompetitionName());
        vo.setCompetitionShortName(comp.getCompetitionShortName());
        vo.setCategoryId(comp.getCategoryId());
        vo.setEnabled(comp.getEnabled());
        vo.setSortNo(comp.getSortNo());
        vo.setRemark(comp.getRemark());
        vo.setOrganizers(organizerVOs);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/competitions")
    public ApiResponse<Long> createCompetition(@RequestBody @Valid CompetitionUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetition e = new DictCompetition();
        e.setCompetitionName(req.getCompetitionName());
        e.setCompetitionShortName(req.getCompetitionShortName());
        e.setCategoryId(req.getCategoryId());
        e.setEnabled(1);
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        competitionService.save(e);

        // 保存主办方关联
        saveOrganizersCompat(e.getId(), req);

        return ApiResponse.ok(e.getId());
    }

    @PutMapping("/competitions/{id}")
    public ApiResponse<Void> updateCompetition(@PathVariable Long id, @RequestBody @Valid CompetitionUpsertReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetition e = new DictCompetition();
        e.setId(id);
        e.setCompetitionName(req.getCompetitionName());
        e.setCompetitionShortName(req.getCompetitionShortName());
        e.setCategoryId(req.getCategoryId());
        e.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        e.setRemark(req.getRemark());
        competitionService.updateById(e);

        // 更新主办方关联
        saveOrganizersCompat(id, req);

        return ApiResponse.ok(null);
    }

    private void saveOrganizersCompat(Long competitionId, CompetitionUpsertReq req) {
        // 新版：organizers
        if (req.getOrganizers() != null && !req.getOrganizers().isEmpty()) {
            saveOrganizers(competitionId, req.getOrganizers());
            return;
        }
        // 旧版兼容：organizerId（单主办方）
        if (req.getOrganizerId() != null) {
            CompetitionOrganizerReq one = new CompetitionOrganizerReq();
            one.setOrganizerId(req.getOrganizerId());
            one.setIsPrimary(true);
            one.setSortNo(0);
            saveOrganizers(competitionId, java.util.List.of(one));
            return;
        }
        // 都没有传：清空
        saveOrganizers(competitionId, java.util.List.of());
    }

    /**
     * 保存竞赛的主办方关联（空列表也会清空旧关联）
     */
    private void saveOrganizers(Long competitionId, java.util.List<CompetitionOrganizerReq> organizers) {
        java.util.List<DictCompetitionOrganizer> list = new java.util.ArrayList<>();
        if (organizers != null && !organizers.isEmpty()) {
            int sortNo = 0;
            for (CompetitionOrganizerReq org : organizers) {
                DictCompetitionOrganizer co = new DictCompetitionOrganizer();
                co.setOrganizerId(org.getOrganizerId());
                co.setIsPrimary(Boolean.TRUE.equals(org.getIsPrimary()) ? 1 : 0);
                co.setSortNo(org.getSortNo() != null ? org.getSortNo() : sortNo++);
                list.add(co);
            }
        }
        competitionOrganizerService.saveCompetitionOrganizers(competitionId, list);
    }

    @PostMapping("/competitions/{id}/toggle")
    public ApiResponse<Void> toggleCompetition(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetition e = competitionService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        e.setEnabled(e.getEnabled() != null && e.getEnabled() == 1 ? 0 : 1);
        competitionService.updateById(e);
        return ApiResponse.ok(null);
    }

    /**
     * 删除竞赛（仅停用状态可删除；有填报引用时不允许删除；删除前清理主办方关联）。
     */
    @DeleteMapping("/competitions/{id}")
    public ApiResponse<Void> deleteCompetition(@PathVariable Long id) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        DictCompetition e = competitionService.getById(id);
        if (e == null)
            return ApiResponse.ok(null);
        if (e.getEnabled() != null && e.getEnabled() == 1) {
            return ApiResponse.fail(1, "启用状态的竞赛不能删除，请先停用");
        }
        long records = awardRecordMapper.selectCount(new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getCompetitionId, id)
                .and(w -> w.eq(BizAwardRecord::getDeleted, 0).or().isNull(BizAwardRecord::getDeleted)));
        if (records > 0) {
            return ApiResponse.fail(1, "仍有获奖填报引用该竞赛，无法删除");
        }
        competitionOrganizerService.saveCompetitionOrganizers(id, java.util.List.of());
        competitionService.removeById(id);
        return ApiResponse.ok(null);
    }

    // ---------------- requests ----------------

    @Data
    public static class CategoryUpsertReq {
        @NotBlank
        private String categoryName;
        private Integer sortNo;
        private String remark;
    }

    @Data
    public static class OrganizerUpsertReq {
        @NotBlank
        private String organizerName;
        private Integer sortNo;
        private String remark;
    }

    @Data
    public static class AwardScopeUpsertReq {
        @NotBlank
        private String scopeName;
        private Integer sortNo;
    }

    @Data
    public static class AwardLevelUpsertReq {
        /**
         * 所属竞赛ID（可选，不关联具体竞赛时为空）
         */
        private Long competitionId;
        @NotNull
        private Long awardScopeId;
        @NotBlank
        private String levelName;
        private Integer sortNo;
    }

    @Data
    public static class CompetitionUpsertReq {
        @NotBlank
        private String competitionName;
        private String competitionShortName;
        @NotNull
        private Long categoryId;
        // 旧版兼容：单主办方
        private Long organizerId;
        private Integer sortNo;
        private String remark;
        /**
         * 主办方列表（可选）
         */
        private java.util.List<CompetitionOrganizerReq> organizers;
    }

    /**
     * 竞赛主办方关联请求
     */
    @Data
    public static class CompetitionOrganizerReq {
        @NotNull
        private Long organizerId;
        /**
         * 是否主要主办方：true-是 false-否
         */
        private Boolean isPrimary;
        private Integer sortNo;
    }

    // ---------------- VOs ----------------

    /**
     * 竞赛详情VO（含主办方列表）
     */
    @Data
    public static class CompetitionDetailVO {
        private Long id;
        private String competitionName;
        private String competitionShortName;
        private Long categoryId;
        private Integer enabled;
        private Integer sortNo;
        private String remark;
        private java.util.List<OrganizerVO> organizers;
    }

    /**
     * 主办方VO（用于竞赛详情中展示）
     */
    @Data
    public static class OrganizerVO {
        private Long id;
        private String organizerName;
        private Integer isPrimary;
        private Integer sortNo;
    }

    /**
     * 竞赛列表VO（含主办方名称列表）
     */
    @Data
    public static class CompetitionVO {
        private Long id;
        private String competitionName;
        private String competitionShortName;
        private Long categoryId;
        // 兼容旧前端字段：列表页如果仍然读 organizerId/organizerName，将能正常展示
        private Long organizerId;
        private String organizerName;
        private Integer enabled;
        private Integer sortNo;
        private String remark;
        /**
         * 主办方名称列表（格式化后的字符串）
         */
        private java.util.List<String> organizerNames;
    }
}
