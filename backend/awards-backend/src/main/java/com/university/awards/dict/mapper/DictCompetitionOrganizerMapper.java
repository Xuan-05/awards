package com.university.awards.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.university.awards.dict.entity.DictCompetitionOrganizer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 竞赛-主办方关联表Mapper
 */
@Mapper
public interface DictCompetitionOrganizerMapper extends BaseMapper<DictCompetitionOrganizer> {
}
