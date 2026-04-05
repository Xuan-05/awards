package com.university.awards.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.university.awards.dict.entity.DictCompetitionOrganizer;

import java.util.List;

/**
 * 竞赛-主办方关联服务
 */
public interface DictCompetitionOrganizerService extends IService<DictCompetitionOrganizer> {

    /**
     * 根据竞赛ID查询主办方列表
     */
    List<DictCompetitionOrganizer> listByCompetitionId(Long competitionId);

    /**
     * 保存竞赛的主办方关联（先删后增）
     */
    void saveCompetitionOrganizers(Long competitionId, List<DictCompetitionOrganizer> organizers);
}
