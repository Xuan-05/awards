<script setup lang="ts">
import AppHeader from '../components/layout/AppHeader.vue'
import AppSidebar, { type MenuItem } from '../components/layout/AppSidebar.vue'

const menu: MenuItem[] = [
  { title: '工作台', path: '/app/workbench' },
  { title: '我的团队', path: '/app/teams' },
  { title: '竞赛填报', path: '/app/records' },
  { title: '我的获奖', path: '/app/my-awards' },
  { title: '消息中心', path: '/app/messages' },
  { title: '个人中心', path: '/app/profile' },
]
</script>

<template>
  <div class="layout">
    <AppHeader />
    <div class="main">
      <div class="sider">
        <AppSidebar base-path="/app" :items="menu" />
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

