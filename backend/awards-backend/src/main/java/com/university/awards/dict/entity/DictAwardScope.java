package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 奖项范围字典（公共池）
 */
@Data
@TableName("dict_award_scope")
public class DictAwardScope {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 范围名称：如国家级、省级、校级
     */
    private String scopeName;

    /**
     * 范围编码：NATIONAL/PROVINCIAL/SCHOOL
     */
    private String scopeCode;

    private Integer enabled;
    private Integer sortNo;
}
