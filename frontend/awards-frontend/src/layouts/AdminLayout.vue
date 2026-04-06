<script setup lang="ts">
import AppHeader from '../components/layout/AppHeader.vue'
import AppSidebar, { type MenuItem } from '../components/layout/AppSidebar.vue'

const menu: MenuItem[] = [
  { title: '控制台', path: '/admin/dashboard', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '审核管理', path: '/admin/audit/tasks', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '竞赛字典', path: '/admin/dicts/competitions', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '竞赛类别库', path: '/admin/dicts/categories', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '主办方库', path: '/admin/dicts/organizers', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '获奖范围库', path: '/admin/dicts/award-scopes', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '获奖等级库', path: '/admin/dicts/award-levels', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '班级字典', path: '/admin/dicts/classes', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '用户组织', path: '/admin/org/depts', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '团队管理', path: '/admin/teams', roles: ['SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '数据导出', path: '/admin/exports', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] },
  { title: '系统设置', path: '/admin/system', roles: ['SYS_ADMIN', 'SCHOOL_ADMIN'] },
  { title: '日志审计', path: '/admin/logs', roles: ['SYS_ADMIN', 'SCHOOL_ADMIN', 'DEPT_ADMIN'] },
  { title: '个人中心', path: '/admin/profile', roles: ['DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'] },
]
</script>

<template>
  <div class="layout">
    <AppHeader />
    <div class="main">
      <div class="sider">
        <AppSidebar base-path="/admin" :items="menu" />
      </div>
      <div class="content">
        <div class="page">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #ffffff 0%, #f8f8fa 50%, #f5f5f7 100%);
}
.main {
  flex: 1;
  display: flex;
  min-height: 0;
}
.sider {
  width: 200px;
  min-width: 200px;
  padding: 12px 0 12px 12px;
  box-sizing: border-box;
}
.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 0 20px 20px 20px;
}

.page {
  flex: 1;
  min-height: 0;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  box-shadow: var(--apple-shadow);
  padding: 20px;
  overflow: auto;
}
</style>

