package com.university.awards.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.service.DictClassService;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户组织相关接口（占位）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>根据 PRD，学生/教师基础信息未来将对接学校权威系统，本系统仅作为只读快照或映射。</li>
 *   <li>当前版本未实现外部数据同步，接口先返回空列表以满足前端页面占位与路由闭环。</li>
 * </ul>
 *
 * <p>权限：需要登录（后续可按角色收敛）。</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserMapper userMapper;
    private final DictClassService classService;

    public UserController(SysUserMapper userMapper, DictClassService classService) {
        this.userMapper = userMapper;
        this.classService = classService;
    }

    /**
     * 学生列表（只读分页）。
     */
    @GetMapping("/students")
    public ApiResponse<PageResult<UserRow>> students(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword
    ) {
        StpUtil.checkLogin();
        return ApiResponse.ok(pageByType("STUDENT", pageNo, pageSize, deptId, classId, keyword));
    }

    /**
     * 教师列表（只读分页）。
     */
    @GetMapping("/teachers")
    public ApiResponse<PageResult<UserRow>> teachers(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword
    ) {
        StpUtil.checkLogin();
        return ApiResponse.ok(pageByType("TEACHER", pageNo, pageSize, deptId, null, keyword));
    }

    /**
     * 管理员列表（只读分页）。
     */
    @GetMapping("/admins")
    public ApiResponse<PageResult<UserRow>> admins(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword
    ) {
        StpUtil.checkLogin();
        return ApiResponse.ok(pageByType("ADMIN", pageNo, pageSize, deptId, null, keyword));
    }

    private PageResult<UserRow> pageByType(String type, long pageNo, long pageSize, Long deptId, Long classId, String keyword) {
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, type)
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .eq(classId != null, SysUser::getClassId, classId)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or()
                                .like(SysUser::getRealName, keyword)
                                .or()
                                .like(SysUser::getStudentNo, keyword)
                                .or()
                                .like(SysUser::getTeacherNo, keyword)
                        )
                        .orderByDesc(SysUser::getId)
        );

        List<SysUser> users = page.getRecords();
        Set<Long> classIds = users.stream().map(SysUser::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> classNames = classIds.isEmpty()
                ? Map.of()
                : classService.listByIds(classIds).stream().collect(Collectors.toMap(DictClass::getId, DictClass::getClassName, (a, b) -> a));

        List<UserRow> rows = users.stream()
                .map(u -> new UserRow(
                        u.getId(),
                        u.getUsername(),
                        u.getRealName(),
                        u.getUserType(),
                        u.getDeptId(),
                        u.getStudentNo(),
                        u.getTeacherNo(),
                        u.getClassId(),
                        classNames.get(u.getClassId()),
                        u.getEnabled()
                ))
                .toList();

        return PageResult.of(page.getTotal(), rows);
    }

    public record UserRow(
            Long id,
            String username,
            String realName,
            String userType,
            Long deptId,
            String studentNo,
            String teacherNo,
            Long classId,
            String className,
            Integer enabled
    ) {
    }
}

