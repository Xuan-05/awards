<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { http } from "../../api/http";
import type { ApiResponse } from "../../types/api";

type Summary = {
  teamCount: number;
  pendingAuditCount: number;
  approvedCount: number;
};
type Todos = {
  unreadMessageCount: number;
  pendingInvitationCount: number;
  draftRecordCount: number;
  rejectedRecordCount: number;
};

const router = useRouter();
const loading = ref(false);
const summary = ref<Summary | null>(null);

const todos = ref<Todos | null>(null);

async function loadSummary() {
  const resp = await http.get<ApiResponse<Summary>>("/dashboard/summary");
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  summary.value = resp.data.data;
}

async function loadTodos() {
  const resp = await http.get<ApiResponse<Todos>>("/dashboard/todos");
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  todos.value = resp.data.data;
}

async function load() {
  loading.value = true;
  try {
    await Promise.all([loadSummary(), loadTodos()]);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const currentDate = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})
</script>

<template>
  <div class="workbench-page">
    <!-- 欢迎区域 -->
    <div class="welcome-section">
      <div class="welcome-content">
        <h1 class="welcome-title">欢迎回来</h1>
        <p class="welcome-subtitle">今天是 {{ currentDate }}，祝您工作顺利</p>
      </div>
      <button class="quick-create-btn" @click="router.push('/app/records/new')">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        新建填报
      </button>
    </div>

    <!-- 统计卡片 -->
<div class="stats-grid" v-loading="loading">
  <div class="stat-card teams">
    <div class="stat-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    </div>
    <div class="stat-content">
      <!-- 文字 在上 -->
      <span class="stat-label">我的团队</span>
      <!-- 数字 在下 -->
      <span class="stat-value">{{ summary?.teamCount ?? 0 }}</span>
    </div>
  </div>

  <div class="stat-card pending">
    <div class="stat-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12,6 12,12 16,14" />
      </svg>
    </div>
    <div class="stat-content">
      <span class="stat-label">待审核</span>
      <span class="stat-value">{{ summary?.pendingAuditCount ?? 0 }}</span>
    </div>
  </div>

  <div class="stat-card approved">
    <div class="stat-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22,4 12,14.01 9,11.01" />
      </svg>
    </div>
    <div class="stat-content">
      <span class="stat-label">已通过</span>
      <span class="stat-value">{{ summary?.approvedCount ?? 0 }}</span>
    </div>
  </div>
</div>

    <!-- 待办事项 -->
    <div class="todos-section">
      <h2 class="section-title">待办事项</h2>
      <div class="todos-grid">
        <div class="todo-card" @click="router.push('/app/messages')">
          <div class="todo-header">
            <div class="todo-icon messages">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
                />
              </svg>
            </div>
            <span class="todo-badge" v-if="todos?.unreadMessageCount">{{
              todos.unreadMessageCount
            }}</span>
          </div>
          <h4 class="todo-title">未读消息</h4>
          <p class="todo-desc">查看最新的系统通知和团队消息</p>
          <div class="todo-action">
            <span>前往消息中心</span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="9,18 15,12 9,6" />
            </svg>
          </div>
        </div>

        <div class="todo-card" @click="router.push('/app/teams')">
          <div class="todo-header">
            <div class="todo-icon invitation">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="8.5" cy="7" r="4" />
                <line x1="20" y1="8" x2="20" y2="14" />
                <line x1="23" y1="11" x2="17" y2="11" />
              </svg>
            </div>
            <span class="todo-badge" v-if="todos?.pendingInvitationCount">{{
              todos.pendingInvitationCount
            }}</span>
          </div>
          <h4 class="todo-title">待处理邀请</h4>
          <p class="todo-desc">处理团队加入邀请</p>
          <div class="todo-action">
            <span>前往团队页</span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="9,18 15,12 9,6" />
            </svg>
          </div>
        </div>

        <div class="todo-card" @click="router.push('/app/records')">
          <div class="todo-header">
            <div class="todo-icon draft">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"
                />
                <path
                  d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"
                />
              </svg>
            </div>
            <span class="todo-badge" v-if="todos?.draftRecordCount">{{
              todos.draftRecordCount
            }}</span>
          </div>
          <h4 class="todo-title">草稿待提交</h4>
          <p class="todo-desc">完成并提交您的草稿填报</p>
          <div class="todo-action">
            <span>前往填报列表</span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="9,18 15,12 9,6" />
            </svg>
          </div>
        </div>

        <div class="todo-card" @click="router.push('/app/records')">
          <div class="todo-header">
            <div class="todo-icon rejected">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="15" y1="9" x2="9" y2="15" />
                <line x1="9" y1="9" x2="15" y2="15" />
              </svg>
            </div>
            <span class="todo-badge" v-if="todos?.rejectedRecordCount">{{
              todos.rejectedRecordCount
            }}</span>
          </div>
          <h4 class="todo-title">被驳回待修改</h4>
          <p class="todo-desc">修改并重新提交被驳回的填报</p>
          <div class="todo-action">
            <span>前往填报列表</span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="9,18 15,12 9,6" />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="shortcuts-section">
      <h2 class="section-title">快捷入口</h2>
      <div class="shortcuts-grid">
        <div class="shortcut-card" @click="router.push('/app/records/new')">
          <div class="shortcut-icon create">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </div>
          <span class="shortcut-label">新建填报</span>
        </div>

        <div class="shortcut-card" @click="router.push('/app/records')">
          <div class="shortcut-icon records">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
              />
              <polyline points="14,2 14,8 20,8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
            </svg>
          </div>
          <span class="shortcut-label">我的填报</span>
        </div>

        <div class="shortcut-card" @click="router.push('/app/teams')">
          <div class="shortcut-icon teams">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
          <span class="shortcut-label">我的团队</span>
        </div>

        <div class="shortcut-card" @click="router.push('/app/messages')">
          <div class="shortcut-icon messages">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
              />
            </svg>
          </div>
          <span class="shortcut-label">消息中心</span>
        </div>

        <div class="shortcut-card" @click="router.push('/app/profile')">
          <div class="shortcut-icon profile">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <span class="shortcut-label">个人中心</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workbench-page {
  padding: 8px;
}

/* Welcome Section */
.welcome-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  border-radius: var(--apple-radius-lg);
  color: white;
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.welcome-subtitle {
  font-size: 14px;
  margin: 0;
  opacity: 0.9;
}

.quick-create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 20px;
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.quick-create-btn svg {
  width: 18px;
  height: 18px;
}

.quick-create-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px; 
  margin-bottom: 24px;
}

.stat-card {
  position: relative;
  padding: 24px 28px;
  min-height: 100px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  transition: all 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--apple-shadow);
}

.stat-icon {
  width: 50px;
  height:50px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  top: 18px;
  right: 25px; 
}

.stat-icon svg {
  width: 32px;
  height: 32px;
}

.stat-card.teams .stat-icon {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.stat-card.pending .stat-icon {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.stat-card.approved .stat-icon {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}



.stat-label {
  position:absolute;
top:48px;
right:110px;
transform: translateY(-50%);
  font-size: 24px;
  font-weight: 540;
  color: var(--apple-text-secondary);
  white-space: nowrap;
}

.stat-value {
  font-size: 52px;
  font-weight: 600;
  color: var(--apple-text);
  position: absolute;
  left: 48px; 
  bottom: 33px; 
}

/* Todos Section */
.todos-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
}

.todos-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.todo-card {
  padding: 20px 14px;
  min-height: 200px; /* 🔥 高度 > 宽度，竖长矩形 */
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.todo-card:hover {
  border-color: var(--apple-primary);
  transform: translateY(-2px);
  box-shadow: var(--apple-shadow);
}

.todo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.todo-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-icon svg {
  width: 30px;
  height: 30px;
}

.todo-icon.messages {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.todo-icon.invitation {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

.todo-icon.draft {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.todo-icon.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.todo-badge {
  min-width: 20px;
  height: 20px;
  border-radius: 10px;
  background: var(--apple-danger);
  color: white;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
    padding: 0 6px;
  position: absolute;
  top: 25px;
  right: 25px;
  transform: none;
}

.todo-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin:4px;
}

.todo-desc {
  font-size: 15px;
  color: var(--apple-text-secondary);
  margin: 0 0 12px 0;
}

.todo-action {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--apple-primary);
  font-weight: 500;
  margin-top: auto;
  align-items: flex-start;
}

.todo-action svg {
  width: 14px;
  height: 14px;
}

/* Shortcuts Section */
.shortcuts-section {
  margin-bottom: 16px;
}

.shortcuts-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.shortcut-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  cursor: pointer;
  transition: all 0.2s;
}

.shortcut-card:hover {
  background: var(--apple-bg-secondary);
  border-color: var(--apple-primary);
}

.shortcut-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shortcut-icon svg {
  width: 20px;
  height: 20px;
}

.shortcut-icon.create {
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  color: white;
}

.shortcut-icon.records {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.shortcut-icon.teams {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

.shortcut-icon.messages {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.shortcut-icon.profile {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.shortcut-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .todos-grid {
    grid-template-columns: 1fr;
  }

  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
