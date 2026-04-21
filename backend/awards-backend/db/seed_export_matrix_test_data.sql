-- =========================================================
-- 导出测试数据（矩阵报表）批量造数脚本
-- 目标：
-- 1) 新增一批学生
-- 2) 新增一批团队与团队成员
-- 3) 新增一批“已通过”竞赛填报记录（覆盖国家级/省部级 + 特/一/二/三/优）
--
-- 说明：
-- - 数据完全基于当前库现有字典（院系、竞赛、奖项范围、奖项等级）动态生成
-- - 可重复执行：会先清理本脚本上次生成的数据（按 SEED 标记）
-- - 仅用于本地联调/导出测试，请勿直接用于生产库
-- =========================================================

USE awards;

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

SET @seed_tag := CONVERT('[SEED_EXPORT_MATRIX]' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @email_like := CONVERT('seedexp+matrix+%@example.edu' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @pwd_hash := '$2a$10$0gPgoZXD6GwMjIdtkd6SxOd9Abbv6e8REtHuyttVk6wPHunyS.GWG'; -- 明文 123456

-- ----------------------------
-- 0) 清理上一次同标签测试数据
-- ----------------------------
DELETE FROM biz_award_record WHERE remark = @seed_tag;

DELETE tm
FROM biz_team_member tm
JOIN biz_team t ON t.id = tm.team_id
WHERE t.remark = @seed_tag;

DELETE tt
FROM biz_team_teacher tt
JOIN biz_team t ON t.id = tt.team_id
WHERE t.remark = @seed_tag;

DELETE FROM biz_team WHERE remark = @seed_tag;

DELETE ur
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.email LIKE @email_like;

DELETE FROM sys_user
WHERE email LIKE @email_like;

-- ----------------------------
-- 1) 基础辅助表（数字、院系、教师、竞赛）
-- ----------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_num;
CREATE TEMPORARY TABLE tmp_num (
  n INT NOT NULL PRIMARY KEY
);

INSERT INTO tmp_num(n)
SELECT ones.i + tens.i * 10 + hundreds.i * 100 + 1 AS n
FROM (
  SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
CROSS JOIN (
  SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens
CROSS JOIN (
  SELECT 0 AS i UNION ALL SELECT 1
) hundreds
WHERE ones.i + tens.i * 10 + hundreds.i * 100 + 1 <= 120;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_dept;
CREATE TEMPORARY TABLE tmp_seed_dept (
  rn INT NOT NULL PRIMARY KEY,
  dept_id BIGINT NOT NULL,
  dept_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL
);

SET @r := 0;
INSERT INTO tmp_seed_dept(rn, dept_id, dept_code)
SELECT (@r := @r + 1) AS rn, d.id, LOWER(d.dept_code)
FROM sys_dept d
WHERE d.enabled = 1
ORDER BY d.sort_no, d.id;

SET @dept_cnt := (SELECT COUNT(*) FROM tmp_seed_dept);

DROP TEMPORARY TABLE IF EXISTS tmp_seed_teachers;
CREATE TEMPORARY TABLE tmp_seed_teachers (
  rn INT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL
);

SET @r := 0;
INSERT INTO tmp_seed_teachers(rn, user_id)
SELECT (@r := @r + 1) AS rn, u.id
FROM sys_user u
WHERE u.enabled = 1
  AND u.user_type = CONVERT('TEACHER' USING utf8mb4) COLLATE utf8mb4_general_ci
ORDER BY u.id;

SET @teacher_cnt := (SELECT COUNT(*) FROM tmp_seed_teachers);

DROP TEMPORARY TABLE IF EXISTS tmp_seed_comp;
CREATE TEMPORARY TABLE tmp_seed_comp (
  rn INT NOT NULL PRIMARY KEY,
  competition_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL
);

SET @r := 0;
INSERT INTO tmp_seed_comp(rn, competition_id, category_id)
SELECT (@r := @r + 1) AS rn, c.id, c.category_id
FROM dict_competition c
WHERE c.enabled = 1
ORDER BY c.sort_no, c.id
LIMIT 60;

SET @comp_cnt := (SELECT COUNT(*) FROM tmp_seed_comp);

-- 奖项范围（国家级/省部级）
SET @scope_nat := (
  SELECT s.id
  FROM dict_award_scope s
  WHERE s.enabled = 1
    AND (s.scope_code = CONVERT('NATIONAL' USING utf8mb4) COLLATE utf8mb4_general_ci
      OR s.scope_name LIKE CONVERT('%国家%' USING utf8mb4) COLLATE utf8mb4_general_ci)
  ORDER BY s.sort_no, s.id
  LIMIT 1
);

SET @scope_prov := (
  SELECT s.id
  FROM dict_award_scope s
  WHERE s.enabled = 1
    AND (s.scope_code = CONVERT('PROVINCIAL' USING utf8mb4) COLLATE utf8mb4_general_ci
      OR s.scope_name LIKE CONVERT('%省%' USING utf8mb4) COLLATE utf8mb4_general_ci
      OR s.scope_name LIKE CONVERT('%部%' USING utf8mb4) COLLATE utf8mb4_general_ci)
  ORDER BY s.sort_no, s.id
  LIMIT 1
);

-- 奖项等级ID（国家级）
SET @lvl_nat_special := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat AND level_name LIKE '%特%' ORDER BY sort_no, id LIMIT 1),
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat ORDER BY sort_no, id LIMIT 1)
);
SET @lvl_nat_first := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat AND (level_name LIKE '%一%' OR level_name LIKE '%1%') ORDER BY sort_no, id LIMIT 1),
  @lvl_nat_special
);
SET @lvl_nat_second := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat AND (level_name LIKE '%二%' OR level_name LIKE '%2%') ORDER BY sort_no, id LIMIT 1),
  @lvl_nat_first
);
SET @lvl_nat_third := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat AND (level_name LIKE '%三%' OR level_name LIKE '%3%') ORDER BY sort_no, id LIMIT 1),
  @lvl_nat_second
);
SET @lvl_nat_excellent := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_nat AND level_name LIKE '%优%' ORDER BY sort_no, id LIMIT 1),
  @lvl_nat_third
);

-- 奖项等级ID（省部级）
SET @lvl_prov_special := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov AND level_name LIKE '%特%' ORDER BY sort_no, id LIMIT 1),
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov ORDER BY sort_no, id LIMIT 1)
);
SET @lvl_prov_first := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov AND (level_name LIKE '%一%' OR level_name LIKE '%1%') ORDER BY sort_no, id LIMIT 1),
  @lvl_prov_special
);
SET @lvl_prov_second := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov AND (level_name LIKE '%二%' OR level_name LIKE '%2%') ORDER BY sort_no, id LIMIT 1),
  @lvl_prov_first
);
SET @lvl_prov_third := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov AND (level_name LIKE '%三%' OR level_name LIKE '%3%') ORDER BY sort_no, id LIMIT 1),
  @lvl_prov_second
);
SET @lvl_prov_excellent := COALESCE(
  (SELECT id FROM dict_award_level WHERE enabled = 1 AND award_scope_id = @scope_prov AND level_name LIKE '%优%' ORDER BY sort_no, id LIMIT 1),
  @lvl_prov_third
);

-- ----------------------------
-- 2) 新增学生（36人）
-- ----------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_student_base;
CREATE TEMPORARY TABLE tmp_seed_student_base (
  n INT NOT NULL PRIMARY KEY,
  dept_id BIGINT NOT NULL,
  dept_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  class_id BIGINT NULL
);

INSERT INTO tmp_seed_student_base(n, dept_id, dept_code, class_id)
SELECT
  n.n,
  COALESCE(d.dept_id, (SELECT MIN(id) FROM sys_dept)) AS dept_id,
  COALESCE(d.dept_code, 'dept') AS dept_code,
  (
    SELECT c.id
    FROM dict_class c
    WHERE c.enabled = 1
      AND c.dept_id = COALESCE(d.dept_id, (SELECT MIN(id) FROM sys_dept))
    ORDER BY c.sort_no, c.id
    LIMIT 1
  ) AS class_id
FROM tmp_num n
LEFT JOIN tmp_seed_dept d
  ON d.rn = ((n.n - 1) % @dept_cnt) + 1
WHERE n.n <= 36
  AND @dept_cnt > 0;

INSERT INTO sys_user (
  username, password_hash, real_name, user_type, dept_id, enabled, created_at, updated_at,
  student_no, teacher_no, class_id, phone, email
)
SELECT
  CONCAT(
    'student_',
    b.dept_code,
    '_',
    LPAD(b.n, 3, '0')
  ) AS username,
  @pwd_hash AS password_hash,
  CONCAT('导出学生', LPAD(b.n, 3, '0')) AS real_name,
  'STUDENT' AS user_type,
  b.dept_id AS dept_id,
  1 AS enabled,
  NOW() AS created_at,
  NOW() AS updated_at,
  CONCAT('SEED', DATE_FORMAT(NOW(), '%y%m'), LPAD(b.n, 6, '0')) AS student_no,
  NULL AS teacher_no,
  b.class_id AS class_id,
  CONCAT('1399', LPAD(b.n, 7, '0')) AS phone,
  CONCAT('seedexp+matrix+', LPAD(b.n, 3, '0'), '@example.edu') AS email
FROM tmp_seed_student_base b;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_students;
CREATE TEMPORARY TABLE tmp_seed_students (
  rn INT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL
);

SET @r := 0;
INSERT INTO tmp_seed_students(rn, user_id, dept_id)
SELECT (@r := @r + 1) AS rn, u.id, u.dept_id
FROM sys_user u
WHERE u.email LIKE @email_like
ORDER BY u.id;

SET @stu_cnt := (SELECT COUNT(*) FROM tmp_seed_students);

-- ----------------------------
-- 3) 新增团队（12个）+ 成员关系
-- ----------------------------
INSERT INTO biz_team (
  team_name, captain_user_id, owner_dept_id, status, remark, deleted, created_at, updated_at
)
SELECT
  CONCAT('SEED导出测试队', LPAD(s.rn, 2, '0')) AS team_name,
  s.user_id AS captain_user_id,
  s.dept_id AS owner_dept_id,
  'ACTIVE' AS status,
  @seed_tag AS remark,
  0 AS deleted,
  NOW() AS created_at,
  NOW() AS updated_at
FROM tmp_seed_students s
WHERE s.rn <= 12;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_teams;
CREATE TEMPORARY TABLE tmp_seed_teams (
  rn INT NOT NULL PRIMARY KEY,
  team_id BIGINT NOT NULL,
  captain_user_id BIGINT NOT NULL,
  owner_dept_id BIGINT NOT NULL
);

SET @r := 0;
INSERT INTO tmp_seed_teams(rn, team_id, captain_user_id, owner_dept_id)
SELECT (@r := @r + 1) AS rn, t.id, t.captain_user_id, t.owner_dept_id
FROM biz_team t
WHERE t.remark = @seed_tag
ORDER BY t.id;

SET @team_cnt := (SELECT COUNT(*) FROM tmp_seed_teams);

-- 队长
INSERT INTO biz_team_member (team_id, user_id, member_order_no, is_captain, join_status)
SELECT t.team_id, t.captain_user_id, 1, 1, 'ACCEPTED'
FROM tmp_seed_teams t;

-- 成员2
INSERT INTO biz_team_member (team_id, user_id, member_order_no, is_captain, join_status)
SELECT t.team_id, s.user_id, 2, 0, 'ACCEPTED'
FROM tmp_seed_teams t
JOIN tmp_seed_students s ON s.rn = ((t.rn + 11 - 1) % @stu_cnt) + 1
WHERE s.user_id <> t.captain_user_id;

-- 成员3
INSERT INTO biz_team_member (team_id, user_id, member_order_no, is_captain, join_status)
SELECT t.team_id, s.user_id, 3, 0, 'ACCEPTED'
FROM tmp_seed_teams t
JOIN tmp_seed_students s ON s.rn = ((t.rn + 23 - 1) % @stu_cnt) + 1
WHERE s.user_id <> t.captain_user_id;

-- 指导教师（若库里有教师，则每队配一名）
INSERT INTO biz_team_teacher (team_id, teacher_user_id, teacher_order_no, join_status)
SELECT t.team_id, te.user_id, 1, 'ACCEPTED'
FROM tmp_seed_teams t
JOIN tmp_seed_teachers te ON te.rn = ((t.rn - 1) % @teacher_cnt) + 1
WHERE @teacher_cnt > 0;

-- 队长角色（便于部分页面联调）
INSERT INTO sys_user_role (user_id, role_id)
SELECT DISTINCT t.captain_user_id, r.id
FROM tmp_seed_teams t
JOIN sys_role r ON r.role_code = 'CAPTAIN'
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- ----------------------------
-- 4) 新增竞赛填报（120条，全部 APPROVED）
-- ----------------------------
INSERT INTO biz_award_record (
  team_id,
  competition_id,
  competition_category_id,
  award_scope_id,
  award_level_id,
  award_date,
  semester,
  project_name,
  team_award_count,
  owner_dept_id,
  status,
  submitter_user_id,
  submit_time,
  final_audit_time,
  remark,
  version,
  deleted,
  created_at,
  updated_at
)
SELECT
  t.team_id,
  c.competition_id,
  c.category_id,
  CASE WHEN MOD(n.n, 2) = 0 THEN @scope_nat ELSE @scope_prov END AS award_scope_id,
  CASE
    WHEN MOD(n.n, 2) = 0 THEN
      CASE MOD(n.n - 1, 5)
        WHEN 0 THEN @lvl_nat_special
        WHEN 1 THEN @lvl_nat_first
        WHEN 2 THEN @lvl_nat_second
        WHEN 3 THEN @lvl_nat_third
        ELSE @lvl_nat_excellent
      END
    ELSE
      CASE MOD(n.n - 1, 5)
        WHEN 0 THEN @lvl_prov_special
        WHEN 1 THEN @lvl_prov_first
        WHEN 2 THEN @lvl_prov_second
        WHEN 3 THEN @lvl_prov_third
        ELSE @lvl_prov_excellent
      END
  END AS award_level_id,
  DATE_ADD('2026-01-01', INTERVAL n.n DAY) AS award_date,
  CASE MOD(n.n, 3)
    WHEN 0 THEN '2024-2025-2'
    WHEN 1 THEN '2025-2026-1'
    ELSE '2025-2026-2'
  END AS semester,
  CONCAT('SEED导出项目-', LPAD(n.n, 3, '0')) AS project_name,
  1 + MOD(n.n - 1, 6) AS team_award_count,
  t.owner_dept_id,
  'APPROVED' AS status,
  t.captain_user_id AS submitter_user_id,
  DATE_ADD('2026-02-01 08:00:00', INTERVAL n.n DAY) AS submit_time,
  DATE_ADD('2026-02-01 10:00:00', INTERVAL n.n DAY) AS final_audit_time,
  @seed_tag AS remark,
  1 AS version,
  0 AS deleted,
  DATE_ADD('2026-02-01 07:30:00', INTERVAL n.n DAY) AS created_at,
  DATE_ADD('2026-02-01 10:00:00', INTERVAL n.n DAY) AS updated_at
FROM tmp_num n
JOIN tmp_seed_teams t ON t.rn = ((n.n - 1) % @team_cnt) + 1
JOIN tmp_seed_comp c ON c.rn = ((n.n - 1) % @comp_cnt) + 1
WHERE n.n <= 120
  AND @team_cnt > 0
  AND @comp_cnt > 0
  AND @scope_nat IS NOT NULL
  AND @scope_prov IS NOT NULL
  AND @lvl_nat_special IS NOT NULL
  AND @lvl_prov_special IS NOT NULL;

-- ----------------------------
-- 5) 结果核对
-- ----------------------------
SELECT 'SEED users' AS metric, COUNT(*) AS cnt
FROM sys_user
WHERE email LIKE @email_like
UNION ALL
SELECT 'SEED teams', COUNT(*)
FROM biz_team
WHERE remark = @seed_tag
UNION ALL
SELECT 'SEED team_members', COUNT(*)
FROM biz_team_member tm
JOIN biz_team t ON t.id = tm.team_id
WHERE t.remark = @seed_tag
UNION ALL
SELECT 'SEED approved_records', COUNT(*)
FROM biz_award_record
WHERE remark = @seed_tag
  AND status = 'APPROVED';

-- 可选：看看奖项覆盖是否均匀
SELECT
  d.scope_name AS 奖项范围,
  l.level_name AS 奖项等级,
  COUNT(*) AS 记录数,
  SUM(r.team_award_count) AS 获奖人次合计
FROM biz_award_record r
JOIN dict_award_scope d ON d.id = r.award_scope_id
JOIN dict_award_level l ON l.id = r.award_level_id
WHERE r.remark = @seed_tag
GROUP BY d.scope_name, l.level_name
ORDER BY d.scope_name, l.level_name;

