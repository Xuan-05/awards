package com.university.awards.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_export_task")
public class BizExportTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String exportType; // 例如 DISCIPLINE_STATS
    private String fileName;
    private String filePath;
    private String filterJson;
    private String taskStatus; // PENDING/RUNNING/SUCCESS/FAILED
    private Long creatorUserId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}

