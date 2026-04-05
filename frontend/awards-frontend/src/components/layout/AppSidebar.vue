<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'

export type MenuItem = {
  title: string
  path: string
  roles?: string[]
  icon?: string
}

const props = defineProps<{
  basePath: string
  items: MenuItem[]
}>()

const user = useUserStore()
const route = useRoute()
const router = useRouter()

const visibleItems = computed(() => {
  return props.items.filter((it) => {
    const roles = it.roles || []
    if (roles.length === 0) return true
    return user.hasAnyRole(...roles)
  })
})

const active = computed(() => {
  const p = route.path
  if (!p.startsWith(props.basePath)) return ''
  const match = visibleItems.value.find((it) => p === it.path || p.startsWith(it.path + '/'))
  return match?.path || ''
})

async function go(path: string) {
  await router.push(path)
}

// 获取菜单图标
function getIcon(item: MenuItem) {
  if (item.icon) return item.icon
  // 根据标题自动匹配图标
  const iconMap: Record<string, string> = {
    '控制台': 'dashboard',
    '审核管理': 'audit',
    '竞赛字典': 'competition',
    '竞赛类别库': 'category',
    '主办方库': 'organizer',
    '获奖范围库': 'scope',
    '获奖等级库': 'level',
    '班级字典': 'class',
    '用户组织': 'users',
    '团队管理': 'team',
    '数据导出': 'export',
    '系统设置': 'settings',
    '日志审计': 'logs',
    '工作台': 'workbench',
    '我的团队': 'team',
    '竞赛填报': 'form',
    '消息中心': 'message',
    '个人中心': 'profile',
  }
  return iconMap[item.title] || 'default'
}
</script>

<template>
  <aside class="app-sidebar">
    <!-- 菜单列表 -->
    <nav class="menu-nav">
      <ul class="menu-list">
        <li 
          v-for="item in visibleItems" 
          :key="item.path"
          class="menu-item"
          :class="{ active: active === item.path }"
          @click="go(item.path)"
        >
          <div class="menu-icon">
            <!-- Dashboard -->
            <svg v-if="getIcon(item) === 'dashboard'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="7" height="7"/>
              <rect x="14" y="3" width="7" height="7"/>
              <rect x="14" y="14" width="7" height="7"/>
              <rect x="3" y="14" width="7" height="7"/>
            </svg>
            <!-- Audit -->
            <svg v-else-if="getIcon(item) === 'audit'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
            <!-- Competition -->
            <svg v-else-if="getIcon(item) === 'competition'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="8" r="7"/>
              <polyline points="8.21,13.89 7,23 12,20 17,23 15.79,13.88"/>
            </svg>
            <!-- Category -->
            <svg v-else-if="getIcon(item) === 'category'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="6" x2="21" y2="6"/>
              <line x1="8" y1="12" x2="21" y2="12"/>
              <line x1="8" y1="18" x2="21" y2="18"/>
              <line x1="3" y1="6" x2="3.01" y2="6"/>
              <line x1="3" y1="12" x2="3.01" y2="12"/>
              <line x1="3" y1="18" x2="3.01" y2="18"/>
            </svg>
            <!-- Organizer -->
            <svg v-else-if="getIcon(item) === 'organizer'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <!-- Scope -->
            <svg v-else-if="getIcon(item) === 'scope'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <circle cx="12" cy="12" r="6"/>
              <circle cx="12" cy="12" r="2"/>
            </svg>
            <!-- Level -->
            <svg v-else-if="getIcon(item) === 'level'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/>
            </svg>
            <!-- Class -->
            <svg v-else-if="getIcon(item) === 'class'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
            </svg>
            <!-- Users -->
            <svg v-else-if="getIcon(item) === 'users'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <!-- Export -->
            <svg v-else-if="getIcon(item) === 'export'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7,10 12,15 17,10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            <!-- Settings -->
            <svg v-else-if="getIcon(item) === 'settings'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            <!-- Logs -->
            <svg v-else-if="getIcon(item) === 'logs'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
            <!-- Workbench -->
            <svg v-else-if="getIcon(item) === 'workbench'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
            </svg>
            <!-- Team -->
            <svg v-else-if="getIcon(item) === 'team'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <!-- Form -->
            <svg v-else-if="getIcon(item) === 'form'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            <!-- Message -->
            <svg v-else-if="getIcon(item) === 'message'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <!-- Profile -->
            <svg v-else-if="getIcon(item) === 'profile'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <!-- Default -->
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
            </svg>
          </div>
          <span class="menu-label">{{ item.title }}</span>
          <div class="menu-indicator" v-if="active === item.path"></div>
        </li>
      </ul>
    </nav>
  </aside>
</template>

<style scoped>
.app-sidebar {
  height: 100%;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid var(--apple-border);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 12px 8px;
}

.menu-nav {
  flex: 1;
  overflow-y: auto;
}

.menu-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--apple-radius);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: var(--apple-text-secondary);
}

.menu-item:hover {
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
}

.menu-item.active {
  background: linear-gradient(135deg, rgba(0, 122, 255, 0.1) 0%, rgba(88, 86, 214, 0.1) 100%);
  color: var(--apple-primary);
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--apple-primary);
  border-radius: 0 3px 3px 0;
}

.menu-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-icon svg {
  width: 100%;
  height: 100%;
}

.menu-label {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.menu-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--apple-primary);
}

/* 滚动条样式 */
.menu-nav::-webkit-scrollbar {
  width: 4px;
}

.menu-nav::-webkit-scrollbar-track {
  background: transparent;
}

.menu-nav::-webkit-scrollbar-thumb {
  background: var(--apple-border);
  border-radius: 2px;
}

.menu-nav::-webkit-scrollbar-thumb:hover {
  background: var(--apple-text-tertiary);
}
</style>

