package com.university.awards.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class SysFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;
    private String fileExt;
    private String mimeType;
    private String storagePath;
    private String previewType; // image/pdf/download_only
    private Long fileSize;
    private Long uploaderUserId;
    private LocalDateTime createdAt;
    private Integer deleted;
}

