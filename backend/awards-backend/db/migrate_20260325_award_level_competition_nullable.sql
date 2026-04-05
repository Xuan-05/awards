-- 获奖等级的 competition_id 改为可空
-- 获奖等级按"获奖范围"配置，不需要关联具体竞赛

ALTER TABLE dict_award_level MODIFY COLUMN competition_id BIGINT NULL COMMENT '所属竞赛ID（可选，不关联具体竞赛时为空）';
