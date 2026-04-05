package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 竞赛类别字典
 */
@Data
@TableName("dict_competition_category")
public class DictCompetitionCategory {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 类别名称
     */
    private String categoryName;

    /**
     * 类别编码
     */
    private String categoryCode;

    private Integer enabled;
    private Integer sortNo;
    private String remark;
}
