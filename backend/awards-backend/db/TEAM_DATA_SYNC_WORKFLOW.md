# 团队共享数据同步流程（前端操作后可随 Git 同步）

适用场景：你在页面上新增/修改了“需要团队共享”的业务数据（例如竞赛字典），希望通过 `commit + push` 让协作者 `pull` 后也得到同样数据。

## 核心原则

- 不直接提交本地数据库文件。
- 把“数据变更”固化为 `db/migration/` 下的 SQL migration 文件。
- 协作者拉代码后执行 migration，实现数据一致。

## 标准流程

1. 在本地页面完成数据操作（例如新增竞赛）。
2. 生成 migration 文件（建议脚本自动生成）：
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\db\scripts\new_migration.ps1 -Name "add_competition_xxx"
   ```
3. 在生成的 SQL 文件中写入本次变更（建议包含回滚说明）。
4. 本地执行该 migration，确认可重复执行或有明确幂等策略。
5. 提交代码：
   - migration SQL
   - 必要说明文档（可选）
6. 推送到 GitHub，协作者 pull 后执行相同 migration。

## 示例（新增竞赛字典）

```sql
START TRANSACTION;

INSERT INTO dict_competition (competition_name, competition_short_name, category_id, enabled, sort_no, remark)
VALUES ('全国大学生XX竞赛', 'XX竞赛', 1, 1, 100, 'shared data')
ON DUPLICATE KEY UPDATE
  competition_short_name = VALUES(competition_short_name),
  category_id = VALUES(category_id),
  enabled = VALUES(enabled),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark);

COMMIT;
```

## 协作者执行建议

- 拉取后优先执行 `db/migration/` 中新增脚本。
- 执行顺序按文件名升序（`VYYYYMMDD__...`）。
- 若项目未来接入 Flyway/Liquibase，可直接复用这些 migration 文件。

## 注意事项

- `data/files/` 为运行期附件目录，默认忽略，不参与数据同步。
- 含隐私/敏感信息的数据不要直接入库到 Git。
- 若必须提交样例数据，请先脱敏并在 PR 中注明用途与来源。
