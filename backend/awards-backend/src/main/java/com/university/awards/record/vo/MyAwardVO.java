package com.university.awards.record.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MyAwardVO {
    private Long recordId;
    private Long teamId;
    private String teamName;
    private Boolean teamDeleted;
    private String competitionName;
    private String awardName;
    private String awardLevel;
    private LocalDate awardTime;
    private String role;
}
