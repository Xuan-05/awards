package com.university.awards.record.controller;

import com.university.awards.common.ApiResponse;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.record.service.AwardRecordService;
import com.university.awards.record.vo.MyAwardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MyAwardController {

    private final AwardRecordService awardRecordService;
    private final AuthzService authzService;

    @GetMapping("/my-awards")
    public ApiResponse<List<MyAwardVO>> myAwards() {
        Long userId = authzService.currentUserId();
        String userType = authzService.hasAnyRole("TEACHER") ? "teacher" : "student";
        return ApiResponse.ok(awardRecordService.getMyAwards(userId, userType));
    }
}
