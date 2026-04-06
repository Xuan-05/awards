<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../api/http'
import * as echarts from 'echarts'
import { useUserStore } from '../stores/user'

type ApiResponse<T> = { code: number; message: string; data: T }
type Summary = { teamCount: number; pendingAuditCount: number; approvedCount: number }
type DeptPending = { deptId: number; pendingCount: number }
type AdminMetrics = { days: string[]; submitCounts: number[]; pendingByDept: DeptPending[] }

const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasAnyRole('DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'))

const loading = ref(false)
const summary = ref<Summary | null>(null)
const metrics = ref<AdminMetrics | null>(null)

const trendEl = ref<HTMLDivElement | null>(null)
const deptEl = ref<HTMLDivElement | null>(null)

let trendChart: echarts.ECharts | null = null
let deptChart: echarts.ECharts | null = null

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<Summary>>('/dashboard/summary')
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    summary.value = resp.data.data

    if (isAdmin.value) {
      const m = await http.get<ApiResponse<AdminMetrics>>('/dashboard/admin/metrics')
      if (m.data.code === 0) metrics.value = m.data.data
    }
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (!metrics.value) return
  if (trendEl.value) {
    trendChart ||= echarts.init(trendEl.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: metrics.value.days },
      yAxis: { type: 'value' },
      series: [{ name: '提交量', type: 'line', data: metrics.value.submitCounts, smooth: true }],
    })
  }
  if (deptEl.value) {
    deptChart ||= echarts.init(deptEl.value)
    deptChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: metrics.value.pendingByDept.map((d) => String(d.deptId)),
        axisLabel: { interval: 0, rotate: 30 },
      },
      yAxis: { type: 'value' },
      series: [{ name: '待审核', type: 'bar', data: metrics.value.pendingByDept.map((d) => d.pendingCount) }],
    })
  }
}

onMounted(async () => {
  if (!userStore.meLoaded) await userStore.fetchMe()
  await load()
  renderCharts()
  window.addEventListener('resize', () => {
    trendChart?.resize()
    deptChart?.resize()
  })
})
</script>

<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">控制台</h1>
      <p class="page-subtitle">数据概览与快捷操作</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-loading="loading">
      <div class="stat-card stat-card-blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.teamCount ?? '-' }}</div>
          <div class="stat-label">团队总数</div>
        </div>
      </div>

      <div class="stat-card stat-card-orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.pendingAuditCount ?? '-' }}</div>
          <div class="stat-label">待审核</div>
        </div>
      </div>

      <div class="stat-card stat-card-green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22,4 12,14.01 9,11.01"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.approvedCount ?? '-' }}</div>
          <div class="stat-label">已通过</div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div v-if="metrics" class="charts-row">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">近 7 天提交趋势</h3>
        </div>
        <div ref="trendEl" class="chart-container" />
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">各院系待审核分布</h3>
          <span class="chart-badge">Top 20</span>
        </div>
        <div ref="deptEl" class="chart-container" />
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="shortcuts-card">
      <h3 class="shortcuts-title">快捷入口</h3>
      <div class="shortcuts-grid">
        <div class="shortcut-item" @click="$router.push('/admin/audit/tasks')">
          <div class="shortcut-icon shortcut-icon-primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
          </div>
          <span class="shortcut-label">审核管理</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/org/depts')">
          <div class="shortcut-icon shortcut-icon-blue">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <span class="shortcut-label">用户组织</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/dicts/competitions')">
          <div class="shortcut-icon shortcut-icon-purple">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
          <span class="shortcut-label">竞赛字典</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/dicts/classes')">
          <div class="shortcut-icon shortcut-icon-cyan">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <span class="shortcut-label">班级字典</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/exports')">
          <div class="shortcut-icon shortcut-icon-green">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7,10 12,15 17,10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </div>
          <span class="shortcut-label">数据导出</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/logs')">
          <div class="shortcut-icon shortcut-icon-orange">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
          </div>
          <span class="shortcut-label">日志审计</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 8px;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Stats Row */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--apple-shadow-md);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.stat-card-blue .stat-icon {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.stat-card-orange .stat-icon {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.stat-card-green .stat-icon {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--apple-text);
  line-height: 1.2;
  letter-spacing: -1px;
}

.stat-label {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin-top: 2px;
}

/* Charts Row */
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.chart-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-secondary);
  padding: 4px 8px;
  border-radius: 6px;
}

.chart-container {
  height: 280px;
}

/* Shortcuts Card */
.shortcuts-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;

  width: 70%;
  max-width: 800px;
  margin: 0 auto;
  box-sizing: border-box;
}
.shortcuts-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
}

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr); 
  gap: 12px;
}

.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 12px;
  border-radius: var(--apple-radius);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  background: transparent;
}

.shortcut-item:hover {
  background: var(--apple-bg-secondary);
}

.shortcut-item:active {
  transform: scale(0.96);
}

.shortcut-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shortcut-icon svg {
  width: 22px;
  height: 22px;
}

.shortcut-icon-primary {
  background: var(--apple-primary);
  color: white;
}

.shortcut-icon-blue {
  background: #5856D6;
  color: white;
}

.shortcut-icon-purple {
  background: #AF52DE;
  color: white;
}

.shortcut-icon-cyan {
  background: #32ADEA;
  color: white;
}

.shortcut-icon-green {
  background: var(--apple-success);
  color: white;
}

.shortcut-icon-orange {
  background: var(--apple-warning);
  color: white;
}

.shortcut-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--apple-text);
  text-align: center;
}

@media (max-width: 1200px) {
  .shortcuts-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: 1fr;
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>

