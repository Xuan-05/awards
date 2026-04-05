package com.university.awards.team.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.common.PageResult;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.team.dto.TeamAdminListRow;
import com.university.awards.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端团队查询（仅 SCHOOL_ADMIN / SYS_ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final AuthzService authz;
    private final TeamService teamService;

    @GetMapping
    public ApiResponse<PageResult<TeamAdminListRow>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) String captainRealName,
            @RequestParam(required = false) Long ownerDeptId) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        return ApiResponse.ok(teamService.adminTeamPage(pageNo, pageSize, teamName, captainRealName, ownerDeptId));
    }
}
