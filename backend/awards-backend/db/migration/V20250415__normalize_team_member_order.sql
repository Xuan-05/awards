-- 归一化团队成员展示顺序（复用列 member_order_no；不新增 sort_order）
-- 前置：MySQL 8+（使用窗口函数）。执行前建议备份：mysqldump --single-transaction awards > backup_awards.sql
--
-- 规则：
-- 1) 队长行 is_captain=1 -> member_order_no = 1
-- 2) 非队长且已接受成员 -> 按 team_id 分组，按原 member_order_no、id 稳定排序后赋值为 2,3,4,...

USE awards;

UPDATE biz_team_member
SET member_order_no = 1
WHERE is_captain = 1;

UPDATE biz_team_member m
INNER JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY team_id
               ORDER BY member_order_no ASC, id ASC
           ) + 1 AS new_order
    FROM biz_team_member
    WHERE is_captain = 0
      AND join_status = 'ACCEPTED'
) x ON m.id = x.id
SET m.member_order_no = x.new_order;
