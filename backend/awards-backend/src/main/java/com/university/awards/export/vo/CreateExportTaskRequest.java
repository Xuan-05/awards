package com.university.awards.export.vo;

import lombok.Data;

@Data
public class CreateExportTaskRequest {
    private String reportCode;
    private String filters;
}

