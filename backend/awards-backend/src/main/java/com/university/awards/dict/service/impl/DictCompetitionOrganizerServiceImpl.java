package com.university.awards.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.university.awards.dict.entity.DictCompetitionOrganizer;
import com.university.awards.dict.mapper.DictCompetitionOrganizerMapper;
import com.university.awards.dict.service.DictCompetitionOrganizerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 竞赛-主办方关联服务实现
 */
@Service
public class DictCompetitionOrganizerServiceImpl extends ServiceImpl<DictCompetitionOrganizerMapper, DictCompetitionOrganizer>
        implements DictCompetitionOrganizerService {

    @Override
    public List<DictCompetitionOrganizer> listByCompetitionId(Long competitionId) {
        return list(new LambdaQueryWrapper<DictCompetitionOrganizer>()
                .eq(DictCompetitionOrganizer::getCompetitionId, competitionId)
                .orderByAsc(DictCompetitionOrganizer::getSortNo)
                .orderByAsc(DictCompetitionOrganizer::getId));
    }

    @Override
    @Transactional
    public void saveCompetitionOrganizers(Long competitionId, List<DictCompetitionOrganizer> organizers) {
        // 先删除旧关联
        remove(new LambdaQueryWrapper<DictCompetitionOrganizer>()
                .eq(DictCompetitionOrganizer::getCompetitionId, competitionId));
        // 再插入新关联
        if (organizers != null && !organizers.isEmpty()) {
            for (DictCompetitionOrganizer org : organizers) {
                org.setCompetitionId(competitionId);
            }
            saveBatch(organizers);
        }
    }
}
