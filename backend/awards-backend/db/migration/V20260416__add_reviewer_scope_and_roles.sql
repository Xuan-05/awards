-- 审核员-竞赛授权表
CREATE TABLE IF NOT EXISTS `biz_reviewer_comp_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reviewer_user_id` bigint NOT NULL COMMENT '审核员用户ID（sys_user.id）',
  `competition_id` bigint NOT NULL COMMENT '可审核竞赛ID（dict_competition.id）',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `valid_from` datetime NULL DEFAULT NULL COMMENT '授权生效时间',
  `valid_to` datetime NULL DEFAULT NULL COMMENT '授权失效时间',
  `created_by` bigint NULL DEFAULT NULL,
  `updated_by` bigint NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reviewer_comp` (`reviewer_user_id`,`competition_id`),
  KEY `idx_scope_reviewer` (`reviewer_user_id`),
  KEY `idx_scope_competition` (`competition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审核员-竞赛授权范围';

-- 审核角色（幂等插入）
INSERT INTO `sys_role` (`role_code`, `role_name`)
SELECT 'COMP_REVIEWER_L1', '竞赛一级审核员'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` WHERE `role_code` = 'COMP_REVIEWER_L1'
);

INSERT INTO `sys_role` (`role_code`, `role_name`)
SELECT 'COMP_REVIEWER_L2', '竞赛二级复审员'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` WHERE `role_code` = 'COMP_REVIEWER_L2'
);

