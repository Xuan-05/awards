# awards-backend 目录与文件说明

本文档用于说明 `backend/awards-backend` 中各目录与文件的职责，帮助你快速定位后端实现。

## 1. 项目定位

- 技术栈：Spring Boot 3 + Sa-Token + MyBatis-Plus + MySQL + EasyExcel。
- 核心业务：竞赛获奖填报、团队邀请、校级审核、消息通知、系统配置、日志审计、导出。
- 鉴权模型：RBAC（`CAPTAIN`/`TEACHER`/`DEPT_ADMIN`/`SCHOOL_ADMIN`/`SYS_ADMIN`）。

## 2. 根目录说明

- `pom.xml`：Maven 构建配置与依赖管理。
- `mvnw`、`mvnw.cmd`：Maven Wrapper 启动脚本（Linux/Windows）。
- `db/`：初始化与迁移 SQL。
- `src/`：应用源码与资源。
- `target/`：Maven 构建产物目录（生成物，不建议手改）。

## 3. 数据库脚本（`db/`）

- `init.sql`：基础表初始化（院系、字典、文件表等）。
- `rbac.sql`：用户/角色/用户角色关联表初始化。
- `m3_business.sql`：业务核心表初始化（团队、填报、审核等）。
- `m4_export.sql`：导出相关表初始化。
- `migrate_20260318_make_competition_code_nullable.sql`：竞赛编码可空迁移（旧库若曾执行，可继续执行下一条迁移彻底删列）。
- `migrate_20260319_remove_dict_code_columns.sql`：删除竞赛类别/竞赛/获奖范围/获奖等级四类字典中的「编码」列，并将 `dict_award_level` 唯一约束改为 `(award_scope_id, award_level_name)`（执行前请备份；若同范围下存在重名等级需先清理数据）。
- `migrate_20260318_add_dict_class.sql`：班级字典表迁移。
- `migrate_20260318_add_user_student_teacher_fields.sql`：`sys_user` 增加学号/工号/班级字段。
- `migrate_20260318_add_sys_message.sql`：站内消息表迁移。
- `migrate_20260318_add_sys_config.sql`：系统配置表迁移与默认参数写入。
- `migrate_20260318_add_sys_logs.sql`：登录日志/操作日志表迁移。

## 4. 资源配置（`src/main/resources`）

- `application.yml`：主配置（端口、数据源、Sa-Token、文件/导出目录、导出 Excel 模板路径等）。
- `application.properties`：可选补充配置；当前仓库内该文件为空，可按需追加 Spring Boot 属性。

## 5. 启动与测试入口

- `src/main/java/com/university/awards/AwardsBackendApplication.java`：Spring Boot 启动类。
- `src/test/java/com/university/awards/AwardsBackendApplicationTests.java`：基础测试入口。

## 6. 核心源码结构（`src/main/java/com/university/awards`）

下面按目录说明职责，并列出文件用途。

### 6.1 common（通用返回/异常/切面）

- `common/ApiResponse.java`：统一响应结构。
- `common/PageResult.java`：分页结果结构。
- `common/BizException.java`：业务异常定义（含业务码）。
- `common/GlobalExceptionHandler.java`：全局异常处理（400/500/BizException）。
- `common/OperateLogAspect.java`：控制器写操作日志 AOP（排除 login）。

### 6.2 config（框架配置）

- `config/MybatisPlusConfig.java`：MyBatis-Plus 插件配置（分页、乐观锁等）。
- `config/WebConfig.java`：Web 层配置（跨域/拦截器相关）。

### 6.3 controller（跨模块基础接口）

- `controller/AuthController.java`：登录、当前用户、改密、登录日志。
- `controller/PingController.java`：健康检查/连通性接口。

### 6.4 rbac（用户角色权限）

- `rbac/entity/SysUser.java`：用户实体。
- `rbac/entity/SysRole.java`：角色实体。
- `rbac/entity/SysUserRole.java`：用户角色关系实体。
- `rbac/mapper/SysUserMapper.java`：用户 Mapper。
- `rbac/mapper/SysRoleMapper.java`：角色 Mapper。
- `rbac/mapper/SysUserRoleMapper.java`：用户角色关系 Mapper。
- `rbac/service/RbacService.java`：RBAC 服务接口。
- `rbac/service/AuthzService.java`：鉴权服务（Spring 组件类：当前用户 ID、角色校验等）。
- `rbac/service/impl/RbacServiceImpl.java`：RBAC 服务实现。
- `rbac/config/RbacSeedRunner.java`：启动时角色种子初始化。
- `rbac/controller/AdminUserController.java`：管理员用户/角色管理接口。

### 6.5 dept（组织院系）

- `dept/entity/SysDept.java`：院系实体。
- `dept/mapper/SysDeptMapper.java`：院系 Mapper。
- `dept/service/SysDeptService.java`：院系服务接口。
- `dept/service/impl/SysDeptServiceImpl.java`：院系服务实现。
- `dept/controller/DeptController.java`：院系查询接口。

### 6.6 classdict（班级字典）

- `classdict/entity/DictClass.java`：班级字典实体。
- `classdict/mapper/DictClassMapper.java`：班级字典 Mapper。
- `classdict/service/DictClassService.java`：班级字典服务接口。
- `classdict/service/impl/DictClassServiceImpl.java`：班级字典服务实现。
- `classdict/controller/ClassController.java`：通用班级查询接口。
- `classdict/controller/AdminClassController.java`：班级字典管理接口。

### 6.7 dict（竞赛相关字典）

- `dict/entity/DictCompetition.java`：竞赛字典实体。
- `dict/entity/DictCompetitionCategory.java`：竞赛类别实体。
- `dict/entity/DictOrganizer.java`：主办方实体。
- `dict/entity/DictAwardScope.java`：获奖范围实体。
- `dict/entity/DictAwardLevel.java`：获奖等级实体。
- `dict/mapper/DictCompetitionMapper.java`：竞赛字典 Mapper。
- `dict/mapper/DictCompetitionCategoryMapper.java`：竞赛类别 Mapper。
- `dict/mapper/DictOrganizerMapper.java`：主办方 Mapper。
- `dict/mapper/DictAwardScopeMapper.java`：获奖范围 Mapper。
- `dict/mapper/DictAwardLevelMapper.java`：获奖等级 Mapper。
- `dict/service/DictCompetitionService.java`：竞赛字典服务接口。
- `dict/service/DictCompetitionCategoryService.java`：竞赛类别服务接口。
- `dict/service/DictOrganizerService.java`：主办方服务接口。
- `dict/service/DictAwardScopeService.java`：获奖范围服务接口。
- `dict/service/DictAwardLevelService.java`：获奖等级服务接口。
- `dict/service/impl/DictCompetitionServiceImpl.java`：竞赛服务实现。
- `dict/service/impl/DictCompetitionCategoryServiceImpl.java`：竞赛类别服务实现。
- `dict/service/impl/DictOrganizerServiceImpl.java`：主办方服务实现。
- `dict/service/impl/DictAwardScopeServiceImpl.java`：获奖范围服务实现。
- `dict/service/impl/DictAwardLevelServiceImpl.java`：获奖等级服务实现。
- `dict/controller/DictController.java`：字典查询/维护接口。

### 6.8 team（团队与邀请）

- `team/entity/BizTeam.java`：团队实体。
- `team/entity/BizTeamMember.java`：团队成员实体。
- `team/entity/BizTeamTeacher.java`：团队指导教师实体。
- `team/entity/BizTeamInvitation.java`：团队邀请实体。
- `team/mapper/BizTeamMapper.java`：团队 Mapper。
- `team/mapper/BizTeamMemberMapper.java`：团队成员 Mapper。
- `team/mapper/BizTeamTeacherMapper.java`：指导教师 Mapper。
- `team/mapper/BizTeamInvitationMapper.java`：邀请 Mapper。
- `team/service/TeamService.java`：团队服务接口。
- `team/service/impl/TeamServiceImpl.java`：团队服务实现（邀请、接受/拒绝、消息联动）。
- `team/controller/TeamController.java`：团队/邀请对外接口。

### 6.9 message（站内消息）

- `message/entity/SysMessage.java`：站内消息实体。
- `message/mapper/SysMessageMapper.java`：消息 Mapper。
- `message/service/SysMessageService.java`：消息基础服务接口。
- `message/service/impl/SysMessageServiceImpl.java`：消息基础服务实现。
- `message/service/MessageWriteService.java`：消息写入抽象（复用写消息逻辑）。
- `message/service/impl/MessageWriteServiceImpl.java`：消息写入实现（统一字段填充）。
- `message/controller/MessageController.java`：消息中心接口（分页、已读、删除、未读数）。

### 6.10 record（获奖填报）

- `record/entity/BizAwardRecord.java`：获奖记录实体。
- `record/entity/BizAwardRecordFile.java`：记录-附件关联实体。
- `record/mapper/BizAwardRecordMapper.java`：获奖记录 Mapper。
- `record/mapper/BizAwardRecordFileMapper.java`：记录附件关联 Mapper。
- `record/service/AwardRecordService.java`：填报服务接口。
- `record/service/impl/AwardRecordServiceImpl.java`：填报服务实现（状态机、权限、配置校验）。
- `record/controller/AwardRecordController.java`：填报对外接口（CRUD、提交、撤回、附件关联）。

### 6.11 audit（审核流）

- `audit/entity/BizAwardRecordAudit.java`：审核日志实体。
- `audit/mapper/BizAwardRecordAuditMapper.java`：审核日志 Mapper。
- `audit/controller/AuditController.java`：校级审核接口（待审、通过、驳回、轨迹）。

### 6.12 file（附件与存储）

- `file/entity/SysFile.java`：文件元数据实体。
- `file/mapper/SysFileMapper.java`：文件元数据 Mapper。
- `file/service/SysFileService.java`：文件元数据服务接口。
- `file/service/impl/SysFileServiceImpl.java`：文件元数据服务实现。
- `file/service/FileStorageService.java`：文件落盘服务（白名单、大小限制、预览类型）。
- `file/controller/FileController.java`：文件预览/下载接口。

### 6.13 export（导出）

- `export/entity/BizExportTask.java`：导出任务实体。
- `export/mapper/BizExportTaskMapper.java`：导出任务 Mapper。
- `export/service/ExportService.java`：导出服务接口。
- `export/service/impl/ExportServiceImpl.java`：导出服务实现。
- `export/controller/ExportController.java`：导出任务接口。

### 6.14 dashboard（工作台）

- `dashboard/controller/DashboardController.java`：汇总指标、趋势、待办接口。

### 6.15 systemconfig（系统配置）

- `systemconfig/entity/SysConfig.java`：系统参数实体。
- `systemconfig/mapper/SysConfigMapper.java`：系统参数 Mapper。
- `systemconfig/service/SysConfigService.java`：系统参数服务接口。
- `systemconfig/service/impl/SysConfigServiceImpl.java`：系统参数服务实现（string/int/bool 读取）。
- `systemconfig/controller/SystemConfigController.java`：系统配置管理接口。
- `systemconfig/controller/SystemRuntimeController.java`：运行时只读配置接口。

### 6.16 log（审计日志）

- `log/entity/SysLoginLog.java`：登录日志实体。
- `log/entity/SysOperateLog.java`：操作日志实体。
- `log/mapper/SysLoginLogMapper.java`：登录日志 Mapper。
- `log/mapper/SysOperateLogMapper.java`：操作日志 Mapper。
- `log/service/SysLoginLogService.java`：登录日志服务接口。
- `log/service/SysOperateLogService.java`：操作日志服务接口。
- `log/service/impl/SysLoginLogServiceImpl.java`：登录日志服务实现。
- `log/service/impl/SysOperateLogServiceImpl.java`：操作日志服务实现。
- `log/controller/AdminLogController.java`：管理员日志查询接口。

### 6.17 importer（导入）

- `importer/controller/AdminImportController.java`：竞赛字典文本导入接口。

### 6.18 sync（外部同步骨架）

- `sync/controller/SyncController.java`：外部数据同步占位/骨架接口。

### 6.19 user（用户组织查询）

- `user/controller/UserController.java`：学生/教师/管理员分页查询接口。

## 7. 关键流程入口索引

- 登录鉴权：`controller/AuthController.java` + `rbac/service/*` + Sa-Token 配置。
- 团队邀请闭环：`team/controller/TeamController.java` -> `team/service/impl/TeamServiceImpl.java` -> `message/service/MessageWriteService.java`。
- 填报-审核闭环：`record/controller/AwardRecordController.java` -> `record/service/impl/AwardRecordServiceImpl.java` -> `audit/controller/AuditController.java`。
- 附件存储链路：`record/controller/AwardRecordController.java` -> `file/service/FileStorageService.java` -> `file/controller/FileController.java`。
- 系统配置生效：`systemconfig/controller/SystemConfigController.java` + `systemconfig/service/impl/SysConfigServiceImpl.java`。

## 8. 运行期目录（相对工作目录，仓库内通常不提交）

`application.yml` 中配置了相对路径，启动时以**进程当前工作目录**为基准解析：

- `data/files`（`storage.base-dir`）：用户上传附件落盘目录，运行后自动创建。
- `data/exports`（`export.out-dir`）：导出任务输出目录，运行后自动创建。
- `templates/detail_template.xlsx`、`templates/summary_template.xlsx`（`export.template.*`）：EasyExcel 导出用模板；**当前仓库未包含 `templates/` 目录**，交付/部署时需将模板文件放到上述路径，或修改配置指向实际位置。

## 9. 生成物/依赖目录说明（仅概述）

- `target/`：Maven 编译与打包输出（`.class`、jar、maven-status 等），自动生成。

> 不建议对 `target/`、`data/` 内文件逐条维护说明；以构建与运行期自动产物为主。

## 10. 常见问题

- **数据库**：先建库，再按顺序执行 `db/init.sql`、`db/rbac.sql`、`db/m3_business.sql`、`db/m4_export.sql`，最后按需执行各 `migrate_*.sql`（顺序一般可按文件名日期；新环境建议全部执行）。
- **端口**：默认 `8081`（见 `application.yml`）。
- **启动**：在项目根目录执行 `./mvnw spring-boot:run` 或 `mvnw.cmd spring-boot:run`（Windows），或 `mvn package` 后 `java -jar target/*.jar`；确保工作目录下模板与 `data/` 路径与配置一致。
- **API 文档**：见 `springdoc` 配置，`/v3/api-docs` 与 Swagger UI 路径以 `application.yml` 为准。

