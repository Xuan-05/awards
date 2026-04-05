package com.university.awards.team.dto;

import java.time.LocalDateTime;

public record TeamAdminListRow(
        Long id,
        String teamName,
        Long captainUserId,
        String captainRealName,
        Long ownerDeptId,
        long memberCount,
        long teacherCount,
        String status,
        LocalDateTime createdAt
) {
}
