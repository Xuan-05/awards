package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 竞赛字典
 */
@Data
@TableName("dict_competition")
public class DictCompetition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 竞赛全称
     */
    private String competitionName;

    /**
     * 竞赛简称
     */
    private String competitionShortName;

    /**
     * 竞赛类别ID
     */
    private Long categoryId;

    private Integer enabled;
    private Integer sortNo;

    /**
     * 备注说明
     */
    private String remark;
}
