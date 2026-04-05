package com.university.awards.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 竞赛-主办方关联表
 */
@Data
@TableName("dict_competition_organizer")
public class DictCompetitionOrganizer {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 竞赛ID
     */
    private Long competitionId;

    /**
     * 主办方ID
     */
    private Long organizerId;

    /**
     * 是否主要主办方：0-否 1-是
     */
    private Integer isPrimary;

    /**
     * 排序号
     */
    private Integer sortNo;
}
