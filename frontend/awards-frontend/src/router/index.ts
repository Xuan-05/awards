import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '../views/LoginView.vue'
import AppLayout from '../layouts/AppLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import WorkbenchView from '../views/app/WorkbenchView.vue'
import MessagesView from '../views/app/MessagesView.vue'
import ProfileView from '../views/app/ProfileView.vue'
import TeamDetailView from '../views/app/TeamDetailView.vue'
import RecordListView from '../views/app/RecordListView.vue'
import RecordEditView from '../views/app/RecordEditView.vue'
import MyAwardsView from '../views/app/MyAwardsView.vue'

// Reuse existing pages temporarily (will be refined by later todos)
import TeamsView from '../views/TeamsView.vue'
import AuditTasksView from '../views/AuditTasksView.vue'
import DashboardView from '../views/DashboardView.vue'
import ExportsView from '../views/ExportsView.vue'
import DictCategoriesView from '../views/DictCategoriesView.vue'
import DictCompetitionsView from '../views/DictCompetitionsView.vue'
import DictOrganizersView from '../views/DictOrganizersView.vue'
import DictAwardScopesView from '../views/DictAwardScopesView.vue'
import DictAwardLevelsView from '../views/DictAwardLevelsView.vue'
import AdminOrgUsersView from '../views/admin/AdminOrgUsersView.vue'
import AdminTeamsView from '../views/admin/AdminTeamsView.vue'
import AdminRecordDetailView from '../views/admin/AdminRecordDetailView.vue'
import AuditL2View from '../views/admin/AuditL2View.vue'
import AdminReviewersView from '../views/admin/AdminReviewersView.vue'
import MyReviewerScopesView from '../views/admin/MyReviewerScopesView.vue'
import DictClassesView from '../views/admin/DictClassesView.vue'
import AdminLogsView from '../views/admin/AdminLogsView.vue'
import AdminSystemSettingsView from '../views/admin/AdminSystemSettingsView.vue'
import AdminApprovedRecordsView from '../views/admin/AdminApprovedRecordsView.vue'
import { useUserStore } from '../stores/user'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 根路径：统一先进入登录，再由守卫按角色纠偏到默认落地页
    { path: '/', redirect: '/login' },
    // 统一登录页（不需要 auth）
    { path: '/login', component: LoginView, meta: { title: '登录' } },

    {
      path: '/app',
      component: AppLayout,
      meta: { auth: true },
      children: [
        // /app 默认页
        { path: '', redirect: '/app/workbench' },
        { path: 'workbench', component: WorkbenchView, meta: { title: '工作台' } },
        { path: 'teams', component: TeamsView, meta: { title: '我的团队' } },
        { path: 'teams/:id', component: TeamDetailView, meta: { title: '团队详情' } },
        { path: 'records', component: RecordListView, meta: { title: '竞赛填报' } },
        { path: 'my-awards', component: MyAwardsView, meta: { title: '我的获奖' } },
        { path: 'records/new', component: RecordEditView, meta: { title: '新建填报' } },
        { path: 'records/:id', component: RecordEditView, meta: { title: '填报详情/编辑' } },
        { path: 'messages', component: MessagesView, meta: { title: '消息中心' } },
        { path: 'profile', component: ProfileView, meta: { title: '个人中心' } },
      ],
    },

    {
      path: '/admin',
      component: AdminLayout,
      // 管理端路由统一要求具有管理员角色（DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN）
      meta: { auth: true, roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN', 'COMP_REVIEWER_L1', 'COMP_REVIEWER_L2'] },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', component: DashboardView, meta: { title: '控制台', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'profile', component: ProfileView, meta: { title: '个人中心', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'audit/tasks', redirect: '/admin/audit/l1' },
        { path: 'audit/l1', component: AuditTasksView, meta: { title: '一级审核台', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN', 'COMP_REVIEWER_L1'] } },
        { path: 'audit/l2', component: AuditL2View, meta: { title: '二级复审台', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN', 'COMP_REVIEWER_L2'] } },
        { path: 'my-scopes', component: MyReviewerScopesView, meta: { title: '我的竞赛权限', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN', 'COMP_REVIEWER_L1', 'COMP_REVIEWER_L2'] } },
        { path: 'records/approved', component: AdminApprovedRecordsView, meta: { title: '竞赛填报信息', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'reviewers', component: AdminReviewersView, meta: { title: '审核账号管理', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'record/detail/:id', component: AdminRecordDetailView, meta: { title: '填报详情', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN', 'COMP_REVIEWER_L1', 'COMP_REVIEWER_L2'] } },
        { path: 'dicts/competitions', component: DictCompetitionsView, meta: { title: '竞赛字典', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'dicts/categories', component: DictCategoriesView, meta: { title: '竞赛类别库', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'dicts/organizers', component: DictOrganizersView, meta: { title: '主办方库', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'dicts/award-scopes', component: DictAwardScopesView, meta: { title: '获奖范围库', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'dicts/award-levels', component: DictAwardLevelsView, meta: { title: '获奖等级库', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'dicts/classes', component: DictClassesView, meta: { title: '班级字典', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'exports', component: ExportsView, meta: { title: '数据导出', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'org/depts', component: AdminOrgUsersView, meta: { title: '用户组织', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'teams', component: AdminTeamsView, meta: { title: '团队管理', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'messages', component: MessagesView, meta: { title: '消息中心', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'system', component: AdminSystemSettingsView, meta: { title: '系统设置', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] } },
        { path: 'logs', component: AdminLogsView, meta: { title: '日志审计', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] } },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const user = useUserStore()
  // 本路由是否需要登录
  const needAuth = Boolean(to.meta?.auth)
  if (!needAuth) return true

  // 没有 token：直接去登录页
  if (!user.authed) return { path: '/login' }

  // 首次进入：加载 /auth/me，用于后续鉴权与菜单渲染
  if (!user.meLoaded) {
    try {
      await user.fetchMe()
    } catch {
      // token 失效等情况：清理本地并回到登录页
      await user.logout()
      return { path: '/login' }
    }
  }

  // If already logged in, keep user out of /login
  if (to.path === '/login') {
    // 已登录用户访问 /login：按角色强制纠偏到对应端首页
    return { path: user.homePath }
  }

  // Root route: decide landing by role
  if (to.path === '/') {
    // 访问根路径：按角色选择落地端
    return { path: user.homePath }
  }

  // 角色鉴权（路由 meta.roles）
  const needRoles = (to.meta?.roles as string[] | undefined) || []
  if (needRoles.length > 0 && !user.hasAnyRole(...needRoles)) {
    // 无权限访问目标路由：按角色回落到对应端首页
    return { path: user.homePath }
  }

  return true
})

