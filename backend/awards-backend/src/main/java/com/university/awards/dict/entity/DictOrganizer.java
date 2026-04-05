package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 主办单位字典（公共池）
 */
@Data
@TableName("dict_organizer")
public class DictOrganizer {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主办单位名称
     */
    private String organizerName;

    /**
     * 单位类型：MINISTRY/ASSOCIATION/ENTERPRISE/UNIVERSITY
     */
    private String organizerType;

    private Integer enabled;
    private Integer sortNo;
    private String remark;
}
