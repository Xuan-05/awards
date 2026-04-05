# awards-frontend 目录与文件说明

本文档用于说明 `frontend/awards-frontend` 中各目录与文件职责，帮助快速理解前端结构。

## 1. 项目定位

- 技术栈：Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus + Axios + ECharts。
- 双端路由：`/app`（学生/教师）与 `/admin`（管理员）。
- 核心能力：登录鉴权、消息中心、团队邀请、填报与附件、审核管理、字典管理、系统设置、日志审计、导出。

## 2. 根目录说明

- `package.json`：前端依赖与脚本。
- `package-lock.json`：依赖锁定文件。
- `vite.config.ts`：Vite 配置（插件、`/api` 代理到 `http://localhost:8081`）。
- `tsconfig.json`：TS 工程引用入口。
- `tsconfig.app.json`：应用端 TS 配置。
- `tsconfig.node.json`：Node/Vite 侧 TS 配置。
- `index.html`：Vite 入口 HTML。
- `public/`：静态资源目录。
- `src/`：应用源码目录。
- `node_modules/`：依赖目录（生成物，不逐文件说明）。
- `dist/`：构建产物目录（生成物，不逐文件说明）。

## 3. 构建脚本（`package.json`）

- `npm run dev`：本地开发启动。
- `npm run build`：类型检查 + 生产构建。
- `npm run preview`：本地预览构建产物。

## 4. 静态资源（`public/`）

- `public/favicon.svg`：浏览器标签图标。
- `public/icons.svg`：通用图标资源（项目静态图标集合）。

## 5. 源码结构（`src/`）

以下为 `src/` 下主要文件说明（与仓库内实际文件一致）。

## 5.1 入口与全局

- `src/main.ts`：应用启动入口（注册 Pinia、Router、全局样式等）。
- `src/App.vue`：根组件容器。
- `src/style.css`：全局样式。
- `src/env.d.ts`：Vite 环境变量类型声明。
- `src/auto-imports.d.ts`：自动导入类型声明（生成文件）。
- `src/components.d.ts`：组件自动注册类型声明（生成文件）。

## 5.2 路由与状态

- `src/router/index.ts`：路由表与守卫（登录校验、角色校验、端首页回落）。
- `src/stores/user.ts`：用户状态（token、me、roles、鉴权 getter、logout/fetchMe）。

## 5.3 API 与类型

- `src/api/http.ts`：Axios 封装（Bearer 注入、401 统一跳转登录）。
- `src/types/api.ts`：通用 API 类型（`ApiResponse`、`PageResult`）。
- `src/types/team.ts`：团队相关类型定义。

## 5.4 布局与通用组件

- `src/layouts/AppLayout.vue`：学生/教师端主布局。
- `src/layouts/AdminLayout.vue`：管理员端主布局。
- `src/components/layout/AppHeader.vue`：顶部栏组件。
- `src/components/layout/AppSidebar.vue`：侧边菜单组件。
- `src/components/layout/AppBreadcrumb.vue`：面包屑组件。
- `src/components/HelloWorld.vue`：示例组件（脚手架遗留，可选清理）。

## 5.5 页面（`src/views`）

### A) 登录与通用页

- `src/views/LoginView.vue`：统一登录页（期望端选择 + 角色落地纠偏）。
- `src/views/HomeView.vue`：主页占位/历史页面。
- `src/views/AwardRecordsView.vue`：历史填报页（旧页面，主要由新 app 页面替代）。

### B) 学生/教师端页面（`src/views/app`）

- `src/views/app/WorkbenchView.vue`：工作台（待办、概览、快捷入口）。
- `src/views/app/MessagesView.vue`：消息中心（未读/已读、邀请处理、标记已读）。
- `src/views/app/ProfileView.vue`：个人中心（个人信息、密码修改入口）。
- `src/views/app/TeamDetailView.vue`：团队详情（成员/教师/邀请信息）。
- `src/views/app/RecordListView.vue`：填报列表（查询、提交、撤回、删除）。
- `src/views/app/RecordEditView.vue`：填报编辑与附件管理（上传/预览/下载/删除关联）。

### C) 共享/通用业务页面（根 views）

- `src/views/TeamsView.vue`：我的团队与我的邀请（创建团队、邀请处理）。
- `src/views/AuditTasksView.vue`：审核任务列表（校级审核操作）。
- `src/views/DashboardView.vue`：管理员控制台。
- `src/views/ExportsView.vue`：导出任务页面。

### D) 字典页面（根 views + admin）

- `src/views/DictCompetitionsView.vue`：竞赛字典管理。
- `src/views/DictCategoriesView.vue`：竞赛类别字典管理。
- `src/views/DictOrganizersView.vue`：主办方字典管理。
- `src/views/DictAwardScopesView.vue`：获奖范围字典管理。
- `src/views/DictAwardLevelsView.vue`：获奖等级字典管理。
- `src/views/admin/DictClassesView.vue`：班级字典管理（管理员端）。

### E) 管理端页面（`src/views/admin`）

- `src/views/admin/AdminOrgUsersView.vue`：用户组织管理（学生/教师/管理员列表与筛选）。
- `src/views/admin/AdminLogsView.vue`：日志审计页（操作日志 + 登录日志）。
- `src/views/admin/AdminSystemSettingsView.vue`：系统设置（`sys_config` 编辑 + runtime 只读展示）。

## 5.6 资源（`src/assets`）

- `src/assets/vue.svg`：Vue 默认图标资源。
- `src/assets/vite.svg`：Vite 默认图标资源。

## 6. 关键业务流（前端视角）

- 登录流：`LoginView.vue` -> `stores/user.ts` -> `router/index.ts` 路由守卫。
- 消息/邀请流：`MessagesView.vue`、`TeamsView.vue` 对接 `/api/messages` 与 `/api/team-invitations/*`。
- 填报/附件/审核流：`RecordListView.vue`、`RecordEditView.vue` 对接 `/api/award-records/*` 与 `/api/files/*`。
- 系统配置流：`AdminSystemSettingsView.vue` 对接 `/api/system/configs`、`/api/system/runtime`。
- 日志审计流：`AdminLogsView.vue` 对接 `/api/admin/logs/operate`、`/api/admin/logs/login`。

## 7. 生成物/依赖目录说明（仅概述）

- `node_modules/`：第三方依赖目录，npm 自动管理。
- `dist/`：构建输出目录，`npm run build` 自动生成。

> 这些目录为自动生成内容，不适合逐文件维护说明。

## 8. 环境变量与代理

- 本仓库根目录未提交 `.env*`；开发环境下 API 基址与代理以 `vite.config.ts` 为准（默认将 `/api` 代理到 `http://localhost:8081`）。
- 生产部署时通常由 Web 服务器将前端静态资源与 `/api` 反向代理到后端，与本地 Vite 代理等价。

## 9. 常见问题

- **安装依赖**：`npm ci` 或 `npm install`（建议使用当前 Node LTS，与 Vite 8 / Vue 3.5 兼容）。
- **开发启动**：`npm run dev`，浏览器访问控制台输出的本地 URL。
- **联调后端**：先启动后端（默认 `8081`），再启动前端；若后端端口变更，需同步修改 `vite.config.ts` 中的 `proxy` 目标。
