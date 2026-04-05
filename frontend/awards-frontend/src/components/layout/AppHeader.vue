<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "../../stores/user";

const router = useRouter();
const user = useUserStore();

const displayName = computed(() => {
  const m = user.me;
  if (!m) return "未登录";
  const name = m.realName?.trim();
  if (name) return name;
  return String(m.id);
});

/** 头像展示用首字符（displayName 已为 string，空串时兜底） */
const avatarInitial = computed(() => {
  const s = displayName.value;
  const ch = s.slice(0, 1);
  return ch || "?";
});

const roleLabel = computed(() => {
  if (!user.me?.roles?.length) return ''
  const role = user.me.roles[0]
  switch (role) {
    case 'SYS_ADMIN': return '系统管理员'
    case 'SCHOOL_ADMIN': return '校级管理员'
    case 'DEPT_ADMIN': return '院系管理员'
    case 'CAPTAIN': return '队长'
    default: return role
  }
});

async function logout() {
  await user.logout();
  await router.replace("/login");
}
</script>

<template>
  <header class="app-header">
    <!-- 左侧品牌区 -->
    <div class="header-left">
      <div class="brand">
        <div class="brand-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="brand-text">
          <h1 class="brand-title">竞赛获奖管理系统</h1>
          <span class="brand-subtitle">Awards Management Platform</span>
        </div>
      </div>
    </div>

    <!-- 右侧用户区 -->
    <div class="header-right">
      <!-- 通知图标 -->
      <button class="header-icon-btn" title="消息通知">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
        <span class="badge">3</span>
      </button>

      <!-- 用户信息 -->
      <div class="user-area" v-if="user.me">
        <div class="user-avatar">
          <span>{{ avatarInitial }}</span>
        </div>
        <div class="user-info">
          <span class="user-name">{{ displayName }}</span>
          <span class="user-role" v-if="roleLabel">{{ roleLabel }}</span>
        </div>
        <div class="user-dropdown">
          <button class="dropdown-trigger" @click="router.push(user.isAdmin ? '/admin/profile' : '/app/profile')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </button>
          <button class="dropdown-trigger logout" @click="logout" title="退出登录">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16,17 21,12 16,7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
        </div>
      </div>

      <button v-else class="login-btn" @click="router.push('/login')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
          <polyline points="10,17 15,12 10,7"/>
          <line x1="15" y1="12" x2="3" y2="12"/>
        </svg>
        登录
      </button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 60px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--apple-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-sizing: border-box;
  position: relative;
  z-index: 100;
}

/* 左侧品牌区 */
.header-left {
  display: flex;
  align-items: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.brand-icon svg {
  width: 22px;
  height: 22px;
  color: white;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
  letter-spacing: -0.3px;
}

.brand-subtitle {
  font-size: 11px;
  color: var(--apple-text-tertiary);
  letter-spacing: 0.5px;
}

/* 右侧用户区 */
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.header-icon-btn svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
}

.header-icon-btn:hover {
  background: var(--apple-bg-secondary);
}

.header-icon-btn:hover svg {
  color: var(--apple-text);
}

.header-icon-btn .badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  background: var(--apple-danger);
  color: white;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

/* 用户区域 */
.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  background: var(--apple-bg-secondary);
  border-radius: 24px;
  border: 1px solid var(--apple-border);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--apple-text);
}

.user-role {
  font-size: 10px;
  color: var(--apple-text-secondary);
}

.user-dropdown {
  display: flex;
  gap: 4px;
}

.dropdown-trigger {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.dropdown-trigger svg {
  width: 16px;
  height: 16px;
  color: var(--apple-text-secondary);
}

.dropdown-trigger:hover {
  background: var(--apple-bg-tertiary);
}

.dropdown-trigger:hover svg {
  color: var(--apple-text);
}

.dropdown-trigger.logout:hover svg {
  color: var(--apple-danger);
}

/* 登录按钮 */
.login-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  border-radius: 20px;
  border: none;
  background: var(--apple-primary);
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.login-btn svg {
  width: 18px;
  height: 18px;
}

.login-btn:hover {
  background: #0066d6;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}
</style>
