package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 奖项等级字典（按竞赛+范围配置）
 */
@Data
@TableName("dict_award_level")
public class DictAwardLevel {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属竞赛ID
     */
    private Long competitionId;

    /**
     * 奖项范围ID
     */
    private Long awardScopeId;

    /**
     * 等级名称：如一等奖、二等奖
     */
    private String levelName;

    private Integer enabled;
    private Integer sortNo;
}
