package com.university.awards.classdict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 班级字典实体（按院系归属）。
 */
@Data
@TableName("dict_class")
public class DictClass {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deptId;
    private String className;
    private Integer enabled;
    private Integer sortNo;
}

