package com.university.awards.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_award_record_file")
public class BizAwardRecordFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long fileId; // sys_file.id
    private Integer deleted;
    private LocalDateTime createdAt;
}

